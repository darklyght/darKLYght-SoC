package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class HDMIInterface extends Bundle {
    val tmds_clock = Output(UInt(10.W))
    val tmds = Output(Vec(3, UInt(10.W)))
}

class HDMIInternal(val AUDIO_BIT_WIDTH: Int, val WIDTH_BITS: Int, val HEIGHT_BITS: Int) extends Bundle {
    val pixel_clock = Input(Clock())
    val audio_clock = Input(Clock())
    val video = Input(Vec(3, UInt(8.W)))
    val audio = Input(Vec(2, UInt(AUDIO_BIT_WIDTH.W)))
    val pos = Output(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS))
    val frame = Output(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS))
    val screen = Output(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS))
}

class HDMIPosition(val WIDTH_BITS: Int, val HEIGHT_BITS: Int) extends Bundle {
    val x = UInt(WIDTH_BITS.W)
    val y = UInt(HEIGHT_BITS.W)
}

class TMDSChannelIO extends Bundle {
    val pixel_clock = Input(Clock())
    val video_data = Input(UInt(8.W))
    val data_island_data = Input(UInt(4.W))
    val control_data = Input(UInt(2.W))
    val mode = Input(UInt(3.W))
    val tmds = Output(UInt(10.W))
}

class HDMIAudioBuffer(val DATA_WIDTH: Int,
                      val ADDR_WIDTH: Int,
                      val ID_WIDTH: Int,
                      val FIFO_DEPTH: Int,
                      val THRESHOLD: Int) extends Module {
    val io = IO(new Bundle {
        val M_AXI = new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH)
        val output = Decoupled(new AXIStream(DATA_WIDTH = 32,
                                             KEEP_EN = false,
                                             LAST_EN = false,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 0))
        val address = Input(UInt(32.W))
        val length = Input(UInt(30.W))
        val start = Input(Bool())
        val repeat = Input(Bool())
        val done = Output(Bool())
    })

    val dma = Module(new AXI4FullToAXIStream(AXIS_DATA_WIDTH = 32,
                                             AXIS_KEEP_EN = false,
                                             AXIS_LAST_EN = false,
                                             AXIS_ID_WIDTH = 0,
                                             AXIS_DEST_WIDTH = 0,
                                             AXIS_USER_WIDTH = 0,
                                             AXI4_DATA_WIDTH = DATA_WIDTH,
                                             AXI4_ADDR_WIDTH = ADDR_WIDTH,
                                             AXI4_ID_WIDTH = ID_WIDTH,
                                             FIFO_DEPTH = FIFO_DEPTH,
                                             THRESHOLD = THRESHOLD))

    dma.io.M_AXI <> io.M_AXI
    dma.io.address <> io.address
    dma.io.length <> io.length
    dma.io.start <> io.start
    dma.io.repeat <> io.repeat
    dma.io.done <> io.done

    io.output <> dma.io.output
}

class HDMIVideoBuffer(val DATA_WIDTH: Int,
                      val ADDR_WIDTH: Int,
                      val ID_WIDTH: Int,
                      val FIFO_DEPTH: Int,
                      val THRESHOLD: Int,
                      val ASYNC_DEPTH: Int) extends Module {
    val io = IO(new Bundle {
        val pixel_clock = Input(Clock())
        val M_AXI = new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH)
        val output = Decoupled(new AXIStream(DATA_WIDTH = 32,
                                             KEEP_EN = false,
                                             LAST_EN = false,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 0))
        val address = Input(UInt(32.W))
        val length = Input(UInt(30.W))
        val start = Input(Bool())
        val repeat = Input(Bool())
        val done = Output(Bool())
    })

    val dma = Module(new AXI4FullToAXIStream(AXIS_DATA_WIDTH = 32,
                                             AXIS_KEEP_EN = false,
                                             AXIS_LAST_EN = false,
                                             AXIS_ID_WIDTH = 0,
                                             AXIS_DEST_WIDTH = 0,
                                             AXIS_USER_WIDTH = 0,
                                             AXI4_DATA_WIDTH = DATA_WIDTH,
                                             AXI4_ADDR_WIDTH = ADDR_WIDTH,
                                             AXI4_ID_WIDTH = ID_WIDTH,
                                             FIFO_DEPTH = FIFO_DEPTH,
                                             THRESHOLD = THRESHOLD))

    val output = Module(new AXIStreamAsyncFIFO(FIFO_DEPTH = 2048,
                                               DATA_WIDTH = 32,
                                               KEEP_EN = false,
                                               LAST_EN = false,
                                               ID_WIDTH = 0,
                                               DEST_WIDTH = 0,
                                               USER_WIDTH = 0))

    dma.io.M_AXI <> io.M_AXI
    dma.io.address <> io.address
    dma.io.length <> io.length
    dma.io.start <> io.start
    dma.io.repeat <> io.repeat
    dma.io.done <> io.done

    io.output <> output.io.deq

    dma.io.output <> output.io.enq

    output.io.enq_clock := clock
    output.io.deq_clock := io.pixel_clock

}

