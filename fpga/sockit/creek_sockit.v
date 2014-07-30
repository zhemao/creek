`define SYNTHESIS

module creek_sockit (
    input          OSC_50_B3B,
    input          RESET_n,

    output [14:0]  HPS_DDR3_A,
    output [2:0]   HPS_DDR3_BA,
    output         HPS_DDR3_CAS_n,
    output         HPS_DDR3_CKE,
    output         HPS_DDR3_CK_n,
    output         HPS_DDR3_CK_p,
    output         HPS_DDR3_CS_n,
    output [3:0]   HPS_DDR3_DM,
    inout  [31:0]  HPS_DDR3_DQ,
    inout  [3:0]   HPS_DDR3_DQS_n,
    inout  [3:0]   HPS_DDR3_DQS_p,
    output         HPS_DDR3_ODT,
    output         HPS_DDR3_RAS_n,
    output         HPS_DDR3_RESET_n,
    input          HPS_DDR3_RZQ,
    output         HPS_DDR3_WE_n,

    output [3:0]   LED,
    input  [3:0]   KEY,
    input  [3:0]   SW
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
wire waiting;
wire local_init_done = 1'b1;

wire [15:0] cur_instr;
wire [9:0]  cur_pc;
wire [4:0]  cur_state;

assign LED = !KEY;

soc_system soc (
    .clk_clk (main_clk),
    .reset_reset_n (RESET_n),
    .hps_memory_mem_a (HPS_DDR3_A),
    .hps_memory_mem_ba (HPS_DDR3_BA),
    .hps_memory_mem_ck (HPS_DDR3_CK_p),
    .hps_memory_mem_ck_n (HPS_DDR3_CK_n),
    .hps_memory_mem_cke (HPS_DDR3_CKE),
    .hps_memory_mem_cs_n (HPS_DDR3_CS_n),
    .hps_memory_mem_dm (HPS_DDR3_DM),
    .hps_memory_mem_ras_n (HPS_DDR3_RAS_n),
    .hps_memory_mem_cas_n (HPS_DDR3_CAS_n),
    .hps_memory_mem_we_n (HPS_DDR3_WE_n),
    .hps_memory_mem_reset_n (HPS_DDR3_RESET_n),
    .hps_memory_mem_dq (HPS_DDR3_DQ),
    .hps_memory_mem_dqs (HPS_DDR3_DQS_p),
    .hps_memory_mem_dqs_n (HPS_DDR3_DQS_n),
    .hps_memory_mem_odt (HPS_DDR3_ODT),
    .hps_memory_oct_rzqin (HPS_DDR3_RZQ),

    .instr_mem_address (instr_writeaddr),
    .instr_mem_writedata (instr_writedata),
    .instr_mem_write (instr_write),

    .ctrl_pause_n (pause_n),
    .ctrl_waiting (waiting),

    .core_pause_n (pause_n),
    .core_local_init_done (local_init_done),
    .core_instr_address (instr_readaddr),
    .core_instr_data (instr_readdata),
    .core_waiting (waiting),
    .core_cur_pc (cur_pc),
    .core_cur_instr (cur_instr),
    .core_cur_state (cur_state),

    .instrumentation_instr (cur_instr),
    .instrumentation_pc (cur_pc),
    .instrumentation_state (cur_state)
);

endmodule
