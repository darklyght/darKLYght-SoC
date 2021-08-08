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