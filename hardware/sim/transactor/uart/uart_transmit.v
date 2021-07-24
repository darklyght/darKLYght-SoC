module uart_transmit #(
    parameter CLOCK_FREQUENCY,
    parameter BAUD_RATE
) (
    input i_clock,
    input i_reset,
    output o_tx,
    input [7:0] i_data,
    output o_data_ready,
    input i_data_valid
);

    localparam N_CLOCKS = CLOCK_FREQUENCY / BAUD_RATE;
    localparam N_WIDTH = $clog2(CLOCK_FREQUENCY / BAUD_RATE);

    reg [N_WIDTH-1:0] sample_counter;
    wire [N_WIDTH-1:0] sample_counter_next;
    reg [3:0] bit_counter;
    wire [3:0] bit_counter_next;
    reg [9:0] shift;
    wire [9:0] shift_next;
    
    /* verilator lint_off WIDTH */
    wire symbol = sample_counter == N_CLOCKS - 1;
    /* verilator lint_on WIDTH */
    wire started = bit_counter != 4'd0;
    wire start = ~started && i_data_valid;
    
    always @(posedge i_clock) begin
        if (i_reset) begin
            sample_counter <= N_WIDTH'(0);
            bit_counter <= 4'd0;
            shift <= 10'b1;
        end else begin
            sample_counter <= sample_counter_next;
            bit_counter <= bit_counter_next;
            shift <= shift_next;
        end
    end
    
    assign sample_counter_next = start || symbol ? N_WIDTH'(0) : sample_counter + N_WIDTH'(1);
    assign bit_counter_next = start ? 4'd10 : symbol && started ? bit_counter - 4'd1 : bit_counter;
    assign shift_next = start ? {1'b1, i_data, 1'b0} : symbol && started ? {1'b1, shift[9:1]} : shift;
    
    assign o_data_ready = ~started;
    assign o_tx = shift[0];

endmodule