class HDMI(val DATA_DELAY: Int = 1,
           val VIDEO_ID_CODE: Int = 1,
           val DVI_OUTPUT: Boolean,
           val VIDEO_REFRESH_RATE: Double,
           val AUDIO_RATE: Int = 48000,
           val AUDIO_BIT_WIDTH: Int = 24,
           val VENDOR_NAME: Bits = "h4C692059616E6700".U(64.W),
           val PRODUCT_DESCRIPTION: Bits = "h4C692059616E67204C692059616E6700".U(128.W),
           val SOURCE_DEVICE_INFORMATION: Bits = "h00".U(8.W)) extends Module {
    val WIDTH_BITS = if (VIDEO_ID_CODE < 4) 10 else
                     if (VIDEO_ID_CODE == 4) 11 else 12
    val HEIGHT_BITS = if (VIDEO_ID_CODE == 16) 11 else 10
    val MULTIPLIER = if (VIDEO_REFRESH_RATE == 59.94 || VIDEO_REFRESH_RATE == 29.97) 1000.0/1001.0 else 1
    val VIDEO_RATE = if (VIDEO_ID_CODE == 1) 25.2E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 2 || VIDEO_ID_CODE == 3) 27.27E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 4) 74.25E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 16) 148.5E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 17 || VIDEO_ID_CODE == 18) 27E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 19) 74.25E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 34) 74.25E6 * MULTIPLIER else
                     if (VIDEO_ID_CODE == 95 || VIDEO_ID_CODE == 105 || VIDEO_ID_CODE == 97 || VIDEO_ID_CODE == 107) 594E6 * MULTIPLIER else
                     0
    val io = IO(new Bundle {
        val hdmi = new HDMIInterface()
        val internal = new HDMIInternal(AUDIO_BIT_WIDTH, WIDTH_BITS, HEIGHT_BITS)
    })
    
    withClock (io.internal.pixel_clock) {
        val CHANNELS = 3
        val hsync = Wire(Bool())
        val vsync = Wire(Bool())
        
        val sync_start = Wire(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS))
        val sync_size = Wire(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS))
        val invert = Wire(Bool())
        
        VIDEO_ID_CODE match {
            case 1 => {
                io.internal.frame.x := 800.U
                io.internal.frame.y := 525.U
                io.internal.screen.x := 640.U
                io.internal.screen.y := 480.U
                sync_start.x := 16.U
                sync_start.y := 10.U
                sync_size.x := 96.U
                sync_size.y := 2.U
                invert := true.B
            }
            case 2 | 3 => {
                io.internal.frame.x := 858.U
                io.internal.frame.y := 525.U
                io.internal.screen.x := 720.U
                io.internal.screen.y := 480.U
                sync_start.x := 16.U
                sync_start.y := 9.U
                sync_size.x := 62.U
                sync_size.y := 6.U
                invert := true.B
            }
            case 4 => {
                io.internal.frame.x := 1650.U
                io.internal.frame.y := 750.U
                io.internal.screen.x := 1280.U
                io.internal.screen.y := 720.U
                sync_start.x := 110.U
                sync_start.y := 5.U
                sync_size.x := 40.U
                sync_size.y := 5.U
                invert := false.B
            }
            case 16 | 34 => {
                io.internal.frame.x := 2200.U
                io.internal.frame.y := 1125.U
                io.internal.screen.x := 1920.U
                io.internal.screen.y := 1080.U
                sync_start.x := 88.U
                sync_start.y := 4.U
                sync_size.x := 44.U
                sync_size.y := 5.U
                invert := false.B
            }
            case 17 | 18 => {
                io.internal.frame.x := 864.U
                io.internal.frame.y := 625.U
                io.internal.screen.x := 720.U
                io.internal.screen.y := 576.U
                sync_start.x := 12.U
                sync_start.y := 5.U
                sync_size.x := 64.U
                sync_size.y := 5.U
                invert := true.B
            }
            case 19 => {
                io.internal.frame.x := 1980.U
                io.internal.frame.y := 750.U
                io.internal.screen.x := 1280.U
                io.internal.screen.y := 720.U
                sync_start.x := 440.U
                sync_start.y := 5.U
                sync_size.x := 40.U
                sync_size.y := 5.U
                invert := false.B
            }
            case 95 | 105 | 97 | 107 => {
                io.internal.frame.x := 4400.U
                io.internal.frame.y := 2250.U
                io.internal.screen.x := 3840.U
                io.internal.screen.y := 2160.U
                sync_start.x := 176.U
                sync_start.y := 8.U
                sync_size.x := 88.U
                sync_size.y := 10.U
                invert := false.B
            }
        }
        
        val pos_reg = RegInit(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS), 0.U.asTypeOf(new HDMIPosition(WIDTH_BITS, HEIGHT_BITS)))
        val pos_reg_piped = ShiftRegister(pos_reg, DATA_DELAY - 1)
        val pos_reg_piped_delayed = RegNext(pos_reg_piped)
        when (pos_reg.x === io.internal.frame.x - 1.U) {
            pos_reg.x := 0.U
            when (pos_reg.y === io.internal.frame.y - 1.U) {
                pos_reg.y := 0.U
            } .otherwise {
                pos_reg.y := pos_reg.y + 1.U
            }
        } . otherwise {
            pos_reg.x := pos_reg.x + 1.U
        }
        
        io.internal.pos <> pos_reg

        hsync := invert ^ (pos_reg_piped_delayed.x >= io.internal.screen.x + sync_start.x && pos_reg_piped_delayed.x < io.internal.screen.x + sync_start.x + sync_size.x)
        vsync := invert ^ (pos_reg_piped_delayed.y >= io.internal.screen.y + sync_start.y && pos_reg_piped_delayed.y < io.internal.screen.y + sync_start.y + sync_size.y)
        
        val video_data_period = Wire(Bool())
        video_data_period := pos_reg_piped.x < io.internal.screen.x && pos_reg_piped.y < io.internal.screen.y
        
        val reset_int = RegInit(true.B)
        
        val mode_int = Wire(UInt(3.W))
        val video_data_int = Wire(Vec(3, UInt(8.W)))
        val control_data_int = Wire(Vec(3, UInt(2.W)))
        val data_island_data_int = Wire(Vec(3, UInt(4.W)))
        
        if (DVI_OUTPUT) {
            val mode = RegInit(1.U(3.W))
            val video_data = RegInit(VecInit(Seq.fill(3)(0.U(8.W))))
            val control_data = RegInit(VecInit(Seq.fill(3)(0.U(2.W))))
            val data_island_data = RegInit(VecInit(Seq.fill(3)(0.U(4.W))))
            mode_int := mode
            video_data_int := video_data
            control_data_int := control_data
            data_island_data_int := data_island_data
            when (video_data_period) {
                mode := 1.U
            } .otherwise {
                mode := 0.U
            }
            video_data := io.internal.video
            control_data(2) := 0.U
            control_data(1) := 0.U
            control_data(0) := Cat(vsync, hsync)
        } else {
            val video_guard = Wire(Bool())
            val video_preamble = Wire(Bool())
            video_guard := pos_reg_piped.x >= io.internal.frame.x - 2.U && pos_reg_piped.x < io.internal.frame.x && (pos_reg_piped.y === io.internal.frame.y - 1.U || pos_reg_piped.y < io.internal.screen.y)
            video_preamble := pos_reg_piped.x >= io.internal.frame.x - 10.U && pos_reg_piped.x < io.internal.frame.x - 2.U && (pos_reg_piped.y === io.internal.frame.y - 1.U || pos_reg_piped.y < io.internal.screen.y)
            
            val MAX_NUM_PACKETS = (io.internal.frame.x - io.internal.screen.x - 2.U - 8.U - 12.U - 2.U - 2.U - 8.U) >> 5
            val NUM_PACKETS = Mux(MAX_NUM_PACKETS > 18.U, 18.U(5.W), MAX_NUM_PACKETS)
            val data_island_period = Wire(Bool())
            val packet_enable = Wire(Bool())
            
            data_island_period := NUM_PACKETS > 0.U && pos_reg_piped.x >= io.internal.screen.x + 10.U && pos_reg_piped.x < io.internal.screen.x + 10.U + (NUM_PACKETS << 5)
            packet_enable := data_island_period && (pos_reg_piped.x + io.internal.screen.x + 22.U)(4, 0) === 31.U
            
            val data_island_guard = Wire(Bool())
            val data_island_preamble = Wire(Bool())
            data_island_guard := NUM_PACKETS > 0.U && ((pos_reg_piped.x >= io.internal.screen.x + 8.U  && pos_reg_piped.x < io.internal.screen.x + 10.U) || (pos_reg_piped.x >= io.internal.screen.x + 10.U + (NUM_PACKETS << 5) && pos_reg_piped.x < io.internal.screen.x + 10.U + (NUM_PACKETS << 5) + 2.U))
            data_island_preamble := NUM_PACKETS > 0.U && pos_reg_piped.x >= io.internal.screen.x && pos_reg_piped.x < io.internal.screen.x + 8.U
            
            val video_field_end = Wire(Bool())
            video_field_end := pos_reg_piped.x === io.internal.screen.x - 1.U && pos_reg_piped.y === io.internal.screen.y - 1.U
            
            val packet_picker = Module(new PacketPicker(VIDEO_ID_CODE, VIDEO_RATE, AUDIO_BIT_WIDTH, AUDIO_RATE, VENDOR_NAME, PRODUCT_DESCRIPTION, SOURCE_DEVICE_INFORMATION))
            val packet_assembler = Module(new PacketAssembler())
            packet_picker.io.pixel_clock := io.internal.pixel_clock
            packet_picker.io.audio_clock := io.internal.audio_clock
            packet_picker.io.video_field_end := video_field_end
            packet_picker.io.packet_enable := packet_enable
            packet_picker.io.packet_counter := packet_assembler.io.packet_counter
            packet_picker.io.audio := io.internal.audio
            packet_assembler.io.pixel_clock := io.internal.pixel_clock
            packet_assembler.io.data_island_period := data_island_period
            packet_assembler.io.header := packet_picker.io.header
            packet_assembler.io.sub := packet_picker.io.sub
            
            val mode = RegInit(2.U(3.W))
            val video_data = RegInit(VecInit(Seq.fill(3)(0.U(8.W))))
            val control_data = RegInit(VecInit(Seq.fill(3)(0.U(2.W))))
            val data_island_data = RegInit(VecInit(Seq.fill(3)(0.U(4.W))))
            mode_int := mode
            video_data_int := video_data
            control_data_int := control_data
            data_island_data_int := data_island_data
            
            when (data_island_guard) {
                mode := 4.U
            } .elsewhen (data_island_period) {
                mode := 3.U
            } .elsewhen (video_guard) {
                mode := 2.U
            } .elsewhen (video_data_period) {
                mode := 1.U
            } .otherwise {
                mode := 0.U
            }
            video_data := io.internal.video
            control_data(2) := Cat(0.U(1.W), data_island_preamble)
            control_data(1) := Cat(0.U(1.W), video_preamble || data_island_preamble)
            control_data(0) := Cat(vsync, hsync)
            data_island_data(2) := packet_assembler.io.packet_data(8, 5)
            data_island_data(1) := packet_assembler.io.packet_data(4, 1)
            data_island_data(0) := Cat(pos_reg_piped.x =/= 0.U, packet_assembler.io.packet_data(0), vsync, hsync)
        }
        reset_int := false.B

        val tmds_0 = Module(new TMDSChannel(0))
        val tmds_1 = Module(new TMDSChannel(1))
        val tmds_2 = Module(new TMDSChannel(2))
        
        io.hdmi.tmds_clock := "b0000011111".U(10.W)
        io.hdmi.tmds(0) := tmds_0.io.channel.tmds
        io.hdmi.tmds(1) := tmds_1.io.channel.tmds
        io.hdmi.tmds(2) := tmds_2.io.channel.tmds
        
        tmds_0.io.channel.pixel_clock := io.internal.pixel_clock
        tmds_0.io.channel.video_data := video_data_int(0)
        tmds_0.io.channel.data_island_data := data_island_data_int(0)
        tmds_0.io.channel.control_data := control_data_int(0)
        tmds_0.io.channel.mode := mode_int
        tmds_1.io.channel.pixel_clock := io.internal.pixel_clock
        tmds_1.io.channel.video_data := video_data_int(1)
        tmds_1.io.channel.data_island_data := data_island_data_int(1)
        tmds_1.io.channel.control_data := control_data_int(1)
        tmds_1.io.channel.mode := mode_int
        tmds_2.io.channel.pixel_clock := io.internal.pixel_clock
        tmds_2.io.channel.video_data := video_data_int(2)
        tmds_2.io.channel.data_island_data := data_island_data_int(2)
        tmds_2.io.channel.control_data := control_data_int(2)
        tmds_2.io.channel.mode := mode_int
    }
}

