module BUFRWrapper #(
    parameter BUFR_DIVIDE = "BYPASS"
) (
    input I,
    output O,
    input CE,
    input CLR
);

    `ifndef SIMULATION
        BUFR #(
            .BUFR_DIVIDE(BUFR_DIVIDE)
        ) bufr (
            .I(I),
            .O(O),
            .CE(CE),
            .CLR(CLR)
        );
    `else
        BUFRSIM #(
            .BUFR_DIVIDE(BUFR_DIVIDE)
        ) bufr (
            .I(I),
            .O(O),
            .CE(CE),
            .CLR(CLR)
        );
    `endif

endmodule

module BUFRSIM #(
    parameter BUFR_DIVIDE = "BYPASS"
) (
    input I,
    output O,
    input CE,
    input CLR
);

    if (BUFR_DIVIDE == "BYPASS") begin
        assign O = I;
    end else begin
        localparam LIMIT = BUFR_DIVIDE == 1 ? 1 : 2 * ((BUFR_DIVIDE + 1) / 2);
        reg [3:0] count = 2 * BUFR_DIVIDE - 1;
        always @(posedge I or negedge I or posedge CLR) begin
            if (CLR) begin
                count <= 0;
            end else begin
                if (count == 2 * BUFR_DIVIDE - 1) begin
                    count <= 0;
                end else begin
                    count <= count + 1;
                end
            end
        end
        assign O = CE ? (count >= LIMIT) : 1'b0;
    end

endmodule

module tb_BUFRWrapper();
    reg I;
    wire O;
    reg CE;
    reg CLR;

    initial begin
        $dumpfile("bufr.vcd");
        $dumpvars();
        I = 0;
        CE = 0;
        CLR = 0;

        #33 CE = 1;
        #28 CE = 0;
        #12 CLR = 1;
        #14 CLR = 0;

        #500 $finish();
    end

    always #5 I = ~I;

    BUFRWrapper #(
        .BUFR_DIVIDE(8)
    ) dut (
        .I(I),
        .O(O),
        .CE(CE),
        .CLR(CLR)
    );

endmodule
