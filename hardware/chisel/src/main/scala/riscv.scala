package project

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import chisel3.experimental.ChiselEnum

class iBus_cmd extends Bundle {
    val program_counter = UInt(32.W)
}

class iBus_rsp extends Bundle {
    val error = Bool()
    val instruction = UInt(32.W)
}

class iBus extends Bundle {
    val cmd = Decoupled(new iBus_cmd)
    val rsp = Flipped(Decoupled(new iBus_rsp))
}

class Interrupts extends Bundle {
    val timer = Input(Bool())
    val external = Input(Bool())
    val software = Input(Bool())
}

class dbgBus_cmd extends Bundle {
    val write = Bool()
    val address = UInt(8.W)
    val data = UInt(32.W)
}

class dbgBus_rsp extends Bundle {
    val data = UInt(32.W)
}

class dbgBus extends Bundle {
    val cmd = Decoupled(new dbgBus_cmd)
    val rsp = Flipped(Decoupled(new dbgBus_cmd))
}

class dBus_cmd extends Bundle {
    val write = Bool()
    val address = UInt(32.W)
    val data = UInt(32.W)
    val size = UInt(2.W)
}

class dBus_rsp extends Bundle {
    val error = Bool()
    val data = UInt(32.W)
}

class dBus extends Bundle {
    val cmd = Decoupled(new dBus_cmd)
    val rsp = Flipped(Decoupled(new dBus_rsp))
}

class VexRiscv extends BlackBox {
    val io = IO(new Bundle {
        val iBus_cmd_valid = Output(Bool())
        val iBus_cmd_ready = Input(Bool())
        val iBus_cmd_payload_pc = Output(UInt(32.W))
        val iBus_rsp_valid = Input(Bool())
        val iBus_rsp_payload_error = Input(Bool())
        val iBus_rsp_payload_inst = Input(UInt(32.W))
        val timerInterrupt = Input(Bool())
        val externalInterrupt = Input(Bool())
        val softwareInterrupt = Input(Bool())
        val debug_bus_cmd_valid = Input(Bool())
        val debug_bus_cmd_ready = Output(Bool())
        val debug_bus_cmd_payload_wr = Input(Bool())
        val debug_bus_cmd_payload_address = Input(UInt(8.W))
        val debug_bus_cmd_payload_data = Input(UInt(32.W))
        val debug_bus_rsp_data = Output(UInt(32.W))
        val debug_resetOut = Output(Bool())
        val dBus_cmd_valid = Output(Bool())
        val dBus_cmd_ready = Input(Bool())
        val dBus_cmd_payload_wr = Output(Bool())
        val dBus_cmd_payload_address = Output(UInt(32.W))
        val dBus_cmd_payload_data = Output(UInt(32.W))
        val dBus_cmd_payload_size = Output(UInt(2.W))
        val dBus_rsp_ready = Input(Bool())
        val dBus_rsp_error = Input(Bool())
        val dBus_rsp_data = Input(UInt(32.W))
        val clk = Input(Clock())
        val reset = Input(Reset())
        val debugReset = Input(Reset())
    })
}

class CacheFrontEndRequest(val ADDR_WIDTH: Int = 32,
                           val DATA_WIDTH: Int = 32) extends Bundle {
    val write = Bool()
    val address = UInt(ADDR_WIDTH.W)
    val data = UInt(DATA_WIDTH.W)
    val size = UInt(log2Ceil(DATA_WIDTH / 8).W)
}

class CacheFrontEndResponse(val DATA_WIDTH: Int = 32) extends Bundle {
    val error = Bool()
    val data = UInt(DATA_WIDTH.W)
}

class CacheFrontEnd(val ADDR_WIDTH: Int = 32,
                    val DATA_WIDTH: Int = 32) extends Bundle {
    val request = Decoupled(new CacheFrontEndRequest(ADDR_WIDTH = ADDR_WIDTH, DATA_WIDTH = DATA_WIDTH))
    val response = Flipped(Valid(new CacheFrontEndResponse(DATA_WIDTH = DATA_WIDTH)))
}

