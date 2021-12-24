package project

import chisel3._
import chisel3.util._

class SyncDebouncer(val CLOCK_FREQUENCY: Int, val SAMPLE_FREQUENCY: Int, val WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val input = Input(UInt(WIDTH.W))
        val output = Output(UInt(WIDTH.W))
    })
    
    // Synchronizer
    
    val input_sync = RegNext(RegNext(io.input))
    
    // Debouncer
    
    val FAC = CLOCK_FREQUENCY / SAMPLE_FREQUENCY
    
    val input_db = Reg(UInt(WIDTH.W))
    val count = RegInit(0.U(log2Ceil(FAC).W))
    val tick = count === (FAC - 1).U
        
    count := count + 1.U
    
    when (tick) {
        count := 0.U
        input_db := input_sync
    }

    io.output := input_db | input_sync
}

class Arbiter(val N_INPUTS: Int,
              val ROUND_ROBIN: Boolean,
              val BLOCKING: Boolean,
              val RELEASE: Boolean) extends Module {
    val io = IO(new Bundle {
        val request = Input(Vec(N_INPUTS, Bool()))
        val acknowledge = Input(Vec(N_INPUTS, Bool()))
        val chosen = Output(UInt(log2Ceil(N_INPUTS + 1).W))
        val chosen_oh = Output(UInt(N_INPUTS.W))
    })

    val chosen_oh = RegInit(1.U(N_INPUTS.W))
    val chosen = RegInit(0.U(log2Ceil(N_INPUTS + 1).W))
    val mask = RegInit(-1.S(N_INPUTS.W))
    val locked = RegInit(false.B)

    io.chosen := chosen
    io.chosen_oh := chosen_oh

    if (BLOCKING && !RELEASE) {
        when (~((io.request.asUInt & chosen_oh.asUInt).orR)) {
            if (ROUND_ROBIN) {
                chosen := PriorityEncoder(io.request.asUInt & mask.asUInt)
                chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
                mask := (-1.S(N_INPUTS.W) << PriorityEncoder(io.request.asUInt & mask.asUInt))(N_INPUTS - 1, 0)
            } else {
                chosen := PriorityEncoder(io.request.asUInt)
                chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
            }
        }
    } else if (BLOCKING && RELEASE) {
        when (io.request.asUInt.orR && (~((~io.acknowledge.asUInt & chosen_oh.asUInt).orR) || ~locked)) {
            if (ROUND_ROBIN) {
                chosen := PriorityEncoder(io.request.asUInt & mask.asUInt)
                chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
                mask := (-1.S(N_INPUTS.W) << PriorityEncoder(io.request.asUInt & mask.asUInt))(N_INPUTS - 1, 0)
                locked := true.B
            } else {
                chosen := PriorityEncoder(io.request.asUInt)
                chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
                locked := true.B
            }
        } .elsewhen (~((~io.acknowledge.asUInt & chosen_oh.asUInt).orR)) {
            locked := false.B
        }
    } else {
        if (ROUND_ROBIN) {
            chosen := PriorityEncoder(io.request.asUInt & mask.asUInt)
            chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
            mask := (-1.S(N_INPUTS.W) << PriorityEncoder(io.request.asUInt & mask.asUInt))(N_INPUTS - 1, 0)
        } else {
            chosen := PriorityEncoder(io.request.asUInt)
            chosen_oh := (1.U(N_INPUTS.W) << PriorityEncoder(io.request.asUInt))(N_INPUTS - 1, 0)
        }
    }
}

class FIFOStatus extends Bundle {
    val overflow = Output(Bool())
    val bad_frame = Output(Bool())
    val good_frame = Output(Bool())
}

