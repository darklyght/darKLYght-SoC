package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class UDPFrameHeader extends Bundle {
    val ip = new IPFrameHeader
    val src_port = UInt(16.W)
    val dst_port = UInt(16.W)
    val length = UInt(16.W)
    val checksum = UInt(16.W)
}

class UDPFrameStatus(val DIRECTION: String) extends Bundle {
    val busy = Output(Bool())
    val error_incomplete_header = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
    val error_incomplete_payload = Output(Bool())
    val error_invalid_checksum = if (DIRECTION == "RECEIVE") Some(Output(Bool())) else None
}

class UDPFrame extends Module {
    val io = IO(new Bundle {
        val tx_udp_header = Flipped(Decoupled(new UDPFrameHeader))
        val tx_udp_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                           KEEP_EN = false,
                                                           LAST_EN = true,
                                                           ID_WIDTH = 0,
                                                           DEST_WIDTH = 0,
                                                           USER_WIDTH = 1)))
        val tx_ip_header = Decoupled(new IPFrameHeader)
        val tx_ip_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                   KEEP_EN = false,
                                                   LAST_EN = true,
                                                   ID_WIDTH = 0,
                                                   DEST_WIDTH = 0,
                                                   USER_WIDTH = 1))
        val tx_status = new UDPFrameStatus(DIRECTION = "TRANSMIT")
        val rx_ip_header = Flipped(Decoupled(new IPFrameHeader))
        val rx_ip_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                          KEEP_EN = false,
                                                          LAST_EN = true,
                                                          ID_WIDTH = 0,
                                                          DEST_WIDTH = 0,
                                                          USER_WIDTH = 1)))
        val rx_udp_header = Decoupled(new UDPFrameHeader)
        val rx_udp_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1))
        val rx_status = new UDPFrameStatus(DIRECTION = "RECEIVE")
    })

    val tx = Module(new UDPFrameTx())
    val rx = Module(new UDPFrameRx())

    tx.io.udp_header <> io.tx_udp_header
    tx.io.udp_input <> io.tx_udp_input
    tx.io.ip_header <> io.tx_ip_header
    tx.io.ip_output <> io.tx_ip_output
    tx.io.status <> io.tx_status

    rx.io.ip_header <> io.rx_ip_header
    rx.io.ip_input <> io.rx_ip_input
    rx.io.udp_header <> io.rx_udp_header
    rx.io.udp_output <> io.rx_udp_output
    rx.io.status <> io.rx_status
}

class UDPFrameTx extends Module {
    val io = IO(new Bundle {
        val udp_header = Flipped(Decoupled(new UDPFrameHeader))
        val udp_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
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
        val status = new UDPFrameStatus(DIRECTION = "TRANSMIT")
    })
    val header = Reg(new UDPFrameHeader)
    val header_valid = RegInit(false.B)
    val input_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1), 2048))
    val checksum_gen = Module(new UDPChecksumGen())

    val frame_pointer = RegInit(0.U(16.W))
    val busy = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload = Value
    }
    val state = RegInit(State.sIdle)

    checksum_gen.io.input_header <> io.udp_header
    checksum_gen.io.input_data <> io.udp_input
    checksum_gen.io.output_header.ready := state === State.sIdle
    checksum_gen.io.output_data <> input_fifo.io.enq
    io.status.busy := busy || checksum_gen.io.status.busy
    io.status.error_incomplete_payload := checksum_gen.io.status.error_incomplete_payload

    input_fifo.io.deq.ready := state === State.sPayload && io.ip_output.ready

    io.ip_header.bits := header.ip
    io.ip_header.valid := header_valid

    when (checksum_gen.io.output_header.fire()) {
        header := checksum_gen.io.output_header.bits
    }

    when (checksum_gen.io.output_header.fire()) {
        header_valid := true.B
    } .elsewhen(io.ip_header.fire()) {
        header_valid := false.B
    }

    when (state === State.sHeader) {
        when (frame_pointer === 0.U) {
            io.ip_output.bits.tdata := header.src_port(15, 8)
        } .elsewhen (frame_pointer === 1.U) {
            io.ip_output.bits.tdata := header.src_port(7, 0)
        } .elsewhen (frame_pointer === 2.U) {
            io.ip_output.bits.tdata := header.dst_port(15, 8)
        } .elsewhen (frame_pointer === 3.U) {
            io.ip_output.bits.tdata := header.dst_port(7, 0)
        } .elsewhen (frame_pointer === 4.U) {
            io.ip_output.bits.tdata := header.length(15, 8)
        } .elsewhen (frame_pointer === 5.U) {
            io.ip_output.bits.tdata := header.length(7, 0)
        } .elsewhen (frame_pointer === 6.U) {
            io.ip_output.bits.tdata := header.checksum(15, 8)
        } .elsewhen (frame_pointer === 7.U) {
            io.ip_output.bits.tdata := header.checksum(7, 0)
        } .otherwise {
            io.ip_output.bits.tdata := 0.U
        }
        io.ip_output.bits.tlast.get := false.B
        io.ip_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ip_output.valid := true.B
    } .elsewhen (state === State.sPayload) {
        io.ip_output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.ip_output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get || frame_pointer === 1.U
        io.ip_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ip_output.valid := input_fifo.io.deq.valid
    } .otherwise {
        io.ip_output.bits.tdata := input_fifo.io.deq.bits.tdata
        io.ip_output.bits.tlast.get := input_fifo.io.deq.bits.tlast.get
        io.ip_output.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
        io.ip_output.valid := false.B
    }

    switch (state) {
        is (State.sIdle) {
            when (checksum_gen.io.output_header.fire()) {
                state := State.sHeader
                frame_pointer := 0.U
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ip_output.fire()) {
                when (frame_pointer === 7.U) {
                    state := State.sPayload
                } .otherwise {
                    frame_pointer := frame_pointer + 1.U
                }
            }
        }
        is (State.sPayload) {
            when (io.ip_output.fire()) {
                when (input_fifo.io.deq.bits.tlast.get) {
                    state := State.sIdle
                    busy := false.B
                }
            }
        }
    }
}

