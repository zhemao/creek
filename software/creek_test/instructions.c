#include "instructions.h"

uint16_t sets_instr(uint8_t bytepos, uint8_t addr, uint8_t value)
{
        return (bytepos & 0x3) << 13 | (addr & 0x1f) << 8 | (value & 0xff);
}

static uint16_t normal_instr(
		uint8_t opcode, uint8_t reg1,
		uint8_t reg2, uint8_t reg3)
{
        return 1 << 15 | (opcode & 0xf) << 11 | (reg1 & 0x7) << 8 |
        	(reg2 & 0x7) << 5 | (reg3 & 0x7) << 2;
}

uint16_t load_instr(uint8_t reg)
{
	return normal_instr(LOAD, reg, 0, 0);
}

uint16_t store_instr(uint8_t reg)
{
	return normal_instr(STORE, reg, 0, 0);
}

uint16_t copy_instr(uint8_t reg)
{
	return normal_instr(COPY, reg, 0, 0);
}

uint16_t addv_instr(uint8_t rega, uint8_t regb, uint8_t regres)
{
	return normal_instr(ADDV, rega, regb, regres);
}

uint16_t adds_instr(uint8_t rega, uint8_t regres)
{
	return normal_instr(ADDS, rega, 0, regres);
}

uint16_t subv_instr(uint8_t rega, uint8_t regb, uint8_t regres)
{
	return normal_instr(SUBV, rega, regb, regres);
}

uint16_t subs_instr(uint8_t rega, uint8_t regres)
{
	return normal_instr(SUBS, rega, 0, regres);
}

uint16_t multv_instr(uint8_t rega, uint8_t regb, uint8_t regres)
{
	return normal_instr(MULTV, rega, regb, regres);
}

uint16_t mults_instr(uint8_t rega, uint8_t regres)
{
	return normal_instr(MULTS, rega, 0, regres);
}

uint16_t square_instr(uint8_t rega, uint8_t regres)
{
	return normal_instr(SQUARE, rega, 0, regres);
}

uint16_t wait_instr(uint8_t reg)
{
	return normal_instr(WAIT, reg, 0, 0);
}
