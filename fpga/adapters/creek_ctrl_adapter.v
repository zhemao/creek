module creek_ctrl_adapter (
    input clk,
    input reset,

    input [7:0] avl_writedata,
    input       avl_write,

    output reg [7:0] avl_readdata,
    input            avl_read,

    output reg pause_n,
    output reg resume,
    input      waiting
);

always @(posedge clk) begin
    if (reset) begin
        pause_n <= 1'b0;
        resume <= 1'b0;
    end else if (avl_write) begin
        pause_n <= avl_writedata[0];
        resume <= avl_writedata[1];
    end else if (avl_read) begin
        avl_readdata <= {5'd0, waiting, resume, pause_n};
    end else begin
        resume <= 1'b0;
    end
end

endmodule
