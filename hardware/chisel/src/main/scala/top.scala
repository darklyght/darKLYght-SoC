package project

import chisel3._
import chisel3.util._

class top(val CLOCK_FREQUENCY: Int, val UART_CLOCK_FREQUENCY: Int) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTSerial()
    })

    val reset_sync = Module(new SyncDebouncer[Reset](CLOCK_FREQUENCY = CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, Reset()))
    val uart_reset_sync = Module(new SyncDebouncer[Reset](CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, Reset()))
    reset_sync.io.input := false.B
    uart_reset_sync.io.input := false.B

    val uart = Module(new UARTDriver(CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, BAUD_RATE = 115200))
    uart.clock := clock
    uart.reset := reset_sync.io.output
    uart.io.uart_clock := io.uart_clock
    uart.io.uart_reset := uart_reset_sync.io.output

    uart.io.uart.rx.serial <> io.uart.rx
    io.uart.tx <> uart.io.uart.tx.serial
    uart.io.uart.tx.data <> uart.io.uart.rx.data
}

object Instance extends App {
    chisel3.Driver.execute(args, () => new top(100000000, 117966903))
}