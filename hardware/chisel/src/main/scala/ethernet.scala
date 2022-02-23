package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class RGMIIPHYSimplex extends Bundle {
    val clock = Clock()
    val data = UInt(4.W)
    val control = UInt(1.W)
}

class RGMIIPHYDuplex extends Bundle {
    val rx = Input(new RGMIIPHYSimplex())
    val tx = Output(new RGMIIPHYSimplex())
}

class GMIIMACTx extends Bundle {
    val clock = Input(Clock())
    val clock_en = Input(Bool())
    val reset = Input(Reset())
    val data = Output(UInt(8.W))
    val data_valid = Output(Bool())
    val error = Output(Bool())
}

class GMIIMACRx extends Bundle {
    val clock = Input(Clock())
    val clock_en = Input(Bool())
    val reset = Input(Reset())
    val data = Input(UInt(8.W))
    val data_valid = Input(Bool())
    val error = Input(Bool())
}

class GMIIMACDuplex extends Bundle {
    val rx = new GMIIMACRx()
    val tx = new GMIIMACTx()
}

class GMIIStatus(val DIRECTION: String) extends Bundle {
    val start_packet = Output(Bool())
    val error_underflow = if (DIRECTION == "TRANSMIT") Some(Output(Bool())) else None
    val error_bad_frame = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_bad_fcs = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class EthernetFrameHeader extends Bundle {
    val dst_mac = UInt(48.W)
    val src_mac = UInt(48.W)
    val ethernet_type = UInt(16.W)
}

class EthernetFrameStatus(val DIRECTION: String) extends Bundle {
    val busy = Output(Bool())
    val error_incomplete_packet = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class EthernetFrameMux(val N_INPUTS: Int) extends Module {
    val io = IO(new Bundle {
        val input_headers = Flipped(Vec(N_INPUTS, Decoupled(new EthernetFrameHeader())))
        val inputs = Flipped(Vec(N_INPUTS, Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                                   KEEP_EN = false,
                                                                   LAST_EN = true,
                                                                   ID_WIDTH = 0,
                                                                   DEST_WIDTH = 0,
                                                                   USER_WIDTH = 1))))
        val output_header = Decoupled(new EthernetFrameHeader())
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
    })

    val arbiter = Module(new LockingArbiter(N_INPUTS = N_INPUTS,
                                     ROUND_ROBIN = false,
                                     BLOCKING = true,
                                     RELEASE = true))
                                     
    for (i <- 0 until N_INPUTS) {
        arbiter.io.request(i) := io.input_headers(i).valid
        arbiter.io.acknowledge(i) := io.inputs(i).bits.tlast.get
        io.input_headers(i).ready := arbiter.io.chosen_oh(i) & io.output_header.ready
        io.inputs(i).ready := arbiter.io.chosen_oh(i) & io.output.ready
    }

    io.output_header.valid := io.input_headers(arbiter.io.chosen).valid
    io.output_header.bits := io.input_headers(arbiter.io.chosen).bits

    io.output.valid := io.inputs(arbiter.io.chosen).valid
    io.output.bits := io.inputs(arbiter.io.chosen).bits
}

class EthernetFrame extends Module {
    val io = IO(new Bundle {
        val tx_header = Flipped(Decoupled(new EthernetFrameHeader()))
        val tx_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                      KEEP_EN = false,
                                                      LAST_EN = true,
                                                      ID_WIDTH = 0,
                                                      DEST_WIDTH = 0,
                                                      USER_WIDTH = 1)))
        val tx_status = new EthernetFrameStatus(DIRECTION = "TRANSMIT")
        val tx_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                               KEEP_EN = false,
                                               LAST_EN = true,
                                               ID_WIDTH = 0,
                                               DEST_WIDTH = 0,
                                               USER_WIDTH = 1))
        val rx_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                      KEEP_EN = false,
                                                      LAST_EN = true,
                                                      ID_WIDTH = 0,
                                                      DEST_WIDTH = 0,
                                                      USER_WIDTH = 1)))
        val rx_header = Decoupled(new EthernetFrameHeader())
        val rx_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                               KEEP_EN = false,
                                               LAST_EN = true,
                                               ID_WIDTH = 0,
                                               DEST_WIDTH = 0,
                                               USER_WIDTH = 1))
        val rx_status = new EthernetFrameStatus(DIRECTION = "RECEIVE")
    })
    val tx = Module(new EthernetFrameTx())
    val rx = Module(new EthernetFrameRx())

    tx.io.header <> io.tx_header
    tx.io.input <> io.tx_input
    tx.io.status <> io.tx_status
    tx.io.output <> io.tx_output

    rx.io.input <> io.rx_input
    rx.io.header <> io.rx_header
    rx.io.output <> io.rx_output
    rx.io.status <> io.rx_status
}

