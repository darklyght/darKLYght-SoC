package project

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object Opcodes extends ChiselEnum {
    val LUI = "b0110111".U(7.W)
    val AUIPC = "b0010111".U(7.W)
    val JAL = "b1101111".U(7.W)
    val JALR = "b1100111".U(7.W)
    val BRANCH = "b1100011".U(7.W)
    val STORE = "b0100011".U(7.W)
    val LOAD = "b0000011".U(7.W)
    val ALUI = "b0010011".U(7.W)
    val ALU = "b0110011".U(7.W)
    val FENCE = "b0001111".U(7.W)
    val CSR = "b1110011".U(7.W)
}

class RVFI extends Bundle {
    val valid = Bool()
    val order = UInt(64.W)
    val insn = UInt(32.W)
    val trap = Bool()
    val halt = Bool()
    val intr = Bool()
    val rs1_addr = UInt(5.W)
    val rs2_addr = UInt(5.W)
    val rs1_rdata = UInt(32.W)
    val rs2_rdata = UInt(32.W)
    val rd_addr = UInt(5.W)
    val rd_wdata = UInt(32.W)
    val pc_rdata = UInt(32.W)
    val pc_wdata = UInt(32.W)
    val mem_addr = UInt(32.W)
    val mem_rmask = UInt(4.W)
    val mem_wmask = UInt(4.W)
    val mem_rdata = UInt(32.W)
    val mem_wdata = UInt(32.W)
}

class FetchToDecode extends Bundle {
    val pc = UInt(32.W)
    val instruction = UInt(32.W)
}

class DecodeToExecute extends Bundle {
    val pc = UInt(32.W)
    val instruction = UInt(32.W)
    val rs1 = UInt(32.W)
    val rs2 = UInt(32.W)
    val immediate = UInt(32.W)
}

class Decode(val FORMAL: Bool) extends Module {
    val io = IO(new Bundle {
        val fetch = Flipped(Decoupled(new FetchToDecode))
        val execute = Decoupled(new DecodeToExecute)

    })
}