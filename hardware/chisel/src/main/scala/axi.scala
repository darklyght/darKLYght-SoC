package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class AXIStream(val DATA_WIDTH: Int = 8,
                val KEEP_EN: Boolean = true,
                val LAST_EN: Boolean = true,
                val ID_WIDTH: Int = 8,
                val DEST_WIDTH: Int = 8,
                val USER_WIDTH: Int = 8) extends Bundle {
    val tdata = UInt(DATA_WIDTH.W)
    val tkeep = if (KEEP_EN) Some(UInt((DATA_WIDTH / 8).W)) else None
    val tlast = if (LAST_EN) Some(Bool()) else None
    val tid = if (ID_WIDTH > 0) Some(UInt(ID_WIDTH.W)) else None
    val tdest = if (DEST_WIDTH > 0) Some(UInt(DEST_WIDTH.W)) else None
    val tuser = if (USER_WIDTH > 0) Some(UInt(USER_WIDTH.W)) else None
}

class AXI4Full(val DATA_WIDTH: Int, val ADDR_WIDTH: Int, val ID_WIDTH: Int) extends Bundle {
    val aw = Irrevocable(new AXI4FullA(ADDR_WIDTH, ID_WIDTH))
    val w = Irrevocable(new AXI4FullW(DATA_WIDTH))
    val b = Flipped(Irrevocable(new AXI4FullB(ID_WIDTH)))
    val ar = Irrevocable(new AXI4FullA(ADDR_WIDTH, ID_WIDTH))
    val r = Flipped(Irrevocable(new AXI4FullR(DATA_WIDTH, ID_WIDTH)))
}

class AXI4FullA(val ADDR_WIDTH: Int, val ID_WIDTH: Int) extends Bundle {
    val id = UInt(ID_WIDTH.W)
    val addr = UInt(ADDR_WIDTH.W)
    val len = UInt(8.W)
    val size = UInt(3.W)
    val burst = UInt(2.W)
    val lock = UInt(1.W)
    val cache = UInt(4.W)
    val prot = UInt(3.W)
    val qos = UInt(4.W)
}

class AXI4FullW(val DATA_WIDTH: Int) extends Bundle {
    val data = UInt(DATA_WIDTH.W)
    val strb = UInt((DATA_WIDTH/8).W)
    val last = Bool()
}

class AXI4FullB(val ID_WIDTH: Int) extends Bundle {
    val id = UInt(ID_WIDTH.W)
    val resp = UInt(2.W)
}

class AXI4FullR(val DATA_WIDTH: Int, val ID_WIDTH: Int) extends Bundle {
    val id = UInt(ID_WIDTH.W)
    val data = UInt(DATA_WIDTH.W)
    val resp = UInt(2.W)
    val last = Bool()
}

class AXIStreamAsyncFIFO(val DATA_WIDTH: Int = 8,
                         val KEEP_EN: Boolean = true,
                         val LAST_EN: Boolean = true,
                         val ID_WIDTH: Int = 8,
                         val DEST_WIDTH: Int = 8,
                         val USER_WIDTH: Int = 8) extends AsyncFIFO[AXIStream](DEPTH = 16,
                                                                               FRAME_FIFO = false,
                                                                               DROP_WHEN_FULL = true,
                                                                               DROP_BAD_FRAME = false,
                                                                               OUTPUT_PIPE = 1,
                                                                               new AXIStream(DATA_WIDTH = DATA_WIDTH,
                                                                                             KEEP_EN = KEEP_EN,
                                                                                             LAST_EN = LAST_EN,
                                                                                             ID_WIDTH = ID_WIDTH,
                                                                                             DEST_WIDTH = DEST_WIDTH,
                                                                                             USER_WIDTH = USER_WIDTH))


class AXICrossbar(val DATA_WIDTH: Int,
                  val ADDR_WIDTH: Int,
                  val ID_WIDTH: Int,
                  val N_MASTERS: Int,
                  val N_SLAVES: Int,
                  val SLAVE_ADDR: Seq[Seq[Bits]],
                  val SLAVE_MASK: Seq[Seq[Bits]],
                  val ALLOWED: Seq[Seq[Bool]]) extends Module {
    val io = IO(new Bundle {
        val S_AXI = Vec(N_MASTERS, Flipped(new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH)))
        val M_AXI = Vec(N_SLAVES + 1, new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
    })
    
    val masters = Seq.tabulate(N_MASTERS)(m => Module(new AXICrossbarMaster(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH, N_SLAVES, SLAVE_ADDR(m), SLAVE_MASK(m), ALLOWED(m))))
    val slaves = Seq.fill(N_SLAVES + 1)(Module(new AXICrossbarSlave(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH, N_MASTERS)))
    
    for (m <- 0 until N_MASTERS) {
        for (s <- 0 until N_SLAVES + 1) {
            masters(m).io.S_AXI <> io.S_AXI(m)
            slaves(s).io.S_AXI(m) <> masters(m).io.M_AXI(s)
            slaves(s).io.M_AXI <> io.M_AXI(s)
        }
    }
}

