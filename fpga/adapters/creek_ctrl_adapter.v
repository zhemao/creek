module creek_ctrl_adapter (
    input clk,
    input reset,

    input [7:0] avl_writedata,
    input       avl_write,

    output reg [7:0] avl_readdata,
    input            avl_read,

    output reg creek_pause_n,
    output reg creek_reset,
    input      creek_waiting
);

always @(posedge clk) begin
    if (reset) begin
        creek_pause_n <= 1'b0;
        creek_reset <= 1'b1;
    end else if (avl_write) begin
        creek_pause_n <= avl_writedata[0];
        creek_reset <= avl_writedata[1];
    end else if (avl_read) begin
        avl_readdata <= {5'd0, creek_waiting, creek_reset, creek_pause_n};
    end else begin
        creek_reset <= 1'b0;
    end
end

endmodule
