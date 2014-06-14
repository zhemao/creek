`define SYNTHESIS

module creek_sockit (
    input         OSC_50_B8A,
    input         RESET_n,

    output [14:0] DDR3_A,
    output [2:0]  DDR3_BA,
    output        DDR3_CAS_n,
    output        DDR3_CKE,
    output        DDR3_CK_n,
    output        DDR3_CK_p,
    output        DDR3_CS_n,
    output [3:0]  DDR3_DM,
    inout  [31:0] DDR3_DQ,
    inout  [3:0]  DDR3_DQS_n,
    inout  [3:0]  DDR3_DQS_p,
    output        DDR3_ODT,
    output        DDR3_RAS_n,
    output        DDR3_RESET_n,
    input         DDR3_RZQ,
    output        DDR3_WE_n,

    output [3:0]  LED
);

wire afi_clk;
wire avl_ready;
wire avl_readdatavalid;
wire [25:0] avl_address;
wire [127:0] avl_readdata;
wire [127:0] avl_writedata;
wire avl_read;
wire avl_write;
wire local_init_done;

wire [9:0]  instr_readaddr;
wire [15:0] instr_readdata;

wire [9:0]  instr_writeaddr;
wire [15:0] instr_writedata;
wire        instr_write;

instr_mem im (
    .clock (OSC_50_B8A),
    .data (instr_writedata),
    .rdaddress (instr_readaddr),
    .wraddress (instr_writeaddr),
    .wren (instr_write),
    .q (instr_readdata)
);

endmodule
