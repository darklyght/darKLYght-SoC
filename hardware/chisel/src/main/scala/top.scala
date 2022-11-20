package project

import chisel3._
import chisel3.util._

abstract class Parameters {
    def CLOCK_FREQUENCY: Int
    def UART_CLOCK_FREQUENCY: Int
    def ETHERNET_CLOCK_FREQUENCY: Int
    def HDMI_PIXEL_CLOCK_FREQUENCY: Int
    def CPU_CLOCK_FREQUENCY: Int
    def UART_BAUD_RATE: Int
    def MAC: String
    def IP: String
    def DEBUG_PORT: String
    def MASTER_PORT: String
    def SLAVE_PORT: String
    def GATEWAY: String
    def SUBNET: String
}

object Synthesis extends Parameters {
    val CLOCK_FREQUENCY = 100000000
    val UART_CLOCK_FREQUENCY = 117966903
    val ETHERNET_CLOCK_FREQUENCY = 125000000
    val HDMI_PIXEL_CLOCK_FREQUENCY = 148500000
    val CPU_CLOCK_FREQUENCY = 50000000
    val UART_BAUD_RATE = 115200
    val MAC = "h000000000002"
    val IP = "hC0A80180"
    val DEBUG_PORT = "h4D2"
    val MASTER_PORT = "h4D3"
    val SLAVE_PORT = "h4D4"
    val GATEWAY = "hC0A80101"
    val SUBNET = "hFFFFFFFF"
}

object Simulation extends Parameters {
    val CLOCK_FREQUENCY = 100000000
    val UART_CLOCK_FREQUENCY = 117966903
    val ETHERNET_CLOCK_FREQUENCY = 125000000
    val HDMI_PIXEL_CLOCK_FREQUENCY = 148500000
    val CPU_CLOCK_FREQUENCY = 50000000
    val UART_BAUD_RATE = 29491200
    val MAC = "h000000000000"
    val IP = "h7F000080"
    val DEBUG_PORT = "h4D2"
    val MASTER_PORT = "h4D3"
    val SLAVE_PORT = "h4D4"
    val GATEWAY = "h7F000001"
    val SUBNET = "hFFFFFFFF"
}

class top(val params: Parameters) extends Module {
    val io = IO(new Bundle {
        val uart_clock = Input(Clock())
        val uart = new UARTSerial()
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet = new RGMIIPHYDuplex()
        val dram = new AXI4Full(DATA_WIDTH = 128, ADDR_WIDTH = 32, ID_WIDTH = 8)
        val led = Output(UInt(8.W))
        val switch = Input(UInt(8.W))
        val hdmi_pixel_clock = Input(Clock())
        val hdmi_audio_clock = Input(Clock())
        val hdmi = new HDMIInterface()
        val cpu_clock = Input(Clock())
    })

    val cpu_reset_out = Wire(Reset())
    val reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    reset_sync.reset := false.B
    reset_sync.io.input := reset.asUInt | cpu_reset_out.asUInt
    val uart_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.UART_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    uart_reset_sync.reset := false.B
    uart_reset_sync.io.input := reset.asUInt | cpu_reset_out.asUInt
    val ethernet_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.ETHERNET_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    ethernet_reset_sync.reset := false.B
    ethernet_reset_sync.io.input := reset.asUInt | cpu_reset_out.asUInt
    val hdmi_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.HDMI_PIXEL_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    hdmi_reset_sync.reset := false.B
    hdmi_reset_sync.io.input := reset.asUInt | cpu_reset_out.asUInt
    val cache_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.CPU_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    cache_reset_sync.reset := false.B
    cache_reset_sync.io.input := reset.asUInt | cpu_reset_out.asUInt
    val cpu_reset_sync = Module(new SyncDebouncer(CLOCK_FREQUENCY = params.CPU_CLOCK_FREQUENCY, SAMPLE_FREQUENCY = 100, WIDTH = 1))
    cpu_reset_sync.reset := false.B
    cpu_reset_sync.io.input := reset.asUInt

