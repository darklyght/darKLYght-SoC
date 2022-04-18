package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class AXI4FullToUDPMetadata(val CHANNEL: String, val ID_WIDTH: Int) extends Bundle {
    val id = UInt(ID_WIDTH.W)
    val length = if (CHANNEL == "R") Some(UInt(8.W)) else None  
}

class AXI4FullToUDPNetwork extends Bundle {
    val mac = UInt(48.W)
    val ip = UInt(32.W)
    val port = UInt(16.W)
}

class AXI4FullToUDP(val MAC: String,
                    val IP: String,
                    val PORT: String,
                    val DATA_WIDTH: Int = 32,
                    val ADDR_WIDTH: Int = 32,
                    val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val network = Input(new AXI4FullToUDPNetwork)
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val input_header = Flipped(Decoupled(new UDPFrameHeader))
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val S_AXI = Flipped(new AXI4Full(DATA_WIDTH = DATA_WIDTH, ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    })

    val tx = Module(new AXI4FullToUDPTx(MAC = MAC,
                                        IP = IP,
                                        PORT = PORT,
                                        DATA_WIDTH = DATA_WIDTH,
                                        ADDR_WIDTH = ADDR_WIDTH,
                                        ID_WIDTH = ID_WIDTH))
    val rx = Module(new AXI4FullToUDPRx(MAC = MAC,
                                        IP = IP,
                                        PORT = PORT,
                                        DATA_WIDTH = DATA_WIDTH,
                                        ADDR_WIDTH = ADDR_WIDTH,
                                        ID_WIDTH = ID_WIDTH))
    
    tx.io.network <> io.network
    tx.io.output <> io.output
    tx.io.output_header <> io.output_header
    tx.io.aw <> io.S_AXI.aw
    tx.io.w <> io.S_AXI.w
    tx.io.ar <> io.S_AXI.ar

    rx.io.input <> io.input
    rx.io.input_header <> io.input_header
    rx.io.b <> io.S_AXI.b
    rx.io.r <> io.S_AXI.r
    rx.io.w_metadata <> tx.io.w_metadata
    rx.io.r_metadata <> tx.io.r_metadata
}

