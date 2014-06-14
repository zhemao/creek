module instr_mem_adapter (
    input        clk,
    input        reset,
    input [9:0]  avl_address,
    input [15:0] avl_writedata,
    input        avl_write,

    output [9:0]  instr_address,
    output [15:0] instr_writedata,
    output        instr_write
);

assign instr_address = avl_address;
assign instr_writedata = avl_writedata;
assign instr_write = avl_write;

endmodule