class TMDSChannel(val CHANNEL: Int) extends Module {
    val io = IO(new Bundle {
        val channel = new TMDSChannelIO()
    })
    
    withClock (io.channel.pixel_clock) {
        val acc = RegInit(0.S(5.W))
        val q_m = Wire(Vec(9, UInt(1.W)))
        val N1D = Wire(UInt(4.W))
        
        val disparity = RegInit(0.S(4.W))
        val diff = Wire(SInt(4.W))
        val d_out = Wire(UInt(10.W))
        
        N1D := PopCount(io.channel.video_data)
        
        when (N1D > 4.U || (N1D === 4.U && io.channel.video_data(0) === 0.U)) {
            q_m(0) := io.channel.video_data(0)
            for (b <- 0 until 7) {
                q_m(b + 1) := ~(q_m(b) ^ io.channel.video_data(b + 1))
            }
            q_m(8) := 0.U
        } .otherwise {
            q_m(0) := io.channel.video_data(0)
            for (b <- 0 until 7) {
                q_m(b + 1) := q_m(b) ^ io.channel.video_data(b + 1)
            }
            q_m(8) := 1.U
        }
        
        diff := PopCount(q_m.asUInt()(7, 0)).asSInt - 4.S
        
        when (io.channel.mode =/= 1.U) {
            disparity := 0.S
        } .otherwise {
            when (disparity === 0.S || diff === 0.S) {
                when (q_m(8) === 0.U) {
                    disparity := disparity - diff
                } .otherwise {
                    disparity := disparity + diff
                }
            } .elsewhen ((diff(3) === 0.U && disparity(3) === 0.U) || (diff(3) === 1.U && disparity(3) === 1.U)) {
                when (q_m(8) === 1.U) {
                    disparity := disparity + 1.S - diff;
                } .otherwise {
                    disparity := disparity - diff;
                }
            } .otherwise {
                when (q_m(8) === 1.U) {
                    disparity := disparity + diff;
                } .otherwise {
                    disparity := disparity - 1.S + diff;
                }
            }
        }
        
        when (disparity === 0.S || diff === 0.S) {
            when (q_m(8) === 0.U) {
                d_out := Cat(1.U, 0.U, ~q_m(7), ~q_m(6), ~q_m(5), ~q_m(4), ~q_m(3), ~q_m(2), ~q_m(1), ~q_m(0))
            } .otherwise {
                d_out := Cat(0.U, 1.U, q_m(7), q_m(6), q_m(5), q_m(4), q_m(3), q_m(2), q_m(1), q_m(0))
            }
        } .elsewhen ((diff(3) === 0.U && disparity(3) === 0.U) || (diff(3) === 1.U && disparity(3) === 1.U)) {
            d_out := Cat(1.U, q_m(8), ~q_m(7), ~q_m(6), ~q_m(5), ~q_m(4), ~q_m(3), ~q_m(2), ~q_m(1), ~q_m(0))
        } .otherwise {
            d_out := Cat(0.U, q_m(8), q_m(7), q_m(6), q_m(5), q_m(4), q_m(3), q_m(2), q_m(1), q_m(0))
        }
        
        val video_coding = Wire(UInt(10.W))
        video_coding := d_out
        
        val control_coding = Wire(UInt(10.W))
        when (io.channel.control_data === 0.U) {
            control_coding := "b1101010100".U
        } .elsewhen (io.channel.control_data === 1.U) {
            control_coding := "b0010101011".U
        } .elsewhen (io.channel.control_data === 2.U) {
            control_coding := "b0101010100".U
        } .otherwise {
            control_coding := "b1010101011".U
        }
        
        val terc4_coding = Wire(UInt(10.W))
        when (io.channel.data_island_data === 0.U) {
            terc4_coding := "b1010011100".U
        } .elsewhen (io.channel.data_island_data === 1.U) {
            terc4_coding := "b1001100011".U
        } .elsewhen (io.channel.data_island_data === 2.U) {
            terc4_coding := "b1011100100".U
        } .elsewhen (io.channel.data_island_data === 3.U) {
            terc4_coding := "b1011100010".U
        } .elsewhen (io.channel.data_island_data === 4.U) {
            terc4_coding := "b0101110001".U
        } .elsewhen (io.channel.data_island_data === 5.U) {
            terc4_coding := "b0100011110".U
        } .elsewhen (io.channel.data_island_data === 6.U) {
            terc4_coding := "b0110001110".U
        } .elsewhen (io.channel.data_island_data === 7.U) {
            terc4_coding := "b0100111100".U
        } .elsewhen (io.channel.data_island_data === 8.U) {
            terc4_coding := "b1011001100".U
        } .elsewhen (io.channel.data_island_data === 9.U) {
            terc4_coding := "b0100111001".U
        } .elsewhen (io.channel.data_island_data === 10.U) {
            terc4_coding := "b0110011100".U
        } .elsewhen (io.channel.data_island_data === 11.U) {
            terc4_coding := "b1011000110".U
        } .elsewhen (io.channel.data_island_data === 12.U) {
            terc4_coding := "b1010001110".U
        } .elsewhen (io.channel.data_island_data === 13.U) {
            terc4_coding := "b1001110001".U
        } .elsewhen (io.channel.data_island_data === 14.U) {
            terc4_coding := "b0101100011".U
        } .otherwise {
            terc4_coding := "b1011000011".U
        }
        
        val video_guard_band = Wire(UInt(10.W))
        if (CHANNEL == 0 || CHANNEL == 2) {
            video_guard_band := "b1011001100".U
        } else {
            video_guard_band := "b0100110011".U
        }
        
        val data_guard_band = Wire(UInt(10.W))
        if (CHANNEL == 1 || CHANNEL == 2) {
            data_guard_band := "b0100110011".U
        } else {
            when (io.channel.control_data === 0.U) {
                data_guard_band := "b1010001110".U
            } .elsewhen (io.channel.control_data === 1.U) {
                data_guard_band := "b1001110001".U
            } .elsewhen (io.channel.control_data === 2.U) {
                data_guard_band := "b0101100011".U
            } .otherwise {
                data_guard_band := "b1011000011".U
            }
        }
        
        val tmds_reg = RegInit("b1101010100".U(10.W))
        withClock (io.channel.pixel_clock) {
            switch (io.channel.mode) {
                is (0.U) {
                    tmds_reg := control_coding
                }
                is (1.U) {
                    tmds_reg := video_coding
                }
                is (2.U) {
                    tmds_reg := video_guard_band
                }
                is (3.U) {
                    tmds_reg := terc4_coding
                }
                is (4.U) {
                    tmds_reg := data_guard_band
                }
            }
        }
        
        val reset_int = RegInit(true.B)
        
        withClock (io.channel.pixel_clock) {
            reset_int := false.B
        }
        
        io.channel.tmds := tmds_reg
    }
}