    val cpu = Module(new VexRiscv)
    val icache = Module(new Cache(ADDR_WIDTH = 32,
                                  DATA_WIDTH = 32,
                                  N_WAYS = 2,
                                  LINE_INDEX_WIDTH = 10,
                                  WORD_INDEX_WIDTH = 3,
                                  AXI4_ADDR_WIDTH = 32,
                                  AXI4_DATA_WIDTH = 128,
                                  AXI4_ID_WIDTH = 1,
                                  CACHEABLE = "h80000000".U,
                                  CACHEABLE_MASK = "hE0000000".U))
    val dcache = Module(new Cache(ADDR_WIDTH = 32,
                                  DATA_WIDTH = 32,
                                  N_WAYS = 2,
                                  LINE_INDEX_WIDTH = 10,
                                  WORD_INDEX_WIDTH = 3,
                                  AXI4_ADDR_WIDTH = 32,
                                  AXI4_DATA_WIDTH = 128,
                                  AXI4_ID_WIDTH = 1,
                                  CACHEABLE = "h80000000".U,
                                  CACHEABLE_MASK = "hE0000000".U))
    val icache_fifo = Module(new AXI4FullAsyncFIFO(FIFO_DEPTH = 16,
                                                   DATA_WIDTH = 128,
                                                   ADDR_WIDTH = 32,
                                                   ID_WIDTH = 1))
    val dcache_fifo = Module(new AXI4FullAsyncFIFO(FIFO_DEPTH = 16,
                                                   DATA_WIDTH = 128,
                                                   ADDR_WIDTH = 32,
                                                   ID_WIDTH = 1))
    val uart = Module(new UARTDriver(CLOCK_FREQUENCY = params.UART_CLOCK_FREQUENCY, BAUD_RATE = params.UART_BAUD_RATE))
    uart.reset := uart_reset_sync.io.output.asBool
    val uart_axi = Module(new UARTAXI(DATA_WIDTH = 128, ADDR_WIDTH = 32, ID_WIDTH = 8))
    uart_axi.reset := reset_sync.io.output.asBool
    val network = Module(new Network(MAC = params.MAC, IP = params.IP, GATEWAY = params.GATEWAY, SUBNET = params.SUBNET))
    network.reset := reset_sync.io.output.asBool
    val debugger = Module(new UDPToAXI4Full(MAC = params.MAC, IP = params.IP, PORT = params.DEBUG_PORT, DATA_WIDTH = 128, ADDR_WIDTH = 32, ID_WIDTH = 1))
    debugger.reset := reset_sync.io.output.asBool
    val REG_FILE_DATA_WIDTH = 128
    val REG_FILE_ADDR_WIDTH = 6
    val REG_FILE_ADDR_WIDTH_EFF = REG_FILE_ADDR_WIDTH - log2Ceil(REG_FILE_DATA_WIDTH / 8)
    val register_file = Module(new AXIRegisterFile(DATA_WIDTH = REG_FILE_DATA_WIDTH, ADDR_WIDTH = REG_FILE_ADDR_WIDTH, ID_WIDTH = 8))
    for (r <- 0 until math.pow(2, REG_FILE_ADDR_WIDTH_EFF).toInt) {
        register_file.io.input(r) := 0.U(REG_FILE_DATA_WIDTH.W)
    }
    register_file.reset := reset_sync.io.output.asBool
    val hdmi = Module(new HDMI(DATA_DELAY = 8,
                               VIDEO_ID_CODE = 16,
                               DVI_OUTPUT = false,
                               VIDEO_REFRESH_RATE = 60,
                               AUDIO_RATE = 48000,
                               AUDIO_BIT_WIDTH = 24))
    hdmi.reset := reset_sync.io.output.asBool
    val hdmi_audio = Module(new HDMIAudioBuffer(DATA_WIDTH = 128,
                                                ADDR_WIDTH = 32,
                                                ID_WIDTH = 1,
                                                FIFO_DEPTH = 512,
                                                THRESHOLD = 128))
    hdmi_audio.reset := reset_sync.io.output.asBool
    val axi_xbar = Module(new AXICrossbar(DATA_WIDTH = 128,
                                          ADDR_WIDTH = 32,
                                          ID_WIDTH = 8,
                                          N_MASTERS = 4,
                                          N_SLAVES = 3,
                                          SLAVE_ADDR = Seq(
                                              Seq("h80000000".U, "h60000000".U, "h40000000".U),
                                              Seq("h80000000".U, "h60000000".U, "h40000000".U),
                                              Seq("h80000000".U, "h60000000".U, "h40000000".U),
                                              Seq("h80000000".U, "h60000000".U, "h40000000".U)
                                          ), 
                                          SLAVE_MASK = Seq(
                                              Seq("hE0000000".U, "hE0000000".U, "hE0000000".U),
                                              Seq("hE0000000".U, "hE0000000".U, "hE0000000".U),
                                              Seq("hE0000000".U, "hE0000000".U, "hE0000000".U),
                                              Seq("hE0000000".U, "hE0000000".U, "hE0000000".U)
                                          ),
                                          ALLOWED = Seq(
                                              Seq(true.B, true.B, true.B),
                                              Seq(true.B, true.B, true.B),
                                              Seq(true.B, true.B, true.B),
                                              Seq(true.B, true.B, true.B)
                                          )))
    axi_xbar.reset := reset_sync.io.output.asBool
    val err_slave = Module(new ErrSlave(DATA_WIDTH = 128, ADDR_WIDTH = 32, ID_WIDTH = 8))
    err_slave.reset := reset_sync.io.output.asBool

