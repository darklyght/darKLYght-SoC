package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class UARTSerial extends Bundle {
    val rx = Input(UInt(1.W))
    val tx = Output(UInt(1.W))
}

class UARTHalf extends Bundle {
    val serial = Input(UInt(1.W))
    val data = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                       KEEP_EN = false,
                                       LAST_EN = false,
                                       ID_WIDTH = 0,
                                       DEST_WIDTH = 0,
                                       USER_WIDTH = 0))
}

class UARTInterface extends Bundle {
    val rx = new UARTHalf()
    val tx = Flipped(new UARTHalf())
}

class UARTAXI(val DATA_WIDTH: Int, val ADDR_WIDTH: Int, val ID_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val S_AXI = Flipped(new AXI4Full(DATA_WIDTH = DATA_WIDTH, ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
        val tx = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                         KEEP_EN = false,
                                         LAST_EN = false,
                                         ID_WIDTH = 0,
                                         DEST_WIDTH = 0,
                                         USER_WIDTH = 0))
        val rx = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = false,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 0)))
    })

    object StateRead extends ChiselEnum {
        val sIdle, sReadResp = Value
    }
    
    object StateWrite extends ChiselEnum {
        val sIdle, sWriteData, sWriteResp = Value
    }

    val read_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val write_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val read_state = RegInit(StateRead.sIdle)
    val write_state = RegInit(StateWrite.sIdle)
    val read_pointer = RegInit(0.U(8.W))
    val write_pointer = RegInit(0.U(8.W))
    val read_count = RegInit(0.U(log2Ceil(1024).W))

    val read_addr_word = Wire(UInt(2.W))
    val write_addr_word = Wire(UInt(2.W))
    read_addr_word := read_addr.addr(log2Ceil(DATA_WIDTH / 8) + 1, log2Ceil(DATA_WIDTH / 8))
    write_addr_word := write_addr.addr(log2Ceil(DATA_WIDTH / 8) + 1, log2Ceil(DATA_WIDTH / 8))

    val rx_queue = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                  KEEP_EN = false,
                                                  LAST_EN = false,
                                                  ID_WIDTH = 0,
                                                  DEST_WIDTH = 0,
                                                  USER_WIDTH = 0), 1024))
    
    val tx_queue = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                  KEEP_EN = false,
                                                  LAST_EN = false,
                                                  ID_WIDTH = 0,
                                                  DEST_WIDTH = 0,
                                                  USER_WIDTH = 0), 1024))

    rx_queue.io.enq <> io.rx
    tx_queue.io.deq <> io.tx

    rx_queue.io.deq.ready := read_state === StateRead.sReadResp && read_addr_word === 0.U
    tx_queue.io.enq.bits.tdata := io.S_AXI.w.bits.data(7, 0)
    tx_queue.io.enq.valid := io.S_AXI.w.valid && write_addr_word === 2.U

    io.S_AXI.ar.ready := read_state === StateRead.sIdle
    io.S_AXI.r.bits.id := read_addr.id
    when (read_addr_word === 1.U) {
        io.S_AXI.r.bits.data := Cat(rx_queue.io.deq.valid, rx_queue.io.count.asTypeOf(UInt(31.W)))
    } .elsewhen (read_addr_word === 3.U) {
        io.S_AXI.r.bits.data := Cat(tx_queue.io.enq.ready, tx_queue.io.count.asTypeOf(UInt(31.W)))
    } otherwise {
        io.S_AXI.r.bits.data := rx_queue.io.deq.bits.tdata.asTypeOf(UInt(32.W))
    }
    io.S_AXI.r.bits.resp := (read_addr_word === 0.U && (read_count === 0.U || read_pointer > read_count)) || (read_addr_word =/= 0.U && read_addr_word =/= 1.U && read_addr_word =/= 3.U)
    io.S_AXI.r.bits.last := read_pointer === read_addr.len
    io.S_AXI.r.valid := read_state === StateRead.sReadResp

    io.S_AXI.aw.ready := write_state === StateWrite.sIdle
    io.S_AXI.w.ready := tx_queue.io.enq.ready
    io.S_AXI.b.bits.id := write_addr.id
    io.S_AXI.b.bits.resp := write_addr_word =/= 2.U
    io.S_AXI.b.valid := write_state === StateWrite.sWriteResp

    switch (read_state) {
        is (StateRead.sIdle) {
            when (io.S_AXI.ar.fire()) {
                read_addr := io.S_AXI.ar.bits
                read_state := StateRead.sReadResp
                read_pointer := 0.U
                read_count := rx_queue.io.count
            }
        }
        is (StateRead.sReadResp) {
            when (io.S_AXI.r.fire()) {
                when (read_pointer === read_addr.len) {
                    read_state := StateRead.sIdle
                } .otherwise {
                    read_pointer := read_pointer + 1.U
                }
            }
        }
    }

    switch (write_state) {
        is (StateWrite.sIdle) {
            when (io.S_AXI.aw.fire()) {
                write_addr := io.S_AXI.aw.bits
                write_state := StateWrite.sWriteData
                write_pointer := 0.U
            }
        }
        is (StateWrite.sWriteData) {
            when (io.S_AXI.w.fire()) {
                when (write_pointer === write_addr.len) {
                    write_state := StateWrite.sWriteResp
                } .otherwise {
                    write_pointer := write_pointer + 1.U
                }
            }
        }
        is (StateWrite.sWriteResp) {
            when (io.S_AXI.b.fire()) {
                write_state := StateWrite.sIdle
            }
        }
    }
}