class AsyncFIFO[T <: Data](val DEPTH: Int,
                           val FRAME_FIFO: Boolean,
                           val DROP_WHEN_FULL: Boolean,
                           val DROP_BAD_FRAME: Boolean,
                           val OUTPUT_PIPE: Int,
                           val TYPE: T) extends Module {
    val io = IO(new Bundle {
        val deq_clock = Input(Clock())
        val deq = Decoupled(TYPE)
        val deq_status = new FIFOStatus()
        val enq_clock = Input(Clock())
        val enq = Flipped(Decoupled(TYPE))
        val enq_status = new FIFOStatus()
        val del = if (FRAME_FIFO) Some(Input(Bool())) else None // Delimiter for frame mode
        val bad = if (DROP_BAD_FRAME) Some(Input(Bool())) else None // Indicator for bad frames
    })
    val SIZE = log2Ceil(DEPTH)

    val enq_rst_sync = Wire(Bool())
    val deq_rst_sync = Wire(Bool())
    val enq_rst = Wire(Bool())
    val deq_rst = Wire(Bool())

    val wr_ptr_gray = Wire(UInt((SIZE + 1).W))
    val wr_ptr_sync_gray = Wire(UInt((SIZE + 1).W))
    val wr_ptr_cur_gray = Wire(UInt((SIZE + 1).W))
    val wr_ptr_update = Wire(Bool())
    val rd_mem = Wire(TYPE)

    val rd_ptr = Wire(UInt((SIZE + 1).W))
    val rd_ptr_gray = Wire(UInt((SIZE + 1).W))
    val wr_ptr_update_sync = Wire(Bool())

    val overflow_sync = Wire(Bool())
    val bad_frame_sync = Wire(Bool())
    val good_frame_sync = Wire(Bool())

    // Reset synchronization

    withClockAndReset(io.enq_clock, reset) {
        val enq_rst_sync_reg = Seq.fill(3)(RegInit(true.B))
        enq_rst_sync_reg(2) := false.B
        enq_rst_sync_reg(1) := enq_rst_sync_reg(2) || deq_rst
        enq_rst_sync_reg(0) := enq_rst_sync_reg(1)
        enq_rst_sync := enq_rst_sync_reg(0)
        enq_rst := enq_rst_sync_reg(2)
    }

    withClockAndReset(io.deq_clock, reset) {
        val deq_rst_sync_reg = Seq.fill(3)(RegInit(true.B))
        deq_rst_sync_reg(2) := false.B
        deq_rst_sync_reg(1) := deq_rst_sync_reg(2) || enq_rst
        deq_rst_sync_reg(0) := deq_rst_sync_reg(1)
        deq_rst_sync := deq_rst_sync_reg(0)
        deq_rst := deq_rst_sync_reg(2)
    }

    // Write logic
    
    withClockAndReset(io.enq_clock, enq_rst_sync) {
        val wr_ptr = RegInit(0.U((SIZE + 1).W))
        val wr_ptr_cur = RegInit(0.U((SIZE + 1).W))
        val wr_ptr_gray_reg = RegInit(0.U((SIZE + 1).W))
        wr_ptr_gray := wr_ptr_gray_reg
        val wr_ptr_sync_gray_reg = RegInit(0.U((SIZE + 1).W))
        wr_ptr_sync_gray := wr_ptr_sync_gray_reg
        val wr_ptr_cur_gray_reg = RegInit(0.U((SIZE + 1).W))
        wr_ptr_cur_gray := wr_ptr_cur_gray_reg

        val rd_ptr_gray_sync = Seq.fill(2)(RegInit(0.U((SIZE + 1).W)))
        rd_ptr_gray_sync(1) := rd_ptr_gray
        rd_ptr_gray_sync(0) := rd_ptr_gray_sync(1)

        val wr_ptr_update_valid = RegInit(false.B)
        val wr_ptr_update_reg = RegInit(false.B)
        wr_ptr_update := wr_ptr_update_reg
        val wr_ptr_update_ack_sync = Seq.fill(2)(RegInit(false.B))
        wr_ptr_update_ack_sync(1) := wr_ptr_update_sync
        wr_ptr_update_ack_sync(0) := wr_ptr_update_ack_sync(1)

        val full = Wire(Bool())
        val full_cur = Wire(Bool())
        val full_wr = Wire(Bool())
        full := wr_ptr_gray === (rd_ptr_gray_sync(0) ^ Cat(3.U(2.W), 0.U((SIZE - 1).W)))
        full_cur := wr_ptr_cur_gray === (rd_ptr_gray_sync(0) ^ Cat(3.U(2.W), 0.U((SIZE - 1).W)))
        full_wr := wr_ptr === (wr_ptr_cur ^ Cat(1.U(1.W), 0.U(SIZE.W)))

        val frame_dropped = RegInit(false.B)
        val overflow = RegInit(false.B)
        val bad_frame = RegInit(false.B)
        val good_frame = RegInit(false.B)
        io.enq_status.overflow := overflow
        io.enq_status.bad_frame := bad_frame
        io.enq_status.good_frame := good_frame

        val overflow_sync_reg = RegInit(false.B)
        val bad_frame_sync_reg = RegInit(false.B)
        val good_frame_sync_reg = RegInit(false.B)
        overflow_sync_reg := overflow_sync_reg ^ overflow
        bad_frame_sync_reg := bad_frame_sync_reg ^ bad_frame
        good_frame_sync_reg := good_frame_sync_reg ^ good_frame
        overflow_sync := overflow_sync_reg
        bad_frame_sync := bad_frame_sync_reg
        good_frame_sync := good_frame_sync_reg

        val mem = Mem(DEPTH, TYPE)
        rd_mem := mem(rd_ptr(SIZE - 1, 0))

        if (FRAME_FIFO) {
            when (wr_ptr_update_valid && wr_ptr_update === wr_ptr_update_ack_sync(0)) {
                wr_ptr_update_valid := false.B
                wr_ptr_sync_gray_reg := wr_ptr_gray_reg
                wr_ptr_update_reg := ~wr_ptr_update_ack_sync(0)
            }
        }

        overflow := false.B
        bad_frame := false.B
        good_frame := false.B

        when (io.enq.fire()) {
            if (!FRAME_FIFO) {
                mem(wr_ptr(SIZE - 1, 0)) := io.enq.bits
                wr_ptr := wr_ptr + 1.U;
                wr_ptr_gray_reg := (wr_ptr + 1.U) ^ ((wr_ptr + 1.U) >> 1)
            } else {
                when (full_cur || full_wr || frame_dropped) {
                    frame_dropped := true.B
                    when (io.del.get) {
                        wr_ptr_cur := wr_ptr
                        wr_ptr_cur_gray_reg := wr_ptr ^ (wr_ptr >> 1)
                        frame_dropped := false.B
                        overflow := true.B
                    }
                } .otherwise {
                    mem(wr_ptr_cur(SIZE - 1, 0)) := io.enq.bits
                    wr_ptr_cur := wr_ptr_cur + 1.U
                    wr_ptr_cur_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                    when (io.del.get) {
                        if (DROP_BAD_FRAME) {
                            when (io.bad.get) {
                                wr_ptr_cur := wr_ptr
                                wr_ptr_cur_gray_reg := wr_ptr ^ (wr_ptr >> 1)
                                bad_frame := true.B
                            } .otherwise {
                                wr_ptr := wr_ptr_cur + 1.U
                                wr_ptr_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                                when (wr_ptr_update === wr_ptr_update_ack_sync(0)) {
                                    wr_ptr_update_valid := false.B
                                    wr_ptr_sync_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                                    wr_ptr_update_reg := ~wr_ptr_update_ack_sync(0)
                                } .otherwise {
                                    wr_ptr_update_valid := true.B
                                }
                                good_frame := true.B
                            }
                        } else {
                            wr_ptr := wr_ptr_cur + 1.U
                            wr_ptr_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                            when (wr_ptr_update === wr_ptr_update_ack_sync(0)) {
                                wr_ptr_update_valid := false.B
                                wr_ptr_sync_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                                wr_ptr_update_reg := ~wr_ptr_update_ack_sync(0)
                            } .otherwise {
                                wr_ptr_update_valid := true.B
                            }
                            good_frame := true.B
                        }
                    }
                }
            }
        }

        if (FRAME_FIFO) {
            if (DROP_WHEN_FULL) {
                io.enq.ready := ~enq_rst_sync
            } else {
                io.enq.ready := (~full_cur || full_wr) && ~enq_rst_sync
            }
        } else {
            io.enq.ready := ~full && ~enq_rst_sync
        }
    }

    // Read logic

    withClockAndReset(io.deq_clock, deq_rst_sync) {
        val rd_ptr_reg = RegInit(0.U((SIZE + 1).W))
        rd_ptr := rd_ptr_reg
        val rd_ptr_gray_reg = RegInit(0.U((SIZE + 1).W))
        rd_ptr_gray := rd_ptr_gray_reg

        val wr_ptr_gray_sync = Seq.fill(2)(RegInit(0.U((SIZE + 1).W)))

        val wr_ptr_update_sync_reg = Seq.fill(3)(RegInit(false.B))
        wr_ptr_update_sync := wr_ptr_update_sync_reg(0)

        val empty = Wire(Bool())
        empty := rd_ptr_gray === (if (FRAME_FIFO) wr_ptr_gray_sync(1) else wr_ptr_gray_sync(0))

        val overflow = Seq.fill(3)(RegInit(false.B))
        val bad_frame = Seq.fill(3)(RegInit(false.B))
        val good_frame = Seq.fill(3)(RegInit(false.B))
        overflow(2) := overflow_sync
        overflow(1) := overflow(2)
        overflow(0) := overflow(1)
        io.deq_status.overflow := overflow(1) ^ overflow(0)
        bad_frame(2) := bad_frame_sync
        bad_frame(1) := bad_frame(2)
        bad_frame(0) := bad_frame(1)
        io.deq_status.bad_frame := bad_frame(1) ^ bad_frame(0)
        good_frame(2) := good_frame_sync
        good_frame(1) := good_frame(2)
        good_frame(0) := good_frame(1)
        io.deq_status.good_frame := good_frame(1) ^ good_frame(0)

        if (!FRAME_FIFO) {
            wr_ptr_gray_sync(1) := wr_ptr_gray
        } else {
            wr_ptr_gray_sync(1) := wr_ptr_sync_gray
        }
        wr_ptr_gray_sync(0) := wr_ptr_gray_sync(1)
        wr_ptr_update_sync_reg(2) := wr_ptr_update
        wr_ptr_update_sync_reg(1) := wr_ptr_update_sync_reg(2)
        wr_ptr_update_sync_reg(0) := wr_ptr_update_sync_reg(1)

        val pipe = Module(new Queue(TYPE, OUTPUT_PIPE))
        pipe.io.enq.bits := rd_mem
        pipe.io.enq.valid := ~empty
        io.deq <> pipe.io.deq

        when (pipe.io.enq.fire()) {
            rd_ptr_reg := rd_ptr_reg + 1.U
            rd_ptr_gray_reg := (rd_ptr_reg + 1.U) ^ ((rd_ptr_reg + 1.U) >> 1)
        }
    }
}

