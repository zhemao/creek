`define SYNTHESIS

module sockit_top (
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

fpga_ddr3 ddr3 (
    .pll_ref_clk (OSC_50_B8A),
    .global_reset_n (RESET_n),
    .soft_reset_n (RESET_n),
    .afi_clk (afi_clk),

    .mem_a(DDR3_A),
    .mem_ba(DDR3_BA),
    .mem_ck(DDR3_CK_p),
    .mem_ck_n(DDR3_CK_n),
    .mem_cke(DDR3_CKE),
    .mem_cs_n(DDR3_CS_n),
    .mem_dm(DDR3_DM),
    .mem_ras_n(DDR3_RAS_n),
    .mem_cas_n(DDR3_CAS_n),
    .mem_we_n(DDR3_WE_n),
    .mem_reset_n(DDR3_RESET_n),
    .mem_dq(DDR3_DQ),
    .mem_dqs(DDR3_DQS_p),
    .mem_dqs_n(DDR3_DQS_n),
    .mem_odt(DDR3_ODT),
    .oct_rzqin(DDR3_RZQ),

    .avl_ready (avl_ready),
    .avl_burstbegin (avl_write || avl_read),
    .avl_addr (avl_address),
    .avl_rdata (avl_readdata),
    .avl_rdata_valid (avl_readdatavalid),
    .avl_wdata (avl_writedata),
    .avl_be (16'hFFFF),
    .avl_read_req (avl_read),
    .avl_write_req (avl_write),
    .avl_size (3'b001),
    .local_init_done (local_init_done)
);

/*wire [9:0]  instr_readaddr;
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

wire pause_n = 1'b1;

CreekCore core (
    .clk (OSC_50_B8A),
    .reset (!RESET_n),
    .io_pause_n (pause_n),
    .io_local_init_done (local_init_done),
    .io_avl_ready (avl_ready),
    .io_avl_address (avl_address),
    .io_avl_readdatavalid (avl_readdatavalid),
    .io_avl_readdata (avl_readdata),
    .io_avl_writedata (avl_writedata),
    .io_avl_read (avl_read),
    .io_avl_write (avl_write),
    .io_instr_address (instr_readaddr),
    .io_instr_data (instr_readdata)
);*/

endmodule
