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
    if (KEEP_EN) {
        val tkeep = UInt((DATA_WIDTH / 8).W)
    }
    if (LAST_EN) {
        val tlast = Bool()
    }
    if (ID_WIDTH > 0) {
        val tid = UInt(ID_WIDTH.W)
    }
    if (DEST_WIDTH > 0) {
        val tdest = UInt(DEST_WIDTH.W)
    }
    if (USER_WIDTH > 0) {
        val tuser = UInt(USER_WIDTH.W)
    }
}

class AXIStreamAsyncFIFO(val DATA_WIDTH: Int = 8,
                         val KEEP_EN: Boolean = true,
                         val LAST_EN: Boolean = true,
                         val ID_WIDTH: Int = 8,
                         val DEST_WIDTH: Int = 8,
                         val USER_WIDTH: Int = 8) extends AsyncFIFO[AXIStream](256, new AXIStream(DATA_WIDTH = DATA_WIDTH,
                                                                                                  KEEP_EN = KEEP_EN,
                                                                                                  LAST_EN = LAST_EN,
                                                                                                  ID_WIDTH = ID_WIDTH,
                                                                                                  DEST_WIDTH = DEST_WIDTH,
                                                                                                  USER_WIDTH = USER_WIDTH))