class EthernetFrameTx extends Module {
    val io = IO(new Bundle {
        val header = Flipped(Decoupled(new EthernetFrameHeader()))
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val status = new EthernetFrameStatus(DIRECTION = "TRANSMIT")
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
    })
    val header = Reg(new EthernetFrameHeader())
    val input_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1), 16))
    val frame_pointer = RegInit(0.U(4.W))
    val busy = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload = Value
    }
    val state = RegInit(State.sIdle)

    io.header.ready := state === State.sIdle
    io.input <> input_fifo.io.enq
    io.status.busy := busy

    input_fifo.io.deq.ready := state === State.sPayload && io.output.ready

    when (io.header.fire()) {
        header := io.header.bits
    }

    when (state === State.sHeader) {
        when (frame_pointer === 0.U) {
            io.output.bits.tdata := header.dst_mac(47, 40)
        } .elsewhen (frame_pointer === 1.U) {
            io.output.bits.tdata := header.dst_mac(39, 32)
        } .elsewhen (frame_pointer === 2.U) {
            io.output.bits.tdata := header.dst_mac(31, 24)
        } .elsewhen (frame_pointer === 3.U) {
            io.output.bits.tdata := header.dst_mac(23, 16)
        } .elsewhen (frame_pointer === 4.U) {
            io.output.bits.tdata := header.dst_mac(15, 8)
        } .elsewhen (frame_pointer === 5.U) {
            io.output.bits.tdata := header.dst_mac(7, 0)
        } .elsewhen (frame_pointer === 6.U) {
            io.output.bits.tdata := header.src_mac(47, 40)
        } .elsewhen (frame_pointer === 7.U) {
            io.output.bits.tdata := header.src_mac(39, 32)
        } .elsewhen (frame_pointer === 8.U) {
            io.output.bits.tdata := header.src_mac(31, 24)
        } .elsewhen (frame_pointer === 9.U) {
            io.output.bits.tdata := header.src_mac(23, 16)
        } .elsewhen (frame_pointer === 10.U) {
            io.output.bits.tdata := header.src_mac(15, 8)
        } .elsewhen (frame_pointer === 11.U) {
            io.output.bits.tdata := header.src_mac(7, 0)
        } .elsewhen (frame_pointer === 12.U) {
            io.output.bits.tdata := header.ethernet_type(15, 8)
        } .elsewhen (frame_pointer === 13.U) {
            io.output.bits.tdata := header.ethernet_type(7, 0)
        } .otherwise {
            io.output.bits.tdata := 0.U
        }
        io.output.bits.tlast.get := false.B
        io.output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.output.valid := true.B
    } .elsewhen (state === State.sPayload) {
        io.output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get
        io.output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.output.valid := input_fifo.io.deq.valid
    } .otherwise {
        io.output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get
        io.output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.output.valid := false.B
    }

    switch (state) {
        is (State.sIdle) {
            when (io.header.fire()) {
                state := State.sHeader
                frame_pointer := 0.U
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.output.fire()) {
                when (frame_pointer === 13.U) {
                    frame_pointer := 0.U
                    state := State.sPayload
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
        is (State.sPayload) {
            when (input_fifo.io.deq.bits.tlast.get) {
                state := State.sIdle
                busy := false.B
            }
        }
    }
}

class EthernetFrameRx extends Module {
    val io = IO(new Bundle {
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val header = Decoupled(new EthernetFrameHeader())
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val status = new EthernetFrameStatus(DIRECTION = "RECEIVE")
    })
    val output_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1), 16))
    val header = Reg(new EthernetFrameHeader())
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(4.W))
    val busy = RegInit(false.B)
    val error_incomplete_packet = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload = Value
    }
    val state = RegInit(State.sIdle)

    io.input.ready := state === State.sIdle || (state === State.sHeader && ~header_valid) || (state === State.sPayload && output_fifo.io.enq.ready)
    io.header.bits := header
    io.header.valid := header_valid
    io.output <> output_fifo.io.deq
    io.status.busy := busy
    io.status.error_incomplete_packet.get := error_incomplete_packet

    output_fifo.io.enq.bits := io.input.bits
    output_fifo.io.enq.valid := io.input.fire() && state === State.sPayload

    when (io.input.fire() && state === State.sHeader && frame_pointer === 13.U) {
        header_valid := true.B
    } .elsewhen (io.header.fire()) {
        header_valid := false.B
    }

    switch (state) {
        is (State.sIdle) {
            error_incomplete_packet := false.B
            when (io.input.fire()) {
                state := State.sHeader
                frame_pointer := 1.U
                header.dst_mac := Cat(io.input.bits.tdata, header.dst_mac(39, 0))
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.input.fire()) {
                frame_pointer := frame_pointer + 1.U
                when (io.input.bits.tlast.get) {
                    state := State.sIdle
                    error_incomplete_packet := true.B
                } .elsewhen (frame_pointer === 13.U) {
                    frame_pointer := 0.U
                    header_valid := true.B
                    state := State.sPayload
                }
                switch (frame_pointer) {
                    is (1.U) {
                        header.dst_mac := Cat(header.dst_mac(47, 40), io.input.bits.tdata, header.dst_mac(31, 0))
                    }
                    is (2.U) {
                        header.dst_mac := Cat(header.dst_mac(47, 32), io.input.bits.tdata, header.dst_mac(23, 0))
                    }
                    is (3.U) {
                        header.dst_mac := Cat(header.dst_mac(47, 24), io.input.bits.tdata, header.dst_mac(15, 0))
                    }
                    is (4.U) {
                        header.dst_mac := Cat(header.dst_mac(47, 16), io.input.bits.tdata, header.dst_mac(7, 0))
                    }
                    is (5.U) {
                        header.dst_mac := Cat(header.dst_mac(47, 8), io.input.bits.tdata)
                    }
                    is (6.U) {
                        header.src_mac := Cat(io.input.bits.tdata, header.src_mac(39, 0))
                    }
                    is (7.U) {
                        header.src_mac := Cat(header.src_mac(47, 40), io.input.bits.tdata, header.src_mac(31, 0))
                    }
                    is (8.U) {
                        header.src_mac := Cat(header.src_mac(47, 32), io.input.bits.tdata, header.src_mac(23, 0))
                    }
                    is (9.U) {
                        header.src_mac := Cat(header.src_mac(47, 24), io.input.bits.tdata, header.src_mac(15, 0))
                    }
                    is (10.U) {
                        header.src_mac := Cat(header.src_mac(47, 16), io.input.bits.tdata, header.src_mac(7, 0))
                    }
                    is (11.U) {
                        header.src_mac := Cat(header.src_mac(47, 8), io.input.bits.tdata)
                    }
                    is (12.U) {
                        header.ethernet_type := Cat(io.input.bits.tdata, header.ethernet_type(7, 0))
                    }
                    is (13.U) {
                        header.ethernet_type := Cat(header.ethernet_type(15, 8), io.input.bits.tdata)
                    }
                }
            }
        }
        is (State.sPayload) {
            when (io.input.bits.tlast.get) {
                state := State.sIdle
                busy := false.B
            }
        }
    }
}

