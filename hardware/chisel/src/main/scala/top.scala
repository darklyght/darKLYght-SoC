package project

import chisel3._
import chisel3.util._

class top extends Module {
    val io = IO(new Bundle {
        val uart = new UARTSerial()
    })

    val uart = Module(new UARTDriver(CLOCK_FREQUENCY = 100000000, BAUD_RATE = 115200))

    uart.io.uart.rx.serial <> io.uart.rx
    io.uart.tx <> uart.io.uart.tx.serial
    uart.io.uart.tx.data <> uart.io.uart.rx.data
}

object Instance extends App {
    chisel3.Driver.execute(args, () => new top)
}