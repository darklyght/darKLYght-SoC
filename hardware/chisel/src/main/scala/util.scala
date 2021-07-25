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
                           val TYPE: T) extends Module {
    val io = IO(new Bundle {
        val deq_clock = Input(Clock())
        val deq_reset = Input(Reset())
        val deq = Decoupled(TYPE)
        val enq_clock = Input(Clock())
        val enq_reset = Input(Reset())
        val enq = Flipped(Decoupled(TYPE))
    })
    val SIZE = log2Ceil(DEPTH)

    val wptr_gray = Wire(UInt((SIZE + 1).W))
    val rptr_gray = Wire(UInt((SIZE + 1).W))

    val r_addr = Wire(UInt(SIZE.W))
    val w_addr = Wire(UInt(SIZE.W))

    withClockAndReset(io.enq_clock, io.enq_reset) {
        val rptr_gray_sync = Seq.fill(2)(RegInit(0.U((SIZE + 1).W)))
        val deq_rst_sync = Seq.fill(2)(RegInit(false.B))
        val wptr_bin = RegInit(0.U((SIZE + 1).W))
        val wptr_gray_reg = RegInit(0.U((SIZE + 1).W))
        val not_full = RegInit(true.B)

        rptr_gray_sync(1) := rptr_gray
        rptr_gray_sync(0) := rptr_gray_sync(1)
        deq_rst_sync(1) := io.enq_reset.asBool()
        deq_rst_sync(0) := deq_rst_sync(1)
        wptr_bin := wptr_bin + (io.enq.valid & not_full)
        wptr_gray_reg := ((wptr_bin + (io.enq.valid & not_full)) >> 1) ^ (wptr_bin + (io.enq.valid & not_full))
        not_full := ~deq_rst_sync(0) && ~((((wptr_bin + (io.enq.valid & not_full)) >> 1) ^ (wptr_bin + (io.enq.valid & not_full))) === Cat(~rptr_gray_sync(0)(SIZE, SIZE - 1), rptr_gray_sync(0)(SIZE - 2, 0)))

        wptr_gray := wptr_gray_reg
        io.enq.ready := not_full

        w_addr := wptr_bin(SIZE - 1, 0)
        
        val mem = Mem(1 << SIZE, TYPE)
        when (io.enq.fire) {
            mem(w_addr) := io.enq.bits
        }
        io.deq.bits := mem(r_addr)
    }

    withClockAndReset(io.deq_clock, io.deq_reset) {
        val wptr_gray_sync = Seq.fill(2)(RegInit(0.U((SIZE + 1).W)))
        val enq_rst_sync = Seq.fill(2)(RegInit(false.B))
        val rptr_bin = RegInit(0.U((SIZE + 1).W))
        val rptr_gray_reg = RegInit(0.U((SIZE + 1).W))
        val not_empty = RegInit(false.B)

        wptr_gray_sync(1) := wptr_gray
        wptr_gray_sync(0) := wptr_gray_sync(1)
        enq_rst_sync(1) := io.deq_reset.asBool()
        enq_rst_sync(0) := enq_rst_sync(1)
        rptr_bin := rptr_bin + (io.deq.ready & not_empty)
        rptr_gray_reg := ((rptr_bin + (io.deq.valid & not_empty)) >> 1) ^ (rptr_bin + (io.deq.valid & not_empty))
        not_empty := ~enq_rst_sync(0) && ~((((rptr_bin + (io.deq.valid & not_empty)) >> 1) ^ (rptr_bin + (io.deq.valid & not_empty))) === wptr_gray_sync(0))

        rptr_gray := rptr_gray_reg
        io.deq.valid := not_empty

        r_addr := rptr_bin(SIZE - 1, 0)
    }
    
    /*
    val mem = Mem(1 << SIZE, TYPE)
    when (io.enq.fire()) {
        mem(wptr_bin(SIZE - 1, 0)) := io.enq.bits
    }
    io.deq.bits := mem(rptr_bin(SIZE - 1, 0))*/
}