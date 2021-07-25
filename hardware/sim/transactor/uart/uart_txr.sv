module uart_txr #(
    parameter string NAME = "uart0"
) (
    input i_clock,
    input i_uart_clock,
    input i_uart_reset,
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

    wire uart_i_uart_reset; 

    SyncDebouncer debounce (
        .clock(i_clock),
        .reset(1'b0),
        .io_input(i_reset),
        .io_output(uart_i_uart_reset)
    );

    wire uart_i_rx;
    wire [7:0] uart_o_rx_data;
    wire uart_i_rx_data_ready;
    wire uart_o_rx_data_valid;
    wire uart_o_tx;
    reg [7:0] uart_i_tx_data;
    wire uart_o_tx_data_ready;
    reg uart_i_tx_data_valid;
    
    UARTDriver uart (
        .clock(i_clock),
        .reset(i_reset),
        .io_uart_clock(i_uart_clock),
        .io_uart_reset(uart_i_uart_reset),
        .io_uart_rx_serial(uart_i_rx),
        .io_uart_rx_data_ready(uart_i_rx_data_ready),
        .io_uart_rx_data_valid(uart_o_rx_data_valid),
        .io_uart_rx_data_bits_tdata(uart_o_rx_data),
        .io_uart_tx_serial(uart_o_tx),
        .io_uart_tx_data_ready(uart_o_tx_data_ready),
        .io_uart_tx_data_valid(uart_i_tx_data_valid),
        .io_uart_tx_data_bits_tdata(uart_i_tx_data)
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
