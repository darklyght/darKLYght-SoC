/* verilator lint_off WIDTH */
/* verilator lint_off CASEINCOMPLETE */
module axi_ram #
(
    // Width of data bus in bits
    parameter DATA_WIDTH = 128,
    // Width of address bus in bits
    parameter ADDR_WIDTH = 29,
    // Width of wstrb (width of data bus in words)
    parameter STRB_WIDTH = (DATA_WIDTH/8),
    // Width of ID signal
    parameter ID_WIDTH = 8,
    // Extra pipeline register on output
    parameter PIPELINE_OUTPUT = 0
)
(
    input  wire                   clk,
    input  wire                   rst,

    input  wire [ID_WIDTH-1:0]    s_axi_awid,
    input  wire [ADDR_WIDTH-1:0]  s_axi_awaddr,
    input  wire [7:0]             s_axi_awlen,
    input  wire [2:0]             s_axi_awsize,
    input  wire [1:0]             s_axi_awburst,
    input  wire                   s_axi_awlock,
    input  wire [3:0]             s_axi_awcache,
    input  wire [2:0]             s_axi_awprot,
    input  wire                   s_axi_awvalid,
    output wire                   s_axi_awready,
    input  wire [DATA_WIDTH-1:0]  s_axi_wdata,
    input  wire [STRB_WIDTH-1:0]  s_axi_wstrb,
    input  wire                   s_axi_wlast,
    input  wire                   s_axi_wvalid,
    output wire                   s_axi_wready,
    output wire [ID_WIDTH-1:0]    s_axi_bid,
    output wire [1:0]             s_axi_bresp,
    output wire                   s_axi_bvalid,
    input  wire                   s_axi_bready,
    input  wire [ID_WIDTH-1:0]    s_axi_arid,
    input  wire [ADDR_WIDTH-1:0]  s_axi_araddr,
    input  wire [7:0]             s_axi_arlen,
    input  wire [2:0]             s_axi_arsize,
    input  wire [1:0]             s_axi_arburst,
    input  wire                   s_axi_arlock,
    input  wire [3:0]             s_axi_arcache,
    input  wire [2:0]             s_axi_arprot,
    input  wire                   s_axi_arvalid,
    output wire                   s_axi_arready,
    output wire [ID_WIDTH-1:0]    s_axi_rid,
    output wire [DATA_WIDTH-1:0]  s_axi_rdata,
    output wire [1:0]             s_axi_rresp,
    output wire                   s_axi_rlast,
    output wire                   s_axi_rvalid,
    input  wire                   s_axi_rready
);

parameter VALID_ADDR_WIDTH = ADDR_WIDTH - $clog2(STRB_WIDTH);
parameter WORD_WIDTH = STRB_WIDTH;
parameter WORD_SIZE = DATA_WIDTH/WORD_WIDTH;

// bus width assertions
initial begin
    if (WORD_SIZE * STRB_WIDTH != DATA_WIDTH) begin
        $error("Error: AXI data width not evenly divisble (instance %m)");
        $finish;
    end

    if (2**$clog2(WORD_WIDTH) != WORD_WIDTH) begin
        $error("Error: AXI word width must be even power of two (instance %m)");
        $finish;
    end
end

localparam [0:0]
    READ_STATE_IDLE = 1'd0,
    READ_STATE_BURST = 1'd1;

reg [0:0] read_state_reg = READ_STATE_IDLE, read_state_next;

localparam [1:0]
    WRITE_STATE_IDLE = 2'd0,
    WRITE_STATE_BURST = 2'd1,
    WRITE_STATE_RESP = 2'd2;

reg [1:0] write_state_reg = WRITE_STATE_IDLE, write_state_next;

reg mem_wr_en;
reg mem_rd_en;

