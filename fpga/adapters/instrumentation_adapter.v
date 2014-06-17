module instrumentation_adapter (
    input clk,
    input reset,

    input      [0:0]  avl_address,
    output reg [15:0] avl_readdata,

    input      [15:0] current_instr,
    input      [9:0]  current_pc
);

always @(posedge clk) begin
    if (reset) begin
        avl_readdata <= 16'd0;
    end else begin
        case (avl_address)
            1'b0: avl_readdata <= current_instr;
            1'b1: avl_readdata <= {6'd0, current_pc};
        endcase
    end
end

endmodule