class AXI4FullToUDPTxAR(val MAC: String,
                        val IP: String,
                        val PORT: String,
                        val DATA_WIDTH: Int = 32,
                        val ADDR_WIDTH: Int = 32,
                        val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val network = Input(new AXI4FullToUDPNetwork)
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val ar = Flipped(Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH)))
        val r_metadata = Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "R", ID_WIDTH = ID_WIDTH))
    })

    val addr_fifo = Module(new Queue(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH), 4))
    val metadata_fifo = Module(new Queue(new AXI4FullToUDPMetadata(CHANNEL = "R", ID_WIDTH = ID_WIDTH), 4))

    object State extends ChiselEnum {
        val sHeader, sAddress = Value
    }
    val state = RegInit(State.sHeader)

    val frame_pointer = RegInit(0.U(3.W))

    io.output.valid := state === State.sAddress && addr_fifo.io.deq.valid
    when (frame_pointer === 0.U) {
        io.output.bits.tdata := 0.U // 0 for read
    } .elsewhen (frame_pointer === 1.U) {
        io.output.bits.tdata := addr_fifo.io.deq.bits.len
    } .elsewhen (frame_pointer === 2.U) {
        io.output.bits.tdata := addr_fifo.io.deq.bits.addr(31, 24)
    } .elsewhen (frame_pointer === 3.U) {
        io.output.bits.tdata := addr_fifo.io.deq.bits.addr(23, 16)
    } .elsewhen (frame_pointer === 4.U) {
        io.output.bits.tdata := addr_fifo.io.deq.bits.addr(15, 8)
    } .otherwise {
        io.output.bits.tdata := addr_fifo.io.deq.bits.addr(7, 0)
    }
    io.output.bits.tlast.get := state === State.sAddress && frame_pointer === 5.U
    io.output.bits.tuser.get := 0.U

    io.output_header.valid := state === State.sHeader && addr_fifo.io.deq.valid
    io.output_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    io.output_header.bits.ip.ethernet.dst_mac := io.network.mac
    io.output_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W)
    io.output_header.bits.ip.src_ip := IP.U(32.W)
    io.output_header.bits.ip.dst_ip := io.network.ip
    io.output_header.bits.ip.version := 4.U(4.W)
    io.output_header.bits.ip.ihl := 5.U(4.W)
    io.output_header.bits.ip.dscp := 0.U(6.W)
    io.output_header.bits.ip.ecn := 0.U(2.W)
    io.output_header.bits.ip.length := 20.U(16.W) + io.output_header.bits.length
    io.output_header.bits.ip.id := 0.U(16.W)
    io.output_header.bits.ip.flags := 2.U(3.W)
    io.output_header.bits.ip.fragment_offset := 0.U(13.W)
    io.output_header.bits.ip.ttl := 64.U (16.W)
    io.output_header.bits.ip.protocol := 17.U(8.W)
    io.output_header.bits.ip.checksum := 0.U
    io.output_header.bits.dst_port := io.network.port
    io.output_header.bits.src_port := PORT.U(16.W)
    io.output_header.bits.length := 6.U + 8.U // Address + Header
    io.output_header.bits.checksum := 0.U

    io.ar <> addr_fifo.io.enq
    io.ar.ready := addr_fifo.io.enq.ready && metadata_fifo.io.enq.ready
    addr_fifo.io.enq.valid := io.ar.valid && metadata_fifo.io.enq.ready

    addr_fifo.io.deq.ready := state === State.sAddress && frame_pointer === 5.U && io.output.fire()

    metadata_fifo.io.enq.bits.id := io.ar.bits.id
    metadata_fifo.io.enq.bits.length.get := io.ar.bits.len
    metadata_fifo.io.enq.valid := io.ar.valid && addr_fifo.io.enq.ready
    io.r_metadata <> metadata_fifo.io.deq

    switch (state) {
        is (State.sHeader) {
            when (io.output_header.fire()) {
                state := State.sAddress
                frame_pointer := 0.U
            }
        }
        is (State.sAddress) {
            when (io.output.fire()) {
                when (frame_pointer === 5.U) {
                    state := State.sHeader
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
    }
}

class AXI4FullToUDPTxAWW(val MAC: String,
                         val IP: String,
                         val PORT: String,
                         val DATA_WIDTH: Int = 32,
                         val ADDR_WIDTH: Int = 32,
                         val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val network = Input(new AXI4FullToUDPNetwork)
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val aw = Flipped(Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH)))
        val w = Flipped(Irrevocable(new AXI4FullW(DATA_WIDTH = DATA_WIDTH)))
        val w_metadata = Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "W", ID_WIDTH = ID_WIDTH))
    })

    val addr_fifo = Module(new Queue(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH), 4))
    val input_fifo = Module(new Queue(new AXI4FullW(DATA_WIDTH = DATA_WIDTH), 512))
    val metadata_fifo = Module(new Queue(new AXI4FullToUDPMetadata(CHANNEL = "W", ID_WIDTH = ID_WIDTH), 4))

    object State extends ChiselEnum {
        val sHeader, sAddress, sPayload = Value
    }
    val state = RegInit(State.sHeader)

    val frame_pointer = RegInit(0.U(3.W))

    io.output.valid := (state === State.sAddress && addr_fifo.io.deq.valid) || (state === State.sPayload && input_fifo.io.deq.valid)
    when (state === State.sPayload) {
        when (frame_pointer === 0.U) {
            io.output.bits.tdata := input_fifo.io.deq.bits.data(31, 24)
        } .elsewhen (frame_pointer === 1.U) {
            io.output.bits.tdata := input_fifo.io.deq.bits.data(23, 16)
        } .elsewhen (frame_pointer === 2.U) {
            io.output.bits.tdata := input_fifo.io.deq.bits.data(15, 8)
        } .otherwise {
            io.output.bits.tdata := input_fifo.io.deq.bits.data(7, 0)
        }
    } .otherwise {
        when (frame_pointer === 0.U) {
            io.output.bits.tdata := 1.U // 1 for write
        } .elsewhen (frame_pointer === 1.U) {
            io.output.bits.tdata := addr_fifo.io.deq.bits.len
        } .elsewhen (frame_pointer === 2.U) {
            io.output.bits.tdata := addr_fifo.io.deq.bits.addr(31, 24)
        } .elsewhen (frame_pointer === 3.U) {
            io.output.bits.tdata := addr_fifo.io.deq.bits.addr(23, 16)
        } .elsewhen (frame_pointer === 4.U) {
            io.output.bits.tdata := addr_fifo.io.deq.bits.addr(15, 8)
        } .otherwise {
            io.output.bits.tdata := addr_fifo.io.deq.bits.addr(7, 0)
        }
    }
    io.output.bits.tlast.get := state === State.sPayload && input_fifo.io.deq.bits.last && frame_pointer === 3.U
    io.output.bits.tuser.get := 0.U

    io.output_header.valid := state === State.sHeader && addr_fifo.io.deq.valid && input_fifo.io.deq.valid
    io.output_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    io.output_header.bits.ip.ethernet.dst_mac := io.network.mac
    io.output_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W)
    io.output_header.bits.ip.src_ip := IP.U(32.W)
    io.output_header.bits.ip.dst_ip := io.network.ip
    io.output_header.bits.ip.version := 4.U(4.W)
    io.output_header.bits.ip.ihl := 5.U(4.W)
    io.output_header.bits.ip.dscp := 0.U(6.W)
    io.output_header.bits.ip.ecn := 0.U(2.W)
    io.output_header.bits.ip.length := 20.U(16.W) + io.output_header.bits.length
    io.output_header.bits.ip.id := 0.U(16.W)
    io.output_header.bits.ip.flags := 2.U(3.W)
    io.output_header.bits.ip.fragment_offset := 0.U(13.W)
    io.output_header.bits.ip.ttl := 64.U (16.W)
    io.output_header.bits.ip.protocol := 17.U(8.W)
    io.output_header.bits.ip.checksum := 0.U
    io.output_header.bits.dst_port := io.network.port
    io.output_header.bits.src_port := PORT.U(16.W)
    io.output_header.bits.length := (addr_fifo.io.deq.bits.len +& 1.U) * 4.U + 6.U + 8.U // Data + Address + Header
    io.output_header.bits.checksum := 0.U

    io.aw <> addr_fifo.io.enq
    io.aw.ready := addr_fifo.io.enq.ready && metadata_fifo.io.enq.ready
    addr_fifo.io.enq.valid := io.aw.valid && metadata_fifo.io.enq.ready
    io.w <> input_fifo.io.enq
    addr_fifo.io.deq.ready := state === State.sAddress && frame_pointer === 5.U && io.output.fire()
    input_fifo.io.deq.ready := state === State.sPayload && frame_pointer === 3.U && io.output.fire()

    metadata_fifo.io.enq.bits.id := io.aw.bits.id
    metadata_fifo.io.enq.valid := io.aw.valid && addr_fifo.io.enq.ready
    io.w_metadata <> metadata_fifo.io.deq

    switch (state) {
        is (State.sHeader) {
            when (io.output_header.fire()) {
                state := State.sAddress
                frame_pointer := 0.U
            }
        }
        is (State.sAddress) {
            when (io.output.fire()) {
                when (frame_pointer === 5.U) {
                    state := State.sPayload
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
        is (State.sPayload) {
            when (io.output.fire()) {
                when (input_fifo.io.deq.bits.last && frame_pointer === 3.U) {
                    state := State.sHeader
                }
                when (frame_pointer === 3.U) {
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
    }
}

class AXI4FullToUDPTx(val MAC: String,
                      val IP: String,
                      val PORT: String,
                      val DATA_WIDTH: Int = 32,
                      val ADDR_WIDTH: Int = 32,
                      val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val network = Input(new AXI4FullToUDPNetwork)
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val aw = Flipped(Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH)))
        val w = Flipped(Irrevocable(new AXI4FullW(DATA_WIDTH = DATA_WIDTH)))
        val ar = Flipped(Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH)))
        val w_metadata = Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "W", ID_WIDTH = ID_WIDTH))
        val r_metadata = Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "R", ID_WIDTH = ID_WIDTH))
    })

    val aww = Module(new AXI4FullToUDPTxAWW(MAC = MAC,
                                            IP = IP,
                                            PORT = PORT,
                                            DATA_WIDTH = DATA_WIDTH,
                                            ADDR_WIDTH = ADDR_WIDTH,
                                            ID_WIDTH = ID_WIDTH))
    val ar = Module(new AXI4FullToUDPTxAR(MAC = MAC,
                                          IP = IP,
                                          PORT = PORT,
                                          DATA_WIDTH = DATA_WIDTH,
                                          ADDR_WIDTH = ADDR_WIDTH,
                                          ID_WIDTH = ID_WIDTH))
    val udp_mux = Module(new UDPFrameMux(N_INPUTS = 2))

    aww.io.network <> io.network
    aww.io.aw <> io.aw
    aww.io.w <> io.w
    io.w_metadata <> aww.io.w_metadata

    ar.io.network <> io.network
    ar.io.ar <> io.ar
    io.r_metadata <> ar.io.r_metadata

    udp_mux.io.inputs(0) <> aww.io.output
    udp_mux.io.input_headers(0) <> aww.io.output_header
    udp_mux.io.inputs(1) <> ar.io.output
    udp_mux.io.input_headers(1) <> ar.io.output_header

    io.output <> udp_mux.io.output
    io.output_header <> udp_mux.io.output_header
}