class UDPFrameRx extends Module {
    val io = IO(new Bundle {
        val ip_header = Flipped(Decoupled(new IPFrameHeader))
        val ip_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                       KEEP_EN = false,
                                                       LAST_EN = true,
                                                       ID_WIDTH = 0,
                                                       DEST_WIDTH = 0,
                                                       USER_WIDTH = 1)))
        val udp_header = Decoupled(new UDPFrameHeader)
        val udp_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                 KEEP_EN = false,
                                                 LAST_EN = true,
                                                 ID_WIDTH = 0,
                                                 DEST_WIDTH = 0,
                                                 USER_WIDTH = 1))
        val status = new UDPFrameStatus(DIRECTION = "RECEIVE")
    })

    val output_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                     KEEP_EN = false,
                                                     LAST_EN = true,
                                                     ID_WIDTH = 0,
                                                     DEST_WIDTH = 0,
                                                     USER_WIDTH = 1), 2048))

    val header = Reg(new UDPFrameHeader)
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(16.W))
    val header_checksum = RegInit(0.U(32.W))
    val busy = RegInit(false.B)
    val odd_frame = RegInit(0.U(1.W))
    val error_incomplete_header = RegInit(false.B)
    val error_incomplete_payload = RegInit(false.B)
    val error_invalid_checksum = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sHeader, sPayload, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    io.ip_header.ready := state === State.sIdle
    io.ip_input.ready := state === State.sIdle || state === State.sEnd || (state === State.sHeader && ~header_valid) || (state === State.sPayload && output_fifo.io.enq.ready)
    io.udp_header.bits := header
    io.udp_header.valid := header_valid
    io.udp_output <> output_fifo.io.deq
    io.status.busy := busy
    io.status.error_incomplete_header.get := error_incomplete_header
    io.status.error_incomplete_payload := error_incomplete_payload
    io.status.error_invalid_checksum.get := error_invalid_checksum

    output_fifo.io.enq.bits.tdata := io.ip_input.bits.tdata
    output_fifo.io.enq.bits.tlast.get := io.ip_input.bits.tlast.get || (state === State.sPayload && frame_pointer === 1.U)
    output_fifo.io.enq.bits.tuser.get := io.ip_input.bits.tuser.get
    output_fifo.io.enq.valid := io.ip_input.fire() && state === State.sPayload

    val header_checksum_part = Wire(UInt(32.W))
    header_checksum_part := header_checksum(15, 0) +& header_checksum(31, 16)

    when (io.udp_header.fire()) {
        header_valid := false.B
    } .elsewhen (io.ip_input.fire() && state === State.sHeader && frame_pointer === 7.U) {
        header_valid := true.B
    }

    switch (state) {
        is (State.sIdle) {
            error_incomplete_header := false.B
            error_incomplete_payload := false.B
            error_invalid_checksum := false.B
            header_checksum := "h0021".U // Protocol 0x0011 + 2 * header length 0x0010
            when (io.ip_header.fire()) {
                header.ip := io.ip_header.bits
            }
            when (io.ip_input.fire() && ~header_valid) {
                state := State.sHeader
                frame_pointer := 1.U
                header.src_port := Cat(io.ip_input.bits.tdata, header.src_port(7, 0))
                busy := true.B
            }
        }
        is (State.sHeader) {
            when (io.ip_input.fire()) {
                frame_pointer := frame_pointer + 1.U
                when (io.ip_input.bits.tlast.get) {
                    state := State.sIdle
                    error_incomplete_header := true.B
                } .elsewhen (frame_pointer === 7.U) {
                    frame_pointer := header.length - 8.U
                    header_valid := true.B
                    odd_frame := 0.U
                    state := State.sPayload
                }
                switch (frame_pointer) {
                    is (1.U) {
                        header.src_port := Cat(header.src_port(15, 8), io.ip_input.bits.tdata)
                        header_checksum := header_checksum + header.ip.src_ip(31, 16) + header.ip.src_ip(15, 0)
                    }
                    is (2.U) {
                        header.dst_port := Cat(io.ip_input.bits.tdata, header.dst_port(7, 0))
                        header_checksum := header_checksum + header.ip.dst_ip(31, 16) + header.ip.dst_ip(15, 0)
                    }
                    is (3.U) {
                        header.dst_port := Cat(header.dst_port(15, 8), io.ip_input.bits.tdata)
                        header_checksum := header_checksum + header.src_port
                    }
                    is (4.U) {
                        header.length := Cat(io.ip_input.bits.tdata, header.length(7, 0))
                        header_checksum := header_checksum + header.dst_port
                    }
                    is (5.U) {
                        header.length := Cat(header.length(15, 8), io.ip_input.bits.tdata)
                    }
                    is (6.U) {
                        header.checksum := Cat(io.ip_input.bits.tdata, header.checksum(7, 0))
                    }
                    is (7.U) {
                        header.checksum := Cat(header.checksum(15, 8), io.ip_input.bits.tdata)
                    }
                }
            }
        }
        is (State.sPayload) {
            when (io.ip_input.fire()) {
                frame_pointer := frame_pointer - 1.U
                odd_frame := ~odd_frame
                when (odd_frame === 1.U) {
                    header_checksum := header_checksum + Cat(0.U(8.W), io.ip_input.bits.tdata) + 2.U
                } .otherwise {
                    header_checksum := header_checksum + Cat(io.ip_input.bits.tdata, 0.U(8.W)) + 2.U
                }
                when (io.ip_input.bits.tlast.get && frame_pointer === 1.U) {
                    state := State.sIdle
                    busy := false.B
                    when (~(header_checksum_part(15, 0) + header_checksum_part(16)) =/= header.checksum) {
                        error_invalid_checksum := true.B
                    }
                } .elsewhen (io.ip_input.bits.tlast.get && frame_pointer > 1.U) {
                    state := State.sIdle
                    busy := false.B
                    error_incomplete_payload := true.B
                    when (~(header_checksum_part(15, 0) + header_checksum_part(16)) =/= header.checksum) {
                        error_invalid_checksum := true.B
                    }
                } .elsewhen (frame_pointer === 1.U) {
                    state := State.sEnd
                }
            }
        }
        is (State.sEnd) {
            when (io.ip_input.fire()) {
                when (io.ip_input.bits.tlast.get) {
                    state := State.sIdle
                    busy := false.B
                    when (~(header_checksum_part(15, 0) + header_checksum_part(16)) =/= header.checksum) {
                        error_invalid_checksum := true.B
                    }
                }
            }
        }
    }
}

