package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class IPFrameHeader(val DIRECTION: String) extends Bundle {
    val ethernet = new EthernetFrameHeader()
    val version = UInt(4.W)
    val ihl = UInt(4.W)
    val dscp = UInt(6.W)
    val ecn = UInt(2.W)
    val length = UInt(16.W)
    val id = UInt(16.W)
    val flags = UInt(3.W)
    val fragment_offset = UInt(13.W)
    val ttl = UInt(8.W)
    val protocol = UInt(8.W)
    val checksum = UInt(16.W)
    val src_ip = UInt(32.W)
    val dst_ip = UInt(32.W)
}

class IPFrameStatus(val DIRECTION: String) extends Bundle {
    val busy = Output(Bool())
    val error_incomplete_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_incomplete_payload = Output(Bool())
    val error_invalid_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_invalid_checksum = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class IPFrame extends Module {
    val io = IO(new Bundle {
        val tx_ip_header = Flipped(Decoupled(new IPFrameHeader(DIRECTION = "TRANSMIT")))
        val tx_ip_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                        KEEP_EN = false,
                                                        LAST_EN = true,
                                                        ID_WIDTH = 0,
                                                        DEST_WIDTH = 0,
                                                        USER_WIDTH = 1)))
        val tx_ethernet_header = Decoupled(new EthernetFrameHeader())
        val tx_ethernet_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                        KEEP_EN = false,
                                                        LAST_EN = true,
                                                        ID_WIDTH = 0,
                                                        DEST_WIDTH = 0,
                                                        USER_WIDTH = 1))
        val tx_status = new IPFrameStatus(DIRECTION = "TRANSMIT")
        val rx_ethernet_header = Flipped(Decoupled(new EthernetFrameHeader()))
        val rx_ethernet_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                                KEEP_EN = false,
                                                                LAST_EN = true,
                                                                ID_WIDTH = 0,
                                                                DEST_WIDTH = 0,
                                                                USER_WIDTH = 1)))
        val rx_ip_header = Decoupled(new IPFrameHeader(DIRECTION = "RECEIVE"))
        val rx_ip_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val rx_status = new IPFrameStatus(DIRECTION = "RECEIVE")
    })

    val tx = Module(new IPFrameTx())
    val rx = Module(new IPFrameRx())

    tx.io.ip_header <> io.tx_ip_header
    tx.io.ip_input <> io.tx_ip_input
    tx.io.ethernet_header <> io.tx_ethernet_header
    tx.io.ethernet_output <> io.tx_ethernet_output
    tx.io.status <> io.tx_status

    rx.io.ethernet_header <> io.rx_ethernet_header
    rx.io.ethernet_input <> io.rx_ethernet_input
    rx.io.ip_header <> io.rx_ip_header
    rx.io.ip_output <> io.rx_ip_output
    rx.io.status <> io.rx_status
}

