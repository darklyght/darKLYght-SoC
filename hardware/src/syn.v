module syn (
    input i_clock,
    input i_reset_n,
    input i_uart_rx,
    output o_uart_tx
);

    wire reset;

    assign reset = ~i_reset_n;

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
    wire uart_clock_fbin;
    wire uart_clock_fbout;
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
        .CLKFBOUT(uart_clock_fbout),
        .CLKFBOUTB(),
        .CLKOUT0(uart_clock_int),
        .CLKOUT0B(),
        .CLKFBIN(uart_clock_fbin),
        .CLKIN1(i_clock),
        .LOCKED(uart_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG uart_clock_fb_bufg (
        .I(uart_clock_fbout),
        .O(uart_clock_fbin)
    );

    BUFG uart_clock_bufg (
        .I(uart_clock_int),
        .O(uart_clock)
    );

    top top (
        .clock(top_clock),
        .reset(reset || ~top_clock_locked),
        .io_uart_clock(uart_clock),
        .io_uart_rx(i_uart_rx),
        .io_uart_tx(o_uart_tx)
    );

endmodule