reg [ID_WIDTH-1:0] read_id_reg = {ID_WIDTH{1'b0}}, read_id_next;
reg [ADDR_WIDTH-1:0] read_addr_reg = {ADDR_WIDTH{1'b0}}, read_addr_next;
reg [7:0] read_count_reg = 8'd0, read_count_next;
reg [2:0] read_size_reg = 3'd0, read_size_next;
reg [1:0] read_burst_reg = 2'd0, read_burst_next;
reg [ID_WIDTH-1:0] write_id_reg = {ID_WIDTH{1'b0}}, write_id_next;
reg [ADDR_WIDTH-1:0] write_addr_reg = {ADDR_WIDTH{1'b0}}, write_addr_next;
reg [7:0] write_count_reg = 8'd0, write_count_next;
reg [2:0] write_size_reg = 3'd0, write_size_next;
reg [1:0] write_burst_reg = 2'd0, write_burst_next;

reg s_axi_awready_reg = 1'b0, s_axi_awready_next;
reg s_axi_wready_reg = 1'b0, s_axi_wready_next;
reg [ID_WIDTH-1:0] s_axi_bid_reg = {ID_WIDTH{1'b0}}, s_axi_bid_next;
reg s_axi_bvalid_reg = 1'b0, s_axi_bvalid_next;
reg s_axi_arready_reg = 1'b0, s_axi_arready_next;
reg [ID_WIDTH-1:0] s_axi_rid_reg = {ID_WIDTH{1'b0}}, s_axi_rid_next;
reg [DATA_WIDTH-1:0] s_axi_rdata_reg = {DATA_WIDTH{1'b0}}, s_axi_rdata_next;
reg s_axi_rlast_reg = 1'b0, s_axi_rlast_next;
reg s_axi_rvalid_reg = 1'b0, s_axi_rvalid_next;
reg [ID_WIDTH-1:0] s_axi_rid_pipe_reg = {ID_WIDTH{1'b0}};
reg [DATA_WIDTH-1:0] s_axi_rdata_pipe_reg = {DATA_WIDTH{1'b0}};
reg s_axi_rlast_pipe_reg = 1'b0;
reg s_axi_rvalid_pipe_reg = 1'b0;

// (* RAM_STYLE="BLOCK" *)
reg [DATA_WIDTH-1:0] mem[(2**VALID_ADDR_WIDTH)-1:0];

wire [VALID_ADDR_WIDTH-1:0] s_axi_awaddr_valid = s_axi_awaddr >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] s_axi_araddr_valid = s_axi_araddr >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] read_addr_valid = read_addr_reg >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] write_addr_valid = write_addr_reg >> (ADDR_WIDTH - VALID_ADDR_WIDTH);

assign s_axi_awready = s_axi_awready_reg;
assign s_axi_wready = s_axi_wready_reg;
assign s_axi_bid = s_axi_bid_reg;
assign s_axi_bresp = 2'b00;
assign s_axi_bvalid = s_axi_bvalid_reg;
assign s_axi_arready = s_axi_arready_reg;
assign s_axi_rid = PIPELINE_OUTPUT ? s_axi_rid_pipe_reg : s_axi_rid_reg;
assign s_axi_rdata = PIPELINE_OUTPUT ? s_axi_rdata_pipe_reg : s_axi_rdata_reg;
assign s_axi_rresp = 2'b00;
assign s_axi_rlast = PIPELINE_OUTPUT ? s_axi_rlast_pipe_reg : s_axi_rlast_reg;
assign s_axi_rvalid = PIPELINE_OUTPUT ? s_axi_rvalid_pipe_reg : s_axi_rvalid_reg;

integer i, j;

// initial begin
//     // two nested loops for smaller number of iterations per loop
//     // workaround for synthesizer complaints about large loop counts
//     for (i = 0; i < 2**VALID_ADDR_WIDTH; i = i + 2**(VALID_ADDR_WIDTH/2)) begin
//         for (j = i; j < i + 2**(VALID_ADDR_WIDTH/2); j = j + 1) begin
//             mem[j] = 0;
//         end
//     end
// end

initial begin
    $readmemh("/home/darklyght/darKLYght-SoC/software/src/test/test.hex", mem);
end

always @* begin
    write_state_next = WRITE_STATE_IDLE;

    mem_wr_en = 1'b0;

    write_id_next = write_id_reg;
    write_addr_next = write_addr_reg;
    write_count_next = write_count_reg;
    write_size_next = write_size_reg;
    write_burst_next = write_burst_reg;

    s_axi_awready_next = 1'b0;
    s_axi_wready_next = 1'b0;
    s_axi_bid_next = s_axi_bid_reg;
    s_axi_bvalid_next = s_axi_bvalid_reg && !s_axi_bready;

    case (write_state_reg)
        WRITE_STATE_IDLE: begin
            s_axi_awready_next = 1'b1;

            if (s_axi_awready && s_axi_awvalid) begin
                write_id_next = s_axi_awid;
                write_addr_next = s_axi_awaddr;
                write_count_next = s_axi_awlen;
                write_size_next = s_axi_awsize < $clog2(STRB_WIDTH) ? s_axi_awsize : $clog2(STRB_WIDTH);
                write_burst_next = s_axi_awburst;

                s_axi_awready_next = 1'b0;
                s_axi_wready_next = 1'b1;
                write_state_next = WRITE_STATE_BURST;
            end else begin
                write_state_next = WRITE_STATE_IDLE;
            end
        end
        WRITE_STATE_BURST: begin
            s_axi_wready_next = 1'b1;

            if (s_axi_wready && s_axi_wvalid) begin
                mem_wr_en = 1'b1;
                if (write_burst_reg != 2'b00) begin
                    write_addr_next = write_addr_reg + (1 << write_size_reg);
                end
                write_count_next = write_count_reg - 1;
                if (write_count_reg > 0) begin
                    write_state_next = WRITE_STATE_BURST;
                end else begin
                    s_axi_wready_next = 1'b0;
                    if (s_axi_bready || !s_axi_bvalid) begin
                        s_axi_bid_next = write_id_reg;
                        s_axi_bvalid_next = 1'b1;
                        s_axi_awready_next = 1'b1;
                        write_state_next = WRITE_STATE_IDLE;
                    end else begin
                        write_state_next = WRITE_STATE_RESP;
                    end
                end
            end else begin
                write_state_next = WRITE_STATE_BURST;
            end
        end
        WRITE_STATE_RESP: begin
            if (s_axi_bready || !s_axi_bvalid) begin
                s_axi_bid_next = write_id_reg;
                s_axi_bvalid_next = 1'b1;
                s_axi_awready_next = 1'b1;
                write_state_next = WRITE_STATE_IDLE;
            end else begin
                write_state_next = WRITE_STATE_RESP;
            end
        end
    endcase
end

always @(posedge clk) begin
    write_state_reg <= write_state_next;

    write_id_reg <= write_id_next;
    write_addr_reg <= write_addr_next;
    write_count_reg <= write_count_next;
    write_size_reg <= write_size_next;
    write_burst_reg <= write_burst_next;

    s_axi_awready_reg <= s_axi_awready_next;
    s_axi_wready_reg <= s_axi_wready_next;
    s_axi_bid_reg <= s_axi_bid_next;
    s_axi_bvalid_reg <= s_axi_bvalid_next;

    for (i = 0; i < WORD_WIDTH; i = i + 1) begin
        if (mem_wr_en & s_axi_wstrb[i]) begin
            mem[write_addr_valid][WORD_SIZE*i +: WORD_SIZE] <= s_axi_wdata[WORD_SIZE*i +: WORD_SIZE];
        end
    end

    if (rst) begin
        write_state_reg <= WRITE_STATE_IDLE;

        s_axi_awready_reg <= 1'b0;
        s_axi_wready_reg <= 1'b0;
        s_axi_bvalid_reg <= 1'b0;
    end
end

always @* begin
    read_state_next = READ_STATE_IDLE;

    mem_rd_en = 1'b0;

    s_axi_rid_next = s_axi_rid_reg;
    s_axi_rlast_next = s_axi_rlast_reg;
    s_axi_rvalid_next = s_axi_rvalid_reg && !(s_axi_rready || (PIPELINE_OUTPUT && !s_axi_rvalid_pipe_reg));

    read_id_next = read_id_reg;
    read_addr_next = read_addr_reg;
    read_count_next = read_count_reg;
    read_size_next = read_size_reg;
    read_burst_next = read_burst_reg;

    s_axi_arready_next = 1'b0;

    case (read_state_reg)
        READ_STATE_IDLE: begin
            s_axi_arready_next = 1'b1;

            if (s_axi_arready && s_axi_arvalid) begin
                read_id_next = s_axi_arid;
                read_addr_next = s_axi_araddr;
                read_count_next = s_axi_arlen;
                read_size_next = s_axi_arsize < $clog2(STRB_WIDTH) ? s_axi_arsize : $clog2(STRB_WIDTH);
                read_burst_next = s_axi_arburst;

                s_axi_arready_next = 1'b0;
                read_state_next = READ_STATE_BURST;
            end else begin
                read_state_next = READ_STATE_IDLE;
            end
        end
        READ_STATE_BURST: begin
            if (s_axi_rready || (PIPELINE_OUTPUT && !s_axi_rvalid_pipe_reg) || !s_axi_rvalid_reg) begin
                mem_rd_en = 1'b1;
                s_axi_rvalid_next = 1'b1;
                s_axi_rid_next = read_id_reg;
                s_axi_rlast_next = read_count_reg == 0;
                if (read_burst_reg != 2'b00) begin
                    read_addr_next = read_addr_reg + (1 << read_size_reg);
                end
                read_count_next = read_count_reg - 1;
                if (read_count_reg > 0) begin
                    read_state_next = READ_STATE_BURST;
                end else begin
                    s_axi_arready_next = 1'b1;
                    read_state_next = READ_STATE_IDLE;
                end
            end else begin
                read_state_next = READ_STATE_BURST;
            end
        end
    endcase
end

always @(posedge clk) begin
    read_state_reg <= read_state_next;

    read_id_reg <= read_id_next;
    read_addr_reg <= read_addr_next;
    read_count_reg <= read_count_next;
    read_size_reg <= read_size_next;
    read_burst_reg <= read_burst_next;

    s_axi_arready_reg <= s_axi_arready_next;
    s_axi_rid_reg <= s_axi_rid_next;
    s_axi_rlast_reg <= s_axi_rlast_next;
    s_axi_rvalid_reg <= s_axi_rvalid_next;

    if (mem_rd_en) begin
        s_axi_rdata_reg <= mem[read_addr_valid];
    end

    if (!s_axi_rvalid_pipe_reg || s_axi_rready) begin
        s_axi_rid_pipe_reg <= s_axi_rid_reg;
        s_axi_rdata_pipe_reg <= s_axi_rdata_reg;
        s_axi_rlast_pipe_reg <= s_axi_rlast_reg;
        s_axi_rvalid_pipe_reg <= s_axi_rvalid_reg;
    end

    if (rst) begin
        read_state_reg <= READ_STATE_IDLE;

        s_axi_arready_reg <= 1'b0;
        s_axi_rvalid_reg <= 1'b0;
        s_axi_rvalid_pipe_reg <= 1'b0;
    end
end

endmodule


/* verilator lint_on CASEINCOMPLETE */

module tb (
    input i_clock,
    input i_uart_clock,
    input i_ethernet_clock,
    input i_ethernet_clock_90,
    input i_hdmi_pixel_clock,
    input i_hdmi_audio_clock,
    input i_cpu_clock,
    input i_reset
);
    
    wire uart_txr_i_uart_rx;
    wire uart_txr_o_uart_tx;
    
    uart_txr uart_txr (
        .i_clock(i_clock),
        .i_reset(i_reset),
        .i_uart_clock(i_uart_clock),
        .i_uart_rx(uart_txr_i_uart_rx),
        .o_uart_tx(uart_txr_o_uart_tx)
    );

    wire udp_txr_i_rx_clock;
    wire [3:0] udp_txr_i_rx_data;
    wire udp_txr_i_rx_control;
    wire udp_txr_o_tx_clock;
    wire [3:0] udp_txr_o_tx_data;
    wire udp_txr_o_tx_control;

    udp_txr udp_txr (
        .i_clock(i_clock),
        .i_ethernet_clock(i_ethernet_clock),
        .i_ethernet_clock_90(i_ethernet_clock_90),
        .i_reset(i_reset),
        .i_phy_rx_clock(udp_txr_i_rx_clock),
        .i_phy_rx_data(udp_txr_i_rx_data),
        .i_phy_rx_control(udp_txr_i_rx_control),
        .o_phy_tx_clock(udp_txr_o_tx_clock),
        .o_phy_tx_data(udp_txr_o_tx_data),
        .o_phy_tx_control(udp_txr_o_tx_control)
    );

    wire [7:0] dram_axi_awid;
    wire [28:0] dram_axi_awaddr;
    wire [7:0] dram_axi_awlen;
    wire [2:0] dram_axi_awsize;
    wire [1:0] dram_axi_awburst;
    wire dram_axi_awlock;
    wire [3:0] dram_axi_awcache;
    wire [2:0] dram_axi_awprot;
    wire dram_axi_awvalid;
    wire dram_axi_awready;
    wire [127:0] dram_axi_wdata;
    wire [15:0] dram_axi_wstrb;
    wire dram_axi_wlast;
    wire dram_axi_wvalid;
    wire dram_axi_wready;
    wire [7:0] dram_axi_bid;
    wire [1:0] dram_axi_bresp;
    wire dram_axi_bvalid;
    wire dram_axi_bready;
    wire [7:0] dram_axi_arid;
    wire [28:0] dram_axi_araddr;
    wire [7:0] dram_axi_arlen;
    wire [2:0] dram_axi_arsize;
    wire [1:0] dram_axi_arburst;
    wire dram_axi_arlock;
    wire [3:0] dram_axi_arcache;
    wire [2:0] dram_axi_arprot;
    wire dram_axi_arvalid;
    wire dram_axi_arready;
    wire [7:0] dram_axi_rid;
    wire [127:0] dram_axi_rdata;
    wire [1:0] dram_axi_rresp;
    wire dram_axi_rlast;
    wire dram_axi_rvalid;
    wire dram_axi_rready;

    axi_ram dram (
        .clk(i_clock),
        .rst(i_reset),
        .s_axi_awid(dram_axi_awid),
        .s_axi_awaddr(dram_axi_awaddr),
        .s_axi_awlen(dram_axi_awlen),
        .s_axi_awsize(dram_axi_awsize),
        .s_axi_awburst(dram_axi_awburst),
        .s_axi_awlock(dram_axi_awlock),
        .s_axi_awcache(dram_axi_awcache),
        .s_axi_awprot(dram_axi_awprot),
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
        .s_axi_araddr(dram_axi_araddr),
        .s_axi_arlen(dram_axi_arlen),
        .s_axi_arsize(dram_axi_arsize),
        .s_axi_arburst(dram_axi_arburst),
        .s_axi_arlock(dram_axi_arlock),
        .s_axi_arcache(dram_axi_arcache),
        .s_axi_arprot(dram_axi_arprot),
        .s_axi_arvalid(dram_axi_arvalid),
        .s_axi_arready(dram_axi_arready),
        .s_axi_rid(dram_axi_rid),
        .s_axi_rdata(dram_axi_rdata),
        .s_axi_rresp(dram_axi_rresp),
        .s_axi_rlast(dram_axi_rlast),
        .s_axi_rvalid(dram_axi_rvalid),
        .s_axi_rready(dram_axi_rready)
    );

    top top (
        .clock(i_clock),
        .reset(i_reset),
        .io_uart_clock(i_uart_clock),
        .io_uart_rx(uart_txr_o_uart_tx),
        .io_uart_tx(uart_txr_i_uart_rx),
        .io_ethernet_clock(i_ethernet_clock),
        .io_ethernet_clock_90(i_ethernet_clock_90),
        .io_ethernet_rx_clock(udp_txr_o_tx_clock),
        .io_ethernet_rx_data(udp_txr_o_tx_data),
        .io_ethernet_rx_control(udp_txr_o_tx_control),
        .io_ethernet_tx_clock(udp_txr_i_rx_clock),
        .io_ethernet_tx_data(udp_txr_i_rx_data),
        .io_ethernet_tx_control(udp_txr_i_rx_control),
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
        .io_dram_aw_bits_qos(),
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
        .io_dram_ar_bits_qos(),
        .io_dram_r_ready(dram_axi_rready),
        .io_dram_r_valid(dram_axi_rvalid),
        .io_dram_r_bits_id(dram_axi_rid),
        .io_dram_r_bits_data(dram_axi_rdata),
        .io_dram_r_bits_resp(dram_axi_rresp),
        .io_dram_r_bits_last(dram_axi_rlast),
        .io_hdmi_pixel_clock(i_hdmi_pixel_clock),
        .io_hdmi_audio_clock(i_hdmi_audio_clock),
        .io_cpu_clock(i_cpu_clock)
    );

endmodule
/* verilator lint_on WIDTH */