class AXI4FullToUDPRx(val MAC: String,
                      val IP: String,
                      val PORT: String,
                      val DATA_WIDTH: Int = 32,
                      val ADDR_WIDTH: Int = 32,
                      val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val input_header = Flipped(Decoupled(new UDPFrameHeader))
        val b = Irrevocable(new AXI4FullB(ID_WIDTH = ID_WIDTH))
        val r = Irrevocable(new AXI4FullR(DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH))
        val w_metadata = Flipped(Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "W", ID_WIDTH = ID_WIDTH)))
        val r_metadata = Flipped(Decoupled(new AXI4FullToUDPMetadata(CHANNEL = "R", ID_WIDTH = ID_WIDTH)))
    })

    val write = RegInit(false.B)
    val data_word = Reg(UInt(32.W))
    val frame_pointer = RegInit(0.U(3.W))
    val counter = RegInit(0.U(11.W))
    val threshold_reached = RegInit(false.B)

    val b_fifo = Module(new Queue(new AXI4FullB(ID_WIDTH = ID_WIDTH), 4))
    val r_fifo = Module(new Queue(new AXI4FullR(DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH), 512))
    val w_metadata_fifo = Module(new Queue(new AXI4FullToUDPMetadata(CHANNEL = "W", ID_WIDTH = ID_WIDTH), 4))
    val r_metadata_fifo = Module(new Queue(new AXI4FullToUDPMetadata(CHANNEL = "R", ID_WIDTH = ID_WIDTH), 4))

    val packet_complete = Module(new Queue(Bool(), 4))

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.input_header.ready := state === State.sIdle && w_metadata_fifo.io.enq.ready && r_metadata_fifo.io.enq.ready
    io.input.ready := state === State.sIdle || state === State.sEnd || state === State.sHeader || (state === State.sPayload && b_fifo.io.enq.ready && r_fifo.io.enq.ready && packet_complete.io.enq.ready)

    io.b <> b_fifo.io.deq
    io.r <> r_fifo.io.deq
    io.r.valid := r_fifo.io.deq.valid && packet_complete.io.deq.valid
    r_fifo.io.deq.ready := io.r.ready && packet_complete.io.deq.valid
    io.w_metadata <> w_metadata_fifo.io.enq
    io.r_metadata <> r_metadata_fifo.io.enq

    b_fifo.io.enq.valid := write && state === State.sPayload && io.input.fire() && w_metadata_fifo.io.deq.valid
    b_fifo.io.enq.bits.id := w_metadata_fifo.io.deq.bits.id
    b_fifo.io.enq.bits.resp := io.input.bits.tdata(1, 0)

    r_fifo.io.enq.valid := ~write && state === State.sPayload && (frame_pointer === 4.U || io.input.bits.tlast.get) && io.input.fire() && r_metadata_fifo.io.deq.valid
    r_fifo.io.enq.bits.id := r_metadata_fifo.io.deq.bits.id
    r_fifo.io.enq.bits.data := data_word
    r_fifo.io.enq.bits.resp := io.input.bits.tdata(1, 0)
    r_fifo.io.enq.bits.last := io.input.bits.tlast.get

    packet_complete.io.enq.valid := ~write && state === State.sPayload && ~threshold_reached && (io.input.bits.tlast.get || counter === (r_metadata_fifo.io.deq.bits.length.get * 16.U - 1.U) / 5.U(13.W) + 1.U)
    packet_complete.io.enq.bits := true.B
    packet_complete.io.deq.ready := r_fifo.io.deq.fire() && r_fifo.io.deq.bits.last

    w_metadata_fifo.io.deq.ready := b_fifo.io.enq.fire()
    r_metadata_fifo.io.deq.ready := r_fifo.io.enq.fire() && r_fifo.io.enq.bits.last

    when (packet_complete.io.enq.fire()) {
        threshold_reached := true.B
    } .elsewhen (state === State.sIdle) {
        threshold_reached := false.B
    }

    switch (state) {
        is (State.sIdle) {
            when (io.input_header.fire() && io.input_header.bits.dst_port === PORT.U(16.W) && io.input_header.bits.ip.dst_ip === IP.U(32.W) && io.input_header.bits.ip.ethernet.dst_mac === MAC.U(48.W)) {
                state := State.sHeader
                frame_pointer := 0.U
            }
        }
        is (State.sHeader) {
            when (io.input.fire()) {
                write := io.input.bits.tdata(0)
                state := State.sPayload
            }
        }
        is (State.sPayload) {
            when (io.input.fire()) {
                when (write) {
                    when (io.input.bits.tlast.get) {
                        state := State.sIdle
                    } .otherwise {
                        state := State.sEnd
                    }
                } .otherwise {
                    when (io.input.bits.tlast.get) {
                        state := State.sIdle
                    }
                    when (frame_pointer === 4.U) {
                        frame_pointer := 0.U
                    } .otherwise {
                        frame_pointer := frame_pointer + 1.U
                    }
                    when (frame_pointer === 0.U) {
                        data_word := Cat(io.input.bits.tdata, data_word(23, 0))
                    } .elsewhen (frame_pointer === 1.U) {
                        data_word := Cat(data_word(31, 24), io.input.bits.tdata, data_word(15, 0))
                    } .elsewhen (frame_pointer === 2.U) {
                        data_word := Cat(data_word(31, 16), io.input.bits.tdata, data_word(7, 0))
                    } .elsewhen (frame_pointer === 3.U) {
                        data_word := Cat(data_word(31, 8), io.input.bits.tdata)
                    }
                    when (io.input.bits.tlast.get) {
                        counter := 0.U
                    } .otherwise {
                        counter := counter + 1.U
                    }
                }
            }
        }
        is (State.sEnd) {
            when (io.input.fire() && io.input.bits.tlast.get) {
                state := State.sIdle
            }
        }
    }
}