class EthernetPHY(val PADDING: Boolean, val MINIMUM_FRAME_LENGTH: Int) extends Module {
    val io = IO(new Bundle {
        val phy_clock = Input(Clock())
        val phy_clock_90 = Input(Clock())
        val phy_reset = Input(Reset())
        val phy = new RGMIIPHYDuplex()
        val rx = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                         KEEP_EN = false,
                                         LAST_EN = true,
                                         ID_WIDTH = 0,
                                         DEST_WIDTH = 0,
                                         USER_WIDTH = 1))
        val rx_mac_status = new GMIIStatus(DIRECTION = "RECEIVE")
        val rx_fifo_status = new FIFOStatus()
        val tx = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                 KEEP_EN = false,
                                                 LAST_EN = true,
                                                 ID_WIDTH = 0,
                                                 DEST_WIDTH = 0,
                                                 USER_WIDTH = 1)))
        val tx_mac_status = new GMIIStatus(DIRECTION = "TRANSMIT")
        val tx_fifo_status = new FIFOStatus()
        val tx_ifg_delay = Input(UInt(8.W))
        val speed = Output(UInt(2.W))
    })
    val phy = Module(new RGMIIPHY())
    val mac = Module(new GMIIMAC(PADDING = PADDING, MINIMUM_FRAME_LENGTH = MINIMUM_FRAME_LENGTH, USER_WIDTH = 8))
    val rx_fifo = Module(new AsyncFIFO[AXIStream](DEPTH = 2048,
                                                  FRAME_FIFO = true,
                                                  DROP_WHEN_FULL = true,
                                                  DROP_BAD_FRAME = true,
                                                  OUTPUT_PIPE = 2,
                                                  new AXIStream(DATA_WIDTH = 8,
                                                                KEEP_EN = false,
                                                                LAST_EN = true,
                                                                ID_WIDTH = 0,
                                                                DEST_WIDTH = 0,
                                                                USER_WIDTH = 1)))
    val tx_fifo = Module(new AsyncFIFO[AXIStream](DEPTH = 2048,
                                                  FRAME_FIFO = true,
                                                  DROP_WHEN_FULL = false,
                                                  DROP_BAD_FRAME = true,
                                                  OUTPUT_PIPE = 2,
                                                  new AXIStream(DATA_WIDTH = 8,
                                                                KEEP_EN = false,
                                                                LAST_EN = true,
                                                                ID_WIDTH = 0,
                                                                DEST_WIDTH = 0,
                                                                USER_WIDTH = 1)))
    
    val rx_prescale = Wire(UInt(3.W))
    withClock(phy.io.mac.rx.clock) {
        val rx_prescale_reg = Reg(UInt(3.W))
        rx_prescale_reg := rx_prescale_reg + 1.U
        rx_prescale := rx_prescale_reg
    }

    val rx_prescale_sync = Wire(UInt(3.W))
    withClock(io.phy_clock) {
        val rx_prescale_sync_reg = Reg(UInt(3.W))
        rx_prescale_sync_reg := Cat(rx_prescale(2), rx_prescale_sync_reg(2, 1))
        rx_prescale_sync := rx_prescale_sync_reg
    }

    val speed = Wire(UInt(2.W))
    val mii_select = Wire(UInt(1.W))
    withClockAndReset(io.phy_clock, io.phy_reset) {
        val rx_speed_count_1 = RegInit(0.U(7.W))
        val rx_speed_count_2 = RegInit(0.U(2.W))
        val speed_reg = RegInit(2.U(2.W))
        val mii_select_reg = RegInit(0.U(1.W))
        
        rx_speed_count_1 := rx_speed_count_1 + 1.U
        when (rx_prescale_sync(1) ^ rx_prescale_sync(0)) {
            rx_speed_count_2 := rx_speed_count_2 + 1.U
        }
        when (rx_speed_count_1.andR) {
            rx_speed_count_1 := 0.U
            rx_speed_count_2 := 0.U
            speed_reg := 0.U
            mii_select_reg := 1.U
        }
        when (rx_speed_count_2.andR) {
            rx_speed_count_1 := 0.U
            rx_speed_count_2 := 0.U
            when (rx_speed_count_1(6, 5) > 0.U) {
                speed_reg := 1.U
                mii_select_reg := 1.U
            } .otherwise {
                speed_reg := 2.U
                mii_select_reg := 0.U
            }
        }
        speed := speed_reg
        mii_select := mii_select_reg
    }

    val rx_mii_select = Wire(UInt(1.W))
    withClock(phy.io.mac.rx.clock) {
        val rx_mii_select_reg = Reg(UInt(2.W))
        rx_mii_select_reg := Cat(mii_select, rx_mii_select_reg(1))
        rx_mii_select := rx_mii_select_reg(0)
    }

    val tx_mii_select = Wire(UInt(1.W))
    withClock(phy.io.mac.tx.clock) {
        val tx_mii_select_reg = Reg(UInt(2.W))
        tx_mii_select_reg := Cat(mii_select, tx_mii_select_reg(1))
        tx_mii_select := tx_mii_select_reg(0)
    }

    phy.clock := io.phy_clock
    phy.io.clock_90 := io.phy_clock_90
    phy.reset := io.phy_reset
    phy.io.phy <> io.phy
    phy.io.speed := speed
    phy.io.mac.rx <> mac.io.rx_in
    phy.io.mac.tx <> mac.io.tx_out

    mac.io.rx_out <> rx_fifo.io.enq
    mac.io.rx_mii_select := rx_mii_select
    mac.io.tx_in <> tx_fifo.io.deq
    mac.io.tx_mii_select := tx_mii_select
    mac.io.tx_ifg_delay := io.tx_ifg_delay

    val rx_reset = Wire(UInt(1.W))
    val tx_reset = Wire(UInt(1.W))

    withClockAndReset(phy.io.mac.rx.clock, reset.asAsyncReset) {
        val rx_reset_reg = RegInit("hF".U(4.W))
        rx_reset_reg := Cat(0.U(1.W), rx_reset_reg(3, 1))
        rx_reset := rx_reset_reg(0)
    }

    withClockAndReset(phy.io.mac.tx.clock, reset.asAsyncReset) {
        val tx_reset_reg = RegInit("hF".U(4.W))
        tx_reset_reg := Cat(0.U(1.W), tx_reset_reg(3, 1))
        tx_reset := tx_reset_reg(0)
    }

    rx_fifo.reset := reset.asBool() || rx_reset.asBool()
    rx_fifo.io.deq_clock := clock
    rx_fifo.io.deq <> io.rx
    rx_fifo.io.deq_status <> io.rx_fifo_status
    rx_fifo.io.enq_clock := phy.io.mac.rx.clock
    rx_fifo.io.del.get := mac.io.rx_out.bits.tlast.get
    rx_fifo.io.bad.get := mac.io.rx_out.bits.tuser.get(0)

    tx_fifo.reset := reset.asBool() || tx_reset.asBool()
    tx_fifo.io.deq_clock := phy.io.mac.tx.clock
    tx_fifo.io.enq_clock := clock
    tx_fifo.io.enq <> io.tx
    tx_fifo.io.enq_status <> io.tx_fifo_status
    tx_fifo.io.del.get := io.tx.bits.tlast.get
    tx_fifo.io.bad.get := io.tx.bits.tuser.get(0)

    val rx_mac_status_sync = Wire(new GMIIStatus(DIRECTION = "RECEIVE"))
    val rx_mac_status = Seq.fill(3)(RegInit(0.U.asTypeOf(new GMIIStatus(DIRECTION = "RECEIVE"))))
    withClockAndReset(phy.io.mac.rx.clock, phy.io.mac.rx.reset) {
        val rx_mac_status_sync_reg = RegInit(0.U.asTypeOf(new GMIIStatus(DIRECTION = "RECEIVE")))
        rx_mac_status_sync_reg := (mac.io.rx_status.asTypeOf(UInt(3.W)) ^ rx_mac_status_sync_reg.asTypeOf(UInt(3.W))).asTypeOf(new GMIIStatus(DIRECTION = "RECEIVE"))
        rx_mac_status_sync := rx_mac_status_sync_reg
    }
    rx_mac_status(2) := rx_mac_status_sync
    rx_mac_status(1) := rx_mac_status(2)
    rx_mac_status(0) := rx_mac_status(1)
    io.rx_mac_status := (rx_mac_status(1).asTypeOf(UInt(3.W)) ^ rx_mac_status(0).asTypeOf(UInt(3.W))).asTypeOf(new GMIIStatus(DIRECTION = "RECEIVE"))
    
    val tx_mac_status_sync = Wire(new GMIIStatus(DIRECTION = "TRANSMIT"))
    val tx_mac_status = Seq.fill(3)(RegInit(0.U.asTypeOf(new GMIIStatus(DIRECTION = "TRANSMIT"))))
    withClockAndReset(phy.io.mac.tx.clock, phy.io.mac.tx.reset) {
        val tx_mac_status_sync_reg = RegInit(0.U.asTypeOf(new GMIIStatus(DIRECTION = "TRANSMIT")))
        tx_mac_status_sync_reg := (mac.io.tx_status.asTypeOf(UInt(2.W)) ^ tx_mac_status_sync_reg.asTypeOf(UInt(2.W))).asTypeOf(new GMIIStatus(DIRECTION = "TRANSMIT"))
        tx_mac_status_sync := tx_mac_status_sync_reg
    }
    tx_mac_status(2) := tx_mac_status_sync
    tx_mac_status(1) := tx_mac_status(2)
    tx_mac_status(0) := tx_mac_status(1)
    io.tx_mac_status := (tx_mac_status(1).asTypeOf(UInt(2.W)) ^ tx_mac_status(0).asTypeOf(UInt(2.W))).asTypeOf(new GMIIStatus(DIRECTION = "TRANSMIT"))

    io.speed := speed
}

