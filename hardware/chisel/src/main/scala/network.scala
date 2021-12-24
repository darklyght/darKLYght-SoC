package project

import chisel3._
import chisel3.util._

class Network(val MAC: String,
              val IP: String,
              val GATEWAY: String,
              val SUBNET: String) extends Module {
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
        val tx_header = Flipped(Decoupled(new UDPFrameHeader))
        val rx_output = Decoupled(new AXIStream(DATA_WIDTH = 8,
                                                KEEP_EN = false,
                                                LAST_EN = true,
                                                ID_WIDTH = 0,
                                                DEST_WIDTH = 0,
                                                USER_WIDTH = 1))
        val rx_header = Decoupled(new UDPFrameHeader)
    })

    val ethernet_phy = Module(new EthernetPHY(PADDING = true, MINIMUM_FRAME_LENGTH = 64))
    val ethernet_frame = Module(new EthernetFrame)
    val ip_frame = Module(new IPFrame)
    val arp_frame = Module(new ARPFrame)
    val ethernet_mux = Module(new EthernetFrameMux(N_INPUTS = 2))
    val udp_frame = Module(new UDPFrame)

    ethernet_phy.io.phy_clock := io.ethernet_clock
    ethernet_phy.io.phy_clock_90 := io.ethernet_clock_90
    ethernet_phy.io.phy_reset := io.ethernet_reset
    ethernet_phy.io.phy <> io.ethernet
    ethernet_phy.io.tx_ifg_delay := 12.U

    ethernet_frame.io.rx_input <> ethernet_phy.io.rx
    ethernet_frame.io.tx_output <> ethernet_phy.io.tx

    arp_frame.io.ip_info.local_mac := MAC.U(48.W)
    arp_frame.io.ip_info.local_ip := IP.U(32.W)
    arp_frame.io.ip_info.gateway_ip := GATEWAY.U(32.W)
    arp_frame.io.ip_info.subnet_mask := SUBNET.U(32.W)
    ethernet_mux.io.inputs(0) <> arp_frame.io.tx_ethernet_output
    ethernet_mux.io.input_headers(0) <> arp_frame.io.tx_ethernet_header
    ethernet_mux.io.inputs(1) <> ip_frame.io.tx_ethernet_output
    ethernet_mux.io.input_headers(1) <> ip_frame.io.tx_ethernet_header
    arp_frame.io.rx_ethernet_input.valid := ethernet_frame.io.rx_output.valid
    arp_frame.io.rx_ethernet_input.bits := ethernet_frame.io.rx_output.bits
    arp_frame.io.rx_ethernet_header.valid := ethernet_frame.io.rx_header.valid
    arp_frame.io.rx_ethernet_header.bits := ethernet_frame.io.rx_header.bits
    ip_frame.io.rx_ethernet_input.valid := ethernet_frame.io.rx_output.valid
    ip_frame.io.rx_ethernet_input.bits := ethernet_frame.io.rx_output.bits
    ip_frame.io.rx_ethernet_header.valid := ethernet_frame.io.rx_header.valid
    ip_frame.io.rx_ethernet_header.bits := ethernet_frame.io.rx_header.bits
    ethernet_frame.io.rx_output.ready := arp_frame.io.rx_ethernet_input.ready && ip_frame.io.rx_ethernet_input.ready
    ethernet_frame.io.rx_header.ready := arp_frame.io.rx_ethernet_header.ready && ip_frame.io.rx_ethernet_header.ready
    ethernet_frame.io.tx_input <> ethernet_mux.io.output
    ethernet_frame.io.tx_header <> ethernet_mux.io.output_header

    ip_frame.io.rx_ip_header <> udp_frame.io.rx_ip_header
    ip_frame.io.rx_ip_output <> udp_frame.io.rx_ip_input
    ip_frame.io.tx_ip_header <> udp_frame.io.tx_ip_header
    ip_frame.io.tx_ip_input <> udp_frame.io.tx_ip_output

    udp_frame.io.rx_udp_header <> io.rx_header
    udp_frame.io.rx_udp_output <> io.rx_output
    udp_frame.io.tx_udp_header <> io.tx_header
    udp_frame.io.tx_udp_input <> io.tx_input
}