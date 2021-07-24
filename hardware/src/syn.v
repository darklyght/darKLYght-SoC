module syn (
    input i_clock,
    input i_reset_n,
    input i_uart_rx,
    output o_uart_tx
);

    wire reset;

    assign reset = ~i_reset_n;

    wire top_clock;
    wire top_clock_fbin;
    wire top_clock_fbout;
    wire top_clock_locked;

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
        .CLKOUT0(top_clock),
        .CLKOUT0B(),
        .CLKFBIN(top_clock_fbin),
        .CLKIN1(i_clock),
        .LOCKED(top_clock_locked),
        .PWRDWN(1'b0),
        .RST(reset)
    );

    BUFG top_clock_bufg (
        .I(top_clock_fbout),
        .O(top_clock_fbin)
    );

    top top (
        .clock(top_clock),
        .reset(reset || ~top_clock_locked),
        .io_uart_rx(i_uart_rx),
        .io_uart_tx(o_uart_tx)
    );

endmodule