module uart_txr #(
    parameter string NAME = "uart0"
) (
    input i_clock,
    input i_reset,
    input i_uart_rx,
    output o_uart_tx
);

    import "DPI-C" function
        chandle uart_create(input string name);
        
    import "DPI-C" function
        int uart_tx_valid(input chandle port);
        
    import "DPI-C" function
        byte uart_tx_data(input chandle port);
        
    import "DPI-C" function
        void uart_rx(input chandle port, byte data);
    
    chandle port;
    
    initial begin
        port = uart_create(NAME);
    end

    wire uart_i_rx;
    wire [7:0] uart_o_rx_data;
    wire uart_i_rx_data_ready;
    wire uart_o_rx_data_valid;
    wire uart_o_tx;
    reg [7:0] uart_i_tx_data;
    wire uart_o_tx_data_ready;
    reg uart_i_tx_data_valid;
    
    uart #(
        .CLOCK_FREQUENCY(100000000),
        .BAUD_RATE(115200)
    ) uart (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .i_rx(uart_i_rx),
        .o_rx_data(uart_o_rx_data),
        .i_rx_data_ready(uart_i_rx_data_ready),
        .o_rx_data_valid(uart_o_rx_data_valid),
        .o_tx(uart_o_tx),
        .i_tx_data(uart_i_tx_data),
        .o_tx_data_ready(uart_o_tx_data_ready),
        .i_tx_data_valid(uart_i_tx_data_valid)
    );
    
    assign uart_i_rx = i_uart_rx;
    assign o_uart_tx = uart_o_tx;
    assign uart_i_rx_data_ready = 1'b1;
    
    always @(posedge i_clock) begin
        if (i_reset) begin
            uart_i_tx_data_valid <= 1'b0;
            uart_i_tx_data <= 8'b0;
        end else begin
            if (uart_o_tx_data_ready) uart_i_tx_data_valid <= uart_tx_valid(port) == 32'b1;
            if (uart_o_tx_data_ready) uart_i_tx_data <= uart_tx_data(port);
            if (uart_o_rx_data_valid) uart_rx(port, uart_o_rx_data);
        end
    end

endmodule
