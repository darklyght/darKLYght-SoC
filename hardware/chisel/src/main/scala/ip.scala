package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class IPFrameHeader extends Bundle {
    val ethernet = new EthernetFrameHeader
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

class ARPFrameHeader extends Bundle {
    val ethernet = new EthernetFrameHeader
    val htype = UInt(16.W)
    val ptype = UInt(16.W)
    val hlen = UInt(8.W)
    val plen = UInt(8.W)
    val opcode = UInt(16.W)
    val sha = UInt(48.W)
    val spa = UInt(32.W)
    val tha = UInt(48.W)
    val tpa = UInt(32.W)
}

class IPFrameStatus(val DIRECTION: String) extends Bundle {
    val busy = Output(Bool())
    val error_incomplete_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_incomplete_payload = Output(Bool())
    val error_invalid_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_invalid_checksum = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class ARPFrameStatus(val DIRECTION: String) extends Bundle {
    val busy = Output(Bool())
    val error_incomplete_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_invalid_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class IPInfo extends Bundle {
    val local_mac = UInt(48.W)
    val local_ip = UInt(32.W)
    val gateway_ip = UInt(32.W)
    val subnet_mask = UInt(32.W)
}

class IPFrame extends Module {
    val io = IO(new Bundle {
        val tx_ip_header = Flipped(Decoupled(new IPFrameHeader))
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
        val rx_ip_header = Decoupled(new IPFrameHeader)
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

class ARPFrame extends Module {
    val io = IO(new Bundle {
        //val tx_arp_header = Flipped(Decoupled(new ARPFrameHeader))
        val tx_ethernet_header = Decoupled(new EthernetFrameHeader())
        val tx_ethernet_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                        KEEP_EN = false,
                                                        LAST_EN = true,
                                                        ID_WIDTH = 0,
                                                        DEST_WIDTH = 0,
                                                        USER_WIDTH = 1))
        val tx_status = new ARPFrameStatus(DIRECTION = "TRANSMIT")
        val rx_ethernet_header = Flipped(Decoupled(new EthernetFrameHeader()))
        val rx_ethernet_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                                KEEP_EN = false,
                                                                LAST_EN = true,
                                                                ID_WIDTH = 0,
                                                                DEST_WIDTH = 0,
                                                                USER_WIDTH = 1)))
        //val rx_arp_header = Decoupled(new ARPFrameHeader)
        val rx_status = new ARPFrameStatus(DIRECTION = "RECEIVE")
        val ip_info = Input(new IPInfo())
    })

    val tx_fifo = Module(new Queue(new ARPFrameHeader, 1))
    val tx = Module(new ARPFrameTx())
    val rx = Module(new ARPFrameRx())

    tx.io.arp_header <> tx_fifo.io.deq
    tx.io.ethernet_header <> io.tx_ethernet_header
    tx.io.ethernet_output <> io.tx_ethernet_output
    tx.io.status <> io.tx_status

    rx.io.arp_header.ready := tx_fifo.io.enq.ready
    rx.io.ethernet_header <> io.rx_ethernet_header
    rx.io.ethernet_input <> io.rx_ethernet_input
    rx.io.status <> io.rx_status

    tx_fifo.io.enq.bits.ethernet.dst_mac := rx.io.arp_header.bits.ethernet.src_mac
    tx_fifo.io.enq.bits.ethernet.src_mac := io.ip_info.local_mac
    tx_fifo.io.enq.bits.ethernet.ethernet_type := "h0806".U(16.W)
    tx_fifo.io.enq.bits.htype := "h0001".U(16.W)
    tx_fifo.io.enq.bits.ptype := "h0800".U(16.W)
    tx_fifo.io.enq.bits.hlen := 6.U(8.W)
    tx_fifo.io.enq.bits.plen := 4.U(8.W)
    tx_fifo.io.enq.bits.opcode := 2.U
    tx_fifo.io.enq.bits.sha := io.ip_info.local_mac
    tx_fifo.io.enq.bits.spa := io.ip_info.local_ip
    tx_fifo.io.enq.bits.tha := rx.io.arp_header.bits.sha
    tx_fifo.io.enq.bits.tpa := rx.io.arp_header.bits.spa
    tx_fifo.io.enq.valid := false.B

    when (rx.io.arp_header.fire()) {
        when (rx.io.arp_header.bits.ethernet.ethernet_type === "h0806".U(16.W) && rx.io.arp_header.bits.htype === "h0001".U(16.W) && rx.io.arp_header.bits.ptype === "h0800".U(16.W)) {
            when (rx.io.arp_header.bits.opcode === 1.U(16.W)) {
                when (rx.io.arp_header.bits.tpa === io.ip_info.local_ip) {
                    tx_fifo.io.enq.bits.opcode := 2.U(16.W)
                    tx_fifo.io.enq.valid := true.B
                }
            } .elsewhen (rx.io.arp_header.bits.opcode === 8.U(16.W)) {
                when (rx.io.arp_header.bits.tha === io.ip_info.local_mac) {
                    tx_fifo.io.enq.bits.opcode := 9.U(16.W)
                    tx_fifo.io.enq.valid := true.B
                }
            }
        }
    }
}

class ARPFrameTx extends Module {
    val io = IO(new Bundle {
        val arp_header = Flipped(Decoupled(new ARPFrameHeader))
        val ethernet_header = Decoupled(new EthernetFrameHeader())
        val ethernet_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                      KEEP_EN = false,
                                                      LAST_EN = true,
                                                      ID_WIDTH = 0,
                                                      DEST_WIDTH = 0,
                                                      USER_WIDTH = 1))
        val status = new ARPFrameStatus(DIRECTION = "TRANSMIT")
    })

    val header = Reg(new ARPFrameHeader)
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(16.W))
    val busy = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader = Value
    }
    val state = RegInit(State.sIdle)

    io.arp_header.ready := state === State.sIdle
    io.status.busy := busy

    io.ethernet_header.bits := header.ethernet
    io.ethernet_header.valid := header_valid

    when (io.arp_header.fire()) {
        header := io.arp_header.bits
    }

    when (io.arp_header.fire()) {
        header_valid := true.B
    } .elsewhen(io.ethernet_header.fire()) {
        header_valid := false.B
    }

    when (state === State.sHeader) {
        when (frame_pointer === 0.U) {
            io.ethernet_output.bits.tdata := header.htype(15, 8)
        } .elsewhen (frame_pointer === 1.U) {
            io.ethernet_output.bits.tdata := header.htype(7, 0)
        } .elsewhen (frame_pointer === 2.U) {
            io.ethernet_output.bits.tdata := header.ptype(15, 8)
        } .elsewhen (frame_pointer === 3.U) {
            io.ethernet_output.bits.tdata := header.ptype(7, 0)
        } .elsewhen (frame_pointer === 4.U) {
            io.ethernet_output.bits.tdata := 6.U(8.W)
        } .elsewhen (frame_pointer === 5.U) {
            io.ethernet_output.bits.tdata := 4.U(8.W)
        } .elsewhen (frame_pointer === 6.U) {
            io.ethernet_output.bits.tdata := header.opcode(15, 8)
        } .elsewhen (frame_pointer === 7.U) {
            io.ethernet_output.bits.tdata := header.opcode(7, 0)
        } .elsewhen (frame_pointer === 8.U) {
            io.ethernet_output.bits.tdata := header.sha(47, 40)
        } .elsewhen (frame_pointer === 9.U) {
            io.ethernet_output.bits.tdata := header.sha(39, 32)
        } .elsewhen (frame_pointer === 10.U) {
            io.ethernet_output.bits.tdata := header.sha(31, 24)
        } .elsewhen (frame_pointer === 11.U) {
            io.ethernet_output.bits.tdata := header.sha(23, 16)
        } .elsewhen (frame_pointer === 12.U) {
            io.ethernet_output.bits.tdata := header.sha(15, 8)
        } .elsewhen (frame_pointer === 13.U) {
            io.ethernet_output.bits.tdata := header.sha(7, 0)
        } .elsewhen (frame_pointer === 14.U) {
            io.ethernet_output.bits.tdata := header.spa(31, 24)
        } .elsewhen (frame_pointer === 15.U) {
            io.ethernet_output.bits.tdata := header.spa(23, 16)
        } .elsewhen (frame_pointer === 16.U) {
            io.ethernet_output.bits.tdata := header.spa(15, 8)
        } .elsewhen (frame_pointer === 17.U) {
            io.ethernet_output.bits.tdata := header.spa(7, 0)
        } .elsewhen (frame_pointer === 18.U) {
            io.ethernet_output.bits.tdata := header.tha(47, 40)
        } .elsewhen (frame_pointer === 19.U) {
            io.ethernet_output.bits.tdata := header.tha(39, 32)
        } .elsewhen (frame_pointer === 20.U) {
            io.ethernet_output.bits.tdata := header.tha(31, 24)
        } .elsewhen (frame_pointer === 21.U) {
            io.ethernet_output.bits.tdata := header.tha(23, 16)
        } .elsewhen (frame_pointer === 22.U) {
            io.ethernet_output.bits.tdata := header.tha(15, 8)
        } .elsewhen (frame_pointer === 23.U) {
            io.ethernet_output.bits.tdata := header.tha(7, 0)
        } .elsewhen (frame_pointer === 24.U) {
            io.ethernet_output.bits.tdata := header.tpa(31, 24)
        } .elsewhen (frame_pointer === 25.U) {
            io.ethernet_output.bits.tdata := header.tpa(23, 16)
        } .elsewhen (frame_pointer === 26.U) {
            io.ethernet_output.bits.tdata := header.tpa(15, 8)
        } .elsewhen (frame_pointer === 27.U) {
            io.ethernet_output.bits.tdata := header.tpa(7, 0)
        } .otherwise {
            io.ethernet_output.bits.tdata := 0.U
        }
        io.ethernet_output.bits.tlast.get := frame_pointer === 27.U
        io.ethernet_output.bits.tuser.get := 0.U
        io.ethernet_output.valid := true.B
    } .otherwise {
        io.ethernet_output.bits.tdata := 0.U
        io.ethernet_output.bits.tlast.get := false.B
        io.ethernet_output.bits.tuser.get := 0.U
        io.ethernet_output.valid := false.B
    }
    
    switch (state) {
        is (State.sIdle) {
            when (io.arp_header.fire()) {
                state := State.sHeader
                frame_pointer := 0.U
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ethernet_output.fire()) {
                frame_pointer := frame_pointer + 1.U
                when (frame_pointer === 27.U) {
                    state := State.sIdle
                }
            }
        }
    }
}

