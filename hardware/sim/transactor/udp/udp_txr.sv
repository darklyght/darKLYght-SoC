module udp_txr (
    input i_clock,
    input i_ethernet_clock,
    input i_ethernet_clock_90,
    input i_reset,
    input i_phy_rx_clock,
    input [3:0] i_phy_rx_data,
    input i_phy_rx_control,
    output o_phy_tx_clock,
    output [3:0] o_phy_tx_data,
    output o_phy_tx_control
);

    import "DPI-C" function
        chandle udp_create(input int port_number);
        
    import "DPI-C" function
        int udp_tx_valid(input chandle port);
        
    import "DPI-C" function
        byte udp_tx_data(input chandle port);
        
    import "DPI-C" function
        void udp_rx(input chandle port, byte data, int last);
    
    chandle port;

    initial begin
        port = udp_create(8000);
    end

    wire udp_i_rx_ready;
    wire udp_o_rx_valid;
    wire [7:0] udp_o_rx_data;
    wire udp_o_rx_last;
    wire udp_o_rx_user;
    wire udp_o_tx_ready;
    reg udp_i_tx_valid;
    reg [7:0] udp_i_tx_data;
    reg udp_i_tx_last;
    wire udp_i_tx_user;
    /* verilator lint_off PINMISSING */
    EthernetPHY ethernet (
        .clock(i_clock),
        .reset(i_reset),
        .io_phy_clock(i_ethernet_clock),
        .io_phy_clock_90(i_ethernet_clock_90),
        .io_phy_reset(i_reset),
        .io_phy_rx_clock(i_phy_rx_clock),
        .io_phy_rx_data(i_phy_rx_data),
        .io_phy_rx_control(i_phy_rx_control),
        .io_phy_tx_clock(o_phy_tx_clock),
        .io_phy_tx_data(o_phy_tx_data),
        .io_phy_tx_control(o_phy_tx_control),
        .io_rx_ready(udp_i_rx_ready),
        .io_rx_valid(udp_o_rx_valid),
        .io_rx_bits_tdata(udp_o_rx_data),
        .io_rx_bits_tlast(udp_o_rx_last),
        .io_rx_bits_tuser(udp_o_rx_user),
        .io_tx_ready(udp_o_tx_ready),
        .io_tx_valid(udp_i_tx_valid),
        .io_tx_bits_tdata(udp_i_tx_data),
        .io_tx_bits_tlast(udp_i_tx_last),
        .io_tx_bits_tuser(udp_i_tx_user),
        .io_tx_ifg_delay(8'd12)
    );
    /* verilator lint_on PINMISSING */
    assign udp_i_rx_ready = 1'b1;
    assign udp_i_tx_user = 1'b0;
    
    always @(posedge i_clock) begin
        if (i_reset) begin
            udp_i_tx_valid <= 1'b0;
            udp_i_tx_data <= 8'b0;
            udp_i_tx_last <= 1'b0;
        end else begin
            if (udp_o_tx_ready) udp_i_tx_valid <= udp_tx_valid(port) > 32'b0;
            if (udp_o_tx_ready) udp_i_tx_last <= udp_tx_valid(port) == 32'b1;
            if (udp_o_tx_ready) udp_i_tx_data <= udp_tx_data(port);
            if (udp_o_rx_valid) udp_rx(port, udp_o_rx_data, int'(udp_o_rx_last));
        end
    end

endmodule
