module tb (
    input i_clock,
    input i_uart_clock,
    input i_reset
);

    initial begin
        $dumpfile("tb.fst");
        $dumpvars();
    end
    
    wire uart_txr_i_uart_rx;
    wire uart_txr_o_uart_tx;
    
    uart_txr uart_txr (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .i_uart_clock(i_uart_clock),
        .i_uart_rx(uart_txr_i_uart_rx),
        .o_uart_tx(uart_txr_o_uart_tx)
    );

    wire io_uart_rx;
    wire io_uart_tx;
    
    top top (
        .clock(i_clock),
        .reset(i_reset),
        .io_uart_clock(i_uart_clock),
        .io_uart_rx(io_uart_rx),
        .io_uart_tx(io_uart_tx)
    );
    
    assign uart_txr_i_uart_rx = io_uart_tx;
    assign io_uart_rx = uart_txr_o_uart_tx;

endmodule