class IPFrameTx extends Module {
    val io = IO(new Bundle {
        val ip_header = Flipped(Decoupled(new IPFrameHeader(DIRECTION = "TRANSMIT")))
        val ip_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                              KEEP_EN = false,
                                              LAST_EN = true,
                                              ID_WIDTH = 0,
                                              DEST_WIDTH = 0,
                                              USER_WIDTH = 1)))
        val ethernet_header = Decoupled(new EthernetFrameHeader())
        val ethernet_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1))
        val status = new IPFrameStatus(DIRECTION = "TRANSMIT")
    })

    val header_fifo = Module(new Queue(new IPFrameHeader(DIRECTION = "TRANSMIT"), 1))
    val input_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1), 4096))
                                                    
    val frame_pointer = RegInit(0.U(16.W))
    val header_checksum = RegInit(0.U(16.W))
    val busy = RegInit(false.B)
    val error_incomplete_payload = RegInit(false.B)

    val header_checksum_int = Wire(UInt(17.W))
    header_checksum_int := 0.U

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.ip_header <> header_fifo.io.enq
    io.ip_input <> input_fifo.io.enq
    io.status.busy := busy
    io.status.error_incomplete_payload := error_incomplete_payload

    header_fifo.io.deq.ready := (state === State.sHeader && frame_pointer === 19.U) || io.ethernet_header.ready
    input_fifo.io.deq.ready := (state === State.sPayload && io.ethernet_output.ready) || state === State.sEnd

    io.ethernet_header.bits <> header_fifo.io.deq.bits.ethernet
    io.ethernet_header.valid := header_fifo.io.deq.valid

    when (state === State.sHeader) {
        when (frame_pointer === 0.U) {
            io.ethernet_output.bits.tdata := Cat(header_fifo.io.deq.bits.version, header_fifo.io.deq.bits.ihl)
        } .elsewhen (frame_pointer === 1.U) {
            io.ethernet_output.bits.tdata := Cat(header_fifo.io.deq.bits.dscp, header_fifo.io.deq.bits.ecn)
            header_checksum := Cat(header_fifo.io.deq.bits.version, header_fifo.io.deq.bits.ihl, header_fifo.io.deq.bits.dscp, header_fifo.io.deq.bits.ecn)
        } .elsewhen (frame_pointer === 2.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.length(15, 8)
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.length
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 3.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.length(7, 0)
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.id
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 4.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.id(15, 8)
            header_checksum_int := header_checksum +& Cat(header_fifo.io.deq.bits.flags, header_fifo.io.deq.bits.fragment_offset)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 5.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.id(7, 0)
            header_checksum_int := header_checksum +& Cat(header_fifo.io.deq.bits.ttl, header_fifo.io.deq.bits.protocol)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 6.U) {
            io.ethernet_output.bits.tdata := Cat(header_fifo.io.deq.bits.flags, header_fifo.io.deq.bits.fragment_offset(12, 8))
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.src_ip(31, 16)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 7.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.fragment_offset(7, 0)
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.src_ip(15, 0)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 8.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.ttl
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.dst_ip(31, 16)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 9.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.protocol
            header_checksum_int := header_checksum +& header_fifo.io.deq.bits.dst_ip(15, 0)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 10.U) {
            io.ethernet_output.bits.tdata := ~header_checksum(15, 8)
        } .elsewhen (frame_pointer === 11.U) {
            io.ethernet_output.bits.tdata := ~header_checksum(7, 0)
        } .elsewhen (frame_pointer === 12.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.src_ip(31, 24)
        } .elsewhen (frame_pointer === 13.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.src_ip(23, 16)
        } .elsewhen (frame_pointer === 14.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.src_ip(15, 8)
        } .elsewhen (frame_pointer === 15.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.src_ip(7, 0)
        } .elsewhen (frame_pointer === 16.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.dst_ip(31, 24)
        } .elsewhen (frame_pointer === 17.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.dst_ip(23, 16)
        } .elsewhen (frame_pointer === 18.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.dst_ip(15, 8)
        } .elsewhen (frame_pointer === 19.U) {
            io.ethernet_output.bits.tdata := header_fifo.io.deq.bits.dst_ip(7, 0)
        } .otherwise {
            io.ethernet_output.bits.tdata := 0.U
        }
        io.ethernet_output.bits.tlast.get := false.B
        io.ethernet_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ethernet_output.valid := true.B
    } .elsewhen (state === State.sPayload) {
        io.ethernet_output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.ethernet_output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get || frame_pointer === 1.U
        io.ethernet_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ethernet_output.valid := input_fifo.io.deq.valid
        header_checksum := 0.U
    } .otherwise {
        io.ethernet_output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.ethernet_output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get
        io.ethernet_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ethernet_output.valid := false.B
        header_checksum := 0.U
    }
    
    switch (state) {
        is (State.sIdle) {
            when (header_fifo.io.deq.valid && input_fifo.io.deq.valid) {
                state := State.sHeader
                frame_pointer := 0.U
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ethernet_output.fire()) {
                when (frame_pointer === 19.U) {
                    frame_pointer := header_fifo.io.deq.bits.length - 20.U
                    state := State.sPayload
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
        is (State.sPayload) {
            when (io.ethernet_output.fire()) {
                frame_pointer := frame_pointer - 1.U
                when (input_fifo.io.deq.bits.tlast.get && frame_pointer === 1.U) {
                    state := State.sIdle
                    busy := false.B
                } .elsewhen (input_fifo.io.deq.bits.tlast.get && frame_pointer > 1.U) {
                    error_incomplete_payload := true.B
                    state := State.sIdle
                    busy := false.B
                } .elsewhen (frame_pointer === 1.U) {
                    state := State.sEnd
                }
            }
        }
        is (State.sEnd) {
            when (input_fifo.io.deq.bits.tlast.get) {
                state := State.sIdle
                busy := false.B
            }
        }
    }
}


class IPFrameRx extends Module {
    val io = IO(new Bundle {
        val ethernet_header = Flipped(Decoupled(new EthernetFrameHeader()))
        val ethernet_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                             KEEP_EN = false,
                                                             LAST_EN = true,
                                                             ID_WIDTH = 0,
                                                             DEST_WIDTH = 0,
                                                             USER_WIDTH = 1)))
        val ip_header = Decoupled(new IPFrameHeader(DIRECTION = "RECEIVE"))
        val ip_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val status = new IPFrameStatus(DIRECTION = "RECEIVE")
    })

    val header_fifo = Module(new Queue(new IPFrameHeader(DIRECTION = "RECEIVE"), 1))
    val output_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1), 4096))

    val header = Reg(new IPFrameHeader(DIRECTION = "RECEIVE"))
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(16.W))
    val header_checksum = RegInit(0.U(16.W))
    val busy = RegInit(false.B)
    val error_incomplete_header = RegInit(false.B)
    val error_incomplete_payload = RegInit(false.B)
    val error_invalid_header = RegInit(false.B)
    val error_invalid_checksum = RegInit(false.B)

    val header_checksum_int = Wire(UInt(17.W))
    header_checksum_int := header_checksum +& Cat(io.ethernet_input.bits.tdata, 0.U(8.W))

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.ethernet_header.ready := header_fifo.io.enq.ready && state === State.sIdle
    io.ethernet_input.ready := state === State.sIdle || state === State.sEnd || (state === State.sHeader && header_fifo.io.enq.ready) || (state === State.sPayload && output_fifo.io.enq.ready)
    io.ip_header.bits := header_fifo.io.deq.bits
    io.ip_header.valid := header_fifo.io.deq.valid
    io.ip_output <> output_fifo.io.deq
    io.status.busy := busy
    io.status.error_incomplete_header.get := error_incomplete_header
    io.status.error_incomplete_payload := error_incomplete_payload
    io.status.error_invalid_header.get := error_invalid_header
    io.status.error_invalid_checksum.get := error_invalid_checksum

    header_fifo.io.enq.bits := header
    header_fifo.io.enq.valid := header_valid
    header_fifo.io.deq.ready := io.ethernet_input.bits.tlast.get || io.ip_header.ready

    output_fifo.io.enq.bits.tdata := io.ethernet_input.bits.tdata
    output_fifo.io.enq.bits.tlast.get := io.ethernet_input.bits.tlast.get || (state === State.sPayload && frame_pointer === 1.U)
    output_fifo.io.enq.bits.tuser.get := io.ethernet_input.bits.tuser.get
    output_fifo.io.enq.valid := io.ethernet_input.fire() && state === State.sPayload

    switch (state) {
        is (State.sIdle) {
            error_incomplete_header := false.B
            error_incomplete_payload := false.B
            error_invalid_header := false.B
            error_invalid_checksum := false.B
            header_checksum := 0.U
            when (io.ethernet_header.fire()) {
                header.ethernet := io.ethernet_header.bits
            }
            when (io.ethernet_input.fire()) {
                state := State.sHeader
                frame_pointer := 1.U
                header.version := io.ethernet_input.bits.tdata(7, 4)
                header.ihl := io.ethernet_input.bits.tdata(3, 0)
                header_checksum := Cat(0.U(8.W), io.ethernet_input.bits.tdata)
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ethernet_input.fire()) {
                frame_pointer := frame_pointer + 1.U
                when (io.ethernet_input.bits.tlast.get) {
                    state := State.sIdle
                    error_incomplete_header := true.B
                } .elsewhen (frame_pointer === 19.U) {
                    frame_pointer := header.length - 20.U
                    when (header.version =/= 4.U(4.W) || header.ihl =/= 5.U(4.W)) {
                        error_invalid_header := true.B
                        state := State.sEnd
                    } .elsewhen (header_checksum_int(15, 0) + header_checksum_int(16) =/= "hFFFF".U(16.W)) {
                        error_invalid_checksum := true.B
                        state := State.sEnd
                    } .otherwise {
                        header_valid := true.B
                        state := State.sPayload
                    }
                }
                when (frame_pointer(0) === 1.U) {
                    header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
                } .otherwise {
                    header_checksum := header_checksum + Cat(0.U(8.W), io.ethernet_input.bits.tdata)
                }
                switch (frame_pointer) {
                    is (1.U) {
                        header.dscp := io.ethernet_input.bits.tdata(7, 2)
                        header.ecn := io.ethernet_input.bits.tdata(1, 0)
                    }
                    is (2.U) {
                        header.length := Cat(io.ethernet_input.bits.tdata, header.length(7, 0))
                    }
                    is (3.U) {
                        header.length := Cat(header.length(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (4.U) {
                        header.id := Cat(io.ethernet_input.bits.tdata, header.id(7, 0))
                    }
                    is (5.U) {
                        header.id := Cat(header.id(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (6.U) {
                        header.flags := io.ethernet_input.bits.tdata(7, 5)
                        header.fragment_offset := Cat(io.ethernet_input.bits.tdata(4, 0), header.fragment_offset(7, 0))
                    }
                    is (7.U) {
                        header.fragment_offset := Cat(header.fragment_offset(4, 0), io.ethernet_input.bits.tdata)
                    }
                    is (8.U) {
                        header.ttl := io.ethernet_input.bits.tdata
                    }
                    is (9.U) {
                        header.protocol := io.ethernet_input.bits.tdata
                    }
                    is (10.U) {
                        header.checksum := Cat(io.ethernet_input.bits.tdata, header.checksum(7, 0))
                    }
                    is (11.U) {
                        header.checksum := Cat(header.checksum(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (12.U) {
                        header.src_ip := Cat(io.ethernet_input.bits.tdata, header.src_ip(23, 0))
                    }
                    is (13.U) {
                        header.src_ip := Cat(header.src_ip(31, 24), io.ethernet_input.bits.tdata, header.src_ip(15, 0))
                    }
                    is (14.U) {
                        header.src_ip := Cat(header.src_ip(31, 16), io.ethernet_input.bits.tdata, header.src_ip(7, 0))
                    }
                    is (15.U) {
                        header.src_ip := Cat(header.src_ip(31, 8), io.ethernet_input.bits.tdata)
                    }
                    is (16.U) {
                        header.dst_ip := Cat(io.ethernet_input.bits.tdata, header.dst_ip(23, 0))
                    }
                    is (17.U) {
                        header.dst_ip := Cat(header.dst_ip(31, 24), io.ethernet_input.bits.tdata, header.dst_ip(15, 0))
                    }
                    is (18.U) {
                        header.dst_ip := Cat(header.dst_ip(31, 16), io.ethernet_input.bits.tdata, header.dst_ip(7, 0))
                    }
                    is (19.U) {
                        header.dst_ip := Cat(header.dst_ip(31, 8), io.ethernet_input.bits.tdata)
                    }
                }
            }
        }
        is (State.sPayload) {
            when (header_fifo.io.enq.fire()) {
                header_valid := false.B
            }
            when (io.ethernet_input.fire()) {
                frame_pointer := frame_pointer - 1.U
                when (io.ethernet_input.bits.tlast.get && frame_pointer === 1.U) {
                    state := State.sIdle
                    busy := false.B
                } .elsewhen (io.ethernet_input.bits.tlast.get && frame_pointer > 1.U) {
                    error_incomplete_payload := true.B
                    state := State.sIdle
                    busy := false.B
                } .elsewhen (frame_pointer === 1.U) {
                    state := State.sEnd
                }
            }
        }
        is (State.sEnd) {
            when (io.ethernet_input.fire()) {
                when (io.ethernet_input.bits.tlast.get) {
                    state := State.sIdle
                    busy := false.B
                }
            }
        }
    }
}