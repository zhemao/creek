package Creek

object Opcode {
    val LOAD   = 0x0
    val STORE  = 0x1
    val COPY   = 0x2
    val ADDV   = 0x4
    val ADDS   = 0x5
    val SUBV   = 0x6
    val SUBS   = 0x7
    val MULTV  = 0x8
    val MULTS  = 0x9
    val SQUARE = 0xA
    val WAIT   = 0xC
}

object Instruction {
    def constructSetsInstr(byte: Int, addr: Int, value: Int) =
        (byte & 0x3) << 13 | (addr & 0x1f) << 8 | (value & 0xff)

    def constructSetsSequence(regnum: Int, regaddr: Int, value: Int) = {
        val addr = regnum << 2 | regaddr
        (0 until 4).map { i =>
            val byteval = (value >> (8 * i)) & 0xff
            constructSetsInstr(i, addr, byteval)
        }
    }

    def constructInstr(opcode: Int, reg1: Int, reg2: Int, reg3: Int) =
        1 << 15 | (opcode & 0xf) << 11 | (reg1 & 0x7) << 8 |
        (reg2 & 0x7) << 5 | (reg3 & 0x7) << 2
}