class UDPToAXI4FullMetadata(val CHANNEL: String) extends Bundle {
    val src_mac = UInt(48.W)
    val src_ip = UInt(32.W)
    val src_port = UInt(16.W)
    val length = if (CHANNEL == "R") Some(UInt(8.W)) else None   
}

class UDPToAXI4Full(val MAC: String,
                    val IP: String,
                    val PORT: String,
                    val DATA_WIDTH: Int = 32,
                    val ADDR_WIDTH: Int = 32,
                    val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val input_header = Flipped(Decoupled(new UDPFrameHeader))
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val M_AXI = new AXI4Full(DATA_WIDTH = DATA_WIDTH, ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH)
    })

    val tx = Module(new UDPToAXI4FullTx(MAC = MAC,
                                        IP = IP,
                                        PORT = PORT,
                                        DATA_WIDTH = DATA_WIDTH,
                                        ID_WIDTH = ID_WIDTH))
    val rx = Module(new UDPToAXI4FullRx(MAC = MAC,
                                        IP = IP,
                                        PORT = PORT,
                                        DATA_WIDTH = DATA_WIDTH,
                                        ADDR_WIDTH = ADDR_WIDTH,
                                        ID_WIDTH = ID_WIDTH))

    tx.io.output <> io.output
    tx.io.output_header <> io.output_header
    tx.io.r <> io.M_AXI.r
    tx.io.b <> io.M_AXI.b

    rx.io.input <> io.input
    rx.io.input_header <> io.input_header
    rx.io.ar <> io.M_AXI.ar
    rx.io.aw <> io.M_AXI.aw
    rx.io.w <> io.M_AXI.w

    tx.io.w_metadata <> rx.io.w_metadata
    tx.io.r_metadata <> rx.io.r_metadata
}