    cpu_reset_out := cpu.io.debug_resetOut

    cpu.io.iBus_cmd_ready := icache.io.frontend.request.ready
    cpu.io.iBus_rsp_valid := icache.io.frontend.response.valid
    cpu.io.iBus_rsp_payload_error := icache.io.frontend.response.bits.error
    cpu.io.iBus_rsp_payload_inst := icache.io.frontend.response.bits.data
    cpu.io.timerInterrupt := register_file.io.output(2)(0)
    cpu.io.externalInterrupt := register_file.io.output(2)(1)
    cpu.io.softwareInterrupt := register_file.io.output(2)(2)
    cpu.io.debug_bus_cmd_valid := false.B
    cpu.io.debug_bus_cmd_payload_wr := false.B
    cpu.io.debug_bus_cmd_payload_address := 0.U
    cpu.io.debug_bus_cmd_payload_data := 0.U
    cpu.io.dBus_cmd_ready := dcache.io.frontend.request.ready
    cpu.io.dBus_rsp_ready := dcache.io.frontend.response.valid
    cpu.io.dBus_rsp_error := dcache.io.frontend.response.bits.error
    cpu.io.dBus_rsp_data := dcache.io.frontend.response.bits.data
    cpu.io.clk := io.cpu_clock
    cpu.io.reset := cache_reset_sync.io.output.asBool
    cpu.io.debugReset := cpu_reset_sync.io.output.asBool

    icache.clock := io.cpu_clock
    icache.reset := cache_reset_sync.io.output.asBool
    icache.io.frontend.request.valid := cpu.io.iBus_cmd_valid
    icache.io.frontend.request.bits.write := false.B
    icache.io.frontend.request.bits.address := cpu.io.iBus_cmd_payload_pc
    icache.io.frontend.request.bits.data := 0.U
    icache.io.frontend.request.bits.size := 0.U

    dcache.clock := io.cpu_clock
    dcache.reset := cache_reset_sync.io.output.asBool
    dcache.io.frontend.request.valid := cpu.io.dBus_cmd_valid
    dcache.io.frontend.request.bits.write := cpu.io.dBus_cmd_payload_wr
    dcache.io.frontend.request.bits.address := cpu.io.dBus_cmd_payload_address
    dcache.io.frontend.request.bits.data := cpu.io.dBus_cmd_payload_data
    dcache.io.frontend.request.bits.size := cpu.io.dBus_cmd_payload_size

    icache_fifo.io.S_AXI_clock := io.cpu_clock
    icache_fifo.io.M_AXI_clock := clock
    dcache_fifo.io.S_AXI_clock := io.cpu_clock
    dcache_fifo.io.M_AXI_clock := clock

    icache_fifo.reset := reset_sync.io.output.asBool || cache_reset_sync.io.output.asBool
    dcache_fifo.reset := reset_sync.io.output.asBool || cache_reset_sync.io.output.asBool
    icache.io.backend <> icache_fifo.io.S_AXI
    dcache.io.backend <> dcache_fifo.io.S_AXI