class RGMIIPHY extends Module {
    val io = IO(new Bundle {
        val clock_90 = Input(Clock())
        val phy = new RGMIIPHYDuplex()
        val mac = Flipped(new GMIIMACDuplex())
        val speed = Input(UInt(2.W))
    })

    val ssio_ddr_input = Module(new SSDDRInput[UInt](TYPE = UInt(5.W)))
    ssio_ddr_input.io.input_clock := io.phy.rx.clock
    ssio_ddr_input.io.input_d := Cat(io.phy.rx.data, io.phy.rx.control)
    io.mac.rx.clock := ssio_ddr_input.io.output_clock
    io.mac.rx.data := Cat(ssio_ddr_input.io.output_q2(4, 1), ssio_ddr_input.io.output_q1(4, 1))
    io.mac.rx.data_valid := ssio_ddr_input.io.output_q1(0)
    io.mac.rx.error := ssio_ddr_input.io.output_q1(0) ^ ssio_ddr_input.io.output_q2(0)
    io.mac.rx.clock_en := true.B

    val tx_clock_1 = RegInit(1.U(1.W))
    val tx_clock_2 = RegInit(0.U(1.W))
    val tx_clock_rise = RegInit(true.B)
    val tx_clock_fall = RegInit(true.B)
    val count = RegInit(0.U(6.W))