class UDPToAXI4FullTxB(val MAC: String,
                       val IP: String,
                       val PORT: String,
                       val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val b = Flipped(Irrevocable(new AXI4FullB(ID_WIDTH = ID_WIDTH)))
        val w_metadata = Flipped(Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "W")))
    })

    val input_fifo = Module(new Queue(new AXI4FullB(ID_WIDTH = ID_WIDTH), 4))
    val metadata_fifo = Module(new Queue(new UDPToAXI4FullMetadata(CHANNEL = "W"), 4))

    object State extends ChiselEnum {
        val sHeader, sPayload = Value
    }
    val state = RegInit(State.sHeader)

    io.output.valid := state === State.sPayload && input_fifo.io.deq.valid
    io.output.bits.tdata := Cat(0.U(6.W), input_fifo.io.deq.bits.resp)
    io.output.bits.tlast.get := true.B
    io.output.bits.tuser.get := 0.U

    io.output_header.valid := state === State.sHeader && metadata_fifo.io.deq.valid && input_fifo.io.deq.valid
    io.output_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    io.output_header.bits.ip.ethernet.dst_mac := metadata_fifo.io.deq.bits.src_mac
    io.output_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W)
    io.output_header.bits.ip.src_ip := IP.U(32.W)
    io.output_header.bits.ip.dst_ip := metadata_fifo.io.deq.bits.src_ip
    io.output_header.bits.ip.version := 4.U(4.W)
    io.output_header.bits.ip.ihl := 5.U(4.W)
    io.output_header.bits.ip.dscp := 0.U(6.W)
    io.output_header.bits.ip.ecn := 0.U(2.W)
    io.output_header.bits.ip.length := 20.U(16.W) + io.output_header.bits.length
    io.output_header.bits.ip.id := 0.U(16.W)
    io.output_header.bits.ip.flags := 2.U(3.W)
    io.output_header.bits.ip.fragment_offset := 0.U(13.W)
    io.output_header.bits.ip.ttl := 64.U (16.W)
    io.output_header.bits.ip.protocol := 17.U(8.W)
    io.output_header.bits.ip.checksum := 0.U
    io.output_header.bits.dst_port := metadata_fifo.io.deq.bits.src_port
    io.output_header.bits.src_port := PORT.U(16.W)
    io.output_header.bits.length := 1.U + 8.U
    io.output_header.bits.checksum := 0.U

    io.b <> input_fifo.io.enq
    input_fifo.io.deq.ready := state === State.sPayload && io.output.fire()

    metadata_fifo.io.enq <> io.w_metadata
    metadata_fifo.io.deq.ready := state === State.sHeader && io.output_header.fire()

    switch (state) {
        is (State.sHeader) {
            when (io.output_header.fire()) {
                state := State.sPayload
            }
        }
        is (State.sPayload) {
            when (io.output.fire()) {
                state := State.sHeader
            }
        }
    }
}

