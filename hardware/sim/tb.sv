module tb (
    input i_clock,
    input i_uart_clock,
    input i_ethernet_clock,
    input i_ethernet_clock_90,
    input i_reset
);
    
    wire uart_txr_i_uart_rx;
    wire uart_txr_o_uart_tx;
    
    uart_txr uart_txr (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .i_uart_clock(i_uart_clock),
        .i_uart_rx(uart_txr_i_uart_rx),
        .o_uart_tx(uart_txr_o_uart_tx)
    );

    wire udp_txr_i_rx_clock;
    wire [3:0] udp_txr_i_rx_data;
    wire udp_txr_i_rx_control;
    wire udp_txr_o_tx_clock;
    wire [3:0] udp_txr_o_tx_data;
    wire udp_txr_o_tx_control;

    udp_txr udp_txr (
        .i_clock(i_clock),
        .i_ethernet_clock(i_ethernet_clock),
        .i_ethernet_clock_90(i_ethernet_clock_90),
        .i_reset(i_reset),
        .i_phy_rx_clock(udp_txr_i_rx_clock),
        .i_phy_rx_data(udp_txr_i_rx_data),
        .i_phy_rx_control(udp_txr_i_rx_control),
        .o_phy_tx_clock(udp_txr_o_tx_clock),
        .o_phy_tx_data(udp_txr_o_tx_data),
        .o_phy_tx_control(udp_txr_o_tx_control)
    );

    top top (
        .clock(i_clock),
        .reset(i_reset),
        .io_uart_clock(i_uart_clock),
        .io_uart_rx(uart_txr_o_uart_tx),
        .io_uart_tx(uart_txr_i_uart_rx),
        .io_ethernet_clock(i_ethernet_clock),
        .io_ethernet_clock_90(i_ethernet_clock_90),
        .io_ethernet_rx_clock(udp_txr_o_tx_clock),
        .io_ethernet_rx_data(udp_txr_o_tx_data),
        .io_ethernet_rx_control(udp_txr_o_tx_control),
        .io_ethernet_tx_clock(udp_txr_i_rx_clock),
        .io_ethernet_tx_data(udp_txr_i_rx_data),
        .io_ethernet_tx_control(udp_txr_i_rx_control)
    );

endmodule