    when (io.speed === 0.U) {
        count := count + 1.U
        tx_clock_rise := false.B
        tx_clock_fall := false.B
        when (count === 24.U) {
            tx_clock_1 := 1.U
            tx_clock_2 := 1.U
            tx_clock_rise := true.B
        } .elsewhen (count >= 49.U) {
            tx_clock_1 := 0.U
            tx_clock_2 := 0.U
            tx_clock_fall := true.B
            count := 0.U
        }
    } .elsewhen (io.speed === 1.U) {
        count := count + 1.U
        tx_clock_rise := false.B
        tx_clock_fall := false.B
        when (count === 2.U) {
            tx_clock_1 := 1.U
            tx_clock_2 := 1.U
            tx_clock_rise := true.B
        } .elsewhen (count >= 4.U) {
            tx_clock_1 := 0.U
            tx_clock_2 := 0.U
            tx_clock_fall := true.B
            count := 0.U
        }
    } .otherwise {
        tx_clock_1 := 1.U
        tx_clock_2 := 0.U
        tx_clock_rise := true.B
        tx_clock_fall := true.B
    }

    val tx_data_1 = Wire(UInt(4.W))
    val tx_data_2 = Wire(UInt(4.W))
    val tx_control_1 = Wire(UInt(1.W))
    val tx_control_2 = Wire(UInt(1.W))

    when (io.speed === 0.U) {
        tx_data_1 := io.mac.tx.data(3, 0)
        tx_data_2 := io.mac.tx.data(3, 0)
        tx_control_1 := Mux(tx_clock_2.asBool(), io.mac.tx.data_valid, io.mac.tx.data_valid ^ io.mac.tx.error)
        tx_control_2 := Mux(tx_clock_2.asBool(), io.mac.tx.data_valid, io.mac.tx.data_valid ^ io.mac.tx.error)
        io.mac.tx.clock_en := tx_clock_fall
    } .elsewhen (io.speed === 1.U) {
        tx_data_1 := io.mac.tx.data(3, 0)
        tx_data_2 := io.mac.tx.data(3, 0)
        tx_control_1 := Mux(tx_clock_2.asBool(), io.mac.tx.data_valid, io.mac.tx.data_valid ^ io.mac.tx.error)
        tx_control_2 := Mux(tx_clock_2.asBool(), io.mac.tx.data_valid, io.mac.tx.data_valid ^ io.mac.tx.error)
        io.mac.tx.clock_en := tx_clock_fall
    } .otherwise {
        tx_data_1 := io.mac.tx.data(3, 0)
        tx_data_2 := io.mac.tx.data(7, 4)
        tx_control_1 := io.mac.tx.data_valid
        tx_control_2 := io.mac.tx.data_valid ^ io.mac.tx.error
        io.mac.tx.clock_en := true.B
    }

    val oddr_clock = Module(new ODDRWrapper(DDR_CLK_EDGE = "SAME_EDGE", INIT = 0, SRTYPE = "ASYNC", WIDTH = 1))
    oddr_clock.io.C := io.clock_90
    oddr_clock.io.CE := true.B
    oddr_clock.io.R := io.mac.tx.reset.asUInt
    oddr_clock.io.S := false.B
    oddr_clock.io.D1 := tx_clock_1
    oddr_clock.io.D2 := tx_clock_2
    io.phy.tx.clock := oddr_clock.io.Q.asBool().asClock()

    val oddr_data = Module(new ODDRWrapper(DDR_CLK_EDGE = "SAME_EDGE", INIT = 0, SRTYPE = "ASYNC", WIDTH = 5))
    oddr_data.io.C := clock
    oddr_data.io.CE := true.B
    oddr_data.io.R := io.mac.tx.reset.asUInt
    oddr_data.io.S := false.B
    oddr_data.io.D1 := Cat(io.mac.tx.data(3, 0), io.mac.tx.data_valid)
    oddr_data.io.D2 := Cat(io.mac.tx.data(7, 4), io.mac.tx.data_valid ^ io.mac.tx.error)
    io.phy.tx.data := oddr_data.io.Q(4, 1)
    io.phy.tx.control := oddr_data.io.Q(0)

    io.mac.tx.clock := clock

    withClockAndReset (io.mac.tx.clock, false.B) {
        io.mac.tx.reset := RegNext(RegNext(RegNext(RegNext(reset))))
    }

    withClockAndReset (io.mac.rx.clock, false.B) {
        io.mac.rx.reset := RegNext(RegNext(RegNext(RegNext(reset))))
    }
}

