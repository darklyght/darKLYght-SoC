module IDDRWrapper #(
    parameter DDR_CLK_EDGE = "SAME_EDGE_PIPELINED",
    parameter INIT_Q1 = 0,
    parameter INIT_Q2 = 0,
    parameter SRTYPE = "ASYNC",
    parameter WIDTH = 1
) (
    input C,
    input CE,
    input R,
    input S,
    input [WIDTH-1:0] D,
    output [WIDTH-1:0] Q1,
    output [WIDTH-1:0] Q2
);

    `ifndef SIMULATION
        genvar i;
        generate
            for (i = 0; i < WIDTH; i = i + 1) begin
                IDDR #(
                    .DDR_CLK_EDGE(DDR_CLK_EDGE),
                    .INIT_Q1(INIT_Q1),
                    .INIT_Q2(INIT_Q2),
                    .SRTYPE(SRTYPE)
                ) iddr (
                    .C(C),
                    .CE(CE),
                    .R(R),
                    .S(S),
                    .D(D[i]),
                    .Q1(Q1[i]),
                    .Q2(Q2[i])
                );
            end
        endgenerate
    `else
        genvar i;
        generate
            for (i = 0; i < WIDTH; i = i + 1) begin
                IDDRSIM #(
                    .DDR_CLK_EDGE(DDR_CLK_EDGE),
                    .INIT_Q1(INIT_Q1),
                    .INIT_Q2(INIT_Q2),
                    .SRTYPE(SRTYPE)
                ) iddr (
                    .C(C),
                    .CE(CE),
                    .R(R),
                    .S(S),
                    .D(D[i]),
                    .Q1(Q1[i]),
                    .Q2(Q2[i])
                );
            end
        endgenerate
    `endif

endmodule

module IDDRSIM #(
    parameter DDR_CLK_EDGE = "SAME_EDGE_PIPELINED",
    parameter INIT_Q1 = 0,
    parameter INIT_Q2 = 0,
    parameter SRTYPE = "ASYNC"
) (
    input C,
    input CE,
    input R,
    input S,
    input D,
    output Q1,
    output Q2
);

    reg q_reg_1 = INIT_Q1;
    reg q_reg_2 = INIT_Q2;

    if (DDR_CLK_EDGE == "OPPOSITE_EDGE") begin
        if (SRTYPE == "ASYNC") begin
            always @(posedge C or negedge C or posedge R or posedge S) begin
                if (R || S) begin
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        q_reg_1 <= D;
                    end else begin
                        q_reg_2 <= D;
                    end
                end
            end
        end else if (SRTYPE == "SYNC") begin
            always @(posedge C or negedge C) begin
                if (R || S) begin
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        q_reg_1 <= D;
                    end else begin
                        q_reg_2 <= D;
                    end
                end
            end
        end
    end else if (DDR_CLK_EDGE == "SAME_EDGE") begin
        reg d_reg_2 = INIT_Q2;
        if (SRTYPE == "ASYNC") begin
            always @(posedge C or negedge C or posedge R or posedge S) begin
                if (R || S) begin
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                    d_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        q_reg_1 <= D;
                        q_reg_2 <= d_reg_2;
                    end else begin
                        d_reg_2 <= D;
                    end
                end
            end
        end else if (SRTYPE == "SYNC") begin
            always @(posedge C or negedge C) begin
                if (R || S) begin
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                    d_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        q_reg_1 <= D;
                        q_reg_2 <= d_reg_2;
                    end else begin
                        d_reg_2 <= D;
                    end
                end
            end
        end
    end else if (DDR_CLK_EDGE == "SAME_EDGE_PIPELINED") begin
        reg d_reg_1 = INIT_Q1;
        reg d_reg_2 = INIT_Q2;
        if (SRTYPE == "ASYNC") begin
            always @(posedge C or negedge C or posedge R or posedge S) begin
                if (R || S) begin
                    d_reg_1 <= INIT_Q1;
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                    d_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        d_reg_1 <= D;
                        q_reg_1 <= d_reg_1;
                        q_reg_2 <= d_reg_2;
                    end else begin
                        d_reg_2 <= D;
                    end
                end
            end
        end else if (SRTYPE == "SYNC") begin
            always @(posedge C or negedge C) begin
                if (R || S) begin
                    d_reg_1 <= INIT_Q1;
                    q_reg_1 <= INIT_Q1;
                    q_reg_2 <= INIT_Q2;
                    d_reg_2 <= INIT_Q2;
                end else if (CE) begin
                    if (C) begin
                        d_reg_1 <= D;
                        q_reg_1 <= d_reg_1;
                        q_reg_2 <= d_reg_2;
                    end else begin
                        d_reg_2 <= D;
                    end
                end
            end
        end
    end

    assign Q1 = q_reg_1;
    assign Q2 = q_reg_2;

endmodule

module tb_IDDRWrapper();
    reg C;
    reg CE;
    reg R;
    reg S;
    reg [5:0] D;
    wire [5:0] Q1;
    wire [5:0] Q2;

    initial begin
        $dumpfile("iddr.vcd");
        $dumpvars();
        C = 1;
        CE = 1;
        R = 1;
        S = 0;
        D = 0;
        #23 R = 0;
        #29 R = 1;
    end

    always #5 C = ~C;

    always @(posedge C or negedge C) begin
        D = D + 1;
        if (D == 6'd63) $finish();
    end

    IDDRWrapper #(
        .DDR_CLK_EDGE("SAME_EDGE"),
        .INIT_Q1(0),
        .INIT_Q2(0),
        .SRTYPE("SYNC"),
        .WIDTH(6)
    ) dut (
        .C(C),
        .CE(CE),
        .R(R),
        .S(S),
        .D(D),
        .Q1(Q1),
        .Q2(Q2)
    );
endmodule
