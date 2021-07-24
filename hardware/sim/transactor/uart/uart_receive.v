module uart_receive #(
    parameter CLOCK_FREQUENCY,
    parameter BAUD_RATE
) (
    input i_clock,
    input i_reset,
    input i_rx,
    output [7:0] o_data,
    input i_data_ready,
    output o_data_valid
);

    localparam N_CLOCKS = CLOCK_FREQUENCY / BAUD_RATE;
    localparam N_SAMPLE = N_CLOCKS / 2;
    localparam N_WIDTH = $clog2(CLOCK_FREQUENCY / BAUD_RATE);

    reg [N_WIDTH-1:0] sample_counter;
    wire [N_WIDTH-1:0] sample_counter_next;
    reg [3:0] bit_counter;
    wire [3:0] bit_counter_next;
    reg [9:0] shift;
    wire [9:0] shift_next;
    reg valid;
    wire valid_next;
    
    /* verilator lint_off WIDTH */
    wire symbol = sample_counter == N_CLOCKS - 1;
    wire sample = sample_counter == N_SAMPLE;
    /* verilator lint_on WIDTH */
    wire started = bit_counter != 4'd0;
    wire start = i_rx == 1'b0 && ~started;
    
    always @(posedge i_clock) begin
        if (i_reset) begin
            sample_counter <= N_WIDTH'(0);
            bit_counter <= 4'd0;
            shift <= 10'b0;
            valid <= 1'b0;
        end else begin
            sample_counter <= sample_counter_next;
            bit_counter <= bit_counter_next;
            shift <= shift_next;
            valid <= valid_next;
        end
    end
    
    assign sample_counter_next = start || symbol ? N_WIDTH'(0) : sample_counter + N_WIDTH'(1);
    assign bit_counter_next = start ? 4'd10 : symbol && started ? bit_counter - 4'd1 : bit_counter;
    assign shift_next = sample && started ? {i_rx, shift[9:1]} : shift;
    assign valid_next = bit_counter == 4'd1 && symbol ? 1'b1 : i_data_ready ? 1'b0 : valid;
    
    assign o_data_valid = valid && ~started;
    assign o_data = shift[8:1];
    
endmodule
