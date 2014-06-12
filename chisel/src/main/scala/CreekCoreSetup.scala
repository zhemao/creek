package Creek

import Chisel._
import Creek.Constants.FloatSize
import Creek.Opcode._
import Creek.Instruction.{constructSetsSequence, constructInstr}
import ChiselFloat.FloatUtils.{floatsToBigInt, floatAdd}

class CreekCoreSetup(
        instr_depth: Int, lanes: Int,
        regdepth: Int, nregs: Int,
        memaddrsize: Int, val testsize: Int) extends Module {
    val VectorWidth = FloatSize * lanes
    val io = new Bundle {
        val pause_n = Bool(INPUT)
        val local_init_done = Bool(INPUT)

        val mem_address = UInt(INPUT, memaddrsize)
        val mem_readdata = UInt(OUTPUT, VectorWidth)
        val mem_readdatavalid = Bool(OUTPUT)
        val mem_read = Bool(INPUT)
        val mem_waitrequest_n = Bool(OUTPUT)
        val mem_writedata = UInt(INPUT, VectorWidth)
        val mem_write = Bool(INPUT)
    }

    val core = Module(
        new CreekCore(instr_depth, lanes, regdepth, nregs, memaddrsize))
    core.io.pause_n := io.pause_n
    core.io.local_init_done := io.local_init_done

    val dummymem = Module(new DummyMemory(memaddrsize, FloatSize * lanes))
    core.io.avl_waitrequest_n := dummymem.io.avl_waitrequest_n
    core.io.avl_readdatavalid := dummymem.io.avl_readdatavalid
    core.io.avl_readdata := dummymem.io.avl_readdata
    io.mem_waitrequest_n := dummymem.io.avl_waitrequest_n
    io.mem_readdatavalid := dummymem.io.avl_readdatavalid
    io.mem_readdata := dummymem.io.avl_readdata

    when (io.pause_n) {
        dummymem.io.avl_address := core.io.avl_address
        dummymem.io.avl_writedata := core.io.avl_writedata
        dummymem.io.avl_read := core.io.avl_read
        dummymem.io.avl_write := core.io.avl_write
    } .otherwise {
        dummymem.io.avl_address := io.mem_address
        dummymem.io.avl_writedata := io.mem_writedata
        dummymem.io.avl_read := io.mem_read
        dummymem.io.avl_write := io.mem_write
    }

    val instructions =
        // set mem registers and reg 1 to count testsize
        constructSetsSequence(0, 2, testsize) ++     // 0 - 3
        constructSetsSequence(1, 2, testsize) ++     // 4 - 7
        // load from mem to reg 1
        Array(constructInstr(LOAD, 1, 0, 0)) ++      // 8
        // set starting mem addr to testsize
        constructSetsSequence(0, 0, testsize) ++     // 9 - 12
        // set reg 2 count to testsize
        constructSetsSequence(2, 2, testsize) ++     // 13 - 16
        // load from mem starting at testsize into reg 2
        Array(constructInstr(LOAD, 2, 0, 0),         // 17
              constructInstr(WAIT, 1, 0, 0),         // 18
              constructInstr(WAIT, 2, 0, 0),         // 19
              // multiply reg 1 by reg 2 and place answer back in reg 1
              constructInstr(MULTV, 1, 2, 1)) ++     // 20
        // set mem start address to 2 * testsize
        constructSetsSequence(0, 0, 2 * testsize) ++ // 21 - 24
        // load from memory starting at 2 * testsize to reg 2
        Array(constructInstr(LOAD, 2, 0, 0),         // 25
              constructInstr(WAIT, 2, 0, 0),         // 26
              // add reg 1 (mult result) by reg 2, storing back in reg 2
              constructInstr(ADDV, 1, 2, 2)) ++      // 27
        constructSetsSequence(0, 0, 3 * testsize) ++ // 28 - 31
              // store addition results
        Array(constructInstr(STORE, 2, 0, 0),        // 32
              constructInstr(WAIT, 2, 0, 0),         // 33
              constructInstr(WAIT, 0, 0, 0))         // 34

    val instr_rom = Vec.fill(instr_depth) { UInt(width = 16) }
    for (i <- 0 until instructions.length) {
        instr_rom(i) := UInt(instructions(i))
    }
    for (i <- instructions.length until instr_depth) {
        instr_rom(i) := UInt(constructInstr(WAIT, 0, 0, 0))
    }
    val instr_addr = Reg(next = core.io.instr_address)
    core.io.instr_data := instr_rom(instr_addr)
}

class CreekCoreSetupTest(c: CreekCoreSetup) extends Tester(c) {
    poke(c.io.local_init_done, 1)
    poke(c.io.pause_n, 0)
    step(1)

    def writeValue(addr: Int, value: BigInt) {
        poke(c.io.mem_write, 1)
        poke(c.io.mem_address, addr)
        poke(c.io.mem_writedata, value)
        step(1)
        poke(c.io.mem_write, 0)
        step(2)
        expect(c.io.mem_waitrequest_n, 0)
        step(6)
        expect(c.io.mem_waitrequest_n, 1)
    }

    def checkValue(addr: Int, value: BigInt) {
        poke(c.io.mem_read, 1)
        poke(c.io.mem_address, addr)
        step(1)
        poke(c.io.mem_read, 0)
        step(2)
        expect(c.io.mem_readdatavalid, 1)
        expect(c.io.mem_readdata, value)
        expect(c.io.mem_waitrequest_n, 0)
        step(6)
        expect(c.io.mem_waitrequest_n, 1)
    }
    
    def floatsToWords(values: Array[Float], lanes: Int) = {
        val numwords = values.length / lanes
        val words = new Array[BigInt](numwords)
        for (i <- 0 until numwords) {
            val float_group = values.slice(i * lanes, (i + 1) * lanes)
            words(i) = floatsToBigInt(float_group)
        }
        words
    }

    val avalues = Array.fill(16){ rnd.nextFloat() * 10000.0f - 5000.0f }
    val bvalues = Array.fill(16){ rnd.nextFloat() * 10000.0f - 5000.0f }
    val cvalues = Array.fill(16){ rnd.nextFloat() * 10000.0f - 5000.0f }
    val resvalues = (avalues zip bvalues zip cvalues).map {
        case ((a, b), c) => floatAdd(a * b, c)
    }
    val awords = floatsToWords(avalues, 4)
    val bwords = floatsToWords(bvalues, 4)
    val cwords = floatsToWords(cvalues, 4)
    val reswords = floatsToWords(resvalues, 4)

    for (i <- 0 until 4) {
        writeValue(i, awords(i))
        writeValue(4 + i, bwords(i))
        writeValue(8 + i, cwords(i))
    }

    poke(c.io.pause_n, 1)
    step(400)
    poke(c.io.pause_n, 0)
    step(1)

    for (i <- 0 until 4) {
        checkValue(12 + i, reswords(i))
    }
}
