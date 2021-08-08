module ODDRWrapper #(
    parameter DDR_CLK_EDGE = "OPPOSITE_EDGE",
    parameter INIT = 0,
    parameter SRTYPE = "ASYNC",
    parameter WIDTH = 1
) (
    input C,
    input CE,
    input R,
    input S,
    input [WIDTH-1:0] D1,
    input [WIDTH-1:0] D2,
    output [WIDTH-1:0] Q
);

    `ifndef SIMULATION
        genvar i;
        generate
            for (i = 0; i < WIDTH; i = i + 1) begin
                ODDR #(
                    .DDR_CLK_EDGE(DDR_CLK_EDGE),
                    .INIT(INIT),
                    .SRTYPE(SRTYPE)
                ) oddr (
                    .C(C),
                    .CE(CE),
                    .R(R),
                    .S(S),
                    .D1(D1[i]),
                    .D2(D2[i]),
                    .Q(Q[i])
                );
            end
        endgenerate
    `else
        genvar i;
        generate
            for (i = 0; i < WIDTH; i = i + 1) begin
                ODDRSIM #(
                    .DDR_CLK_EDGE(DDR_CLK_EDGE),
                    .INIT(INIT),
                    .SRTYPE(SRTYPE)
                ) oddr (
                    .C(C),
                    .CE(CE),
                    .R(R),
                    .S(S),
                    .D1(D1[i]),
                    .D2(D2[i]),
                    .Q(Q[i])
                );
            end
        endgenerate
    `endif

endmodule

module ODDRSIM #(
    parameter DDR_CLK_EDGE = "OPPOSITE_EDGE",
    parameter INIT = 0,
    parameter SRTYPE = "ASYNC"
) (
    input C,
    input CE,
    input R,
    input S,
    input D1,
    input D2,
    output Q
);
    reg q_reg = INIT;
    /* verilator lint_off WIDTH */
    if (DDR_CLK_EDGE == "OPPOSITE_EDGE") begin
    /* verilator lint_on WIDTH */
        if (SRTYPE == "ASYNC") begin
            always @(posedge C or negedge C or posedge R or posedge S) begin
                if (R || S) begin
                    q_reg <= INIT;
                end else if (CE) begin
                    if (C) begin
                        q_reg <= D1;
                    end else begin
                        q_reg <= D2;
                    end
                    
                end
            end
        end else if (SRTYPE == "SYNC") begin
            always @(posedge C or negedge C) begin
                if (R || S) begin
                    q_reg <= INIT;
                end else if (CE) begin
                    if (C) begin
                        q_reg <= D1;
                    end else begin
                        q_reg <= D2;
                    end
                end
            end
        end
    end else if (DDR_CLK_EDGE == "SAME_EDGE") begin
        reg d_reg_2 = INIT;
        if (SRTYPE == "ASYNC") begin
            always @(posedge C or negedge C or posedge R or posedge S) begin
                if (R || S) begin
                    q_reg <= INIT;
                    d_reg_2 <= INIT;
                end else if (CE) begin
                    if (C) begin
                        q_reg <= D1;
                        d_reg_2 <= D2;
                    end else begin
                        q_reg <= d_reg_2;
                    end
                end
            end
        end else if (SRTYPE == "SYNC") begin
            always @(posedge C or negedge C) begin
                if (R || S) begin
                    q_reg <= INIT;
                    d_reg_2 <= INIT;
                end else if (CE) begin
                    if (C) begin
                        q_reg <= D1;
                        d_reg_2 <= D2;
                    end else begin
                        q_reg <= d_reg_2;
                    end
                end
            end
        end
    end

    assign Q = q_reg;

endmodule

module tb_ODDRWrapper();
    reg C;
    reg CE;
    reg R;
    reg S;
    reg [5:0] D1;
    reg [5:0] D2;
    wire [5:0] Q;

    initial begin
        $dumpfile("oddr.vcd");
        $dumpvars();
        C = 1;
        CE = 1;
        R = 1;
        S = 0;
        D1 = 0;
        D2 = 63;
        #23 R = 0;
        #29 R = 1;
    end

    always #5 C = ~C;

    always @(posedge C) begin
        D1 = D1 + 1;
        if (D1 == 6'd63) $finish();
    end

    always @(posedge C) begin
        D2 = D2 - 1;
    end

    ODDRWrapper #(
        .DDR_CLK_EDGE("SAME_EDGE"),
        .INIT(0),
        .SRTYPE("SYNC"),
        .WIDTH(6)
    ) dut (
        .C(C),
        .CE(CE),
        .R(R),
        .S(S),
        .D1(D1),
        .D2(D2),
        .Q(Q)
    );
endmodule
