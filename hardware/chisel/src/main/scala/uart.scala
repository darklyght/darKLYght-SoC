package project

import chisel3._
import chisel3.util._

class UARTSerial extends Bundle {
    val rx = Input(UInt(1.W))
    val tx = Output(UInt(1.W))
}

class UARTHalf extends Bundle {
    val serial = Input(UInt(1.W))
    val data = Decoupled(UInt(8.W))
}

class UARTInterface extends Bundle {
    val rx = new UARTHalf()
    val tx = Flipped(new UARTHalf())
}

class UARTDriver(val CLOCK_FREQUENCY: Int, val BAUD_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val uart = new UARTInterface()
    })

    val rx = Module(new UARTReceiver(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))
    val tx = Module(new UARTTransmitter(CLOCK_FREQUENCY = CLOCK_FREQUENCY, BAUD_RATE = BAUD_RATE))

    rx.io.rx.serial := RegNext(io.uart.rx.serial)
    io.uart.rx.data <> rx.io.rx.data
    io.uart.tx.serial := RegNext(tx.io.tx.serial)
    tx.io.tx.data <> io.uart.tx.data
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
    io.rx.data.bits := shift(8, 1)
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
        shift := Cat(1.U(1.W), io.tx.data.bits, 0.U(1.W))
    } .elsewhen (symbol && started) {
        bit_counter := bit_counter - 1.U
        shift := Cat(1.U(1.W), shift(9, 1))
    }
    
    io.tx.data.ready := ~started
    io.tx.serial := shift(0)
}