class AXICrossbarDecoder(val ADDR_WIDTH: Int,
                         val ID_WIDTH: Int,
                         val N_SLAVES: Int,
                         val SLAVE_ADDR: Seq[Bits],
                         val SLAVE_MASK: Seq[Bits],
                         val ALLOWED: Seq[Bool]) extends Module {
    val io = IO(new Bundle {
        val input = Input(new AXI4FullA(ADDR_WIDTH, ID_WIDTH))
        val decode = Output(Vec(N_SLAVES + 1, Bool()))
    })
    
    val prerequest = Wire(Vec(N_SLAVES, Bool()))
    val decerr = Wire(Vec(1, Bool()))
    
    for (s <- 0 until N_SLAVES) {
        prerequest(s) := (((io.input.addr ^ SLAVE_ADDR(s).asUInt()) & SLAVE_MASK(s).asUInt()) === 0.U) && ALLOWED(s)
    }
    decerr(0) := ~(prerequest.asUInt.orR)
    
    io.decode := VecInit(IndexedSeq((prerequest ++ decerr):_*))
}

class AXICrossbarMaster(val DATA_WIDTH: Int,
                        val ADDR_WIDTH: Int,
                        val ID_WIDTH: Int,
                        val N_SLAVES: Int,
                        val SLAVE_ADDR: Seq[Bits],
                        val SLAVE_MASK: Seq[Bits],
                        val ALLOWED: Seq[Bool]) extends Module {
    val io = IO(new Bundle {
        val S_AXI = Flipped(new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
        val M_AXI = Vec(N_SLAVES + 1, new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
    })
    
    val aw_decoder = Module(new AXICrossbarDecoder(ADDR_WIDTH, ID_WIDTH, N_SLAVES, SLAVE_ADDR, SLAVE_MASK, ALLOWED))
    val aw_queue = Module(new Queue(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), 2))
    val aw_slave = Module(new Queue(Vec(N_SLAVES + 1, Bool()), 8))
    
    aw_queue.io.deq.ready := false.B
    for (s <- 0 until N_SLAVES + 1) {
        io.M_AXI(s).aw.bits := aw_queue.io.deq.bits
        io.M_AXI(s).aw.valid := false.B
        when (aw_slave.io.deq.bits(s) & aw_slave.io.deq.valid) {
            io.M_AXI(s).aw.valid := aw_queue.io.deq.valid
            aw_queue.io.deq.ready := io.M_AXI(s).aw.ready
        }
    }

    aw_decoder.io.input <> io.S_AXI.aw.bits
    aw_queue.io.enq.bits := io.S_AXI.aw.bits
    aw_queue.io.enq.valid := io.S_AXI.aw.valid
    io.S_AXI.aw.ready := aw_queue.io.enq.ready & aw_slave.io.enq.ready
    aw_slave.io.enq.bits := aw_decoder.io.decode
    aw_slave.io.enq.valid := io.S_AXI.aw.valid
    
    val w_queue = Module(new Queue(new AXI4FullW(DATA_WIDTH), 2))
    
    w_queue.io.deq.ready := false.B
    for (s <- 0 until N_SLAVES + 1) {
        io.M_AXI(s).w.bits := w_queue.io.deq.bits
        io.M_AXI(s).w.valid := false.B
        when (aw_slave.io.deq.bits(s) & aw_slave.io.deq.valid) {
            io.M_AXI(s).w.valid := w_queue.io.deq.valid
            w_queue.io.deq.ready := io.M_AXI(s).w.ready
        }
    }
    
    w_queue.io.enq <> io.S_AXI.w
    
    val b_queue = Module(new Queue(new AXI4FullB(ID_WIDTH), 2))
    
    b_queue.io.enq.bits := io.M_AXI(0).b.bits
    b_queue.io.enq.valid := false.B
    for (s <- 0 until N_SLAVES + 1) {
        io.M_AXI(s).b.ready := false.B
        when (aw_slave.io.deq.bits(s) & aw_slave.io.deq.valid) {
            b_queue.io.enq.bits := io.M_AXI(s).b.bits
            b_queue.io.enq.valid := io.M_AXI(s).b.valid
            io.M_AXI(s).b.ready := b_queue.io.enq.ready
        }
    }
    
    io.S_AXI.b <> b_queue.io.deq
    aw_slave.io.deq.ready := b_queue.io.enq.fire()
    
    val ar_decoder = Module(new AXICrossbarDecoder(ADDR_WIDTH, ID_WIDTH, N_SLAVES, SLAVE_ADDR, SLAVE_MASK, ALLOWED))
    val ar_queue = Module(new Queue(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), 2))
    val ar_slave = Module(new Queue(Vec(N_SLAVES + 1, Bool()), 8))
    
    ar_queue.io.deq.ready := false.B
    for (s <- 0 until N_SLAVES + 1) {
        io.M_AXI(s).ar.bits := ar_queue.io.deq.bits
        io.M_AXI(s).ar.valid := false.B
        when (ar_slave.io.deq.bits(s) & ar_slave.io.deq.valid) {
            io.M_AXI(s).ar.valid := ar_queue.io.deq.valid
            ar_queue.io.deq.ready := io.M_AXI(s).ar.ready
        }
    }

    ar_decoder.io.input <> io.S_AXI.ar.bits
    ar_queue.io.enq.bits := io.S_AXI.ar.bits
    ar_queue.io.enq.valid := io.S_AXI.ar.valid
    io.S_AXI.ar.ready := ar_queue.io.enq.ready & ar_slave.io.enq.ready
    ar_slave.io.enq.bits := ar_decoder.io.decode
    ar_slave.io.enq.valid := io.S_AXI.ar.valid
    
    val r_queue = Module(new Queue(new AXI4FullR(DATA_WIDTH, ID_WIDTH), 2))
    
    r_queue.io.enq.bits := io.M_AXI(0).r.bits
    r_queue.io.enq.valid := false.B
    for (s <- 0 until N_SLAVES + 1) {
        io.M_AXI(s).r.ready := false.B
        when (ar_slave.io.deq.bits(s) & ar_slave.io.deq.valid) {
            r_queue.io.enq.bits := io.M_AXI(s).r.bits
            r_queue.io.enq.valid := io.M_AXI(s).r.valid
            io.M_AXI(s).r.ready := r_queue.io.enq.ready
        }
    }
    
    io.S_AXI.r <> r_queue.io.deq
    ar_slave.io.deq.ready := r_queue.io.enq.fire() & r_queue.io.enq.bits.last.asBool()
}

