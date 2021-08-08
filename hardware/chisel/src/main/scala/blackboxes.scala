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
