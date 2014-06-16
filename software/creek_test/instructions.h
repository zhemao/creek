#ifndef CREEK_TEST_INSTRUCTION_H
#define CREEK_TEST_INSTRUCTION_H

#include <stdint.h>

enum creek_opcode {
	LOAD = 0x0,
	STORE = 0x1,
	COPY = 0x2,
	ADDV = 0x4,
	ADDS = 0x5,
	SUBV = 0x6,
	SUBS = 0x7,
	MULTV = 0x8,
	MULTS = 0x9,
	SQUARE = 0xA,
	WAIT = 0xC
};

uint16_t sets_instr(uint8_t bytepos, uint8_t addr, uint8_t value);

uint16_t load_instr(uint8_t reg);

uint16_t store_instr(uint8_t reg);

uint16_t copy_instr(uint8_t reg);

uint16_t addv_instr(uint8_t rega, uint8_t regb, uint8_t regres);

uint16_t adds_instr(uint8_t rega, uint8_t regres);

uint16_t subv_instr(uint8_t rega, uint8_t regb, uint8_t regres);

uint16_t subs_instr(uint8_t rega, uint8_t regres);

uint16_t multv_instr(uint8_t rega, uint8_t regb, uint8_t regres);

uint16_t mults_instr(uint8_t rega, uint8_t regres);

uint16_t square_instr(uint8_t rega, uint8_t regres);

uint16_t wait_instr(uint8_t reg);

#endif