class UDPChecksumGen extends Module {
    val io = IO(new Bundle {
        val input_header = Flipped(Decoupled(new UDPFrameHeader))
        val input_data = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                         KEEP_EN = false,
                                                         LAST_EN = true,
                                                         ID_WIDTH = 0,
                                                         DEST_WIDTH = 0,
                                                         USER_WIDTH = 1)))
        val output_header = Decoupled(new UDPFrameHeader)
        val output_data = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                  KEEP_EN = false,
                                                  LAST_EN = true,
                                                  ID_WIDTH = 0,
                                                  DEST_WIDTH = 0,
                                                  USER_WIDTH = 1))
        val status = new UDPFrameStatus(DIRECTION = "TRANSMIT")
    })

    val input_fifo = Module(new Queue(new AXIStream(DATA_WIDTH = 8,
                                                    KEEP_EN = false,
                                                    LAST_EN = true,
                                                    ID_WIDTH = 0,
                                                    DEST_WIDTH = 0,
                                                    USER_WIDTH = 1), 2048))

    val header_checksum = RegInit(0.U(32.W))
    val header = Reg(new UDPFrameHeader)
    val header_valid = RegInit(false.B)
    val frame_pointer = RegInit(0.U(16.W))
    val odd_frame = RegInit(0.U(1.W))
    val error_incomplete_payload = RegInit(false.B)
    val busy = RegInit(false.B)

    object State extends ChiselEnum {
        val sIdle, sChecksum, sEnd = Value
    }
    val state = RegInit(State.sIdle)

    val header_checksum_part = Wire(UInt(32.W))
    header_checksum_part := header_checksum(15, 0) +& header_checksum(31, 16)

    io.input_header.ready := state === State.sIdle && ~header_valid
    io.output_header.bits.ip <> header.ip
    io.output_header.bits.src_port := header.src_port
    io.output_header.bits.dst_port := header.dst_port
    io.output_header.bits.length := header.length
    io.output_header.bits.checksum := ~(header_checksum_part(15, 0) + header_checksum_part(16))
    io.output_header.valid := header_valid

    io.input_data <> input_fifo.io.enq
    io.output_data.bits <> input_fifo.io.deq.bits
    io.output_data.bits.tdata := input_fifo.io.deq.bits.tdata
    io.output_data.bits.tlast.get := input_fifo.io.deq.bits.tlast.get || (state === State.sChecksum && frame_pointer === 1.U)
    io.output_data.bits.tuser.get := input_fifo.io.deq.bits.tuser.get
    io.output_data.valid := (input_fifo.io.deq.valid && state =/= State.sEnd)
    input_fifo.io.deq.ready := (state === State.sChecksum || state === State.sEnd) && io.output_data.ready

    io.status.busy := busy
    io.status.error_incomplete_payload := error_incomplete_payload

    when (io.input_header.fire()) {
        header := io.input_header.bits
    }

    when (io.output_header.fire()) {
        header_valid := false.B
    }

    switch (state) {
        is (State.sIdle) {
            frame_pointer := io.input_header.bits.length - 8.U
            odd_frame := 0.U
            busy := false.B
            error_incomplete_payload := false.B
            when (io.input_header.fire()) {
                state := State.sChecksum
                header_checksum := "h0021".U +& io.input_header.bits.ip.src_ip(31, 16) +& io.input_header.bits.ip.src_ip(15, 0) +& io.input_header.bits.ip.dst_ip(31, 16) +& io.input_header.bits.ip.dst_ip(15, 0) +& io.input_header.bits.src_port +& io.input_header.bits.dst_port
                busy := true.B
            }
        }
        is (State.sChecksum) {
            when (input_fifo.io.deq.valid) {
                frame_pointer := frame_pointer - 1.U
                odd_frame := ~odd_frame
                when (odd_frame === 1.U) {
                    header_checksum := header_checksum + Cat(0.U(8.W), input_fifo.io.deq.bits.tdata) + 2.U
                } .otherwise {
                    header_checksum := header_checksum + Cat(input_fifo.io.deq.bits.tdata, 0.U(8.W)) + 2.U
                }
                when (input_fifo.io.deq.bits.tlast.get && frame_pointer === 1.U) {
                    state := State.sIdle
                    busy := false.B
                    header_valid := true.B
                } .elsewhen (input_fifo.io.deq.bits.tlast.get && frame_pointer > 1.U) {
                    error_incomplete_payload := true.B
                    state := State.sIdle
                    busy := false.B
                    header_valid := true.B
                } .elsewhen (frame_pointer === 1.U) {
                    state := State.sEnd
                    header_valid := true.B
                }
            }
        }
        is (State.sEnd) {
            when (input_fifo.io.deq.fire()) {
                when (input_fifo.io.deq.bits.tlast.get) {
                    state := State.sIdle
                    busy := false.B
                }
            }
        }
    }
}