`define SYNTHESIS

module creek_sockit (
    input          OSC_50_B3B,
    input          RESET_n,

    output [14:0]  DDR3_A,
    output [2:0]   DDR3_BA,
    output         DDR3_CAS_n,
    output         DDR3_CKE,
    output         DDR3_CK_n,
    output         DDR3_CK_p,
    output         DDR3_CS_n,
    output [3:0]   DDR3_DM,
    inout  [31:0]  DDR3_DQ,
    inout  [3:0]   DDR3_DQS_n,
    inout  [3:0]   DDR3_DQS_p,
    output         DDR3_ODT,
    output         DDR3_RAS_n,
    output         DDR3_RESET_n,
    input          DDR3_RZQ,
    output         DDR3_WE_n,

    output [3:0]   LED
);

wire [9:0]  instr_readaddr;
wire [15:0] instr_readdata;

wire [9:0]  instr_writeaddr;
wire [15:0] instr_writedata;
wire        instr_write;

wire main_clk = OSC_50_B3B;

instr_mem im (
    .clock (main_clk),
    .data (instr_writedata),
    .rdaddress (instr_readaddr),
    .wraddress (instr_writeaddr),
    .wren (instr_write),
    .q (instr_readdata)
);

wire pause_n;
wire resume;
wire waiting;
wire local_init_done;

nios_system nios (
    .clk_clk (main_clk),
    .reset_reset_n (RESET_n),
    .memory_mem_a (DDR3_A),
    .memory_mem_ba (DDR3_BA),
    .memory_mem_ck (DDR3_CK_p),
    .memory_mem_ck_n (DDR3_CK_n),
    .memory_mem_cke (DDR3_CKE),
    .memory_mem_cs_n (DDR3_CS_n),
    .memory_mem_dm (DDR3_DM),
    .memory_mem_ras_n (DDR3_RAS_n),
    .memory_mem_cas_n (DDR3_CAS_n),
    .memory_mem_we_n (DDR3_WE_n),
    .memory_mem_reset_n (DDR3_RESET_n),
    .memory_mem_dq (DDR3_DQ),
    .memory_mem_dqs (DDR3_DQS_p),
    .memory_mem_dqs_n (DDR3_DQS_n),
    .memory_mem_odt (DDR3_ODT),
    .oct_rzqin (DDR3_RZQ),

    .instr_address (instr_writeaddr),
    .instr_writedata (instr_writedata),
    .instr_write (instr_write),

    .creek_ctrl_pause_n (pause_n),
    .creek_ctrl_resume (resume),
    .creek_ctrl_waiting (waiting),

    .core_pause_n (pause_n),
    .core_local_init_done (local_init_done),
    .core_instr_address (instr_readaddr),
    .core_instr_data (instr_readdata),
    .core_resume (resume),
    .core_waiting (waiting),
    .status_local_init_done (local_init_done)
);

endmodule