class ARPFrameRx extends Module {
    val io = IO(new Bundle {
        val ethernet_header = Flipped(Decoupled(new EthernetFrameHeader()))
        val ethernet_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                             KEEP_EN = false,
                                                             LAST_EN = true,
                                                             ID_WIDTH = 0,
                                                             DEST_WIDTH = 0,
                                                             USER_WIDTH = 1)))
        val arp_header = Decoupled(new ARPFrameHeader)
        val status = new ARPFrameStatus(DIRECTION = "RECEIVE")
    })

    val header = Reg(new ARPFrameHeader)
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(16.W))
    val busy = RegInit(false.B)
    val error_incomplete_header = RegInit(false.B)
    val error_invalid_header = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.ethernet_header.ready := state === State.sIdle
    io.ethernet_input.ready := state === State.sIdle || state === State.sEnd || (state === State.sHeader && ~header_valid)
    io.arp_header.bits := header
    io.arp_header.valid := header_valid
    io.status.busy := busy
    io.status.error_incomplete_header.get := error_incomplete_header
    io.status.error_invalid_header.get := error_invalid_header

    when (io.arp_header.fire()) {
        header_valid := false.B
    } .elsewhen (io.ethernet_input.fire() && state === State.sHeader && frame_pointer === 27.U) {
        header_valid := true.B
    }

    switch (state) {
        is (State.sIdle) {
            error_incomplete_header := false.B
            error_invalid_header := false.B
            when (io.ethernet_header.fire()) {
                header.ethernet := io.ethernet_header.bits
            }
            when (io.ethernet_input.fire() && ~header_valid) {
                state := State.sHeader
                frame_pointer := 1.U
                header.htype := Cat(io.ethernet_input.bits.tdata, header.htype(7, 0))
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ethernet_input.fire()) {
                frame_pointer := frame_pointer + 1.U
                when (io.ethernet_input.bits.tlast.get && frame_pointer === 27.U) {
                    state := State.sIdle
                    busy := false.B
                    when (header.hlen =/= 6.U(8.W) || header.plen =/= 4.U(8.W)) {
                        error_invalid_header := true.B
                    }
                    header_valid := true.B
                } .elsewhen (io.ethernet_input.bits.tlast.get && frame_pointer < 27.U) {
                    state := State.sIdle
                    busy := false.B
                    error_incomplete_header := true.B
                } .elsewhen (frame_pointer === 27.U) {
                    header_valid := true.B
                    state := State.sEnd
                }

                switch (frame_pointer) {
                    is (1.U) {
                        header.htype := Cat(header.htype(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (2.U) {
                        header.ptype := Cat(io.ethernet_input.bits.tdata, header.ptype(7, 0))
                    }
                    is (3.U) {
                        header.ptype := Cat(header.ptype(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (4.U) {
                        header.hlen := io.ethernet_input.bits.tdata
                    }
                    is (5.U) {
                        header.plen := io.ethernet_input.bits.tdata
                    }
                    is (6.U) {
                        header.opcode := Cat(io.ethernet_input.bits.tdata, header.opcode(7, 0))
                    }
                    is (7.U) {
                        header.opcode := Cat(header.opcode(15, 8), io.ethernet_input.bits.tdata)
                    }
                    is (8.U) {
                        header.sha := Cat(io.ethernet_input.bits.tdata, header.sha(39, 0))
                    }
                    is (9.U) {
                        header.sha := Cat(header.sha(47, 40), io.ethernet_input.bits.tdata, header.sha(31, 0))
                    }
                    is (10.U) {
                        header.sha := Cat(header.sha(47, 32), io.ethernet_input.bits.tdata, header.sha(23, 0))
                    }
                    is (11.U) {
                        header.sha := Cat(header.sha(47, 24), io.ethernet_input.bits.tdata, header.sha(15, 0))
                    }
                    is (12.U) {
                        header.sha := Cat(header.sha(47, 16), io.ethernet_input.bits.tdata, header.sha(7, 0))
                    }
                    is (13.U) {
                        header.sha := Cat(header.sha(47, 8), io.ethernet_input.bits.tdata)
                    }
                    is (14.U) {
                        header.spa := Cat(io.ethernet_input.bits.tdata, header.spa(23, 0))
                    }
                    is (15.U) {
                        header.spa := Cat(header.spa(31, 24), io.ethernet_input.bits.tdata, header.spa(15, 0))
                    }
                    is (16.U) {
                        header.spa := Cat(header.spa(31, 16), io.ethernet_input.bits.tdata, header.spa(7, 0))
                    }
                    is (17.U) {
                        header.spa := Cat(header.spa(31, 8), io.ethernet_input.bits.tdata)
                    }
                    is (18.U) {
                        header.tha := Cat(io.ethernet_input.bits.tdata, header.tha(39, 0))
                    }
                    is (19.U) {
                        header.tha := Cat(header.tha(47, 40), io.ethernet_input.bits.tdata, header.tha(31, 0))
                    }
                    is (20.U) {
                        header.tha := Cat(header.tha(47, 32), io.ethernet_input.bits.tdata, header.tha(23, 0))
                    }
                    is (21.U) {
                        header.tha := Cat(header.tha(47, 24), io.ethernet_input.bits.tdata, header.tha(15, 0))
                    }
                    is (22.U) {
                        header.tha := Cat(header.tha(47, 16), io.ethernet_input.bits.tdata, header.tha(7, 0))
                    }
                    is (23.U) {
                        header.tha := Cat(header.tha(47, 8), io.ethernet_input.bits.tdata)
                    }
                    is (24.U) {
                        header.tpa := Cat(io.ethernet_input.bits.tdata, header.tpa(23, 0))
                    }
                    is (25.U) {
                        header.tpa := Cat(header.tpa(31, 24), io.ethernet_input.bits.tdata, header.tpa(15, 0))
                    }
                    is (26.U) {
                        header.tpa := Cat(header.tpa(31, 16), io.ethernet_input.bits.tdata, header.tpa(7, 0))
                    }
                    is (27.U) {
                        header.tpa := Cat(header.tpa(31, 8), io.ethernet_input.bits.tdata)
                    }
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

class IPFrameTx extends Module {
    val io = IO(new Bundle {
        val ip_header = Flipped(Decoupled(new IPFrameHeader))
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
    val header = Reg(new IPFrameHeader)
    val header_valid = RegInit(false.B)
    val input_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1), 16))
                                                    
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

    io.ip_header.ready := state === State.sIdle
    io.ip_input <> input_fifo.io.enq
    io.status.busy := busy
    io.status.error_incomplete_payload := error_incomplete_payload

    input_fifo.io.deq.ready := (state === State.sPayload && io.ethernet_output.ready) || state === State.sEnd

    io.ethernet_header.bits := header.ethernet
    io.ethernet_header.valid := header_valid

    when (io.ip_header.fire()) {
        header := io.ip_header.bits
    }

    when (io.ip_header.fire()) {
        header_valid := true.B
    } .elsewhen(io.ethernet_header.fire()) {
        header_valid := false.B
    }

    when (state === State.sHeader) {
        when (frame_pointer === 0.U) {
            io.ethernet_output.bits.tdata := Cat(header.version, header.ihl)
        } .elsewhen (frame_pointer === 1.U) {
            io.ethernet_output.bits.tdata := Cat(header.dscp, header.ecn)
            header_checksum := Cat(header.version, header.ihl, header.dscp, header.ecn)
        } .elsewhen (frame_pointer === 2.U) {
            io.ethernet_output.bits.tdata := header.length(15, 8)
            header_checksum_int := header_checksum +& header.length
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 3.U) {
            io.ethernet_output.bits.tdata := header.length(7, 0)
            header_checksum_int := header_checksum +& header.id
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 4.U) {
            io.ethernet_output.bits.tdata := header.id(15, 8)
            header_checksum_int := header_checksum +& Cat(header.flags, header.fragment_offset)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 5.U) {
            io.ethernet_output.bits.tdata := header.id(7, 0)
            header_checksum_int := header_checksum +& Cat(header.ttl, header.protocol)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 6.U) {
            io.ethernet_output.bits.tdata := Cat(header.flags, header.fragment_offset(12, 8))
            header_checksum_int := header_checksum +& header.src_ip(31, 16)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 7.U) {
            io.ethernet_output.bits.tdata := header.fragment_offset(7, 0)
            header_checksum_int := header_checksum +& header.src_ip(15, 0)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 8.U) {
            io.ethernet_output.bits.tdata := header.ttl
            header_checksum_int := header_checksum +& header.dst_ip(31, 16)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 9.U) {
            io.ethernet_output.bits.tdata := header.protocol
            header_checksum_int := header_checksum +& header.dst_ip(15, 0)
            header_checksum := header_checksum_int(15, 0) + header_checksum_int(16)
        } .elsewhen (frame_pointer === 10.U) {
            io.ethernet_output.bits.tdata := ~header_checksum(15, 8)
        } .elsewhen (frame_pointer === 11.U) {
            io.ethernet_output.bits.tdata := ~header_checksum(7, 0)
        } .elsewhen (frame_pointer === 12.U) {
            io.ethernet_output.bits.tdata := header.src_ip(31, 24)
        } .elsewhen (frame_pointer === 13.U) {
            io.ethernet_output.bits.tdata := header.src_ip(23, 16)
        } .elsewhen (frame_pointer === 14.U) {
            io.ethernet_output.bits.tdata := header.src_ip(15, 8)
        } .elsewhen (frame_pointer === 15.U) {
            io.ethernet_output.bits.tdata := header.src_ip(7, 0)
        } .elsewhen (frame_pointer === 16.U) {
            io.ethernet_output.bits.tdata := header.dst_ip(31, 24)
        } .elsewhen (frame_pointer === 17.U) {
            io.ethernet_output.bits.tdata := header.dst_ip(23, 16)
        } .elsewhen (frame_pointer === 18.U) {
            io.ethernet_output.bits.tdata := header.dst_ip(15, 8)
        } .elsewhen (frame_pointer === 19.U) {
            io.ethernet_output.bits.tdata := header.dst_ip(7, 0)
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
            when (io.ip_header.fire()) {
                state := State.sHeader
                frame_pointer := 0.U
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ethernet_output.fire()) {
                when (frame_pointer === 19.U) {
                    frame_pointer := header.length - 20.U
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
        val ip_header = Decoupled(new IPFrameHeader)
        val ip_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val status = new IPFrameStatus(DIRECTION = "RECEIVE")
    })
    val output_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1), 16))

    val header = Reg(new IPFrameHeader)
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

    io.ethernet_header.ready := state === State.sIdle
    io.ethernet_input.ready := state === State.sIdle || state === State.sEnd || (state === State.sHeader && ~header_valid) || (state === State.sPayload && output_fifo.io.enq.ready)
    io.ip_header.bits := header
    io.ip_header.valid := header_valid
    io.ip_output <> output_fifo.io.deq
    io.status.busy := busy
    io.status.error_incomplete_header.get := error_incomplete_header
    io.status.error_incomplete_payload := error_incomplete_payload
    io.status.error_invalid_header.get := error_invalid_header
    io.status.error_invalid_checksum.get := error_invalid_checksum

    output_fifo.io.enq.bits.tdata := io.ethernet_input.bits.tdata
    output_fifo.io.enq.bits.tlast.get := io.ethernet_input.bits.tlast.get || (state === State.sPayload && frame_pointer === 1.U)
    output_fifo.io.enq.bits.tuser.get := io.ethernet_input.bits.tuser.get
    output_fifo.io.enq.valid := io.ethernet_input.fire() && state === State.sPayload

    when (io.ip_header.fire()) {
        header_valid := false.B
    } .elsewhen (io.ethernet_input.fire() && state === State.sHeader && frame_pointer === 19.U) {
        header_valid := true.B
    }

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
            when (io.ethernet_input.fire() && ~header_valid) {
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