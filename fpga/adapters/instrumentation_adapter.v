module instrumentation_adapter (
    input clk,
    input reset,

    input      [1:0]  avl_address,
    output reg [15:0] avl_readdata,

    input      [15:0] current_instr,
    input      [9:0]  current_pc,
    input      [4:0]  current_state
);

always @(posedge clk) begin
    if (reset) begin
        avl_readdata <= 16'd0;
    end else begin
        case (avl_address)
            2'b00: avl_readdata <= current_instr;
            2'b01: avl_readdata <= {6'd0, current_pc};
            2'b10: avl_readdata <= {11'd0, current_state};
        endcase
    end
end

endmodule
