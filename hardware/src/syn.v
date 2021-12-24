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
    output o_ethernet_tx_control
);

    wire reset;

    assign reset = ~i_reset_n;
    assign o_ethernet_reset_n = i_reset_n;

    wire top_clock_int;
    wire top_clock_fbin;
    wire top_clock_fbout;
    wire top_clock_locked;
    wire top_clock;

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
        .CLKOUT0_DUTY_CYCLE(0.500)
    ) top_clock_mmcm (
        .CLKFBOUT(top_clock_fbout),
        .CLKFBOUTB(),
        .CLKOUT0(top_clock_int),
        .CLKOUT0B(),
        .CLKFBIN(top_clock_fbin),
        .CLKIN1(i_clock),
        .LOCKED(top_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG top_clock_fb_bufg (
        .I(top_clock_fbout),
        .O(top_clock_fbin)
    );

    BUFG top_clock_bufg (
        .I(top_clock_int),
        .O(top_clock)
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
        .io_ethernet_tx_control(o_ethernet_tx_control)
    );

endmodule