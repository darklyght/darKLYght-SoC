package project

import chisel3._
import chisel3.util._

class top(val CLOCK_FREQUENCY: Int,
          val UART_CLOCK_FREQUENCY: Int,
          val ETHERNET_CLOCK_FREQUENCY: Int,
          val UART_BAUD_RATE: Int,
          val MAC: String,
          val IP: String,
          val GATEWAY: String,
          val SUBNET: String) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTSerial()
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet = new RGMIIPHYDuplex()
    })

    val reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    reset_sync.reset := false.B
    reset_sync.io.input := reset.asUInt
    val uart_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    uart_reset_sync.reset := false.B
    uart_reset_sync.io.input := reset.asUInt
    val ethernet_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = ETHERNET_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    ethernet_reset_sync.reset := false.B
    ethernet_reset_sync.io.input := reset.asUInt

    val uart = Module(new UARTDriver(CLOCK_FREQUENCY = UART_CLOCK_FREQUENCY, BAUD_RATE = UART_BAUD_RATE))
    uart.clock := io.uart_clock
    uart.reset := uart_reset_sync.io.output.asBool
    uart.io.uart_clock := io.uart_clock

    uart.io.uart.rx.serial <> io.uart.rx
    io.uart.tx <> uart.io.uart.tx.serial
    uart.io.uart.tx.data <> uart.io.uart.rx.data

    val network = Module (new Network(MAC = MAC, IP = IP, GATEWAY = GATEWAY, SUBNET = SUBNET))

    network.clock := clock
    network.reset := reset_sync.io.output.asBool
    network.io.ethernet_clock := io.ethernet_clock
    network.io.ethernet_clock_90 := io.ethernet_clock_90
    network.io.ethernet_reset := ethernet_reset_sync.io.output.asBool
    network.io.ethernet <> io.ethernet
    network.io.tx_input <> network.io.rx_output
    network.io.tx_input.valid := network.io.rx_output.valid
    network.io.tx_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    network.io.tx_header.bits.ip.ethernet.dst_mac := network.io.rx_header.bits.ip.ethernet.src_mac
    network.io.tx_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W) //network.io.rx_header.bits.ip.ethernet.ethernet_type
    network.io.tx_header.bits.ip.src_ip := IP.U(32.W)
    network.io.tx_header.bits.ip.dst_ip := network.io.rx_header.bits.ip.src_ip
    network.io.tx_header.bits.ip.version := 4.U(4.W) //network.io.rx_header.bits.ip.version
    network.io.tx_header.bits.ip.ihl := 5.U(4.W) //network.io.rx_header.bits.ip.ihl
    network.io.tx_header.bits.ip.dscp := 0.U(6.W) //network.io.rx_header.bits.ip.dscp
    network.io.tx_header.bits.ip.ecn := 0.U(2.W) //network.io.rx_header.bits.ip.ecn
    network.io.tx_header.bits.ip.length := 20.U(16.W) + network.io.rx_header.bits.length //network.io.rx_header.bits.ip.length
    network.io.tx_header.bits.ip.id := 0.U(16.W) //network.io.rx_header.bits.ip.id
    network.io.tx_header.bits.ip.flags := 2.U(3.W) //network.io.rx_header.bits.ip.flags
    network.io.tx_header.bits.ip.fragment_offset := 0.U(13.W) //network.io.rx_header.bits.ip.fragment_offset
    network.io.tx_header.bits.ip.ttl := 64.U (16.W) //network.io.rx_header.bits.ip.ttl
    network.io.tx_header.bits.ip.protocol := 17.U(8.W) //network.io.rx_header.bits.ip.protocol
    network.io.tx_header.bits.ip.checksum := 0.U
    network.io.tx_header.bits.dst_port := network.io.rx_header.bits.src_port
    network.io.tx_header.bits.src_port := network.io.rx_header.bits.dst_port
    network.io.tx_header.bits.length := network.io.rx_header.bits.length
    network.io.tx_header.bits.checksum := 0.U
    network.io.tx_header.valid := network.io.rx_header.valid && network.io.rx_header.bits.ip.ethernet.ethernet_type === "h0800".U(16.W)
    network.io.rx_header.ready := network.io.tx_header.ready
}

object Instance extends App {
    (new chisel3.stage.ChiselStage).execute(
        Array("-X", "mverilog", "--target-dir", "../src/hdl"),
        Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new top(CLOCK_FREQUENCY = 100000000,
                                                                  UART_CLOCK_FREQUENCY = 117966903,
                                                                  ETHERNET_CLOCK_FREQUENCY = 125000000,
                                                                  UART_BAUD_RATE = 115200,
                                                                  MAC = "h000000000002",
                                                                  IP = "hC0A80180",
                                                                  GATEWAY = "hC0A80101",
                                                                  SUBNET = "hFFFFFFFF")))
    // (new chisel3.stage.ChiselStage).execute(
    //     Array("-X", "mverilog", "--target-dir", "../src/hdl"),
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new top(CLOCK_FREQUENCY = 100000000,
    //                                                               UART_CLOCK_FREQUENCY = 117966903,
    //                                                               ETHERNET_CLOCK_FREQUENCY = 125000000,
    //                                                               UART_BAUD_RATE = 29491200,
    //                                                               MAC = "h000000000000",
    //                                                               IP = "h7F000080",
    //                                                               GATEWAY = "h7F000001",
    //                                                               SUBNET = "hFFFFFFFF")))
    )
}