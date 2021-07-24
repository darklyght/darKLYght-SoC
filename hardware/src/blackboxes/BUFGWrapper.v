module BUFGWrapper (
    input I,
    output O
);

    BUFG bufg (
        .I(I),
        .O(O)
    );

endmodule