class PacketAssembler extends Module {
    val io = IO(new Bundle {
        val pixel_clock = Input(Clock())
        val data_island_period = Input(Bool())
        val header = Input(UInt(24.W))
        val sub = Input(Vec(4, UInt(56.W)))
        val packet_data = Output(UInt(9.W))
        val packet_counter = Output(UInt(5.W))
    })
    
    def next_ecc(ecc: UInt, next_bch_bit: UInt): UInt = {
        return (ecc >> 1) ^ Mux(ecc(0) ^ next_bch_bit.asBool(), "b10000011".U, 0.U)
    }
    
    withClock(io.pixel_clock) {
        val packet_counter_reg = RegInit(0.U(5.W))
        when(io.data_island_period) {
            packet_counter_reg := packet_counter_reg + 1.U
        }
        io.packet_counter := packet_counter_reg
        
        val counter_t2_p0 = Cat(packet_counter_reg, 0.U(1.W))
        val counter_t2_p1 = Cat(packet_counter_reg, 1.U(1.W))
        
        val parity = RegInit(VecInit(Seq.fill(5)(0.U(8.W))))
        
        val bch = Wire(Vec(4, UInt(63.W)))
        for (i <- 0 until 4) {
            bch(i) := Cat(parity(i), io.sub(i))
        }
        val bch4 = Cat(parity(4), io.header)
        
        io.packet_data := Cat(bch(3)(counter_t2_p1), bch(2)(counter_t2_p1), bch(1)(counter_t2_p1), bch(0)(counter_t2_p1), bch(3)(counter_t2_p0), bch(2)(counter_t2_p0), bch(1)(counter_t2_p0), bch(0)(counter_t2_p0), bch4(packet_counter_reg))
        
        val parity_next = Wire(Vec(5, UInt(8.W)))
        val parity_next_next = Wire(Vec(4, UInt(8.W)))
        
        for (i <- 0 until 5) {
            if (i == 4) {
                parity_next(i) := next_ecc(parity(i), io.header(packet_counter_reg))
            } else {
                parity_next(i) := next_ecc(parity(i), io.sub(i)(counter_t2_p0))
                parity_next_next(i) := next_ecc(parity_next(i), io.sub(i)(counter_t2_p1))
            }
        }
        
        when (io.data_island_period) {
            when (packet_counter_reg < 28.U) {
                for (i <- 0 until 4) {
                    parity(i) := parity_next_next(i)
                }
                when (packet_counter_reg < 24.U) {
                    parity(4) := parity_next(4)
                }
            } .elsewhen (packet_counter_reg === 31.U) {
                parity := 0.U.asTypeOf(Vec(5, UInt(8.W)))
            }
        } .otherwise {
            parity := 0.U.asTypeOf(Vec(5, UInt(8.W)))
        }
    }
}

