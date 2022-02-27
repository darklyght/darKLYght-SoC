module syn (
    input i_clock,
    input i_reset_n,
    input i_uart_rx,
    output o_uart_tx,
    output o_ethernet_reset_n,
    input i_ethernet_rx_clock,
    input [3:0] i_ethernet_rx_data,
    input i_ethernet_rx_control,
    output o_ethernet_tx_clock,
    output [3:0] o_ethernet_tx_data,
    output o_ethernet_tx_control,
    output [14:0] o_ddr3_addr,
    output [2:0] o_ddr3_ba,
    output o_ddr3_cas_n,
    output o_ddr3_ck_n,
    output o_ddr3_ck_p,
    output o_ddr3_cke,
    output o_ddr3_ras_n,
    output o_ddr3_reset_n,
    output o_ddr3_we_n,
    inout [15:0] io_ddr3_dq,
    inout [1:0] io_ddr3_dqs_n,
    inout [1:0] io_ddr3_dqs_p,
    output [1:0] o_ddr3_dm,
    output o_ddr3_odt,
    output [7:0] o_led,
    input [7:0] i_switch
);

    wire reset;

    assign reset = ~i_reset_n;
    assign o_ethernet_reset_n = i_reset_n;

    wire top_clock_int;
    wire top_clock_fb;
    wire top_clock_locked;
    wire top_clock;
    wire dram_clock_int;
    wire dram_clock;

    MMCME2_BASE #(
        .BANDWIDTH("OPTIMIZED"),
        .CLKOUT4_CASCADE("FALSE"),
        .STARTUP_WAIT("FALSE"),
        .CLKIN1_PERIOD(10.000),
        .DIVCLK_DIVIDE(1),
        .CLKFBOUT_MULT_F(10.000),
        .CLKFBOUT_PHASE(0.000),
        .CLKOUT0_DIVIDE_F(10.000),
        .CLKOUT0_PHASE(0.000),
        .CLKOUT0_DUTY_CYCLE(0.500),
        .CLKOUT1_DIVIDE(5),
        .CLKOUT1_PHASE(0.000),
        .CLKOUT1_DUTY_CYCLE(0.500)
    ) top_clock_mmcm (
        .CLKFBOUT(top_clock_fb),
        .CLKFBOUTB(),
        .CLKOUT0(top_clock_int),
        .CLKOUT0B(),
        .CLKOUT1(dram_clock_int),
        .CLKOUT1B(),
        .CLKFBIN(top_clock_fb),
        .CLKIN1(i_clock),
        .LOCKED(top_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG top_clock_bufg (
        .I(top_clock_int),
        .O(top_clock)
    );

    BUFG dram_clock_bufg (
        .I(dram_clock_int),
        .O(dram_clock)
    );

    wire uart_clock_int;
    wire uart_clock_fb;
    wire uart_clock_locked;
    wire uart_clock;

    MMCME2_BASE #(
        .BANDWIDTH("OPTIMIZED"),
        .CLKOUT4_CASCADE("FALSE"),
        .STARTUP_WAIT("FALSE"),
        .CLKIN1_PERIOD(10.000),
        .DIVCLK_DIVIDE(9),
        .CLKFBOUT_MULT_F(62.375),
        .CLKFBOUT_PHASE(0.000),
        .CLKOUT0_DIVIDE_F(5.875),
        .CLKOUT0_PHASE(0.000),
        .CLKOUT0_DUTY_CYCLE(0.500)
    ) uart_clock_mmcm (
        .CLKFBOUT(uart_clock_fb),
        .CLKFBOUTB(),
        .CLKOUT0(uart_clock_int),
        .CLKOUT0B(),
        .CLKFBIN(uart_clock_fb),
        .CLKIN1(i_clock),
        .LOCKED(uart_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG uart_clock_bufg (
        .I(uart_clock_int),
        .O(uart_clock)
    );

    wire ethernet_clock_int;
    wire ethernet_clock90_int;
    wire ethernet_200clock_int;
    wire ethernet_clock_fb;
    wire ethernet_clock_locked;
    wire ethernet_clock;
    wire ethernet_clock90;
    wire ethernet_200clock;
    wire ethernet_probe_int;
    wire ethernet_probe;

    MMCME2_BASE #(
        .BANDWIDTH("OPTIMIZED"),
        .CLKOUT4_CASCADE("FALSE"),
        .STARTUP_WAIT("FALSE"),
        .CLKIN1_PERIOD(10.000),
        .DIVCLK_DIVIDE(1),
        .CLKFBOUT_MULT_F(10.000),
        .CLKFBOUT_PHASE(0.000),
        .CLKOUT0_DIVIDE_F(8.000),
        .CLKOUT0_PHASE(0.000),
        .CLKOUT0_DUTY_CYCLE(0.500),
        .CLKOUT1_DIVIDE(8),
        .CLKOUT1_PHASE(90.000),
        .CLKOUT1_DUTY_CYCLE(0.500),
        .CLKOUT2_DIVIDE(5),
        .CLKOUT2_PHASE(0.000),
        .CLKOUT2_DUTY_CYCLE(0.500),
        .CLKOUT3_DIVIDE(4),
        .CLKOUT3_PHASE(0.000),
        .CLKOUT3_DUTY_CYCLE(0.500),
        .REF_JITTER1(0.010)
    ) ethernet_clock_mmcm (
        .CLKFBOUT(ethernet_clock_fb),
        .CLKFBOUTB(),
        .CLKOUT0(ethernet_clock_int),
        .CLKOUT0B(),
        .CLKOUT1(ethernet_clock90_int),
        .CLKOUT1B(),
        .CLKOUT2(ethernet_200clock_int),
        .CLKOUT2B(),
        .CLKOUT3(ethernet_probe_int),
        .CLKOUT3B(),
        .CLKFBIN(ethernet_clock_fb),
        .CLKIN1(i_clock),
        .LOCKED(ethernet_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG ethernet_clock_bufg (
        .I(ethernet_clock_int),
        .O(ethernet_clock)
    );

    BUFG ethernet_clock90_bufg (
        .I(ethernet_clock90_int),
        .O(ethernet_clock90)
    );

    BUFG ethernet_200clock_bufg (
        .I(ethernet_200clock_int),
        .O(ethernet_200clock)
    );

    BUFG ethernet_probe_bufg (
        .I(ethernet_probe_int),
        .O(ethernet_probe)
    );

    wire [3:0] ethernet_rx_data_delay;
    wire ethernet_rx_control_delay;

    IDELAYCTRL idelayctrl (
        .REFCLK(ethernet_200clock),
        .RST(reset)
    );

    IDELAYE2 #(
        .IDELAY_TYPE("FIXED")
    ) ethernet_rx_data_0 (
        .IDATAIN(i_ethernet_rx_data[0]),
        .DATAOUT(ethernet_rx_data_delay[0]),
        .DATAIN(1'b0),
        .C(1'b0),
        .CE(1'b0),
        .INC(1'b0),
        .CINVCTRL(1'b0),
        .CNTVALUEIN(5'd0),
        .CNTVALUEOUT(),
        .LD(1'b0),
        .LDPIPEEN(1'b0),
        .REGRST(1'b0)
    );

    IDELAYE2 #(
        .IDELAY_TYPE("FIXED")
    ) ethernet_rx_data_1 (
        .IDATAIN(i_ethernet_rx_data[1]),
        .DATAOUT(ethernet_rx_data_delay[1]),
        .DATAIN(1'b0),
        .C(1'b0),
        .CE(1'b0),
        .INC(1'b0),
        .CINVCTRL(1'b0),
        .CNTVALUEIN(5'd0),
        .CNTVALUEOUT(),
        .LD(1'b0),
        .LDPIPEEN(1'b0),
        .REGRST(1'b0)
    );

    IDELAYE2 #(
        .IDELAY_TYPE("FIXED")
    ) ethernet_rx_data_2 (
        .IDATAIN(i_ethernet_rx_data[2]),
        .DATAOUT(ethernet_rx_data_delay[2]),
        .DATAIN(1'b0),
        .C(1'b0),
        .CE(1'b0),
        .INC(1'b0),
        .CINVCTRL(1'b0),
        .CNTVALUEIN(5'd0),
        .CNTVALUEOUT(),
        .LD(1'b0),
        .LDPIPEEN(1'b0),
        .REGRST(1'b0)
    );

    IDELAYE2 #(
        .IDELAY_TYPE("FIXED")
    ) ethernet_rx_data_3 (
        .IDATAIN(i_ethernet_rx_data[3]),
        .DATAOUT(ethernet_rx_data_delay[3]),
        .DATAIN(1'b0),
        .C(1'b0),
        .CE(1'b0),
        .INC(1'b0),
        .CINVCTRL(1'b0),
        .CNTVALUEIN(5'd0),
        .CNTVALUEOUT(),
        .LD(1'b0),
        .LDPIPEEN(1'b0),
        .REGRST(1'b0)
    );

    IDELAYE2 #(
        .IDELAY_TYPE("FIXED")
    ) ethernet_rx_control (
        .IDATAIN(i_ethernet_rx_control),
        .DATAOUT(ethernet_rx_control_delay),
        .DATAIN(1'b0),
        .C(1'b0),
        .CE(1'b0),
        .INC(1'b0),
        .CINVCTRL(1'b0),
        .CNTVALUEIN(5'd0),
        .CNTVALUEOUT(),
        .LD(1'b0),
        .LDPIPEEN(1'b0),
        .REGRST(1'b0)
    );

    wire [7:0] dram_axi_awid;
    wire [31:0] dram_axi_awaddr;
    wire [7:0] dram_axi_awlen;
    wire [2:0] dram_axi_awsize;
    wire [1:0] dram_axi_awburst;
    wire dram_axi_awlock;
    wire [3:0] dram_axi_awcache;
    wire [2:0] dram_axi_awprot;
    wire [3:0] dram_axi_awqos;
    wire dram_axi_awvalid;
    wire dram_axi_awready;
    wire [31:0] dram_axi_wdata;
    wire [3:0] dram_axi_wstrb;
    wire dram_axi_wlast;
    wire dram_axi_wvalid;
    wire dram_axi_wready;
    wire [7:0] dram_axi_bid;
    wire [1:0] dram_axi_bresp;
    wire dram_axi_bvalid;
    wire dram_axi_bready;
    wire [7:0] dram_axi_arid;
    wire [31:0] dram_axi_araddr;
    wire [7:0] dram_axi_arlen;
    wire [2:0] dram_axi_arsize;
    wire [1:0] dram_axi_arburst;
    wire dram_axi_arlock;
    wire [3:0] dram_axi_arcache;
    wire [2:0] dram_axi_arprot;
    wire [3:0] dram_axi_arqos;
    wire dram_axi_arvalid;
    wire dram_axi_arready;
    wire [7:0] dram_axi_rid;
    wire [31:0] dram_axi_rdata;
    wire [1:0] dram_axi_rresp;
    wire dram_axi_rlast;
    wire dram_axi_rvalid;
    wire dram_axi_rready;

    MemoryController u_MemoryController (
        .ddr3_addr(o_ddr3_addr),
        .ddr3_ba(o_ddr3_ba),
        .ddr3_cas_n(o_ddr3_cas_n),
        .ddr3_ck_n(o_ddr3_ck_n),
        .ddr3_ck_p(o_ddr3_ck_p),
        .ddr3_cke(o_ddr3_cke),
        .ddr3_ras_n(o_ddr3_ras_n),
        .ddr3_reset_n(o_ddr3_reset_n),
        .ddr3_we_n(o_ddr3_we_n),
        .ddr3_dq(io_ddr3_dq),
        .ddr3_dqs_n(io_ddr3_dqs_n),
        .ddr3_dqs_p(io_ddr3_dqs_p),
        .init_calib_complete(o_init_calib_complete),
        .ddr3_dm(o_ddr3_dm),
        .ddr3_odt(o_ddr3_odt),
        .ui_clk(),
        .ui_clk_sync_rst(),
        .mmcm_locked(),
        .aresetn(~reset && top_clock_locked),
        .app_sr_req(1'b0),
        .app_ref_req(1'b0),
        .app_zq_req(1'b0),
        .app_sr_active(),
        .app_ref_ack(),
        .app_zq_ack(),
        .s_axi_awid(dram_axi_awid),
        .s_axi_awaddr(dram_axi_awaddr[28:0]),
        .s_axi_awlen(dram_axi_awlen),
        .s_axi_awsize(dram_axi_awsize),
        .s_axi_awburst(dram_axi_awburst),
        .s_axi_awlock(dram_axi_awlock),
        .s_axi_awcache(dram_axi_awcache),
        .s_axi_awprot(dram_axi_awprot),
        .s_axi_awqos(dram_axi_awqos),
        .s_axi_awvalid(dram_axi_awvalid),
        .s_axi_awready(dram_axi_awready),
        .s_axi_wdata(dram_axi_wdata),
        .s_axi_wstrb(dram_axi_wstrb),
        .s_axi_wlast(dram_axi_wlast),
        .s_axi_wvalid(dram_axi_wvalid),
        .s_axi_wready(dram_axi_wready),
        .s_axi_bid(dram_axi_bid),
        .s_axi_bresp(dram_axi_bresp),
        .s_axi_bvalid(dram_axi_bvalid),
        .s_axi_bready(dram_axi_bready),
        .s_axi_arid(dram_axi_arid),
        .s_axi_araddr(dram_axi_araddr[28:0]),
        .s_axi_arlen(dram_axi_arlen),
        .s_axi_arsize(dram_axi_arsize),
        .s_axi_arburst(dram_axi_arburst),
        .s_axi_arlock(dram_axi_arlock),
        .s_axi_arcache(dram_axi_arcache),
        .s_axi_arprot(dram_axi_arprot),
        .s_axi_arqos(dram_axi_arqos),
        .s_axi_arvalid(dram_axi_arvalid),
        .s_axi_arready(dram_axi_arready),
        .s_axi_rid(dram_axi_rid), 
        .s_axi_rdata(dram_axi_rdata),
        .s_axi_rresp(dram_axi_rresp),
        .s_axi_rlast(dram_axi_rlast),
        .s_axi_rvalid(dram_axi_rvalid),
        .s_axi_rready(dram_axi_rready),
        .sys_clk_i(top_clock),
        .clk_ref_i(dram_clock),
        .sys_rst(reset || ~top_clock_locked)
    );

    top top (
        .clock(top_clock),
        .reset(reset || ~top_clock_locked || ~uart_clock_locked || ~ethernet_clock_locked),
        .io_uart_clock(uart_clock),
        .io_uart_rx(i_uart_rx),
        .io_uart_tx(o_uart_tx),
        .io_ethernet_clock(ethernet_clock),
        .io_ethernet_clock_90(ethernet_clock90),
        .io_ethernet_rx_clock(i_ethernet_rx_clock),
        .io_ethernet_rx_data(ethernet_rx_data_delay),
        .io_ethernet_rx_control(ethernet_rx_control_delay),
        .io_ethernet_tx_clock(o_ethernet_tx_clock),
        .io_ethernet_tx_data(o_ethernet_tx_data),
        .io_ethernet_tx_control(o_ethernet_tx_control),
        .io_dram_aw_ready(dram_axi_awready),
        .io_dram_aw_valid(dram_axi_awvalid),
        .io_dram_aw_bits_id(dram_axi_awid),
        .io_dram_aw_bits_addr(dram_axi_awaddr),
        .io_dram_aw_bits_len(dram_axi_awlen),
        .io_dram_aw_bits_size(dram_axi_awsize),
        .io_dram_aw_bits_burst(dram_axi_awburst),
        .io_dram_aw_bits_lock(dram_axi_awlock),
        .io_dram_aw_bits_cache(dram_axi_awcache),
        .io_dram_aw_bits_prot(dram_axi_awprot),
        .io_dram_aw_bits_qos(dram_axi_awqos),
        .io_dram_w_ready(dram_axi_wready),
        .io_dram_w_valid(dram_axi_wvalid),
        .io_dram_w_bits_data(dram_axi_wdata),
        .io_dram_w_bits_strb(dram_axi_wstrb),
        .io_dram_w_bits_last(dram_axi_wlast),
        .io_dram_b_ready(dram_axi_bready),
        .io_dram_b_valid(dram_axi_bvalid),
        .io_dram_b_bits_id(dram_axi_bid),
        .io_dram_b_bits_resp(dram_axi_bresp),
        .io_dram_ar_ready(dram_axi_arready),
        .io_dram_ar_valid(dram_axi_arvalid),
        .io_dram_ar_bits_id(dram_axi_arid),
        .io_dram_ar_bits_addr(dram_axi_araddr),
        .io_dram_ar_bits_len(dram_axi_arlen),
        .io_dram_ar_bits_size(dram_axi_arsize),
        .io_dram_ar_bits_burst(dram_axi_arburst),
        .io_dram_ar_bits_lock(dram_axi_arlock),
        .io_dram_ar_bits_cache(dram_axi_arcache),
        .io_dram_ar_bits_prot(dram_axi_arprot),
        .io_dram_ar_bits_qos(dram_axi_arqos),
        .io_dram_r_ready(dram_axi_rready),
        .io_dram_r_valid(dram_axi_rvalid),
        .io_dram_r_bits_id(dram_axi_rid[0]),
        .io_dram_r_bits_data(dram_axi_rdata),
        .io_dram_r_bits_resp(dram_axi_rresp),
        .io_dram_r_bits_last(dram_axi_rlast),
        .io_led(o_led),
        .io_switch(i_switch)
    );

endmodule