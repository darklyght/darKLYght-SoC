module BUFIOWrapper (
    input I,
    output O
);

    `ifndef SIMULATION
        BUFIO bufio (
            .I(I),
            .O(O)
        );
    `else
        BUFIOSIM bufio (
            .I(I),
            .O(O)
        );
    `endif

endmodule

module BUFIOSIM(
    input I,
    output O
);
    assign O = I;
endmodule