class PacketPicker(val VIDEO_ID_CODE: Int,
                   val VIDEO_RATE: Double,
                   val AUDIO_BIT_WIDTH: Int,
                   val AUDIO_RATE: Int,
                   val VENDOR_NAME: Bits,
                   val PRODUCT_DESCRIPTION: Bits,
                   val SOURCE_DEVICE_INFORMATION: Bits) extends Module {
    val io = IO(new Bundle {
        val pixel_clock = Input(Clock())
        val audio_clock = Input(Clock())
        val video_field_end = Input(Bool())
        val packet_enable = Input(Bool())
        val packet_counter = Input(UInt(5.W))
        val audio = Input(Vec(2, UInt(AUDIO_BIT_WIDTH.W)))
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })
    
    val SAMPLING_FREQUENCY = if (AUDIO_RATE == 32000) "b0011".U else
                             if (AUDIO_RATE == 44100) "b0000".U else
                             if (AUDIO_RATE == 88200) "b1000".U else
                             if (AUDIO_RATE == 176400) "b1100".U else
                             if (AUDIO_RATE == 48000) "b0010".U else
                             if (AUDIO_RATE == 96000) "b1010".U else
                             if (AUDIO_RATE == 192000) "b1110".U else
                             "b1111".U
    val AUDIO_BIT_WIDTH_COMPARATOR = if (AUDIO_BIT_WIDTH < 20) 20 else
                                     if (AUDIO_BIT_WIDTH == 20) 25 else
                                     if (AUDIO_BIT_WIDTH < 24) 24 else
                                     if (AUDIO_BIT_WIDTH == 24) 29 else
                                     -1
    val WORD_LENGTH = (AUDIO_BIT_WIDTH_COMPARATOR - AUDIO_BIT_WIDTH).U(3.W)
    val WORD_LENGTH_LIMIT = if (AUDIO_BIT_WIDTH <= 20) 0.U(1.W) else 1.U(1.W)
    val WORD_LENGTH_PACKET = Cat(WORD_LENGTH(0), WORD_LENGTH(1), WORD_LENGTH(2), WORD_LENGTH_LIMIT)
    
    val audio_int = Wire(Vec(2, UInt(AUDIO_BIT_WIDTH.W)))
    val audio_sample_int = Wire(Bool())
    withClock(io.audio_clock) {
        val audio_reg = RegNext(io.audio)
        val audio_sample = RegInit(false.B)
        audio_int := audio_reg
        audio_sample := ~audio_sample
        audio_sample_int := audio_sample
    }
    
    withClock(io.pixel_clock) {
        val packet_type = RegInit(0.U(8.W))
        val headers = Wire(Vec(255, UInt(24.W)))
        val subs = Wire(Vec(255, Vec(4, UInt(56.W))))
        
        headers := 0.U.asTypeOf(Vec(255, UInt(24.W)))
        subs := 0.U.asTypeOf(Vec(255, Vec(4, UInt(56.W))))
        
        io.header := headers(packet_type)
        io.sub := subs(packet_type)
        
        headers(0) := 0.U
        subs(0) := 0.U.asTypeOf(Vec(4, UInt(56.W)))
        
        val audio_clock_wrap = Wire(Bool())
        val audio_clock = Module(new AudioClockPacket(VIDEO_RATE, AUDIO_RATE))
        audio_clock.io.pixel_clock := io.pixel_clock
        audio_clock.io.audio_clock := io.audio_clock
        audio_clock_wrap := audio_clock.io.audio_clock_wrap
        headers(1) := audio_clock.io.header
        subs(1) := audio_clock.io.sub
        
        val audio_sample_sync = RegInit(VecInit(Seq.fill(2)(false.B)))
        val samples_remaining = RegInit(0.U(2.W))
        val samples_buffer = Reg(Vec(2, Vec(4, Vec(2, UInt(AUDIO_BIT_WIDTH.W)))))
        val samples_buffer_current = RegInit(0.U(1.W))
        val samples_buffer_used = RegInit(false.B)
        val samples_buffer_ready = RegInit(false.B)
        
        audio_sample_sync(1) := audio_sample_int
        audio_sample_sync(0) := audio_sample_sync(1)
        
        when (samples_buffer_used) {
            samples_buffer_used := false.B
            samples_buffer_ready := false.B
        }
        
        when (audio_sample_sync(0) ^ audio_sample_sync(1)) {
            samples_buffer(samples_buffer_current)(samples_remaining) := audio_int
            when (samples_remaining === 3.U) {
                samples_remaining := 0.U
                samples_buffer_ready := true.B
                samples_buffer_current := ~samples_buffer_current
            } .otherwise {
                samples_remaining := samples_remaining + 1.U
            }
        }
        
        val audio_sample_word = Reg(Vec(4, Vec(2, UInt(24.W))))
        val audio_sample_word_present = RegInit(VecInit(Seq.fill(4)(true.B)))
        
        val frame_counter = RegInit(0.U(8.W))
        
        when(io.packet_counter === 31.U && packet_type === 2.U) {
            frame_counter := frame_counter + 4.U
            when (frame_counter >= 192.U) {
                frame_counter := frame_counter - 192.U
            }
        }
        
        val audio_sample = Module(new AudioPacket(AUDIO_BIT_WIDTH = 24, WORD_LENGTH = "b1010".U)) // Bug in Chisel, use direct value for WORD_LENGTH.
        audio_sample.io.frame_counter := frame_counter
        audio_sample.io.valid_bit := 0.U.asTypeOf(Vec(4, UInt(2.W)))
        audio_sample.io.user_data_bit := 0.U.asTypeOf(Vec(4, UInt(2.W)))
        audio_sample.io.audio_sample_word := audio_sample_word
        audio_sample.io.audio_sample_word_present := audio_sample_word_present
        headers(2) := audio_sample.io.header
        subs(2) := audio_sample.io.sub
        
        val video_info = Module(new VideoInfoPacket(VIDEO_ID_CODE = VIDEO_ID_CODE))
        headers(130) := video_info.io.header
        subs(130) := video_info.io.sub
        
        val product_info = Module(new ProductInfoPacket(VENDOR_NAME, PRODUCT_DESCRIPTION, SOURCE_DEVICE_INFORMATION))
        headers(131) := product_info.io.header
        subs(131) := product_info.io.sub
        
        val audio_info = Module(new AudioInfoPacket())
        headers(132) := audio_info.io.header
        subs(132) := audio_info.io.sub
        
        val audio_info_sent = RegInit(false.B)
        val video_info_sent = RegInit(false.B)
        val product_info_sent = RegInit(false.B)
        val audio_clock_wrap_prev = RegInit(false.B)
        
        when (io.video_field_end) {
            audio_info_sent := false.B
            video_info_sent := false.B
            product_info_sent := false.B
            packet_type := 0.U
        } .elsewhen (io.packet_enable) {
            when (audio_clock_wrap_prev ^ audio_clock_wrap) {
                packet_type := 1.U
                audio_clock_wrap_prev := audio_clock_wrap
            } .elsewhen (samples_buffer_ready) {
                packet_type := 2.U
                audio_sample_word := samples_buffer(~samples_buffer_current)
                for (i <- 0 until 4) {
                    audio_sample_word_present(i) := true.B
                }
                samples_buffer_used := true.B
            } .elsewhen (~audio_info_sent) {
                packet_type := 132.U
                audio_info_sent := true.B
            } .elsewhen (~video_info_sent) {
                packet_type := 130.U
                video_info_sent := true.B
            } .elsewhen (~product_info_sent) {
                packet_type := 131.U
                product_info_sent := true.B
            } .otherwise {
                packet_type := 0.U
            }
        }
    }
}

