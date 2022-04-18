package project

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog
import chisel3.experimental.RawParam

class BUFRWrapper(val BUFR_DIVIDE: String) extends BlackBox(Map(
                  "BUFR_DIVIDE" -> RawParam(BUFR_DIVIDE))) {
    val io = IO(new Bundle {
        val I = Input(Clock())
        val O = Output(Clock())
        val CE = Input(Bool())
        val CLR = Input(Bool())
    })
}

class BUFIOWrapper extends BlackBox {
    val io = IO(new Bundle {
        val I = Input(Clock())
        val O = Output(Clock())
    })
}

class IDDRWrapper(val DDR_CLK_EDGE: String,
                  val INIT_Q1: Int,
                  val INIT_Q2: Int,
                  val SRTYPE: String,
                  val WIDTH: Int) extends BlackBox(Map(
                  "DDR_CLK_EDGE" -> DDR_CLK_EDGE,
                  "INIT_Q1" -> INIT_Q1,
                  "INIT_Q2" -> INIT_Q2,
                  "SRTYPE" -> SRTYPE,
                  "WIDTH" -> WIDTH)) {
    val io = IO(new Bundle {
        val C = Input(Clock())
        val CE = Input(Bool())
        val R = Input(Bool())
        val S = Input(Bool())
        val D = Input(UInt(WIDTH.W))
        val Q1 = Output(UInt(WIDTH.W))
        val Q2 = Output(UInt(WIDTH.W))
    })
}

class ODDRWrapper(val DDR_CLK_EDGE: String,
                  val INIT: Int,
                  val SRTYPE: String,
                  val WIDTH: Int) extends BlackBox(Map(
                  "DDR_CLK_EDGE" -> DDR_CLK_EDGE,
                  "INIT" -> INIT,
                  "SRTYPE" -> SRTYPE,
                  "WIDTH" -> WIDTH)) {
    val io = IO(new Bundle {
        val C = Input(Clock())
        val CE = Input(Bool())
        val R = Input(Bool())
        val S = Input(Bool())
        val D1 = Input(UInt(WIDTH.W))
        val D2 = Input(UInt(WIDTH.W))
        val Q = Output(UInt(WIDTH.W))
    })
}

class OSERDESE2Wrapper(val DATA_RATE_OQ: String = "DDR",
                       val DATA_RATE_TQ: String = "DDR",
                       val DATA_WIDTH: Int = 1,
                       val INIT_OQ: String = "1'b0",
                       val INIT_TQ: String = "1'b0",
                       val SERDES_MODE: String = "MASTER",
                       val SRVAL_OQ: String = "1'b0",
                       val SRVAL_TQ: String = "1'b0",
                       val TBYTE_CTL: String = "FALSE",
                       val TBYTE_SRC: String = "FALSE",
                       val TRISTATE_WIDTH: Int = 4) extends BlackBox(Map(
                       "DATA_RATE_OQ" -> DATA_RATE_OQ,
                       "DATA_RATE_TQ" -> DATA_RATE_TQ,
                       "DATA_WIDTH" -> DATA_WIDTH,
                       "INIT_OQ" -> RawParam(INIT_OQ),
                       "INIT_TQ" -> RawParam(INIT_TQ),
                       "SERDES_MODE" -> SERDES_MODE,
                       "SRVAL_OQ" -> RawParam(SRVAL_OQ),
                       "SRVAL_TQ" -> RawParam(SRVAL_TQ),
                       "TBYTE_CTL" -> TBYTE_CTL,
                       "TBYTE_SRC" -> TBYTE_SRC,
                       "TRISTATE_WIDTH" -> TRISTATE_WIDTH)) {

    val io = IO(new Bundle {
        val OFB = Output(UInt(1.W))
        val OQ = Output(UInt(1.W))
        val SHIFTOUT1 = Output(UInt(1.W))
        val SHIFTOUT2 = Output(UInt(1.W))
        val TBYTEOUT = Output(UInt(1.W))
        val TFB = Output(Bool())
        val TQ = Output(Bool())
        val CLK = Input(Clock())
        val CLKDIV = Input(Clock())
        val D1 = Input(UInt(1.W))
        val D2 = Input(UInt(1.W))
        val D3 = Input(UInt(1.W))
        val D4 = Input(UInt(1.W))
        val D5 = Input(UInt(1.W))
        val D6 = Input(UInt(1.W))
        val D7 = Input(UInt(1.W))
        val D8 = Input(UInt(1.W))
        val OCE = Input(Bool())
        val RST = Input(Reset())
        val SHIFTIN1 = Input(UInt(1.W))
        val SHIFTIN2 = Input(UInt(1.W))
        val T1 = Input(UInt(1.W))
        val T2 = Input(UInt(1.W))
        val T3 = Input(UInt(1.W))
        val T4 = Input(UInt(1.W))
        val TBYTEIN = Input(UInt(1.W))
        val TCE = Input(Bool())
    })                      
                       
}