class GMIIMAC(val PADDING: Boolean,
              val MINIMUM_FRAME_LENGTH: Int,
              val USER_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val rx_in = new GMIIMACRx()
        val rx_out = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = USER_WIDTH))
        val rx_mii_select = Input(UInt(1.W))
        val rx_status = new GMIIStatus(DIRECTION = "RECEIVE")
        val tx_in = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = USER_WIDTH)))
        val tx_out = new GMIIMACTx()
        val tx_mii_select = Input(UInt(1.W))
        val tx_status = new GMIIStatus(DIRECTION = "TRANSMIT")
        val tx_ifg_delay = Input(UInt(8.W))
    })

    val rx = Module(new GMIIRx(USER_WIDTH = USER_WIDTH))
    rx.io.gmii <> io.rx_in
    io.rx_out <> rx.io.m_axi
    rx.io.mii_select <> io.rx_mii_select
    io.rx_status <> rx.io.status

    val tx = Module(new GMIITx(PADDING = PADDING, MINIMUM_FRAME_LENGTH = MINIMUM_FRAME_LENGTH, USER_WIDTH = USER_WIDTH))
    tx.io.s_axi <> io.tx_in
    io.tx_out <> tx.io.gmii
    tx.io.mii_select <> io.tx_mii_select
    io.tx_status <> tx.io.status
    tx.io.ifg_delay <> io.tx_ifg_delay
}

class GMIIRx(val USER_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val m_axi = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                            KEEP_EN = false,
                                            LAST_EN = true,
                                            ID_WIDTH = 0,
                                            DEST_WIDTH = 0,
                                            USER_WIDTH = USER_WIDTH))
        val gmii = new GMIIMACRx()
        val mii_select = Input(UInt(1.W))
        val status = new GMIIStatus(DIRECTION = "RECEIVE")
    })

    withClockAndReset(io.gmii.clock, io.gmii.reset) {
        val PREAMBLE = "h55".U(8.W)
        val DELIMITER = "hD5".U(8.W)

        object State extends ChiselEnum {
            val sIdle, sPayload, sWaitLast = Value
        }

        val state = RegInit(State.sIdle)

        val reset_crc = Wire(Bool())
        val update_crc = Wire(Bool())

        val gmii_rx_data = Seq.fill(5)(Reg(UInt(8.W)))
        val gmii_rx_data_valid = Seq.fill(5)(RegInit(false.B))
        val gmii_rx_error = Seq.fill(5)(Reg(Bool()))

        val m_axi_tdata = Reg(UInt(8.W))
        val m_axi_valid = RegInit(false.B)
        val m_axi_tlast = Reg(Bool())
        val m_axi_tuser = Reg(UInt(USER_WIDTH.W))
        io.m_axi.bits.tdata := m_axi_tdata
        io.m_axi.valid := m_axi_valid
        io.m_axi.bits.tlast.get := m_axi_tlast
        io.m_axi.bits.tuser.get := m_axi_tuser

        val mii_odd = RegInit(false.B)
        val mii_locked = RegInit(false.B)

        when (io.gmii.clock_en) {
            when (io.mii_select === 1.U) {
                mii_odd := ~mii_odd
                gmii_rx_data(4) := Cat(io.gmii.data(3, 0), gmii_rx_data(4)(7, 4))
                when (mii_locked) {
                    mii_locked := io.gmii.data_valid
                } .elsewhen (io.gmii.data_valid && Cat(io.gmii.data(3, 0), gmii_rx_data(4)(7, 4)) === DELIMITER) {
                    mii_locked := true.B
                    mii_odd := true.B
                }
                when (mii_odd) {
                    gmii_rx_data_valid(4) := io.gmii.data_valid & gmii_rx_data_valid(4)
                    gmii_rx_data_valid(3) := gmii_rx_data_valid(4) & io.gmii.data_valid
                    gmii_rx_data_valid(2) := gmii_rx_data_valid(3) & io.gmii.data_valid
                    gmii_rx_data_valid(1) := gmii_rx_data_valid(2) & io.gmii.data_valid
                    gmii_rx_data_valid(0) := gmii_rx_data_valid(1) & io.gmii.data_valid
                    gmii_rx_data(3) := gmii_rx_data(4)
                    gmii_rx_data(2) := gmii_rx_data(3)
                    gmii_rx_data(1) := gmii_rx_data(2)
                    gmii_rx_data(0) := gmii_rx_data(1)
                    gmii_rx_error(4) := io.gmii.error | gmii_rx_error(4)
                    gmii_rx_error(3) := gmii_rx_error(4)
                    gmii_rx_error(2) := gmii_rx_error(3)
                    gmii_rx_error(1) := gmii_rx_error(2)
                    gmii_rx_error(0) := gmii_rx_error(1)
                } .otherwise {
                    gmii_rx_data_valid(4) := io.gmii.data_valid
                    gmii_rx_error(4) := io.gmii.error
                }
            } .otherwise {
                gmii_rx_data_valid(4) := io.gmii.data_valid
                gmii_rx_data_valid(3) := gmii_rx_data_valid(4) & io.gmii.data_valid
                gmii_rx_data_valid(2) := gmii_rx_data_valid(3) & io.gmii.data_valid
                gmii_rx_data_valid(1) := gmii_rx_data_valid(2) & io.gmii.data_valid
                gmii_rx_data_valid(0) := gmii_rx_data_valid(1) & io.gmii.data_valid
                gmii_rx_data(4) := io.gmii.data
                gmii_rx_data(3) := gmii_rx_data(4)
                gmii_rx_data(2) := gmii_rx_data(3)
                gmii_rx_data(1) := gmii_rx_data(2)
                gmii_rx_data(0) := gmii_rx_data(1)
                gmii_rx_error(4) := io.gmii.error
                gmii_rx_error(3) := gmii_rx_error(4)
                gmii_rx_error(2) := gmii_rx_error(3)
                gmii_rx_error(1) := gmii_rx_error(2)
                gmii_rx_error(0) := gmii_rx_error(1)
            }
        }

        val start_packet = RegInit(false.B)
        val error_bad_frame = RegInit(false.B)
        val error_bad_fcs = RegInit(false.B)
        io.status.start_packet := start_packet
        io.status.error_bad_frame.get := error_bad_frame
        io.status.error_bad_fcs.get := error_bad_fcs

        val crc_state = RegInit("hFFFFFFFF".U(32.W))
        val lfsr = Module(new LFSR(WIDTH = 32,
                                   POLYNOMIAL = BigInt("4c11db7", 16),
                                   CONFIGURATION = "GALOIS",
                                   FEED_FORWARD = false,
                                   REVERSE = true,
                                   DATA_WIDTH = 8))
        lfsr.io.data_in := gmii_rx_data(0)
        lfsr.io.state_in := crc_state
        when (reset_crc) {
            crc_state := "hFFFFFFFF".U
        } .elsewhen (update_crc) {
            crc_state := lfsr.io.state_out
        }

        state := State.sIdle
        reset_crc := false.B
        update_crc := false.B
        m_axi_tdata := 0.U
        m_axi_valid := false.B
        m_axi_tlast := false.B
        m_axi_tuser := 0.U
        start_packet := false.B
        error_bad_frame := false.B
        error_bad_fcs := false.B
        when (~io.gmii.clock_en) {
            state := state
        } .elsewhen (io.mii_select === 1.U && ~mii_odd) {
            state := state
        } .otherwise {
            switch (state) {
                is (State.sIdle) {
                    reset_crc := true.B
                    when (gmii_rx_data_valid(0) && ~gmii_rx_error(0) && gmii_rx_data(0) === DELIMITER) {
                        start_packet := true.B
                        state := State.sPayload
                    } .otherwise {
                        state := State.sIdle
                    }
                }
                is (State.sPayload) {
                    update_crc := true.B
                    m_axi_tdata := gmii_rx_data(0)
                    m_axi_valid := true.B
                    when (gmii_rx_data_valid(0) && gmii_rx_error(0)) {
                        m_axi_tlast := true.B
                        m_axi_tuser := 1.U
                        error_bad_frame := true.B
                        state := State.sWaitLast
                    } .elsewhen (~io.gmii.data_valid) {
                        m_axi_tlast := true.B
                        when (gmii_rx_error(4) || gmii_rx_error(3) || gmii_rx_error(2) || gmii_rx_error(0)) {
                            m_axi_tuser := 1.U
                            error_bad_frame := true.B
                        } .elsewhen (Cat(gmii_rx_data(4), gmii_rx_data(3), gmii_rx_data(2), gmii_rx_data(1)) === ~lfsr.io.state_out) {
                            m_axi_tuser := 0.U
                        } .otherwise {
                            m_axi_tuser := 1.U
                            error_bad_frame := true.B
                            error_bad_fcs := true.B
                        }
                        state := State.sIdle
                    } .otherwise {
                        state := State.sPayload
                    }
                }
                is (State.sWaitLast) {
                    when (~io.gmii.data_valid) {
                        state := State.sIdle
                    } .otherwise {
                        state := State.sWaitLast
                    }
                }
            }
        }
    }
}

