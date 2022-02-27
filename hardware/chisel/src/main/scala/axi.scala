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
        prerequest(s) := (((io.input.addr ^ SLAVE_ADDR(s).asUInt()) & SLAVE_MASK(s).asUInt()) === 0.U) && ALLOWED(s) && (io.input.addr(ADDR_WIDTH - 1, 12) === (io.input.addr + io.input.len)(ADDR_WIDTH - 1, 12))
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
    
    io.S_AXI(b_queue.io.deq.bits.id).b.valid := b_queue.io.deq.valid
    b_queue.io.deq.ready := io.S_AXI(b_queue.io.deq.bits.id).b.ready
    aw_master.io.deq.ready := w_queue.io.deq.fire() && w_queue.io.deq.bits.last
    
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
    io.S_AXI(r_queue.io.deq.bits.id).r.valid := r_queue.io.deq.valid
    r_queue.io.deq.ready := io.S_AXI(r_queue.io.deq.bits.id).r.ready
    ar_master.io.deq.ready := ar_queue.io.deq.fire()
}

class ErrSlave(val DATA_WIDTH: Int,
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
    val read_pointer = RegInit(0.U(8.W))
    val write_pointer = RegInit(0.U(8.W))

    io.S_AXI.ar.ready := read_state === StateRead.sIdle
    io.S_AXI.r.bits.id := read_addr.id
    io.S_AXI.r.bits.data := 0.U(DATA_WIDTH.W)
    io.S_AXI.r.bits.resp := Mux((read_addr.addr(ADDR_WIDTH - 1, 12) === (read_addr.addr + read_addr.len)(ADDR_WIDTH - 1, 12)), 3.U(2.W), 2.U(2.W))
    io.S_AXI.r.bits.last := read_pointer === 0.U
    io.S_AXI.r.valid := read_state === StateRead.sReadResp

    io.S_AXI.aw.ready := write_state === StateWrite.sIdle
    io.S_AXI.w.ready := write_state === StateWrite.sIdle || write_state === StateWrite.sWriteData
    io.S_AXI.b.bits.id := write_addr.id
    io.S_AXI.b.bits.resp := Mux((write_addr.addr(ADDR_WIDTH - 1, 12) === (write_addr.addr + write_addr.len)(ADDR_WIDTH - 1, 12)), 3.U(2.W), 2.U(2.W))
    io.S_AXI.b.valid := write_state === StateWrite.sWriteResp

    switch (read_state) {
        is (StateRead.sIdle) {
            when (io.S_AXI.ar.fire()) {
                read_state := StateRead.sReadResp
                read_addr := io.S_AXI.ar.bits
                read_pointer := io.S_AXI.ar.bits.len
            }
        }
        is (StateRead.sReadResp) {
            when (io.S_AXI.r.fire()) {
                when (read_pointer === 0.U) {
                    read_state := StateRead.sIdle
                } .otherwise {
                    read_pointer := read_pointer - 1.U
                }
            }
        }
    }
    
    switch (write_state) {
        is (StateWrite.sIdle) {
            when (io.S_AXI.aw.fire()) {
                write_state := StateWrite.sWriteData
                write_addr := io.S_AXI.aw.bits
                write_pointer := io.S_AXI.aw.bits.len
            }
        }
        is (StateWrite.sWriteData) {
            when (io.S_AXI.w.fire()) {
                when (write_pointer === 0.U) {
                    write_state := StateWrite.sWriteResp
                } .otherwise {
                    write_pointer := write_pointer - 1.U
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

class AXIRegisterFile(val DATA_WIDTH: Int,
                      val ADDR_WIDTH: Int,
                      val ID_WIDTH: Int) extends Module {
    
    val ADDR_WIDTH_EFF = ADDR_WIDTH - log2Ceil(DATA_WIDTH / 8)

    val io = IO(new Bundle {
        val S_AXI = Flipped(new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
        val input = Input(Vec(math.pow(2, ADDR_WIDTH_EFF).toInt, UInt(DATA_WIDTH.W)))
        val output = Output(Vec(math.pow(2, ADDR_WIDTH_EFF).toInt, UInt(DATA_WIDTH.W)))
    })

    object StateRead extends ChiselEnum {
        val sIdle, sReadResp = Value
    }
    
    object StateWrite extends ChiselEnum {
        val sIdle, sWriteData, sWriteResp = Value
    }

    val registers = RegInit(VecInit(Seq.fill(math.pow(2, ADDR_WIDTH_EFF).toInt)(0.U(DATA_WIDTH.W))))
    val read_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val write_addr = Reg(new AXI4FullA(ADDR_WIDTH = ADDR_WIDTH, ID_WIDTH = ID_WIDTH))
    val read_state = RegInit(StateRead.sIdle)
    val write_state = RegInit(StateWrite.sIdle)
    val read_pointer = RegInit(0.U(8.W))
    val write_pointer = RegInit(0.U(8.W))
    val write_response = RegInit(0.U(2.W))

    //
    // Configures registers
    // Valid read flags registers that are valid for read
    // Valid write flags registers that are valid for write
    // Input read flags registers that update values based on input ports
    //

    val valid_read = Wire(Vec(math.pow(2, ADDR_WIDTH_EFF).toInt, Bool()))
    val valid_write = Wire(Vec(math.pow(2, ADDR_WIDTH_EFF).toInt, Bool()))
    val input_read = Wire(Vec(math.pow(2, ADDR_WIDTH_EFF).toInt, Bool()))

    for (r <- 0 until math.pow(2, ADDR_WIDTH_EFF).toInt) {
        valid_read(r) := true.B
        valid_write(r) := true.B
        input_read(r) := false.B
        when (input_read(r)) {
            registers(r) := io.input(r)
        }
    }

    valid_write(1) := false.B
    input_read(1) := true.B

    val w_bits_vec = Wire(Vec(DATA_WIDTH / 8, UInt(8.W)))
    val w_bits = w_bits_vec.asUInt

    for (b <- 0 until DATA_WIDTH / 8) {
        w_bits_vec(b) := Mux(io.S_AXI.w.bits.strb(b), io.S_AXI.w.bits.data(b * 8 + 7, b * 8), registers(write_addr.addr + write_pointer)(b * 8 + 7, b * 8))
    }

    io.S_AXI.ar.ready := read_state === StateRead.sIdle
    io.S_AXI.r.bits.id := read_addr.id
    io.S_AXI.r.bits.data := registers(read_addr.addr + read_pointer)
    io.S_AXI.r.bits.resp := Mux(valid_read(read_addr.addr + read_pointer), 0.U(2.W), 2.U(2.W))
    io.S_AXI.r.bits.last := read_pointer === read_addr.len
    io.S_AXI.r.valid := read_state === StateRead.sReadResp

    io.S_AXI.aw.ready := write_state === StateWrite.sIdle
    io.S_AXI.w.ready := write_state === StateWrite.sWriteData
    io.S_AXI.b.bits.id := write_addr.id
    io.S_AXI.b.bits.resp := write_response
    io.S_AXI.b.valid := write_state === StateWrite.sWriteResp

    io.output := registers

    switch (read_state) {
        is (StateRead.sIdle) {
            when (io.S_AXI.ar.fire()) {
                read_state := StateRead.sReadResp
                read_addr := io.S_AXI.ar.bits
                read_pointer := 0.U
            }
        }
        is (StateRead.sReadResp) {
            when (io.S_AXI.r.fire()) {
                when (read_pointer === read_addr.len) {
                    read_state := StateRead.sIdle
                } .otherwise {
                    read_pointer := read_pointer + 1.U
                }
            }
        }
    }
    
    switch (write_state) {
        is (StateWrite.sIdle) {
            when (io.S_AXI.aw.fire()) {
                write_state := StateWrite.sWriteData
                write_addr := io.S_AXI.aw.bits
                write_pointer := 0.U
                write_response := 0.U
            }
        }
        is (StateWrite.sWriteData) {
            when (io.S_AXI.w.fire()) {
                when (valid_write(write_addr.addr + write_pointer)) {
                    registers(write_addr.addr + write_pointer) := w_bits
                } .otherwise {
                    write_response := 2.U(2.W)
                }
                when (write_pointer === write_addr.len) {
                    write_state := StateWrite.sWriteResp
                } .otherwise {
                    write_pointer := write_pointer + 1.U
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