class AudioClockPacket(val VIDEO_RATE: Double, val AUDIO_RATE: Int) extends Module {
    val io = IO(new Bundle {
        val pixel_clock = Input(Clock())
        val audio_clock = Input(Clock())
        val audio_clock_wrap = Output(Bool())
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })
    
    val N: Int = if (AUDIO_RATE % 125 == 0) (16 * AUDIO_RATE / 125) else
                 if (AUDIO_RATE % 225 == 0) (32 * AUDIO_RATE / 225) else
                 (16 * AUDIO_RATE / 125)
    val COUNTER_WIDTH = log2Ceil(N / 128)
    val COUNTER_END = (N / 128 - 1).U(COUNTER_WIDTH.W)
    val STAMP_IDEAL = VIDEO_RATE * N / 128 / AUDIO_RATE
    val STAMP_WIDTH = log2Ceil((STAMP_IDEAL * 1.1).toInt)
    val audio_clock_wrap_int = Wire(Bool())
    
    withClock(io.audio_clock) {
        val counter = RegInit(0.U(COUNTER_WIDTH.W))
        val audio_clock_wrap = RegInit(false.B)
        
        when (counter === COUNTER_END) {
            counter := 0.U
            audio_clock_wrap := ~audio_clock_wrap
        } .otherwise {
            counter := counter + 1.U
        }
        audio_clock_wrap_int := audio_clock_wrap
    }
    
    withClock(io.pixel_clock) {
        val audio_clock_wrap_sync = RegInit(VecInit(Seq.fill(2)(false.B)))
        audio_clock_wrap_sync(0) := audio_clock_wrap_int
        audio_clock_wrap_sync(1) := audio_clock_wrap_sync(0)
        
        val cycle_time_stamp = RegInit(0.U(20.W))
        val stamp_counter = RegInit(0.U(STAMP_WIDTH.W))
        val audio_clock_wrap_out = RegInit(false.B)
        
        when (audio_clock_wrap_sync(0) ^ audio_clock_wrap_sync(1)) {
            stamp_counter := 0.U
            cycle_time_stamp := Cat(0.U((20 - STAMP_WIDTH).W), stamp_counter + 1.U)
            audio_clock_wrap_out := ~audio_clock_wrap_out
        } .otherwise {
            stamp_counter := stamp_counter + 1.U
        }
        
        io.header := 1.U
        for (i <- 0 until 4) {
            io.sub(i) := Cat(N.U(20.W)(7, 0), N.U(20.W)(15, 8), 0.U(4.W), N.U(20.W)(19, 16), cycle_time_stamp(7, 0), cycle_time_stamp(15, 8), 0.U(4.W), cycle_time_stamp(19, 16), 0.U(8.W))
        }
        io.audio_clock_wrap := audio_clock_wrap_out
    }
}