class AXICrossbarSlave(val DATA_WIDTH: Int,
                       val ADDR_WIDTH: Int,
                       val ID_WIDTH: Int,
                       val N_MASTERS: Int) extends Module {
    val io = IO(new Bundle {
        val S_AXI = Vec(N_MASTERS, Flipped(new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH)))
        val M_AXI = new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH)
    })
    
    val aw_arbiter = Module(new RRArbiter(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), N_MASTERS))
    val aw_queue = Module(new Queue(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), 2))
    val aw_master = Module(new Queue(UInt(log2Ceil(N_MASTERS + 1).W), 8))
    
    for ((a, i) <- aw_arbiter.io.in zip io.S_AXI) {
        a <> i.aw
    }
    
    aw_queue.io.enq.bits <> aw_arbiter.io.out.bits
    aw_queue.io.enq.valid := aw_arbiter.io.out.valid
    aw_arbiter.io.out.ready := aw_queue.io.enq.ready & aw_master.io.enq.ready
    aw_master.io.enq.bits := aw_arbiter.io.chosen
    aw_master.io.enq.valid := aw_arbiter.io.out.valid
    io.M_AXI.aw <> aw_queue.io.deq
    
    val w_queue = Module(new Queue(new AXI4FullW(DATA_WIDTH), 2))
    
    for (m <- 0 until N_MASTERS) {
        io.S_AXI(m).w.ready := false.B
    }
    
    w_queue.io.enq.bits := io.S_AXI(aw_master.io.deq.bits).w.bits
    w_queue.io.enq.valid := io.S_AXI(aw_master.io.deq.bits).w.valid & aw_master.io.deq.valid
    io.S_AXI(aw_master.io.deq.bits).w.ready := w_queue.io.enq.ready & aw_master.io.deq.valid
    io.M_AXI.w <> w_queue.io.deq
    
    val b_queue = Module(new Queue(new AXI4FullB(ID_WIDTH), 2))
    
    b_queue.io.enq <> io.M_AXI.b
    
    for (m <- 0 until N_MASTERS) {
        io.S_AXI(m).b.bits := b_queue.io.deq.bits
        io.S_AXI(m).b.valid := false.B
    }
    
    io.S_AXI(aw_master.io.deq.bits).b.valid := b_queue.io.deq.valid & aw_master.io.deq.valid
    b_queue.io.deq.ready := io.S_AXI(aw_master.io.deq.bits).b.ready & aw_master.io.deq.valid
    aw_master.io.deq.ready := b_queue.io.deq.fire()
    
    val ar_arbiter = Module(new RRArbiter(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), N_MASTERS))
    val ar_queue = Module(new Queue(new AXI4FullA(ADDR_WIDTH, ID_WIDTH), 2))
    val ar_master = Module(new Queue(UInt(log2Ceil(N_MASTERS + 1).W), 8))
    
    for ((a, i) <- ar_arbiter.io.in zip io.S_AXI) {
        a <> i.ar
    }
    
    ar_queue.io.enq.bits <> ar_arbiter.io.out.bits
    ar_queue.io.enq.valid := ar_arbiter.io.out.valid
    ar_arbiter.io.out.ready := ar_queue.io.enq.ready & ar_master.io.enq.ready
    ar_master.io.enq.bits := ar_arbiter.io.chosen
    ar_master.io.enq.valid := ar_arbiter.io.out.valid
    io.M_AXI.ar <> ar_queue.io.deq
    
    val r_queue = Module(new Queue(new AXI4FullR(DATA_WIDTH, ID_WIDTH), 2))
    
    for (m <- 0 until N_MASTERS) {
        io.S_AXI(m).r.bits := r_queue.io.deq.bits
        io.S_AXI(m).r.valid := false.B
    }
    
    r_queue.io.enq <> io.M_AXI.r
    io.S_AXI(ar_master.io.deq.bits).r.valid := r_queue.io.deq.valid & ar_master.io.deq.valid
    r_queue.io.deq.ready := io.S_AXI(ar_master.io.deq.bits).r.ready & ar_master.io.deq.valid
    ar_master.io.deq.ready := r_queue.io.deq.fire() & r_queue.io.deq.bits.last.asBool()
}

