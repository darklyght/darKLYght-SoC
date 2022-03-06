package project

import chisel3._
import chisel3.util._

class top(val CLOCK_FREQUENCY: Int,
          val UART_CLOCK_FREQUENCY: Int,
          val ETHERNET_CLOCK_FREQUENCY: Int,
          val UART_BAUD_RATE: Int,
          val MAC: String,
          val IP: String,
          val DEBUG_PORT: String,
          val MASTER_PORT: String,
          val SLAVE_PORT: String,
          val GATEWAY: String,
          val SUBNET: String) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTSerial()
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet = new RGMIIPHYDuplex()
        val dram = new AXI4Full(DATA_WIDTH = 32, ADDR_WIDTH = 32, ID_WIDTH = 8)
        val led = Output(UInt(8.W))
        val switch = Input(UInt(8.W))
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

    val network = Module(new Network(MAC = MAC, IP = IP, GATEWAY = GATEWAY, SUBNET = SUBNET))
    val debugger = Module(new UDPToAXI4Full(MAC = MAC, IP = IP, PORT = DEBUG_PORT, DATA_WIDTH = 32, ADDR_WIDTH = 32, ID_WIDTH = 1))
    val remote_m = Module(new UDPToAXI4Full(MAC = MAC, IP = IP, PORT = MASTER_PORT, DATA_WIDTH = 32, ADDR_WIDTH = 32, ID_WIDTH = 1))
    val remote_s = Module(new AXI4FullToUDP(MAC = MAC, IP = IP, PORT = SLAVE_PORT, DATA_WIDTH = 32, ADDR_WIDTH = 32, ID_WIDTH = 1))
    val network_out = Module(new UDPFrameMux(N_INPUTS = 3))
    val axi_xbar = Module(new AXICrossbar(DATA_WIDTH = 32,
                                          ADDR_WIDTH = 32,
                                          ID_WIDTH = 8,
                                          N_MASTERS = 2,
                                          N_SLAVES = 3,
                                          SLAVE_ADDR = Seq(
                                              Seq("h20000000".U, "h40000000".U, "h00001000".U),
                                              Seq("h20000000".U, "h40000000".U, "h00001000".U)
                                          ), 
                                          SLAVE_MASK = Seq(
                                              Seq("hE0000000".U, "hE0000000".U, "hFFFFFFC0".U),
                                              Seq("hE0000000".U, "hE0000000".U, "hFFFFFFC0".U)
                                          ),
                                          ALLOWED = Seq(
                                              Seq(true.B, true.B, true.B),
                                              Seq(true.B, true.B, true.B)
                                          )))
    val REG_FILE_DATA_WIDTH = 32
    val REG_FILE_ADDR_WIDTH = 6
    val REG_FILE_ADDR_WIDTH_EFF = REG_FILE_ADDR_WIDTH - log2Ceil(REG_FILE_DATA_WIDTH / 8)
    val register_file = Module(new AXIRegisterFile(DATA_WIDTH = REG_FILE_DATA_WIDTH, ADDR_WIDTH = REG_FILE_ADDR_WIDTH, ID_WIDTH = 8))
    for (r <- 0 until math.pow(2, REG_FILE_ADDR_WIDTH_EFF).toInt) {
        register_file.io.input(r) := 0.U(REG_FILE_DATA_WIDTH.W)
    }
    register_file.io.input(1) := Cat(0.U(24.W), io.switch)
    io.led := register_file.io.output(0)(7, 0)
    remote_s.io.network.mac := Cat(register_file.io.output(2), register_file.io.output(3)(31, 16))
    remote_s.io.network.ip := register_file.io.output(4)
    remote_s.io.network.port := register_file.io.output(5)(15, 0)

    val err_slave = Module(new ErrSlave(DATA_WIDTH = 32, ADDR_WIDTH = 32, ID_WIDTH = 8))

    network.clock := clock
    network.reset := reset_sync.io.output.asBool
    network.io.ethernet_clock := io.ethernet_clock
    network.io.ethernet_clock_90 := io.ethernet_clock_90
    network.io.ethernet_reset := ethernet_reset_sync.io.output.asBool
    network.io.ethernet <> io.ethernet

    network_out.io.inputs(0) <> debugger.io.output
    network_out.io.input_headers(0) <> debugger.io.output_header
    network_out.io.inputs(1) <> remote_m.io.output
    network_out.io.input_headers(1) <> remote_m.io.output_header
    network_out.io.inputs(2) <> remote_s.io.output
    network_out.io.input_headers(2) <> remote_s.io.output_header

    network.io.tx_input <> network_out.io.output
    network.io.tx_header <> network_out.io.output_header

    debugger.io.M_AXI <> axi_xbar.io.S_AXI(0)
    remote_m.io.M_AXI <> axi_xbar.io.S_AXI(1)
    axi_xbar.io.S_AXI(0).aw.bits.id := 0.U
    axi_xbar.io.S_AXI(0).ar.bits.id := 0.U
    axi_xbar.io.S_AXI(1).aw.bits.id := 1.U
    axi_xbar.io.S_AXI(1).ar.bits.id := 1.U
    axi_xbar.io.M_AXI(0) <> io.dram
    axi_xbar.io.M_AXI(1) <> remote_s.io.S_AXI
    axi_xbar.io.M_AXI(2) <> register_file.io.S_AXI
    axi_xbar.io.M_AXI(3) <> err_slave.io.S_AXI
    debugger.io.input <> network.io.rx_output
    debugger.io.input_header <> network.io.rx_header
    remote_m.io.input <> network.io.rx_output
    remote_m.io.input_header <> network.io.rx_header
    remote_s.io.input <> network.io.rx_output
    remote_s.io.input_header <> network.io.rx_header
    network.io.rx_output.ready := debugger.io.input.ready && remote_m.io.input.ready && remote_s.io.input.ready
    network.io.rx_header.ready := debugger.io.input_header.ready && remote_m.io.input_header.ready && remote_s.io.input.ready

    // Loopback
    // network.io.tx_input <> network.io.rx_output
    // network.io.tx_input.valid := network.io.rx_output.valid
    // network.io.tx_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    // network.io.tx_header.bits.ip.ethernet.dst_mac := network.io.rx_header.bits.ip.ethernet.src_mac
    // network.io.tx_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W) //network.io.rx_header.bits.ip.ethernet.ethernet_type
    // network.io.tx_header.bits.ip.src_ip := IP.U(32.W)
    // network.io.tx_header.bits.ip.dst_ip := network.io.rx_header.bits.ip.src_ip
    // network.io.tx_header.bits.ip.version := 4.U(4.W) //network.io.rx_header.bits.ip.version
    // network.io.tx_header.bits.ip.ihl := 5.U(4.W) //network.io.rx_header.bits.ip.ihl
    // network.io.tx_header.bits.ip.dscp := 0.U(6.W) //network.io.rx_header.bits.ip.dscp
    // network.io.tx_header.bits.ip.ecn := 0.U(2.W) //network.io.rx_header.bits.ip.ecn
    // network.io.tx_header.bits.ip.length := 20.U(16.W) + network.io.rx_header.bits.length //network.io.rx_header.bits.ip.length
    // network.io.tx_header.bits.ip.id := 0.U(16.W) //network.io.rx_header.bits.ip.id
    // network.io.tx_header.bits.ip.flags := 2.U(3.W) //network.io.rx_header.bits.ip.flags
    // network.io.tx_header.bits.ip.fragment_offset := 0.U(13.W) //network.io.rx_header.bits.ip.fragment_offset
    // network.io.tx_header.bits.ip.ttl := 64.U (16.W) //network.io.rx_header.bits.ip.ttl
    // network.io.tx_header.bits.ip.protocol := 17.U(8.W) //network.io.rx_header.bits.ip.protocol
    // network.io.tx_header.bits.ip.checksum := 0.U
    // network.io.tx_header.bits.dst_port := network.io.rx_header.bits.src_port
    // network.io.tx_header.bits.src_port := network.io.rx_header.bits.dst_port
    // network.io.tx_header.bits.length := network.io.rx_header.bits.length
    // network.io.tx_header.bits.checksum := 0.U
    // network.io.tx_header.valid := network.io.rx_header.valid && network.io.rx_header.bits.ip.ethernet.ethernet_type === "h0800".U(16.W)
    // network.io.rx_header.ready := network.io.tx_header.ready
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
                                                                  DEBUG_PORT = "h4D2",
                                                                  MASTER_PORT = "h4D3",
                                                                  SLAVE_PORT = "h4D4",
                                                                  GATEWAY = "hC0A80101",
                                                                  SUBNET = "hFFFFFFFF"))))
    // (new chisel3.stage.ChiselStage).execute(
    //     Array("-X", "mverilog", "--target-dir", "../src/hdl"),
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new top(CLOCK_FREQUENCY = 100000000,
    //                                                               UART_CLOCK_FREQUENCY = 117966903,
    //                                                               ETHERNET_CLOCK_FREQUENCY = 125000000,
    //                                                               UART_BAUD_RATE = 29491200,
    //                                                               MAC = "h000000000000",
    //                                                               IP = "h7F000080",
    //                                                               DEBUG_PORT = "h4D2",
    //                                                               MASTER_PORT = "h4D3",
    //                                                               SLAVE_PORT = "h4D4",
    //                                                               GATEWAY = "h7F000001",
    //                                                               SUBNET = "hFFFFFFFF"))))
}