class UDPToAXI4FullTxR(val MAC: String,
                       val IP: String,
                       val PORT: String,
                       val DATA_WIDTH: Int = 32,
                       val ID_WIDTH: Int = 1) extends Module {
    val N_BYTES = DATA_WIDTH / 8
    val io = IO(new Bundle {
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val r = Flipped(Irrevocable(new AXI4FullR(DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH)))
        val r_metadata = Flipped(Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "R")))
    })

    val input_fifo = Module(new Queue(new AXI4FullR(DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH), 512))
    val metadata_fifo = Module(new Queue(new UDPToAXI4FullMetadata(CHANNEL = "R"), 4))
    val frame_pointer = RegInit(0.U(log2Ceil(N_BYTES).W))
    val response = RegInit(0.U(8.W))
    val tdata_bytes = Wire(Vec(N_BYTES, UInt(8.W)))

    tdata_bytes := input_fifo.io.deq.bits.data.asTypeOf(Vec(N_BYTES, UInt(8.W)))

    object State extends ChiselEnum {
        val sHeader, sPayload, sResponse = Value
    }
    val state = RegInit(State.sHeader)

    io.output.valid := (state === State.sPayload && input_fifo.io.deq.valid) || state === State.sResponse
    when (state === State.sPayload) {
        io.output.bits.tdata := tdata_bytes(N_BYTES.U - 1.U - frame_pointer)
    } .otherwise {
        io.output.bits.tdata := response
    }
    io.output.bits.tlast.get := state === State.sResponse
    io.output.bits.tuser.get := 0.U

    io.output_header.valid := state === State.sHeader && metadata_fifo.io.deq.valid && input_fifo.io.deq.valid
    io.output_header.bits.ip.ethernet.src_mac := MAC.U(48.W)
    io.output_header.bits.ip.ethernet.dst_mac := metadata_fifo.io.deq.bits.src_mac
    io.output_header.bits.ip.ethernet.ethernet_type := "h0800".U(16.W)
    io.output_header.bits.ip.src_ip := IP.U(32.W)
    io.output_header.bits.ip.dst_ip := metadata_fifo.io.deq.bits.src_ip
    io.output_header.bits.ip.version := 4.U(4.W)
    io.output_header.bits.ip.ihl := 5.U(4.W)
    io.output_header.bits.ip.dscp := 0.U(6.W)
    io.output_header.bits.ip.ecn := 0.U(2.W)
    io.output_header.bits.ip.length := 20.U(16.W) + io.output_header.bits.length
    io.output_header.bits.ip.id := 0.U(16.W)
    io.output_header.bits.ip.flags := 2.U(3.W)
    io.output_header.bits.ip.fragment_offset := 0.U(13.W)
    io.output_header.bits.ip.ttl := 64.U (16.W)
    io.output_header.bits.ip.protocol := 17.U(8.W)
    io.output_header.bits.ip.checksum := 0.U
    io.output_header.bits.dst_port := metadata_fifo.io.deq.bits.src_port
    io.output_header.bits.src_port := PORT.U(16.W)
    io.output_header.bits.length := (metadata_fifo.io.deq.bits.length.get +& 1.U) * N_BYTES.U + 1.U + 8.U // Data + Response + Header
    io.output_header.bits.checksum := 0.U

    io.r_metadata <> metadata_fifo.io.enq

    io.r <> input_fifo.io.enq
    input_fifo.io.deq.ready := state === State.sPayload && frame_pointer === (N_BYTES - 1).U && io.output.fire()

    metadata_fifo.io.deq.ready := io.output_header.ready && input_fifo.io.deq.valid

    when (state === State.sPayload && input_fifo.io.deq.fire()) {
        when (input_fifo.io.deq.bits.resp === 1.U(2.W)) {
            response := Cat(response(7, 4), 1.U(2.W), response(1, 0))
        } .elsewhen (input_fifo.io.deq.bits.resp === 2.U(2.W)) {
            response := Cat(response(7, 6), 2.U(2.W), response(3, 0))
        } .elsewhen (input_fifo.io.deq.bits.resp === 3.U(2.W)) {
            response := Cat(3.U(2.W), response(5, 0))
        }
    } .elsewhen (state === State.sResponse && io.output.fire()) {
        response := 0.U
    }

    switch (state) {
        is (State.sHeader) {
            when (io.output_header.fire()) {
                state := State.sPayload
            }
        }
        is (State.sPayload) {
            when (io.output.fire()) {
                when (input_fifo.io.deq.bits.last && frame_pointer === (N_BYTES - 1).U) {
                    state := State.sResponse
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
        is (State.sResponse) {
            when (io.output.fire()) {
                state := State.sHeader
            }
        }
    }
}

class UDPToAXI4FullTx(val MAC: String,
                      val IP: String,
                      val PORT: String,
                      val DATA_WIDTH: Int = 32,
                      val ID_WIDTH: Int = 1) extends Module {
    val io = IO(new Bundle {
        val output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                             KEEP_EN = false,
                                             LAST_EN = true,
                                             ID_WIDTH = 0,
                                             DEST_WIDTH = 0,
                                             USER_WIDTH = 1))
        val output_header = Decoupled(new UDPFrameHeader)
        val b = Flipped(Irrevocable(new AXI4FullB(ID_WIDTH = ID_WIDTH)))
        val r = Flipped(Irrevocable(new AXI4FullR(DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH)))
        val w_metadata = Flipped(Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "W")))
        val r_metadata = Flipped(Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "R")))
    })

    val udp_mux = Module(new UDPFrameMux(N_INPUTS = 2))
    val b = Module(new UDPToAXI4FullTxB(MAC = MAC, IP = IP, PORT = PORT, ID_WIDTH = ID_WIDTH))
    val r = Module(new UDPToAXI4FullTxR(MAC = MAC, IP = IP, PORT = PORT, DATA_WIDTH = DATA_WIDTH, ID_WIDTH = ID_WIDTH))

    b.io.b <> io.b
    b.io.w_metadata <> io.w_metadata
    r.io.r <> io.r
    r.io.r_metadata <> io.r_metadata

    udp_mux.io.inputs(0) <> b.io.output
    udp_mux.io.input_headers(0) <> b.io.output_header
    udp_mux.io.inputs(1) <> r.io.output
    udp_mux.io.input_headers(1) <> r.io.output_header

    io.output <> udp_mux.io.output
    io.output_header <> udp_mux.io.output_header
}