class AudioInfoPacket(val AUDIO_CHANNEL_COUNT: Bits = 1.U(3.W),
                      val CHANNEL_ALLOCATION: Bits = 0.U(8.W),
                      val DOWN_MIX_INHIBITED: Bits = 0.U(1.W),
                      val LEVEL_SHIFT_VALUE: Bits = 0.U(4.W),
                      val LOW_FREQUENCY_EFFECTS_PLAYBACK_LEVEL: Bits = 0.U(2.W)) extends Module {
    val io = IO(new Bundle {
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })
    
    val AUDIO_CODING_TYPE = 0.U(4.W)
    val SAMPLING_FREQUENCY = 0.U(3.W)
    val SAMPLE_SIZE = 0.U(2.W)
    
    val LENGTH = 10.U(5.W)
    val VERSION = 1.U(8.W)
    val TYPE = 4.U(7.W)
    
    io.header := Cat(0.U(3.W), LENGTH, VERSION, 1.U(1.W), TYPE)
    
    val bytes = Wire(Vec(28, UInt(8.W)))
    
    bytes(0) := 1.U(8.W) + ~(io.header(23, 16) + io.header(15, 8) + io.header(7, 0) + bytes(5) + bytes(4) + bytes(3) + bytes(2) + bytes(1))
    bytes(1) := Cat(AUDIO_CODING_TYPE, 0.U(1.W), AUDIO_CHANNEL_COUNT)
    bytes(2) := Cat(0.U(3.W), SAMPLING_FREQUENCY, SAMPLE_SIZE)
    bytes(3) := 0.U
    bytes(4) := CHANNEL_ALLOCATION
    bytes(5) := Cat(DOWN_MIX_INHIBITED, LEVEL_SHIFT_VALUE, 0.U(1.W), LOW_FREQUENCY_EFFECTS_PLAYBACK_LEVEL)
    
    for (b <- 6 until 28) {
        bytes(b) := 0.U
    }
    
    for (i <- 0 until 4) {
        io.sub(i) := Cat(bytes(i * 7 + 6), bytes(i * 7 + 5), bytes(i * 7 + 4), bytes(i * 7 + 3), bytes(i * 7 + 2), bytes(i * 7 + 1), bytes(i * 7 + 0))
    }
}

class AudioPacket(val AUDIO_BIT_WIDTH: Int,
                  val GRADE: Bits = 0.U(1.W),
                  val SAMPLE_WORD_TYPE: Bits = 0.U(1.W),
                  val COPYRIGHT_NOT_ASSERTED: Bits = 1.U(1.W),
                  val PRE_EMPHASIS: Bits = 0.U(3.W),
                  val MODE: Bits = 0.U(2.W),
                  val CATEGORY_CODE: Bits = 0.U(8.W),
                  val SOURCE_NUMBER: Bits = 0.U(4.W),
                  val SAMPLING_FREQUENCY: Bits = 0.U(4.W),
                  val CLOCK_ACCURACY: Bits = 0.U(2.W),
                  val WORD_LENGTH: Bits = 0.U(4.W),
                  val ORIGINAL_SAMPLING_FREQUENCY: Bits = 0.U(4.W),
                  val LAYOUT: Bits = 0.U(1.W)) extends Module {
    val io = IO(new Bundle {
        val frame_counter = Input(UInt(8.W))
        val valid_bit = Input(Vec(4, UInt(2.W)))
        val user_data_bit = Input(Vec(4, UInt(2.W)))
        val audio_sample_word = Input(Vec(4, Vec(2, UInt(AUDIO_BIT_WIDTH.W))))
        val audio_sample_word_present = Input(Vec(4, Bool()))
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })
    
    val CHANNEL_STATUS_LENGTH = 192
    val CHANNEL_LEFT = Wire(UInt(4.W))
    val CHANNEL_RIGHT = Wire(UInt(4.W))
    CHANNEL_LEFT := 1.U
    CHANNEL_RIGHT := 2.U
    val channel_status_left = Wire(UInt(CHANNEL_STATUS_LENGTH.W))
    val channel_status_right = Wire(UInt(CHANNEL_STATUS_LENGTH.W))
    
    channel_status_left := Cat(0.U(152.W), ORIGINAL_SAMPLING_FREQUENCY, WORD_LENGTH, 0.U(2.W), CLOCK_ACCURACY, SAMPLING_FREQUENCY, CHANNEL_LEFT, SOURCE_NUMBER, CATEGORY_CODE, MODE, PRE_EMPHASIS, COPYRIGHT_NOT_ASSERTED, SAMPLE_WORD_TYPE, GRADE)
    channel_status_right := Cat(0.U(152.W), ORIGINAL_SAMPLING_FREQUENCY, WORD_LENGTH, 0.U(2.W), CLOCK_ACCURACY, SAMPLING_FREQUENCY, CHANNEL_RIGHT, SOURCE_NUMBER, CATEGORY_CODE, MODE, PRE_EMPHASIS, COPYRIGHT_NOT_ASSERTED, SAMPLE_WORD_TYPE, GRADE)
    
    val parity_bit = Wire(Vec(4, Vec(2, UInt(1.W))))
    val aligned_frame_counter = Wire(Vec(4, UInt(8.W)))
    
    io.header := Cat(aligned_frame_counter(3) === 0.U && io.audio_sample_word_present(3),
                     aligned_frame_counter(2) === 0.U && io.audio_sample_word_present(2),
                     aligned_frame_counter(1) === 0.U && io.audio_sample_word_present(1),
                     aligned_frame_counter(0) === 0.U && io.audio_sample_word_present(0),
                     0.U(4.W),
                     0.U(3.W),
                     LAYOUT,
                     io.audio_sample_word_present(3) === 1.U,
                     io.audio_sample_word_present(2) === 1.U,
                     io.audio_sample_word_present(1) === 1.U,
                     io.audio_sample_word_present(0) === 1.U,
                     2.U(8.W))
                     
    for (i <- 0 until 4) {
        when (io.frame_counter + i.U >= CHANNEL_STATUS_LENGTH.U) {
            aligned_frame_counter(i) := io.frame_counter + i.U - CHANNEL_STATUS_LENGTH.U
        } .otherwise {
            aligned_frame_counter(i) := io.frame_counter + i.U
        }
        parity_bit(i)(0) := Cat(channel_status_left(aligned_frame_counter(i)), io.user_data_bit(i)(0), io.valid_bit(i)(0), io.audio_sample_word(i)(0)).xorR
        parity_bit(i)(1) := Cat(channel_status_right(aligned_frame_counter(i)), io.user_data_bit(i)(1), io.valid_bit(i)(1), io.audio_sample_word(i)(1)).xorR
        
        when (io.audio_sample_word_present(i)) {
            io.sub(i) := Cat(parity_bit(i)(1), channel_status_right(aligned_frame_counter(i)), io.user_data_bit(i)(1), io.valid_bit(i)(1), parity_bit(i)(0), channel_status_left(aligned_frame_counter(i)), io.user_data_bit(i)(0), io.valid_bit(i)(0), io.audio_sample_word(i)(1).pad(24), io.audio_sample_word(i)(0).pad(24))
        } .otherwise {
            io.sub(i) := 0.U(56.W)
        }
    }
}

