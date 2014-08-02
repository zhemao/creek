#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include "instructions.h"
#include "creek.h"

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

int creek_init(struct creek *creek)
{
	int fd;

	creek->instr_num = 0;
	creek->last_mem_pos = 0;

	fd = open("/dev/mem", O_RDWR);
	if (fd < 0)
		return -1;

	creek->iomem = (volatile void *) mmap(NULL, LWHPS2FPGA_SIZE,
				PROT_READ | PROT_WRITE, MAP_SHARED,
				fd, LWHPS2FPGA_BASE);
	close(fd);

	if (creek->iomem == MAP_FAILED)
		return -1;

	return 0;
}

void creek_release(struct creek *creek)
{
	munmap((void *) creek->iomem, LWHPS2FPGA_SIZE);
}

void creek_write_instr(struct creek *creek, uint16_t instr)
{
	creek->instructions[creek->instr_num] = instr;
	creek->instr_num++;
}

void set_scalar_int(struct creek *creek,
		uint8_t regnum, uint8_t saddr, uint32_t value)
{
	uint8_t byteval;
	int i;
	uint8_t addr = (regnum << REGNUM_SHIFT) | saddr;

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

void creek_prep_reg(struct creek *creek, int regnum,
		uint32_t start, uint32_t count)
{
	set_scalar_int(creek, regnum, REG_START, creek_addr(start));
	set_scalar_int(creek, regnum, REG_STEP, 1);
	set_scalar_int(creek, regnum, REG_COUNT, creek_len(count));
}

void creek_load_reg(struct creek *creek,
		uint8_t regnum, void *values, uint32_t length)
{
	uint32_t load_start = (uint32_t) (values - creek->iomem);
	creek_prep_reg(creek, 0, load_start, (uint32_t) length);
	creek_prep_reg(creek, regnum, 0, (uint32_t) length);
	creek_write_instr(creek, load_instr(regnum));
}

void creek_store_reg(struct creek *creek,
		uint8_t regnum, void *values, uint32_t length)
{
	uint32_t store_start = (uint32_t) (values - creek->iomem);
	creek_prep_reg(creek, 0, store_start, (uint32_t) length);
	creek_prep_reg(creek, regnum, 0, (uint32_t) length);
	creek_write_instr(creek, store_instr(regnum));
}

void creek_run_and_sync(struct creek *creek)
{
	uint8_t waiting;
	uint16_t last_pc;
	volatile uint16_t *instr_mem, *cur_instr, *cur_pc, *cur_state;
	volatile uint8_t *creek_ctrl;
	int i;

	instr_mem = (volatile uint16_t *) (creek->iomem + INSTR_MEM_OFFSET);
	creek_ctrl = (volatile uint8_t *) (creek->iomem + CREEK_CTRL_OFFSET);
	cur_instr = (volatile uint16_t *) (creek->iomem + CURRENT_INSTR_OFFSET);
	cur_pc = (volatile uint16_t *) (creek->iomem + CURRENT_PC_OFFSET);
	cur_state = (volatile uint16_t *) (creek->iomem + CURRENT_STATE_OFFSET);

	*creek_ctrl |= (1 << CREEK_CTRL_RESET);

	last_pc = *cur_pc;
	printf("Starting pc: %d\n", last_pc);

	creek_write_instr(creek, wait_instr(0));

	for (i = 0; i < creek->instr_num; i++) {
		instr_mem[i] = creek->instructions[i];
	}

	*creek_ctrl |= (1 << CREEK_CTRL_PAUSE_N);

	do {
		waiting = (*creek_ctrl >> CREEK_CTRL_WAITING) & 0x1;
		if (last_pc != *cur_pc) {
			last_pc = *cur_pc;
			printf("Current pc: %d / %d\n", last_pc,
					creek->instr_num);
			printf("Current instr: %x\n", *cur_instr);
			printf("Current state: %d\n", *cur_state);
		}
	} while (!waiting);

	*creek_ctrl &= ~(1 << CREEK_CTRL_PAUSE_N);
	creek->instr_num = 0;
}

volatile void *creek_malloc(struct creek *creek, unsigned int size)
{
	volatile void *ptr;

	size = creek_len(size) << CREEK_ADDR_SHIFT;

	if (creek->last_mem_pos + size > DATA_MEM_LENGTH) {
		return NULL;
	}

	ptr = creek->iomem + creek->last_mem_pos;
	creek->last_mem_pos += size;

	return ptr;
}
