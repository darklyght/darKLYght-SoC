package project

import chisel3._
import chisel3.util._

class Network extends Module {
    val io = IO(new Bundle {
        val ethernet_clock = Input(Clock())
        val ethernet_clock_90 = Input(Clock())
        val ethernet_reset = Input(Reset())
        val ethernet = new RGMIIPHYDuplex()
        val tx_input = Flipped(Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                       KEEP_EN = false,
                                                       LAST_EN = true,
                                                       ID_WIDTH = 0,
                                                       DEST_WIDTH = 0,
                                                       USER_WIDTH = 1)))
        val tx_header = Flipped(Decoupled(new IPFrameHeader(DIRECTION = "TRANSMIT")))
        val rx_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val rx_header = Decoupled(new IPFrameHeader(DIRECTION = "RECEIVE"))
    })

    val ethernet_phy = Module(new EthernetPHY(PADDING = false, MINIMUM_FRAME_LENGTH = 64))
    val ethernet_frame = Module(new EthernetFrame)
    val ip_frame = Module(new IPFrame)

    ethernet_phy.io.phy_clock := io.ethernet_clock
    ethernet_phy.io.phy_clock_90 := io.ethernet_clock_90
    ethernet_phy.io.phy_reset := io.ethernet_reset
    ethernet_phy.io.phy <> io.ethernet
    ethernet_phy.io.tx_ifg_delay := 12.U

    ethernet_frame.io.rx_input <> ethernet_phy.io.rx
    ethernet_frame.io.tx_output <> ethernet_phy.io.tx
    ethernet_frame.io.rx_output <> ip_frame.io.rx_ethernet_input
    ethernet_frame.io.rx_header <> ip_frame.io.rx_ethernet_header
    ethernet_frame.io.tx_input <> ip_frame.io.tx_ethernet_output
    ethernet_frame.io.tx_header <> ip_frame.io.tx_ethernet_header

    ip_frame.io.rx_ip_header <> io.rx_header
    ip_frame.io.rx_ip_output <> io.rx_output
    ip_frame.io.tx_ip_header <> io.tx_header
    ip_frame.io.tx_ip_input <> io.tx_input
}