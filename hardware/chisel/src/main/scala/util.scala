package project

import chisel3._
import chisel3.util._

class SyncDebouncer[T <: Data](val CLOCK_FREQUENCY: Int, val SAMPLE_FREQUENCY: Int, val TYPE: T) extends Module {
    val io = IO(new Bundle {
        val input = Input(TYPE)
        val output = Output(TYPE)
    })
    
    // Synchronizer
    
    val input_sync = RegNext(RegNext(io.input))
    
    // Debouncer
    
    val FAC = CLOCK_FREQUENCY / SAMPLE_FREQUENCY
    
    val input_db = Reg(TYPE)
    val count = RegInit(0.U(log2Ceil(FAC).W))
    val tick = count === (FAC - 1).U
        
    count := count + 1.U
    
    when (tick) {
        count := 0.U
        input_db := input_sync
    }

    io.output := input_db
}

class AsyncFIFO[T <: Data](val DEPTH: Int,
                           val FRAME_FIFO: Boolean,
                           val DROP_WHEN_FULL: Boolean,
                           val OUTPUT_PIPE: Int,
                           val TYPE: T) extends Module {
    val io = IO(new Bundle {
        val deq_clock = Input(Clock())
        val deq = Decoupled(TYPE)
        val enq_clock = Input(Clock())
        val enq = Flipped(Decoupled(TYPE))
        val del = Input(Bool()) // Delimiter for frame mode
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

        val mem = Mem(DEPTH, TYPE)
        rd_mem := mem(rd_ptr(SIZE - 1, 0))

        if (FRAME_FIFO) {
            when (wr_ptr_update_valid && wr_ptr_update === wr_ptr_update_ack_sync(0)) {
                wr_ptr_update_valid := false.B
                wr_ptr_sync_gray_reg := wr_ptr_gray_reg
                wr_ptr_update_reg := ~wr_ptr_update_ack_sync(0)
            }
        }

        when (io.enq.fire()) {
            if (!FRAME_FIFO) {
                mem(wr_ptr(SIZE - 1, 0)) := io.enq.bits
                wr_ptr := wr_ptr + 1.U;
                wr_ptr_gray_reg := (wr_ptr + 1.U) ^ ((wr_ptr + 1.U) >> 1)
            } else {
                when (full_cur || full_wr || frame_dropped) {
                    frame_dropped := true.B
                    when (io.del) {
                        wr_ptr_cur := wr_ptr
                        wr_ptr_cur_gray_reg := wr_ptr ^ (wr_ptr >> 1)
                        frame_dropped := false.B
                    }
                } .otherwise {
                    mem(wr_ptr_cur(SIZE - 1, 0)) := io.enq.bits
                    wr_ptr_cur := wr_ptr_cur + 1.U
                    wr_ptr_cur_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                    when (io.del) {
                        wr_ptr := wr_ptr_cur + 1.U
                        wr_ptr_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                        when (wr_ptr_update === wr_ptr_update_ack_sync(0)) {
                            wr_ptr_update_valid := false.B
                            wr_ptr_sync_gray_reg := (wr_ptr_cur + 1.U) ^ ((wr_ptr_cur + 1.U) >> 1)
                            wr_ptr_update_reg := ~wr_ptr_update_ack_sync(0)
                        } .otherwise {
                            wr_ptr_update_valid := true.B
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