class DecErrSlave(val DATA_WIDTH: Int,
                  val ADDR_WIDTH: Int,
                  val ID_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val S_AXI = Flipped(new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
    })

    val read_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val write_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))

    object StateRead extends ChiselEnum {
        val sIdle, sReadResp = Value
    }
    
    object StateWrite extends ChiselEnum {
        val sIdle, sWriteData, sWriteResp = Value
    }

    val read_state = RegInit(StateRead.sIdle)
    val write_state = RegInit(StateWrite.sIdle)

    io.S_AXI.ar.ready := read_state === StateRead.sIdle
    io.S_AXI.r.bits.id := read_addr.id
    io.S_AXI.r.bits.data := 0.U(DATA_WIDTH.W)
    io.S_AXI.r.bits.resp := 3.U(2.W)
    io.S_AXI.r.bits.last := read_addr.len === 0.U
    io.S_AXI.r.valid := read_state === StateRead.sReadResp

    io.S_AXI.aw.ready := write_state === StateWrite.sIdle
    io.S_AXI.w.ready := write_state === StateWrite.sIdle || write_state === StateWrite.sWriteData
    io.S_AXI.b.bits.id := write_addr.id
    io.S_AXI.b.bits.resp := 3.U(2.W)
    io.S_AXI.b.valid := write_state === StateWrite.sWriteResp

    switch (read_state) {
        is (StateRead.sIdle) {
            when (io.S_AXI.ar.fire()) {
                read_state := StateRead.sReadResp
                read_addr := io.S_AXI.ar.bits
            }
        }
        is (StateRead.sReadResp) {
            when (io.S_AXI.r.fire()) {
                when (read_addr.len === 0.U) {
                    read_state := StateRead.sIdle
                } .otherwise {
                    read_addr.len := read_addr.len - 1.U
                }
            }
        }
    }
    
    switch (write_state) {
        is (StateWrite.sIdle) {
            when (io.S_AXI.aw.fire()) {
                write_state := StateWrite.sWriteData
                write_addr := io.S_AXI.aw.bits
            }
        }
        is (StateWrite.sWriteData) {
            when (io.S_AXI.w.fire()) {
                when (write_addr.len === 0.U) {
                    write_state := StateWrite.sWriteResp
                } .otherwise {
                    write_addr.len := write_addr.len - 1.U
                }
            }
        }
        is (StateWrite.sWriteResp) {
            when (io.S_AXI.b.fire()) {
                write_state := StateWrite.sIdle
            }
        }
    }
}