class UARTDriver(val CLOCK_FREQUENCY: Int, val BAUD_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTInterface()
    })

    val rx = Module(new UARTReceiver(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))
    val tx = Module(new UARTTransmitter(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))

    val rx_fifo = Module(new AXIStreamAsyncFIFO(FIFO_DEPTH = 16,
                                                DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = false,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 0))
    val tx_fifo = Module(new AXIStreamAsyncFIFO(FIFO_DEPTH = 16,
                                                DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = false,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 0))                                       

    rx.clock := io.uart_clock
    tx.clock := io.uart_clock
    rx_fifo.io.enq_clock := io.uart_clock
    rx_fifo.io.deq_clock := clock
    tx_fifo.io.enq_clock := clock
    tx_fifo.io.deq_clock := io.uart_clock

    withClock(io.uart_clock) {
        rx.io.rx.serial := RegNext(RegNext(io.uart.rx.serial))
        io.uart.tx.serial := RegNext(RegNext(tx.io.tx.serial))
    }
    io.uart.rx.data <> rx_fifo.io.deq
    rx_fifo.io.enq <> rx.io.rx.data
    tx_fifo.io.enq <> io.uart.tx.data
    tx.io.tx.data <> tx_fifo.io.deq
}

class UARTReceiver(val CLOCK_FREQUENCY: Int, val BAUD_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val rx = new UARTHalf()
    })

    val COUNT = CLOCK_FREQUENCY / BAUD_RATE;
    val WIDTH = log2Ceil(COUNT)
    
    val sample_counter = RegInit(0.U(WIDTH.W))
    val bit_counter = RegInit(0.U(4.W))
    val shift = RegInit(0.U(10.W))
    val valid = RegInit(false.B)
    val symbol = Wire(Bool())
    val sample = Wire(Bool())
    val started = Wire(Bool())
    val start = Wire(Bool())
    
    symbol := sample_counter === (COUNT - 1).U
    sample := sample_counter === (COUNT / 2).U
    started := bit_counter =/= 0.U
    start := io.rx.serial === 0.U && ~started
    
    when (start || symbol) {
        sample_counter := 0.U
    } .otherwise {
        sample_counter := sample_counter + 1.U
    }
    
    when (start) {
        bit_counter := 10.U
    } .elsewhen (symbol && started) {
        bit_counter := bit_counter - 1.U
    }
    
    when (sample && started) {
        shift := Cat(io.rx.serial, shift(9, 1))
    }
    
    when (bit_counter === 1.U && symbol) {
        valid := true.B
    } .elsewhen (io.rx.data.ready) {
        valid := false.B
    }
    
    io.rx.data.valid := valid && ~started
    io.rx.data.bits.tdata := shift(8, 1)
}

class UARTTransmitter(val CLOCK_FREQUENCY: Int, val BAUD_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Flipped(new UARTHalf())
    })
    
    val COUNT = CLOCK_FREQUENCY / BAUD_RATE;
    val WIDTH = log2Ceil(COUNT)

    val sample_counter = RegInit(0.U(WIDTH.W))
    val bit_counter = RegInit(0.U(4.W))
    val shift = RegInit(1.U(10.W))
    val symbol = Wire(Bool())
    val started = Wire(Bool())
    val start = Wire(Bool())
    
    symbol := sample_counter === (COUNT - 1).U
    started := bit_counter =/= 0.U
    start := ~started && io.tx.data.valid
    
    when (start || symbol) {
        sample_counter := 0.U
    } .otherwise {
        sample_counter := sample_counter + 1.U
    }
    
    when (start) {
        bit_counter := 10.U
        shift := Cat(1.U(1.W), io.tx.data.bits.tdata, 0.U(1.W))
    } .elsewhen (symbol && started) {
        bit_counter := bit_counter - 1.U
        shift := Cat(1.U(1.W), shift(9, 1))
    }
    
    io.tx.data.ready := ~started
    io.tx.serial := shift(0)
}