class GMIITx(val PADDING: Boolean,
             val MINIMUM_FRAME_LENGTH: Int,
             val USER_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val s_axi = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = USER_WIDTH)))
        val gmii = new GMIIMACTx()
        val mii_select = Input(UInt(1.W))
        val ifg_delay = Input(UInt(8.W))
        val status = new GMIIStatus(DIRECTION = "TRANSMIT")
    })

    withClockAndReset(io.gmii.clock, io.gmii.reset) {
        val PREAMBLE = "h55".U(8.W)
        val DELIMITER = "hD5".U(8.W)

        object State extends ChiselEnum {
            val sIdle, sPreamble, sPayload, sLast, sPad, sFCS, sWaitEnd, sIFG = Value
        }

        val state = RegInit(State.sIdle)

        val reset_crc = Wire(Bool())
        val update_crc = Wire(Bool())

        val s_axi_tdata = Reg(UInt(8.W))
        val s_axi_ready = RegInit(false.B)
        io.s_axi.ready := s_axi_ready
        
        val mii_odd = Reg(Bool())
        val mii_msn = Reg(UInt(4.W))
        
        val frame_pointer = RegInit(0.U(16.W))

        val gmii_tx_data_next = Wire(UInt(8.W))
        val gmii_tx_data = Reg(UInt(8.W))
        val gmii_tx_data_valid = RegInit(false.B)
        val gmii_tx_error = RegInit(false.B)
        gmii_tx_data := Mux(io.gmii.clock_en && ~mii_odd && io.mii_select === 1.U, Cat(0.U(4.W), gmii_tx_data_next(3, 0)), gmii_tx_data_next)
        io.gmii.data := gmii_tx_data
        io.gmii.data_valid := gmii_tx_data_valid
        io.gmii.error := gmii_tx_error

        val start_packet = RegInit(false.B)
        val error_underflow = RegInit(false.B)
        io.status.start_packet := start_packet
        io.status.error_underflow.get := error_underflow

        val crc_state = RegInit("hFFFFFFFF".U(32.W))
        val lfsr = Module(new LFSR(WIDTH = 32,
                                   POLYNOMIAL = BigInt("4c11db7", 16),
                                   CONFIGURATION = "GALOIS",
                                   FEED_FORWARD = false,
                                   REVERSE = true,
                                   DATA_WIDTH = 8))
        lfsr.io.data_in := s_axi_tdata
        lfsr.io.state_in := crc_state
        when (reset_crc) {
            crc_state := "hFFFFFFFF".U
        } .elsewhen (update_crc) {
            crc_state := lfsr.io.state_out
        }
        
        state := State.sIdle
        reset_crc := false.B
        update_crc := false.B
        s_axi_ready := false.B
        gmii_tx_data_next := 0.U(8.W)
        gmii_tx_data_valid := false.B
        gmii_tx_error := false.B
        start_packet := false.B
        error_underflow := false.B
        when (~io.gmii.clock_en) {
            gmii_tx_data_next := gmii_tx_data
            gmii_tx_data_valid := gmii_tx_data_valid
            gmii_tx_error := gmii_tx_error
            state := state
        } .elsewhen (io.mii_select.asBool() && mii_odd) {
            mii_odd := false.B
            gmii_tx_data_next := Cat(0.U(4.W), mii_msn)
            gmii_tx_data_valid := gmii_tx_data_valid
            gmii_tx_error := gmii_tx_error
            state := state
        } .otherwise {
            switch (state) {
                is (State.sIdle) {
                    reset_crc := true.B
                    mii_odd := false.B
                    when (io.s_axi.valid) {
                        mii_odd := true.B
                        frame_pointer := 1.U
                        gmii_tx_data_next := PREAMBLE
                        gmii_tx_data_valid := true.B
                        state := State.sPreamble
                    } .otherwise {
                        state := State.sIdle
                    }
                }
                is (State.sPreamble) {
                    reset_crc := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    gmii_tx_data_next := PREAMBLE
                    gmii_tx_data_valid := true.B
                    when (frame_pointer === 6.U) {
                        s_axi_ready := true.B
                        s_axi_tdata := io.s_axi.bits.tdata
                        state := State.sPreamble
                    } .elsewhen (frame_pointer === 7.U) {
                        frame_pointer := 0.U
                        when(s_axi_ready) {
                            s_axi_ready := true.B
                            s_axi_tdata := io.s_axi.bits.tdata
                        }
                        gmii_tx_data_next := DELIMITER
                        start_packet := true.B
                        when (io.s_axi.bits.tlast.get) {
                            s_axi_ready := ~s_axi_ready
                            when (io.s_axi.bits.tuser.get(0) === 1.U) {
                                gmii_tx_error := true.B
                                frame_pointer := 0.U
                                state := State.sIFG
                            } .otherwise {
                                state := State.sLast
                            }
                        } .otherwise {
                            state := State.sPayload
                        }
                    } .otherwise {
                        state := State.sPreamble
                    }
                }
                is (State.sPayload) {
                    update_crc := true.B
                    s_axi_ready := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    gmii_tx_data_next := s_axi_tdata
                    gmii_tx_data_valid := true.B
                    s_axi_tdata := io.s_axi.bits.tdata
                    when (io.s_axi.valid) {
                        when (io.s_axi.bits.tlast.get) {
                            s_axi_ready := ~s_axi_ready
                            when (io.s_axi.bits.tuser.get(0) === 1.U) {
                                gmii_tx_error := true.B
                                frame_pointer := 0.U
                                state := State.sIFG
                            } .otherwise {
                                state := State.sLast
                            }
                        } .otherwise {
                            state := State.sPayload
                        }
                    } .otherwise {
                        gmii_tx_error := true.B
                        frame_pointer := 0.U
                        error_underflow := true.B
                        state := State.sWaitEnd
                    }
                }
                is (State.sLast) {
                    update_crc := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    gmii_tx_data_next := s_axi_tdata
                    gmii_tx_data_valid := true.B
                    if (PADDING) {
                        when (frame_pointer < (MINIMUM_FRAME_LENGTH - 5).U) {
                            s_axi_tdata := 0.U
                            state := State.sPad
                        } .otherwise {
                            frame_pointer := 0.U
                            state := State.sFCS
                        }
                    } else {
                        frame_pointer := 0.U
                        state := State.sFCS
                    }
                }
                is (State.sPad) {
                    update_crc := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    gmii_tx_data_next := 0.U
                    gmii_tx_data_valid := true.B
                    s_axi_tdata := 0.U
                    when (frame_pointer < (MINIMUM_FRAME_LENGTH - 5).U) {
                        state := State.sPad
                    } .otherwise {
                        frame_pointer := 0.U
                        state := State.sFCS
                    }
                }
                is (State.sFCS) {
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    when (frame_pointer === 0.U) {
                        gmii_tx_data_next := ~crc_state(7, 0)
                        state := State.sFCS
                    } .elsewhen (frame_pointer === 1.U) {
                        gmii_tx_data_next := ~crc_state(15, 8)
                        state := State.sFCS
                    } .elsewhen (frame_pointer === 2.U) {
                        gmii_tx_data_next := ~crc_state(23, 16)
                        state := State.sFCS
                    } .otherwise {
                        gmii_tx_data_next := ~crc_state(31, 24)
                        frame_pointer := 0.U
                        state := State.sIFG
                    }
                    gmii_tx_data_valid := true.B
                }
                is (State.sWaitEnd) {
                    reset_crc := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    s_axi_ready := true.B
                    when (io.s_axi.valid) {
                        when (io.s_axi.bits.tlast.get) {
                            s_axi_ready := false.B
                            when (frame_pointer < io.ifg_delay - 1.U) {
                                state := State.sIFG
                            } .otherwise {
                                state := State.sIdle
                            }
                        } .otherwise {
                            state := State.sWaitEnd
                        }
                    } .otherwise {
                        state := State.sWaitEnd
                    }
                }
                is (State.sIFG) {
                    reset_crc := true.B
                    mii_odd := true.B
                    frame_pointer := frame_pointer + 1.U
                    when (frame_pointer < io.ifg_delay - 1.U) {
                        state := State.sIFG
                    } .otherwise {
                        state := State.sIdle
                    }
                }
            }

            when (io.mii_select === 1.U) {
                mii_msn := gmii_tx_data_next(7, 4)
            }
        }
    }
}
