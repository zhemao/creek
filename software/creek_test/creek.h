#ifndef CREEK_TEST_CREEK_H
#define CREEK_TEST_CREEK_H

#include <stdint.h>

#define REGNUM_SHIFT 2
#define REG_START 0
#define REG_STEP 1
#define REG_COUNT 2
#define REG_SCALAR 3

#define CREEK_MEM_REG 0
#define CREEK_LANES 4
#define CREEK_ADDR_MASK 0x3
#define CREEK_ADDR_SHIFT 2
#define CREEK_BYTES_PER_WORD (CREEK_LANES * 4)
#define CREEK_VEC_DEPTH 256

#define CREEK_CTRL_PAUSE_N 0
#define CREEK_CTRL_RESUME 1
#define CREEK_CTRL_WAITING 2

#define INSTR_MEM_DEPTH 1024

struct creek {
	uint16_t instr_num;
	uint16_t instructions[INSTR_MEM_DEPTH];
};

uint32_t creek_len(uint32_t size);
uint32_t creek_addr(uint32_t size);

void creek_init(struct creek *creek);
void creek_write_instr(struct creek *creek, uint16_t instr);

void set_scalar_int(struct creek *creek,
		uint8_t regnum, uint8_t saddr, uint32_t value);
void set_scalar_float(struct creek *creek,
		uint8_t regnum, uint8_t saddr, float value);

void creek_load_reg(struct creek *creek,
		uint8_t regnum, float *values, uint32_t length);
void creek_store_reg(struct creek *creek,
		uint8_t regnum, float *values, uint32_t length);
void creek_run_and_sync(struct creek *creek);

#endif
