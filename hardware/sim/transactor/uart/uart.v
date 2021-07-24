module uart #(
    parameter CLOCK_FREQUENCY,
    parameter BAUD_RATE  
) (
    input i_clock,
    input i_reset,
    input i_rx,
    output [7:0] o_rx_data,
    input i_rx_data_ready,
    output o_rx_data_valid,
    output o_tx,
    input [7:0] i_tx_data,
    output o_tx_data_ready,
    input i_tx_data_valid
);

    uart_receive #(
        .CLOCK_FREQUENCY(CLOCK_FREQUENCY),
        .BAUD_RATE(BAUD_RATE)
    ) receive (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .i_rx(i_rx),
        .o_data(o_rx_data),
        .i_data_ready(i_rx_data_ready),
        .o_data_valid(o_rx_data_valid)
    );
    
    uart_transmit #(
        .CLOCK_FREQUENCY(CLOCK_FREQUENCY),
        .BAUD_RATE(BAUD_RATE)
    ) transmit (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .o_tx(o_tx),
        .i_data(i_tx_data),
        .o_data_ready(o_tx_data_ready),
        .i_data_valid(i_tx_data_valid)
    );
    
endmodule
