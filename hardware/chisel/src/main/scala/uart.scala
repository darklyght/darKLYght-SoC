package project

import chisel3._
import chisel3.util._

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

class UARTDriver(val CLOCK_FREQUENCY: Int, val BAUD_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart_reset = Input(Reset())
        val uart = new UARTInterface()
    })

    val rx = Module(new UARTReceiver(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))
    val tx = Module(new UARTTransmitter(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))

    val rx_fifo = Module(new AXIStreamAsyncFIFO(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = false,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 0))
    val tx_fifo = Module(new AXIStreamAsyncFIFO(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = false,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 0))                                       

    rx.clock := io.uart_clock
    tx.clock := io.uart_clock
    rx_fifo.io.enq_clock := io.uart_clock
    rx_fifo.io.deq_clock := clock
    rx_fifo.io.enq_reset := io.uart_reset
    rx_fifo.io.deq_reset := reset
    tx_fifo.io.enq_clock := clock
    tx_fifo.io.deq_clock := io.uart_clock
    tx_fifo.io.enq_reset := reset
    tx_fifo.io.deq_reset := io.uart_reset

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