class UDPToAXI4FullRx(val MAC: String,
                      val IP: String,
                      val PORT: String,
                      val DATA_WIDTH: Int = 32,
                      val ADDR_WIDTH: Int = 32,
                      val ID_WIDTH: Int = 1) extends Module {
    val N_BYTES = DATA_WIDTH / 8
    val io = IO(new Bundle {
        val input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1)))
        val input_header = Flipped(Decoupled(new UDPFrameHeader))
        val aw = Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
        val w = Irrevocable(new AXI4FullW(DATA_WIDTH = DATA_WIDTH))
        val ar = Irrevocable(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
        val w_metadata = Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "W"))
        val r_metadata = Decoupled(new UDPToAXI4FullMetadata(CHANNEL = "R"))
    })

    val write = RegInit(false.B)
    val header = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val udp_header = Reg(new UDPFrameHeader)
    val data_word = Reg(Vec(N_BYTES - 1, UInt(8.W)))
    val frame_pointer = RegInit(0.U((log2Ceil(N_BYTES) + 1).W))
    val counter = RegInit(0.U(log2Ceil(N_BYTES * 256).W))
    val threshold_reached = RegInit(false.B)

    val aw_fifo = Module(new Queue(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH), 4))
    val ar_fifo = Module(new Queue(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH), 4))
    val w_fifo = Module(new Queue(new AXI4FullW(DATA_WIDTH = DATA_WIDTH), 512))

    val w_metadata_fifo = Module(new Queue(new UDPToAXI4FullMetadata(CHANNEL = "W"), 4))
    val r_metadata_fifo = Module(new Queue(new UDPToAXI4FullMetadata(CHANNEL = "R"), 4))

    val packet_complete = Module(new Queue(Bool(), 4))

    val word_strb = RegInit(0.U(4.W)) // To write 32-bit words

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.input_header.ready := state === State.sIdle && w_metadata_fifo.io.enq.ready && r_metadata_fifo.io.enq.ready
    io.input.ready := state === State.sIdle || state === State.sEnd || ((state === State.sHeader || state === State.sPayload) && aw_fifo.io.enq.ready && ar_fifo.io.enq.ready && w_fifo.io.enq.ready && r_metadata_fifo.io.enq.ready && packet_complete.io.enq.ready)

    io.aw <> aw_fifo.io.deq
    io.aw.valid := aw_fifo.io.deq.valid && packet_complete.io.deq.valid
    aw_fifo.io.deq.ready := io.aw.ready && packet_complete.io.deq.valid
    io.w <> w_fifo.io.deq
    io.w.valid := w_fifo.io.deq.valid && packet_complete.io.deq.valid
    w_fifo.io.deq.ready := io.w.ready && packet_complete.io.deq.valid
    io.ar <> ar_fifo.io.deq
    io.w_metadata <> w_metadata_fifo.io.deq
    io.r_metadata <> r_metadata_fifo.io.deq

    aw_fifo.io.enq.valid := write && state === State.sHeader && frame_pointer === 5.U && io.input.fire()
    aw_fifo.io.enq.bits.id := 0.U
    aw_fifo.io.enq.bits.addr := header.addr
    aw_fifo.io.enq.bits.len := io.input.bits.tdata
    aw_fifo.io.enq.bits.size := 4.U
    aw_fifo.io.enq.bits.burst := 1.U
    aw_fifo.io.enq.bits.lock := 0.U
    aw_fifo.io.enq.bits.cache := 0.U
    aw_fifo.io.enq.bits.prot := 0.U
    aw_fifo.io.enq.bits.qos := 0.U

    ar_fifo.io.enq.valid := ~write && state === State.sHeader && frame_pointer === 5.U && io.input.fire()
    ar_fifo.io.enq.bits.id := 0.U
    ar_fifo.io.enq.bits.addr := header.addr
    ar_fifo.io.enq.bits.len := io.input.bits.tdata
    ar_fifo.io.enq.bits.size := 4.U
    ar_fifo.io.enq.bits.burst := 1.U
    ar_fifo.io.enq.bits.lock := 0.U
    ar_fifo.io.enq.bits.cache := 0.U
    ar_fifo.io.enq.bits.prot := 0.U
    ar_fifo.io.enq.bits.qos := 0.U

    w_fifo.io.enq.valid := write && state === State.sPayload && (frame_pointer === (N_BYTES - 1).U || io.input.bits.tlast.get) && io.input.fire()
    w_fifo.io.enq.bits.data := Cat(data_word.asUInt, io.input.bits.tdata)
    w_fifo.io.enq.bits.strb := Cat(Mux(word_strb(3), "hF".U(4.W), 0.U(4.W)), Mux(word_strb(2), "hF".U(4.W), 0.U(4.W)), Mux(word_strb(1), "hF".U(4.W), 0.U(4.W)), Mux(word_strb(0), "hF".U(4.W), 0.U(4.W)))//~(0.U((DATA_WIDTH / 4).W))
    w_fifo.io.enq.bits.last := io.input.bits.tlast.get

    packet_complete.io.enq.valid := write && state === State.sPayload && ~threshold_reached && (io.input.bits.tlast.get || counter === (header.len * (N_BYTES * N_BYTES - N_BYTES).U - 1.U) / N_BYTES.U((log2Ceil(N_BYTES * 256) + 2).W) + 1.U)
    packet_complete.io.enq.bits := true.B
    packet_complete.io.deq.ready := w_fifo.io.deq.fire() && w_fifo.io.deq.bits.last

    w_metadata_fifo.io.enq.valid := write && state === State.sHeader && frame_pointer === 5.U && io.input.fire()
    w_metadata_fifo.io.enq.bits.src_mac := udp_header.ip.ethernet.src_mac
    w_metadata_fifo.io.enq.bits.src_ip := udp_header.ip.src_ip
    w_metadata_fifo.io.enq.bits.src_port := udp_header.src_port

    r_metadata_fifo.io.enq.valid := ~write && state === State.sHeader && frame_pointer === 5.U && io.input.fire()
    r_metadata_fifo.io.enq.bits.src_mac := udp_header.ip.ethernet.src_mac
    r_metadata_fifo.io.enq.bits.src_ip := udp_header.ip.src_ip
    r_metadata_fifo.io.enq.bits.src_port := udp_header.src_port
    r_metadata_fifo.io.enq.bits.length.get := io.input.bits.tdata

    when (packet_complete.io.enq.fire()) {
        threshold_reached := true.B
    } .elsewhen (state === State.sIdle) {
        threshold_reached := false.B
    }

    switch (state) {
        is (State.sIdle) {
            when (io.input_header.fire() && io.input_header.bits.dst_port === PORT.U(16.W) && io.input_header.bits.ip.dst_ip === IP.U(32.W) && io.input_header.bits.ip.ethernet.dst_mac === MAC.U(48.W)) {
                state := State.sHeader
                frame_pointer := 0.U
                udp_header := io.input_header.bits
            }
        }
        is (State.sHeader) {
            when (io.input.fire()) {
                when (write && frame_pointer === 5.U) {
                    state := State.sPayload
                } .elsewhen (~write && frame_pointer === 5.U) {
                    when (io.input.bits.tlast.get) {
                        state := State.sIdle
                    } .otherwise {
                        state := State.sEnd
                    }
                }
                when (write && frame_pointer === 5.U) {
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
                when (frame_pointer === 0.U) {
                    write := io.input.bits.tdata(0).asBool
                    word_strb := io.input.bits.tdata(7, 4)
                } .elsewhen (frame_pointer === 1.U) {
                    header.addr := Cat(io.input.bits.tdata, header.addr(23, 0))
                } .elsewhen (frame_pointer === 2.U) {
                    header.addr := Cat(header.addr(31, 24), io.input.bits.tdata, header.addr(15, 0))
                } .elsewhen (frame_pointer === 3.U) {
                    header.addr := Cat(header.addr(31, 16), io.input.bits.tdata, header.addr(7, 0))
                } .elsewhen (frame_pointer === 4.U) {
                    header.addr := Cat(header.addr(31, 8), io.input.bits.tdata)
                } .elsewhen (frame_pointer === 5.U) {
                    header.len := io.input.bits.tdata
                }
            }
        }
        is (State.sPayload) {
            when (io.input.fire()) {
                when (io.input.bits.tlast.get) {
                    state := State.sIdle
                }
                when (frame_pointer === (N_BYTES - 1).U) {
                    frame_pointer := 0.U
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
                data_word((N_BYTES - 2).U - frame_pointer) := io.input.bits.tdata
                when (io.input.bits.tlast.get) {
                    counter := 0.U
                } .otherwise {
                    counter := counter + 1.U
                }
            }
        }
        is (State.sEnd) {
            when (io.input.fire() && io.input.bits.tlast.get) {
                state := State.sIdle
            }
        }
    }
}

class Network(val MAC: String,
              val IP: String,
              val GATEWAY: String,
              val SUBNET: String) extends Module {
    val io = IO(new Bundle {
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet_reset = Input(Reset())
        val ethernet = new RGMIIPHYDuplex()
        val tx_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                       KEEP_EN = false,
                                                       LAST_EN = true,
                                                       ID_WIDTH = 0,
                                                       DEST_WIDTH = 0,
                                                       USER_WIDTH = 1)))
        val tx_header = Flipped(Decoupled(new UDPFrameHeader))
        val rx_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val rx_header = Decoupled(new UDPFrameHeader)
    })

    val ethernet_phy = Module(new EthernetPHY(PADDING = true, MINIMUM_FRAME_LENGTH = 64))
    val ethernet_frame = Module(new EthernetFrame)
    val ip_frame = Module(new IPFrame)
    val arp_frame = Module(new ARPFrame)
    val ethernet_mux = Module(new EthernetFrameMux(N_INPUTS = 2))
    val udp_frame = Module(new UDPFrame)

    ethernet_phy.io.phy_clock := io.ethernet_clock
    ethernet_phy.io.phy_clock_90 := io.ethernet_clock_90
    ethernet_phy.io.phy_reset := io.ethernet_reset
    ethernet_phy.io.phy <> io.ethernet
    ethernet_phy.io.tx_ifg_delay := 12.U

    ethernet_frame.io.rx_input <> ethernet_phy.io.rx
    ethernet_frame.io.tx_output <> ethernet_phy.io.tx

    arp_frame.io.ip_info.local_mac := MAC.U(48.W)
    arp_frame.io.ip_info.local_ip := IP.U(32.W)
    arp_frame.io.ip_info.gateway_ip := GATEWAY.U(32.W)
    arp_frame.io.ip_info.subnet_mask := SUBNET.U(32.W)
    ethernet_mux.io.inputs(0) <> arp_frame.io.tx_ethernet_output
    ethernet_mux.io.input_headers(0) <> arp_frame.io.tx_ethernet_header
    ethernet_mux.io.inputs(1) <> ip_frame.io.tx_ethernet_output
    ethernet_mux.io.input_headers(1) <> ip_frame.io.tx_ethernet_header
    arp_frame.io.rx_ethernet_input.valid := ethernet_frame.io.rx_output.valid
    arp_frame.io.rx_ethernet_input.bits := ethernet_frame.io.rx_output.bits
    arp_frame.io.rx_ethernet_header.valid := ethernet_frame.io.rx_header.valid
    arp_frame.io.rx_ethernet_header.bits := ethernet_frame.io.rx_header.bits
    ip_frame.io.rx_ethernet_input.valid := ethernet_frame.io.rx_output.valid
    ip_frame.io.rx_ethernet_input.bits := ethernet_frame.io.rx_output.bits
    ip_frame.io.rx_ethernet_header.valid := ethernet_frame.io.rx_header.valid
    ip_frame.io.rx_ethernet_header.bits := ethernet_frame.io.rx_header.bits
    ethernet_frame.io.rx_output.ready := arp_frame.io.rx_ethernet_input.ready && ip_frame.io.rx_ethernet_input.ready
    ethernet_frame.io.rx_header.ready := arp_frame.io.rx_ethernet_header.ready && ip_frame.io.rx_ethernet_header.ready
    ethernet_frame.io.tx_input <> ethernet_mux.io.output
    ethernet_frame.io.tx_header <> ethernet_mux.io.output_header

    ip_frame.io.rx_ip_header <> udp_frame.io.rx_ip_header
    ip_frame.io.rx_ip_output <> udp_frame.io.rx_ip_input
    ip_frame.io.tx_ip_header <> udp_frame.io.tx_ip_header
    ip_frame.io.tx_ip_input <> udp_frame.io.tx_ip_output

    udp_frame.io.rx_udp_header <> io.rx_header
    udp_frame.io.rx_udp_output <> io.rx_output
    udp_frame.io.tx_udp_header <> io.tx_header
    udp_frame.io.tx_udp_input <> io.tx_input
}