class SSDDRInput[T <: Data](val TYPE: T) extends Module {
    val io = IO(new Bundle {
        val input_clock = Input(Clock())
        val input_d = Input(TYPE)
        val output_clock = Output(Clock())
        val output_q1 = Output(TYPE)
        val output_q2 = Output(TYPE)
    })

    val WIDTH = io.input_d.getWidth
    val bufio = Module(new BUFIOWrapper())
    bufio.io.I := io.input_clock

    val bufr = Module(new BUFRWrapper(BUFR_DIVIDE = "\"BYPASS\""))
    bufr.io.I := io.input_clock
    bufr.io.CE := true.B
    bufr.io.CLR := false.B

    val iddr = Module(new IDDRWrapper(DDR_CLK_EDGE = "SAME_EDGE_PIPELINED", INIT_Q1 = 0, INIT_Q2 = 0, SRTYPE = "ASYNC", WIDTH = WIDTH))
    iddr.io.C := bufio.io.O
    iddr.io.CE := true.B
    iddr.io.R := false.B
    iddr.io.S := false.B
    iddr.io.D := io.input_d.asTypeOf(UInt(WIDTH.W))

    io.output_clock := bufr.io.O
    io.output_q1 := iddr.io.Q1.asTypeOf(TYPE)
    io.output_q2 := iddr.io.Q2.asTypeOf(TYPE)
}