class Cache(val ADDR_WIDTH: Int = 32,
            val DATA_WIDTH: Int = 32,
            val N_WAYS: Int = 2,
            val LINE_INDEX_WIDTH: Int = 10,
            val WORD_INDEX_WIDTH: Int = 3,
            val AXI4_ADDR_WIDTH: Int = 32,
            val AXI4_DATA_WIDTH: Int = 128,
            val AXI4_ID_WIDTH: Int = 1,
            val CACHEABLE: Bits,
            val CACHEABLE_MASK: Bits) extends Module {
    val io = IO(new Bundle {
        val frontend = Flipped(new CacheFrontEnd(ADDR_WIDTH = ADDR_WIDTH, DATA_WIDTH = DATA_WIDTH))
        val backend = new AXI4Full(DATA_WIDTH = AXI4_DATA_WIDTH, ADDR_WIDTH = AXI4_ADDR_WIDTH, ID_WIDTH = AXI4_ID_WIDTH)
    })

    val N_BYTES = DATA_WIDTH / 8
    val BYTE_WIDTH = log2Ceil(N_BYTES)
    val AXI4_N_BYTES = AXI4_DATA_WIDTH / 8
    val AXI4_BYTE_WIDTH = log2Ceil(AXI4_N_BYTES)
    val N_BURST = WORD_INDEX_WIDTH - log2Ceil(AXI4_DATA_WIDTH / DATA_WIDTH)
    val TAG_WIDTH = ADDR_WIDTH - BYTE_WIDTH - WORD_INDEX_WIDTH - LINE_INDEX_WIDTH

    object State extends ChiselEnum {
        val sIdle, sCheck, sWriteMemAddr, sWriteMemData, sWriteMemResp, sReadMemAddr, sReadMemData, sWait, sUncachedWriteMemAddr, sUncachedWriteMemData, sUncachedWriteMemResp, sUncachedReadMemAddr, sUncachedReadMemData = Value
    }

    val state = RegInit(State.sIdle)

    val response_pipe = Module(new Pipe(new CacheFrontEndResponse(DATA_WIDTH = DATA_WIDTH), 0))
    val frontend_request_reg = RegInit(0.U.asTypeOf(new CacheFrontEndRequest(ADDR_WIDTH = ADDR_WIDTH, DATA_WIDTH = DATA_WIDTH)))
    val valid_reg = RegInit(false.B)

    when ((state === State.sIdle || state === State.sCheck) && io.frontend.request.fire()) {
        frontend_request_reg := io.frontend.request.bits
    }

    when ((state === State.sIdle || state === State.sCheck) && io.frontend.request.fire()) {
        valid_reg := true.B
    } .elsewhen (response_pipe.io.enq.valid || frontend_request_reg.write) {
        valid_reg := false.B
    }

    val write_strobe = Wire(UInt(N_BYTES.W))
    write_strobe := (Fill(N_BYTES, 1.U) >> (N_BYTES.U - (1.U(N_BYTES.W) << frontend_request_reg.size))) << frontend_request_reg.address(BYTE_WIDTH - 1, 0)

    val error = RegInit(false.B)
    error := io.frontend.request.bits.size > log2Ceil(N_BYTES).U

    val tag = frontend_request_reg.address(ADDR_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH)
    val line = frontend_request_reg.address(ADDR_WIDTH - TAG_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH)
    val word = frontend_request_reg.address(ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH - WORD_INDEX_WIDTH)

    val burst = RegInit(0.U(N_BURST.W))
    val read_resp = RegInit(0.U(2.W))

    val data_mem = SyncReadMem(math.pow(2, LINE_INDEX_WIDTH).toInt, Vec(N_WAYS * math.pow(2, WORD_INDEX_WIDTH).toInt * N_BYTES, UInt(8.W)))

    val data_mem_waddr = Wire(UInt(LINE_INDEX_WIDTH.W))
    val data_mem_wdata = Wire(Vec(N_WAYS * math.pow(2, WORD_INDEX_WIDTH).toInt * N_BYTES, UInt(8.W)))
    val data_mem_wdata_fe = Wire(Vec(N_WAYS, Vec(math.pow(2, WORD_INDEX_WIDTH).toInt, UInt(DATA_WIDTH.W))))
    val data_mem_wdata_be = Wire(Vec(N_WAYS, Vec(math.pow(2, N_BURST).toInt, UInt(AXI4_DATA_WIDTH.W))))

    val data_mem_wstrb = Wire(Vec(N_WAYS * math.pow(2, WORD_INDEX_WIDTH).toInt * N_BYTES, Bool()))
    val data_mem_wstrb_fe = Wire(Vec(N_WAYS, Vec(math.pow(2, WORD_INDEX_WIDTH).toInt, Vec(N_BYTES, Bool()))))
    val data_mem_wstrb_be = Wire(Vec(N_WAYS, Vec(math.pow(2, N_BURST).toInt, Vec(AXI4_N_BYTES, Bool()))))
    
    val data_mem_raddr = Wire(UInt(LINE_INDEX_WIDTH.W))
    val data_mem_rdata = Wire(Vec(N_WAYS * math.pow(2, WORD_INDEX_WIDTH).toInt * N_BYTES, UInt(8.W)))
    val data_mem_rdata_fe = Wire(Vec(N_WAYS, Vec(math.pow(2, WORD_INDEX_WIDTH).toInt, UInt(DATA_WIDTH.W))))
    val data_mem_rdata_be = Wire(Vec(N_WAYS, Vec(math.pow(2, N_BURST).toInt, UInt(AXI4_DATA_WIDTH.W))))

    data_mem.write(data_mem_waddr, data_mem_wdata, data_mem_wstrb)
    data_mem_rdata := data_mem.read(data_mem_raddr)

    data_mem_rdata_fe := data_mem_rdata.asTypeOf(data_mem_rdata_fe)
    data_mem_rdata_be := data_mem_rdata.asTypeOf(data_mem_rdata_be)

    val tag_mem = SyncReadMem(math.pow(2, LINE_INDEX_WIDTH).toInt, Vec(N_WAYS, UInt(TAG_WIDTH.W)))
    val tag_mem_waddr = Wire(UInt(LINE_INDEX_WIDTH.W))
    val tag_mem_wdata = Wire(Vec(N_WAYS, UInt(TAG_WIDTH.W)))
    val tag_mem_wstrb = Wire(Vec(N_WAYS, Bool()))
    val tag_mem_raddr = Wire(UInt(LINE_INDEX_WIDTH.W))
    val tag_mem_rdata = Wire(Vec(N_WAYS, UInt(TAG_WIDTH.W)))

    tag_mem.write(tag_mem_waddr, tag_mem_wdata, tag_mem_wstrb)
    tag_mem_rdata := tag_mem.read(tag_mem_raddr)

    val valid = RegInit(VecInit(Seq.fill(math.pow(2, LINE_INDEX_WIDTH).toInt)(VecInit(Seq.fill(N_WAYS)(false.B)))))
    val dirty = RegInit(VecInit(Seq.fill(math.pow(2, LINE_INDEX_WIDTH).toInt)(VecInit(Seq.fill(N_WAYS)(false.B)))))

    val valid_line = RegInit(VecInit(Seq.fill(N_WAYS)(false.B)))
    val dirty_line = RegInit(VecInit(Seq.fill(N_WAYS)(false.B)))
    valid_line := valid(Mux(state =/= State.sIdle && state =/= State.sCheck, line, io.frontend.request.bits.address(ADDR_WIDTH - TAG_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH)))
    dirty_line := dirty(Mux(state =/= State.sIdle && state =/= State.sCheck, line, io.frontend.request.bits.address(ADDR_WIDTH - TAG_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH)))

    val hit = Wire(Bool())
    val way_hit = Wire(Vec(N_WAYS, Bool()))
    val way_select = Wire(Vec(N_WAYS, Bool()))
    val way_hit_bin = Wire(UInt(log2Ceil(N_WAYS).W))
    val way_select_bin = Wire(UInt(log2Ceil(N_WAYS).W))
    val way_select_bin_reg = RegEnable(way_select_bin, 1.U, state === State.sCheck && ~hit)

    hit := way_hit.asUInt.orR
    way_hit := tag_mem_rdata.zip(valid_line).map{ case (way_tag, way_valid) => {
        way_tag === tag && way_valid
    }}

    if (N_WAYS > 1) {
        way_select := UIntToOH(way_select_bin).asTypeOf(way_select)
        way_hit_bin := OHToUInt(way_hit)
        way_select_bin := LFSR(N_WAYS)
    } else {
        way_select := 1.U.asTypeOf(way_select)
        way_hit_bin := 0.U
        way_select_bin := 0.U
    }

    when (io.backend.r.fire() && io.backend.r.bits.last) {
        valid(line)(way_select_bin_reg) := true.B
    }

    when (state === State.sCheck && hit && frontend_request_reg.write) {
        dirty(line)(way_hit_bin) := true.B
    }
    when (io.backend.w.fire() && io.backend.w.bits.last) {
        dirty(line)(way_select_bin_reg) := false.B
    }

    data_mem_waddr := line
    data_mem_wdata := Mux(state === State.sReadMemData, data_mem_wdata_be.asTypeOf(data_mem_wdata), data_mem_wdata_fe.asTypeOf(data_mem_wdata))
    data_mem_wdata_fe := Fill(N_WAYS, Fill(math.pow(2, WORD_INDEX_WIDTH).toInt, frontend_request_reg.data)).asTypeOf(data_mem_wdata_fe)
    data_mem_wdata_be := Fill(N_WAYS, Fill(math.pow(2, N_BURST).toInt, io.backend.r.bits.data)).asTypeOf(data_mem_wdata_be)
    data_mem_wstrb := Mux(state === State.sReadMemData, data_mem_wstrb_be.asTypeOf(data_mem_wstrb), data_mem_wstrb_fe.asTypeOf(data_mem_wstrb))
    data_mem_wstrb_fe := Fill(N_WAYS, Fill(math.pow(2, WORD_INDEX_WIDTH).toInt, Fill(N_BYTES, false.B))).asTypeOf(data_mem_wstrb_fe)
    data_mem_wstrb_be := Fill(N_WAYS, Fill(math.pow(2, N_BURST).toInt, Fill(AXI4_N_BYTES, false.B))).asTypeOf(data_mem_wstrb_be)
    data_mem_wstrb_fe(way_hit_bin)(word) := (Fill(N_BYTES, state === State.sCheck && hit && frontend_request_reg.write) & write_strobe).asTypeOf(Vec(N_BYTES, Bool()))
    data_mem_wstrb_be(way_select_bin_reg)(burst) := Fill(AXI4_N_BYTES, state === State.sReadMemData && io.backend.r.fire()).asTypeOf(Vec(AXI4_N_BYTES, Bool()))
    data_mem_raddr := Mux(state =/= State.sIdle && state =/= State.sCheck, line, io.frontend.request.bits.address(ADDR_WIDTH - TAG_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH))

    tag_mem_waddr := line
    tag_mem_wdata := Fill(N_WAYS, tag).asTypeOf(tag_mem_wdata)
    tag_mem_wstrb := Fill(N_WAYS, false.B).asTypeOf(tag_mem_wstrb)
    tag_mem_wstrb(way_select_bin_reg) := state === State.sReadMemData && io.backend.r.fire() && io.backend.r.bits.last
    tag_mem_raddr := Mux(state =/= State.sIdle && state =/= State.sCheck, line, io.frontend.request.bits.address(ADDR_WIDTH - TAG_WIDTH - 1, ADDR_WIDTH - TAG_WIDTH - LINE_INDEX_WIDTH))

    switch (state) {
        is (State.sIdle) {
            when (io.frontend.request.fire()) {
                when (((io.frontend.request.bits.address ^ CACHEABLE.asUInt) & CACHEABLE_MASK.asUInt) === 0.U) {
                    state := State.sCheck
                } .otherwise {
                    when (io.frontend.request.bits.write) {
                        state := State.sUncachedWriteMemAddr
                    } .otherwise {
                        state := State.sUncachedReadMemAddr
                    }
                }
            }
        }
        is (State.sCheck) {
            when (io.frontend.request.fire()) {
                when (((io.frontend.request.bits.address ^ CACHEABLE.asUInt) & CACHEABLE_MASK.asUInt) === 0.U) {
                    when (hit) {
                        state := State.sCheck
                    } .otherwise {
                        when (dirty_line(way_select_bin)) {
                            state := State.sWriteMemAddr
                        } .otherwise {
                            state := State.sReadMemAddr
                        }
                    }
                } .otherwise {
                    when (io.frontend.request.bits.write) {
                        state := State.sUncachedWriteMemAddr
                    } .otherwise {
                        state := State.sUncachedReadMemAddr
                    }
                }
            } .elsewhen (valid_reg) {
                when (((frontend_request_reg.address ^ CACHEABLE.asUInt) & CACHEABLE_MASK.asUInt) === 0.U) {
                    when (hit) {
                        state := State.sCheck
                    } .otherwise {
                        when (dirty_line(way_select_bin)) {
                            state := State.sWriteMemAddr
                        } .otherwise {
                            state := State.sReadMemAddr
                        }
                    }
                } .otherwise {
                    when (io.frontend.request.bits.write) {
                        state := State.sUncachedWriteMemAddr
                    } .otherwise {
                        state := State.sUncachedReadMemAddr
                    }
                }
            } .otherwise {
                state := State.sIdle
            }
        }
        is (State.sWriteMemAddr) {
            burst := 0.U
            when (io.backend.aw.fire()) {
                state := State.sWriteMemData
            }
        }
        is (State.sWriteMemData) {
            when (io.backend.w.fire()) {
                burst := burst + 1.U
                when (io.backend.w.bits.last) {
                    state := State.sWriteMemResp
                }
            }
        }
        is (State.sWriteMemResp) {
            when (io.backend.b.fire()) {
                when (io.backend.b.bits.resp =/= 0.U) {
                    state := State.sWriteMemData
                } .otherwise {
                    state := State.sReadMemAddr
                }
            }
        }
        is (State.sReadMemAddr) {
            burst := 0.U
            read_resp := 0.U
            when (io.backend.ar.fire()) {
                state := State.sReadMemData
            }
        }
        is (State.sReadMemData) {
            when (io.backend.r.fire()) {
                burst := burst + 1.U
                read_resp := read_resp | io.backend.r.bits.resp
                when (io.backend.r.bits.last) {
                    when ((read_resp | io.backend.r.bits.resp).orR) {
                        state := State.sReadMemAddr
                    } .otherwise {
                        state := State.sWait
                    }
                }
            }
        }
        is (State.sWait) {
            state := State.sCheck
        }
        is (State.sUncachedWriteMemAddr) {
            when (io.backend.aw.fire()) {
                state := State.sUncachedWriteMemData
            }
        }
        is (State.sUncachedWriteMemData) {
            when (io.backend.w.fire() && io.backend.w.bits.last) {
                    state := State.sUncachedWriteMemResp
            }
        }
        is (State.sUncachedWriteMemResp) {
            when (io.backend.b.fire()) {
                when (io.backend.b.bits.resp =/= 0.U) {
                    state := State.sUncachedWriteMemData
                } .otherwise {
                    state := State.sCheck
                }
            }
        }
        is (State.sUncachedReadMemAddr) {
            when (io.backend.ar.fire()) {
                state := State.sUncachedReadMemData
            }
        }
        is (State.sUncachedReadMemData) {
            when (io.backend.r.fire() && io.backend.r.bits.last) {
                    when (io.backend.r.bits.resp =/= 0.U) {
                        state := State.sUncachedReadMemAddr
                    } .otherwise {
                        state := State.sCheck
                    }
            }
        }
    }

    val uncached_addr = Wire(UInt(AXI4_ADDR_WIDTH.W))
    val uncached_index = Wire(UInt(log2Ceil(AXI4_DATA_WIDTH / DATA_WIDTH).W))
    val uncached_read_data = Wire(Vec(AXI4_DATA_WIDTH / DATA_WIDTH, UInt(DATA_WIDTH.W)))
    val uncached_write_data = Wire(Vec(AXI4_DATA_WIDTH / DATA_WIDTH, UInt(DATA_WIDTH.W)))
    val uncached_write_strobe = Wire(Vec(AXI4_DATA_WIDTH / DATA_WIDTH, UInt(N_BYTES.W)))
    uncached_addr := Cat(frontend_request_reg.address(ADDR_WIDTH - 1, log2Ceil(AXI4_N_BYTES)), 0.U(log2Ceil(AXI4_N_BYTES).W))
    uncached_index := frontend_request_reg.address(log2Ceil(AXI4_N_BYTES) - 1, log2Ceil(N_BYTES))
    uncached_read_data := io.backend.r.bits.data.asTypeOf(uncached_read_data)
    uncached_write_data := Fill(AXI4_DATA_WIDTH / DATA_WIDTH, frontend_request_reg.data).asTypeOf(uncached_write_data)
    uncached_write_strobe := Fill(AXI4_DATA_WIDTH / DATA_WIDTH, 0.U(N_BYTES.W)).asTypeOf(uncached_write_strobe)
    uncached_write_strobe(uncached_index) := write_strobe

    io.frontend.request.ready := (state === State.sIdle || (state === State.sCheck && hit))
    response_pipe.io.enq.valid := Mux(state === State.sUncachedReadMemData, io.backend.r.valid && io.backend.r.bits.last, state === State.sCheck && hit && valid_reg)
    response_pipe.io.enq.bits.error := error
    response_pipe.io.enq.bits.data := Mux(state === State.sUncachedReadMemData, uncached_read_data(uncached_index), data_mem_rdata_fe(way_hit_bin)(word))
    io.frontend.response <> response_pipe.io.deq

    io.backend.ar.valid := state === State.sReadMemAddr || state === State.sUncachedReadMemAddr
    io.backend.ar.bits.addr := Mux(state === State.sUncachedReadMemAddr, uncached_addr, Cat(tag, line, 0.U(N_BURST.W), 0.U(AXI4_BYTE_WIDTH.W)))
    io.backend.ar.bits.id := 0.U
    io.backend.ar.bits.len := Mux(state === State.sUncachedReadMemAddr, 0.U, N_BURST.U)
    io.backend.ar.bits.size := log2Ceil(AXI4_DATA_WIDTH).U
    io.backend.ar.bits.burst := 1.U
    io.backend.ar.bits.lock := 0.U
    io.backend.ar.bits.cache := 2.U
    io.backend.ar.bits.prot := 0.U
    io.backend.ar.bits.qos := 0.U
    
    io.backend.r.ready := state === State.sReadMemData || state === State.sUncachedReadMemData
    
    io.backend.aw.valid := state === State.sWriteMemAddr || state === State.sUncachedWriteMemAddr
    io.backend.aw.bits.addr := Mux(state === State.sUncachedWriteMemAddr, uncached_addr, Cat(tag_mem_rdata(way_select_bin_reg), line, 0.U(N_BURST.W), 0.U(AXI4_BYTE_WIDTH.W)))
    io.backend.aw.bits.id := 0.U
    io.backend.aw.bits.len := Mux(state === State.sUncachedWriteMemAddr, 0.U, N_BURST.U)
    io.backend.aw.bits.size := log2Ceil(AXI4_DATA_WIDTH).U
    io.backend.aw.bits.burst := 1.U
    io.backend.aw.bits.lock := 0.U
    io.backend.aw.bits.cache := 2.U
    io.backend.aw.bits.prot := 0.U
    io.backend.aw.bits.qos := 0.U
    
    io.backend.w.valid := state === State.sWriteMemData || state === State.sUncachedWriteMemData
    io.backend.w.bits.data := Mux(state === State.sUncachedWriteMemData, uncached_write_data.asTypeOf(io.backend.w.bits.data), data_mem_rdata_be(way_select_bin_reg)(burst))
    io.backend.w.bits.strb := Mux(state === State.sUncachedWriteMemData, uncached_write_strobe.asTypeOf(io.backend.w.bits.strb), Fill(AXI4_DATA_WIDTH / 8, 1.U))
    io.backend.w.bits.last := Mux(state === State.sUncachedWriteMemData, true.B, burst === N_BURST.U)
    
    io.backend.b.ready := state === State.sWriteMemResp || state === State.sUncachedWriteMemResp
}