class VideoInfoPacket(val VIDEO_FORMAT: Bits = 0.U(2.W),
                      val ACTIVE_FORMAT_INFO_PRESENT: Bits = 0.U(1.W),
                      val BAR_INFO: Bits = 0.U(2.W),
                      val SCAN_INFO: Bits = 0.U(2.W),
                      val COLORIMETRY: Bits = 0.U(2.W),
                      val PICTURE_ASPECT_RATIO: Bits = 0.U(2.W),
                      val ACTIVE_FORMAT_ASPECT_RATIO: Bits = 8.U(4.W),
                      val IT_CONTENT: Bits = 0.U(1.W),
                      val EXTENDED_COLORIMETRY: Bits = 0.U(3.W),
                      val RGB_QUANTIZATION_RANGE: Bits = 0.U(2.W),
                      val NON_UNIFORM_PICTURE_SCALING: Bits = 0.U(2.W),
                      val VIDEO_ID_CODE: Int,
                      val YCC_QUANTIZATION_RANGE: Bits = 0.U(2.W),
                      val CONTENT_TYPE: Bits = 0.U(2.W),
                      val PIXEL_REPETITION: Bits = 0.U(4.W)) extends Module {
    val io = IO(new Bundle {
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })

    val LENGTH = 13.U(5.W)
    val VERSION = 2.U(8.W)
    val TYPE = 2.U(7.W)
    
    io.header := Cat(0.U(3.W), LENGTH, VERSION, 1.U(1.W), TYPE)
    
    val bytes = Wire(Vec(28, UInt(8.W)))
    
    bytes(0) := 1.U(8.W) + ~(io.header(23, 16) + io.header(15, 8) + io.header(7, 0) + bytes(13) + bytes(12) + bytes(11) + bytes(10) + bytes(9) + bytes(8) + bytes(7) + bytes(8) + bytes(7) + bytes(6) + bytes(5) + bytes(4) + bytes(3) + bytes(2) + bytes(1))
    bytes(1) := Cat(0.U(1.W), VIDEO_FORMAT, ACTIVE_FORMAT_INFO_PRESENT, BAR_INFO, SCAN_INFO)
    bytes(2) := Cat(COLORIMETRY, PICTURE_ASPECT_RATIO, ACTIVE_FORMAT_ASPECT_RATIO)
    bytes(3) := Cat(IT_CONTENT, EXTENDED_COLORIMETRY, RGB_QUANTIZATION_RANGE, NON_UNIFORM_PICTURE_SCALING)
    bytes(4) := Cat(0.U, VIDEO_ID_CODE.U(7.W))
    bytes(5) := Cat(YCC_QUANTIZATION_RANGE, CONTENT_TYPE, PIXEL_REPETITION)
    when (BAR_INFO.asUInt() =/= 0.U(2.W)) {
        bytes(6) := 255.U
        bytes(7) := 255.U
        bytes(8) := 0.U
        bytes(9) := 0.U
        bytes(10) := 255.U
        bytes(11) := 255.U
        bytes(12) := 0.U
        bytes(13) := 0.U
    } .otherwise {
        bytes(6) := 0.U
        bytes(7) := 0.U
        bytes(8) := 0.U
        bytes(9) := 0.U
        bytes(10) := 0.U
        bytes(11) := 0.U
        bytes(12) := 0.U
        bytes(13) := 0.U
    }
    for (b <- 14 until 28) {
        bytes(b) := 0.U
    }
    
    for (i <- 0 until 4) {
        io.sub(i) := Cat(bytes(i * 7 + 6), bytes(i * 7 + 5), bytes(i * 7 + 4), bytes(i * 7 + 3), bytes(i * 7 + 2), bytes(i * 7 + 1), bytes(i * 7 + 0))
    }
}

class ProductInfoPacket(val VENDOR_NAME: Bits = "h4C692059616E6700".U(64.W),
                        val PRODUCT_DESCRIPTION: Bits = "h4C692059616E67204C692059616E6700".U(128.W),
                        val SOURCE_DEVICE_INFORMATION: Bits = "h00".U(8.W)) extends Module {
    val io = IO(new Bundle {
        val header = Output(UInt(24.W))
        val sub = Output(Vec(4, UInt(56.W)))
    })
    val LENGTH = 25.U(5.W)
    val VERSION = 1.U(8.W)
    val TYPE = 3.U(7.W)
    
    io.header := Cat(0.U(3.W), LENGTH, VERSION, 1.U(1.W), TYPE)
    
    val bytes = Wire(Vec(28, UInt(8.W)))
    
    bytes(0) := 1.U(8.W) + ~(io.header(23, 16) + io.header(15, 8) + io.header(7, 0) + bytes(25) + bytes(24) + bytes(23) + bytes(22) + bytes(21) + bytes(20) + bytes(19) + bytes(18) + bytes(17) + bytes(16) + bytes(15) + bytes(14) + bytes(13) + bytes(12) + bytes(11) + bytes(10) + bytes(9) + bytes(8) + bytes(7) + bytes(8) + bytes(7) + bytes(6) + bytes(5) + bytes(4) + bytes(3) + bytes(2) + bytes(1))
    
    val vendor_name = Wire(Vec(8, UInt(8.W)))
    val product_description = Wire(Vec(16, UInt(8.W)))
    
    for (i <- 0 until 8) {
        vendor_name(i) := VENDOR_NAME((7 - i + 1) * 8 - 1, (7 - i) * 8)
    }
    for (i <- 0 until 16) {
        product_description(i) := PRODUCT_DESCRIPTION((15 - i + 1) * 8 - 1, (15 - i) * 8)
    }
    
    for (b <- 1 until 9) {
        bytes(b) := Mux(vendor_name(b - 1) === "h30".U, 0.U, vendor_name(b - 1))
    }
    for (b <- 9 until 25) {
        bytes(b) := Mux(product_description(b - 9) === "h30".U, 0.U, product_description(b - 9))
    }
    
    bytes(25) := SOURCE_DEVICE_INFORMATION
    
    for (b <- 26 until 28) {
        bytes(b) := 0.U
    }
    
    for (i <- 0 until 4) {
        io.sub(i) := Cat(bytes(i * 7 + 6), bytes(i * 7 + 5), bytes(i * 7 + 4), bytes(i * 7 + 3), bytes(i * 7 + 2), bytes(i * 7 + 1), bytes(i * 7 + 0))
    }
}