class LFSR (val WIDTH: Int,
            val POLYNOMIAL: BigInt,
            val CONFIGURATION: String,
            val FEED_FORWARD: Boolean,
            val REVERSE: Boolean,
            val DATA_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val data_in = Input(UInt(DATA_WIDTH.W))
        val state_in = Input(UInt(WIDTH.W))
        val data_out = Output(UInt(DATA_WIDTH.W))
        val state_out = Output(UInt(WIDTH.W))
    })

    var lfsr_mask_state = Seq.fill(WIDTH)(UInt(WIDTH.W))
    var lfsr_mask_data = Seq.fill(WIDTH)(UInt(DATA_WIDTH.W))
    var output_mask_state = Seq.fill(DATA_WIDTH)(UInt(WIDTH.W))
    var output_mask_data = Seq.fill(DATA_WIDTH)(UInt(DATA_WIDTH.W))

    var state_val = UInt(WIDTH.W)
    var data_val = UInt(DATA_WIDTH.W)

    for (i <- 0 until WIDTH) {
        if (i == 0) {
            lfsr_mask_state = lfsr_mask_state.updated(i, Cat(0.U((WIDTH - i - 1).W), 1.U(1.W)))
        } else if (i == WIDTH - 1) {
            lfsr_mask_state = lfsr_mask_state.updated(i, Cat(1.U(1.W), 0.U((i).W)))
        } else {
            lfsr_mask_state = lfsr_mask_state.updated(i, Cat(0.U((WIDTH - i - 1).W), 1.U(1.W), 0.U((i).W)))
        }
        lfsr_mask_data = lfsr_mask_data.updated(i, 0.U(DATA_WIDTH.W))
    }
    for (i <- 0 until DATA_WIDTH) {
        if (i == 0) {
            output_mask_state = output_mask_state.updated(i, Cat(0.U((WIDTH - i - 1).W), 1.U(1.W)))
        } else if (i == WIDTH - 1) {
            output_mask_state = output_mask_state.updated(i, Cat(1.U(1.W), 0.U((i).W)))
        } else if (i < WIDTH) {
            output_mask_state = output_mask_state.updated(i, Cat(0.U((WIDTH - i - 1).W), 1.U(1.W), 0.U((i).W)))
        } else {
            output_mask_state = output_mask_state.updated(i, 0.U(WIDTH.W))
        }
        output_mask_data = output_mask_data.updated(i, 0.U(DATA_WIDTH.W))
    }

    if (CONFIGURATION == "FIBONACCI") {
        for (i <- DATA_WIDTH - 1 to 0 by -1) {
            state_val = lfsr_mask_state(WIDTH - 1)
            data_val = lfsr_mask_data(WIDTH - 1) ^ (1.U << i)

            for (j <- 1 until WIDTH) {
                if ((POLYNOMIAL & (1 << j)) != 0) {
                    state_val = lfsr_mask_state(j - 1) ^ state_val
                    data_val = lfsr_mask_data(j - 1) ^ data_val
                }
            }

            for (j <- WIDTH - 1 to 1 by -1) {
                lfsr_mask_state = lfsr_mask_state.updated(j, lfsr_mask_state(j - 1))
                lfsr_mask_data = lfsr_mask_data.updated(j, lfsr_mask_data(j - 1))
            }

            for (j <- DATA_WIDTH - 1 to 1 by -1) {
                output_mask_state = output_mask_state.updated(j, output_mask_state(j - 1))
                output_mask_data = output_mask_data.updated(j, output_mask_data(j - 1))
            }

            output_mask_state = output_mask_state.updated(0, state_val)
            output_mask_data = output_mask_data.updated(0, data_val)
            if (FEED_FORWARD) {
                state_val = 0.U(WIDTH.W)
                data_val = 1.U << i
            }
            lfsr_mask_state = lfsr_mask_state.updated(0, state_val)
            lfsr_mask_data = lfsr_mask_data.updated(0, data_val)
        }
    } else if (CONFIGURATION == "GALOIS") {
        for (i <- DATA_WIDTH - 1 to 0 by -1) {
            state_val = lfsr_mask_state(WIDTH - 1)
            data_val = lfsr_mask_data(WIDTH - 1) ^ (1.U << i)

            for (j <- WIDTH - 1 to 1 by -1) {
                lfsr_mask_state = lfsr_mask_state.updated(j, lfsr_mask_state(j - 1))
                lfsr_mask_data = lfsr_mask_data.updated(j, lfsr_mask_data(j - 1))
            }

            for (j <- DATA_WIDTH - 1 to 1 by -1) {
                output_mask_state = output_mask_state.updated(j, output_mask_state(j - 1))
                output_mask_data = output_mask_data.updated(j, output_mask_data(j - 1))
            }

            output_mask_state = output_mask_state.updated(0, state_val)
            output_mask_data = output_mask_data.updated(0, data_val)
            if (FEED_FORWARD) {
                state_val = 0.U(WIDTH.W)
                data_val = 1.U << i
            }
            lfsr_mask_state = lfsr_mask_state.updated(0, state_val)
            lfsr_mask_data = lfsr_mask_data.updated(0, data_val)

            for (j <- 1 until WIDTH) {
                if ((POLYNOMIAL & (1 << j)) != 0) {
                    lfsr_mask_state = lfsr_mask_state.updated(j, lfsr_mask_state(j) ^ state_val)
                    lfsr_mask_data = lfsr_mask_data.updated(j, lfsr_mask_data(j) ^ data_val)
                }
            }
        }
    }

    if (REVERSE) {
        lfsr_mask_state = lfsr_mask_state.reverse
        lfsr_mask_data = lfsr_mask_data.reverse
        output_mask_state = output_mask_state.reverse
        output_mask_data = output_mask_data.reverse

        for (i <- 0 until WIDTH) {
            lfsr_mask_data = lfsr_mask_data.updated(i, Reverse(lfsr_mask_data(i)))
            lfsr_mask_state = lfsr_mask_state.updated(i, Reverse(lfsr_mask_state(i)))
        }
        for (i <- 0 until DATA_WIDTH) {
            output_mask_data = output_mask_data.updated(i, Reverse(output_mask_data(i)))
            output_mask_state = output_mask_state.updated(i, Reverse(output_mask_data(i)))
        }
    }

    val state_out_int = Wire(Vec(WIDTH, UInt(1.W)))
    val data_out_int = Wire(Vec(DATA_WIDTH, UInt(1.W)))

    for (n <- 0 until WIDTH) {
        state_out_int(n) := Cat(io.state_in & lfsr_mask_state(n), io.data_in & lfsr_mask_data(n)).xorR
    }

    for (n <- 0 until DATA_WIDTH) {
        data_out_int(n) := Cat(io.state_in & output_mask_state(n), io.data_in & output_mask_data(n)).xorR
    }

    io.state_out := state_out_int.asTypeOf(UInt(WIDTH.W))
    io.data_out := data_out_int.asTypeOf(UInt(DATA_WIDTH.W))
}
