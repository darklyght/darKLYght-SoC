package project

import chisel3._
import chisel3.util._

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

class EthernetAXIShim(val N_SLAVES: Int, val PORTS: Seq[Bits], val DATA_WIDTH: Int, val ADDR_WIDTH: Int, val ID_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val M_AXI = Vec(N_SLAVES, new AXI4Full(DATA_WIDTH, ADDR_WIDTH, ID_WIDTH))
    })
}