    uart.io.uart_clock := io.uart_clock

    uart.io.uart.rx.serial <> io.uart.rx
    io.uart.tx <> uart.io.uart.tx.serial
    uart_axi.io.tx <> uart.io.uart.tx.data
    uart_axi.io.rx <> uart.io.uart.rx.data

    network.io.ethernet_clock := io.ethernet_clock
    network.io.ethernet_clock_90 := io.ethernet_clock_90
    network.io.ethernet_reset := ethernet_reset_sync.io.output.asBool
    network.io.ethernet <> io.ethernet

    network.io.tx_input <> debugger.io.output
    network.io.tx_header <> debugger.io.output_header
    debugger.io.input <> network.io.rx_output
    debugger.io.input_header <> network.io.rx_header

    debugger.io.M_AXI <> axi_xbar.io.S_AXI(0)
    hdmi_audio.io.M_AXI <> axi_xbar.io.S_AXI(1)
    icache_fifo.io.M_AXI <> axi_xbar.io.S_AXI(2)
    dcache_fifo.io.M_AXI <> axi_xbar.io.S_AXI(3)
    axi_xbar.io.S_AXI(0).aw.bits.id := 0.U
    axi_xbar.io.S_AXI(0).ar.bits.id := 0.U
    axi_xbar.io.S_AXI(1).aw.bits.id := 1.U
    axi_xbar.io.S_AXI(1).ar.bits.id := 1.U
    axi_xbar.io.S_AXI(2).aw.bits.id := 2.U
    axi_xbar.io.S_AXI(2).ar.bits.id := 2.U
    axi_xbar.io.S_AXI(3).aw.bits.id := 3.U
    axi_xbar.io.S_AXI(3).ar.bits.id := 3.U
    axi_xbar.io.M_AXI(0) <> io.dram
    axi_xbar.io.M_AXI(1) <> register_file.io.S_AXI
    axi_xbar.io.M_AXI(2) <> uart_axi.io.S_AXI
    axi_xbar.io.M_AXI(3) <> err_slave.io.S_AXI

    register_file.io.input(0) := Cat(0.U(71.W), hdmi_audio.io.done, 0.U(24.W), io.switch)
    io.led := register_file.io.output(1)(7, 0)
    hdmi_audio.io.address := register_file.io.output(1)(95, 64)
    hdmi_audio.io.length := register_file.io.output(1)(61, 32)
    hdmi_audio.io.start := register_file.io.output(1)(63)
    hdmi_audio.io.repeat := register_file.io.output(1)(62)

    hdmi.io.internal.pixel_clock := io.hdmi_pixel_clock
    hdmi.io.internal.audio_clock := io.hdmi_audio_clock
    hdmi.io.internal.video(0) := hdmi.io.internal.pos.x(7, 0)
    hdmi.io.internal.video(1) := hdmi.io.internal.pos.y(7, 0)
    hdmi.io.internal.video(2) := hdmi.io.internal.pos.x(7, 0) + hdmi.io.internal.pos.y(7, 0)

    val audio_clock_sync = RegInit(VecInit(Seq.fill(3)(false.B)))
    audio_clock_sync(0) := io.hdmi_audio_clock.asBool
    audio_clock_sync(1) := audio_clock_sync(0)
    audio_clock_sync(2) := audio_clock_sync(1)

    hdmi.io.internal.audio(0) := Mux(hdmi_audio.io.output.valid, Cat(0.U(8.W), hdmi_audio.io.output.bits.tdata(15, 0)), 0.U)
    hdmi.io.internal.audio(1) := Mux(hdmi_audio.io.output.valid, Cat(0.U(8.W), hdmi_audio.io.output.bits.tdata(31, 16)), 0.U)
    hdmi_audio.io.output.ready := audio_clock_sync(1) & ~audio_clock_sync(2)

    io.hdmi <> hdmi.io.hdmi
}

object Instance extends App {
    (new chisel3.stage.ChiselStage).execute(
       Array("-X", "mverilog", "--target-dir", "../src/hdl"),
       Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new top(Simulation))))
}