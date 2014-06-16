#include <system.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "instructions.h"
#include "creek.h"

static volatile uint16_t *instr_mem = (uint16_t *) INSTR_MEM_ADAPTER_0_BASE;
static volatile uint8_t *creek_ctrl = (uint8_t *) CREEK_CTRL_ADAPTER_0_BASE;

uint32_t creek_len(uint32_t size)
{
	uint32_t realsize = size >> CREEK_ADDR_SHIFT;
	if ((size & CREEK_ADDR_MASK) != 0)
		realsize += 1;
	return realsize;
}

uint32_t creek_addr(uint32_t size)
{
	return creek_len(size / 4);
}

void creek_init(struct creek *creek)
{
	creek->instr_num = 0;
}

void creek_write_instr(struct creek *creek, uint16_t instr)
{
	instr_mem[creek->instr_num] = instr;
	creek->instr_num++;
}

void set_scalar_int(struct creek *creek,
		uint8_t regnum, uint8_t saddr, uint32_t value)
{
	uint8_t byteval;
	int i;
	uint8_t addr = (regnum << REGNUM_SHIFT) || saddr;

	for (i = 0; i < 4; i++) {
		byteval = (value >> (i * 8)) & 0xff;
		creek_write_instr(creek, sets_instr(i, addr, byteval));
	}
}

void set_scalar_float(struct creek *creek,
		uint8_t regnum, uint8_t saddr, float value)
{
	uint32_t ivalue;

	memcpy(&ivalue, &value, sizeof(value));
	set_scalar_int(creek, regnum, saddr, ivalue);
}

static void prep_register(struct creek *creek, int regnum,
		uint32_t start, uint32_t count)
{
	set_scalar_int(creek, regnum, REG_START, creek_addr(start));
	set_scalar_int(creek, regnum, REG_STEP, 1);
	set_scalar_int(creek, regnum, REG_COUNT, creek_len(count));
}

void creek_load_reg(struct creek *creek,
		uint8_t regnum, float *values, uint32_t length)
{
	prep_register(creek, 0, (uint32_t) values, (uint32_t) length);
	prep_register(creek, regnum, 0, (uint32_t) length);
	creek_write_instr(creek, load_instr(regnum));
}

void creek_store_reg(struct creek *creek,
		uint8_t regnum, float *values, uint32_t length)
{
	prep_register(creek, 0, (uint32_t) values, (uint32_t) length);
	prep_register(creek, regnum, 0, (uint32_t) length);
	creek_write_instr(creek, store_instr(regnum));
}

void creek_run_and_sync(struct creek *creek)
{
	uint8_t ctrl_val;

	printf("%x\n", *creek_ctrl);

	creek_write_instr(creek, wait_instr(0));
	*creek_ctrl |= (1 << CREEK_CTRL_PAUSE_N);
	*creek_ctrl |= (1 << CREEK_CTRL_RESUME);
	*creek_ctrl &= ~(1 << CREEK_CTRL_RESUME);

	do {
		ctrl_val = *creek_ctrl;
	} while (!((ctrl_val >> CREEK_CTRL_WAITING) & 1));

	*creek_ctrl &= ~(1 << CREEK_CTRL_PAUSE_N);
}
