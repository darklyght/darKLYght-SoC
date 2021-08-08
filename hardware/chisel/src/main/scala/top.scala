package project

import chisel3._
import chisel3.util._

class top(val CLOCK_FREQUENCY: Int, val UART_CLOCK_FREQUENCY: Int, val ETHERNET_CLOCK_FREQUENCY: Int) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTSerial()
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet = new RGMIIPHYDuplex()
    })

    val reset_sync = Module(new SyncDebouncer[Reset](CLOCK_FREQUENCY = CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, Reset()))
    reset_sync.io.input := false.B
    val uart_reset_sync = Module(new SyncDebouncer[Reset](CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, Reset()))
    uart_reset_sync.io.input := false.B
    val ethernet_reset_sync = Module(new SyncDebouncer[Reset](CLOCK_FREQUENCY = ETHERNET_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, Reset()))
    ethernet_reset_sync.io.input := false.B

    val uart = Module(new UARTDriver(CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, BAUD_RATE = 115200 * 256))
    uart.clock := io.uart_clock
    uart.reset := uart_reset_sync.io.output
    uart.io.uart_clock := io.uart_clock

    uart.io.uart.rx.serial <> io.uart.rx
    io.uart.tx <> uart.io.uart.tx.serial
    uart.io.uart.tx.data <> uart.io.uart.rx.data

    val network = Module (new Network())
    network.clock := clock
    network.reset := reset_sync.io.output
    network.io.ethernet_clock := io.ethernet_clock
    network.io.ethernet_clock_90 := io.ethernet_clock_90
    network.io.ethernet_reset := ethernet_reset_sync.io.output
    network.io.ethernet <> io.ethernet
    network.io.tx_input <> network.io.rx_output
    network.io.tx_header <> network.io.rx_header
}

object Instance extends App {
    chisel3.Driver.execute(args, () => new top(100000000, 117966903, 125000000))
}