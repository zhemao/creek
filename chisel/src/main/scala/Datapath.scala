package Creek

import Chisel._
import ChiselCrossbar._
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt, floatAdd}
import Creek.Constants.FloatSize

class Datapath(val lanes: Int, regdepth: Int, val nregs: Int, memaddrsize: Int)
        extends Module {

    // +1 because of memory controller registers
    val RealNRegs = nregs + 1
    val ScalarAddrSize = log2Up(RealNRegs) + 2
    val ScalarWidth = FloatSize
    val VectorWidth = FloatSize * lanes

    val io = new Bundle {
        val local_init_done = Bool(INPUT)
        val avl_waitrequest_n = Bool(INPUT)
        val avl_address = UInt(OUTPUT, memaddrsize)
        val avl_readdatavalid = Bool(INPUT)
        val avl_readdata = UInt(INPUT, VectorWidth)
        val avl_writedata = UInt(OUTPUT, VectorWidth)
        val avl_read = Bool(OUTPUT)
        val avl_write = Bool(OUTPUT)

        val input_select = Vec.fill(5) { UInt(INPUT, log2Up(RealNRegs)) }
        val output_select = Vec.fill(3) { UInt(INPUT, log2Up(RealNRegs)) }

        val reg_read_busy = Vec.fill(RealNRegs) { Bool(OUTPUT) }
        val reg_write_busy = Vec.fill(RealNRegs) { Bool(OUTPUT) }
        val reg_copy_reset = Vec.fill(RealNRegs) { Bool(INPUT) }

        val adder_reset = Bool(INPUT)
        val adder_busy = Bool(OUTPUT)
        val adder_use_scalar = Bool(INPUT)
        val adder_subtract = Bool(INPUT)

        val mult_reset = Bool(INPUT)
        val mult_busy = Bool(OUTPUT)
        val mult_use_scalar = Bool(INPUT)
        val mult_square = Bool(INPUT)

        val mem_ready = Bool(OUTPUT)
        val mem_start_read = Bool(INPUT)
        val mem_start_write = Bool(INPUT)

        val scalar_address = UInt(INPUT, ScalarAddrSize)
        val scalar_writedata = UInt(INPUT, ScalarWidth)
        val scalar_byteenable = UInt(INPUT, ScalarWidth / 8)
        val scalar_write = Bool(INPUT)
    }

    val input_fwidth = new UnitForwardInput(lanes).getWidth
    val input_bwidth = new UnitBackwardInput(lanes).getWidth
    val output_fwidth = 1
    val output_bwidth = new UnitBackwardOutput(lanes).getWidth

    val in_switch = Module(new CrossbarSwitch(
        input_fwidth, input_bwidth, RealNRegs, 5))
    val out_switch = Module(new CrossbarSwitch(
        output_fwidth, output_bwidth, RealNRegs, 3))

    in_switch.io.select := io.input_select
    out_switch.io.select := io.output_select

    val scalar_regsel = io.scalar_address(ScalarAddrSize - 1, 2)
    val scalar_regaddr = io.scalar_address(1, 0)

    in_switch.io.fw_left(0) := Bits(0)
    out_switch.io.fw_left(0) := Bits(0)

    io.reg_read_busy(0) := Bool(true)
    io.reg_write_busy(0) := Bool(true)

    // The user-accessible registers start from 1
    // Register 0 is reserved for the zero vector register
    // memory control registers
    for (i <- 1 to nregs) {
        val reg = Module(new RegisterSet(regdepth, VectorWidth, ScalarWidth))
        val ufi = new UnitForwardInput(lanes)
        val ubi = new UnitBackwardInput(lanes)
            .fromBits(in_switch.io.bw_left(i))
        val ubo = new UnitBackwardOutput(lanes)
            .fromBits(out_switch.io.bw_left(i))

        reg.io.copy_reset := io.reg_copy_reset(i)

        ufi.vector_readdata := reg.io.vector_readdata
        ufi.busy := reg.io.read_busy
        ufi.scalar_value := reg.io.scalar_value
        in_switch.io.fw_left(i) := ufi.toBits

        reg.io.read_reset := ubi.read_reset
        reg.io.vector_read := ubi.vector_read

        out_switch.io.fw_left(i) := reg.io.write_busy

        reg.io.write_reset := ubo.write_reset
        reg.io.vector_writedata := ubo.vector_writedata
        reg.io.vector_write := ubo.vector_write

        io.reg_read_busy(i) := reg.io.read_busy
        io.reg_write_busy(i) := reg.io.write_busy

        reg.io.scalar_writeaddr := scalar_regaddr
        reg.io.scalar_writedata := io.scalar_writedata
        reg.io.scalar_write := io.scalar_write && (scalar_regsel === UInt(i))
        reg.io.scalar_byteenable := io.scalar_byteenable
    }

    def connectArithmeticUnit(unit: ArithmeticUnit, ai: Int, bi: Int, ri: Int) {
        val a_ufi = new UnitForwardInput(lanes)
            .fromBits(in_switch.io.fw_bottom(ai))
        val a_ubi = new UnitBackwardInput(lanes)
        val b_ufi = new UnitForwardInput(lanes)
            .fromBits(in_switch.io.fw_bottom(bi))
        val b_ubi = new UnitBackwardInput(lanes)
        val res_ubo = new UnitBackwardOutput(lanes)

        unit.io.a_vreg_data := a_ufi.vector_readdata
        unit.io.a_vreg_busy := a_ufi.busy
        unit.io.a_scalar_data := a_ufi.scalar_value

        a_ubi.read_reset := unit.io.a_vreg_reset
        a_ubi.vector_read := unit.io.a_vreg_read
        in_switch.io.bw_bottom(ai) := a_ubi.toBits

        unit.io.b_vreg_data := b_ufi.vector_readdata
        unit.io.b_vreg_busy := b_ufi.busy

        b_ubi.read_reset := unit.io.b_vreg_reset
        b_ubi.vector_read := unit.io.b_vreg_read
        in_switch.io.bw_bottom(bi) := b_ubi.toBits

        unit.io.res_vreg_busy := out_switch.io.fw_bottom(ri).toBool
        res_ubo.write_reset := unit.io.res_vreg_reset
        res_ubo.vector_writedata := unit.io.res_vreg_data
        res_ubo.vector_write := unit.io.res_vreg_write
        out_switch.io.bw_bottom(ri) := res_ubo.toBits
    }

    val adder = Module(new AdderUnit(lanes))
    adder.io.reset := io.adder_reset
    io.adder_busy := adder.io.busy
    adder.io.use_scalar := io.adder_use_scalar
    adder.io.invert_b := io.adder_subtract
    connectArithmeticUnit(adder, 0, 1, 0)

    val multiplier = Module(new MultiplierUnit(lanes))
    multiplier.io.reset := io.mult_reset
    io.mult_busy := multiplier.io.busy
    multiplier.io.use_scalar := io.mult_use_scalar
    multiplier.io.double_a := io.mult_square
    connectArithmeticUnit(multiplier, 2, 3, 1)

    val memctrl = Module(new MemoryController(memaddrsize, VectorWidth))
    val mem_ufi = new UnitForwardInput(lanes)
        .fromBits(in_switch.io.fw_bottom(4))
    val mem_ubi = new UnitBackwardInput(lanes)
    val mem_ubo = new UnitBackwardOutput(lanes)

    memctrl.io.reg_readdata := mem_ufi.vector_readdata
    memctrl.io.reg_busy := mem_ufi.busy

    mem_ubi.read_reset := memctrl.io.reg_read_reset
    mem_ubi.vector_read := memctrl.io.reg_read
    in_switch.io.bw_bottom(4) := mem_ubi.toBits

    mem_ubo.vector_writedata := memctrl.io.reg_writedata
    mem_ubo.vector_write := memctrl.io.reg_write
    mem_ubo.write_reset := memctrl.io.reg_write_reset
    out_switch.io.bw_bottom(2) := mem_ubo.toBits

    memctrl.io.start_read := io.mem_start_read
    memctrl.io.start_write := io.mem_start_write

    val memctrl_reg = Module(new MemoryControllerRegisters(ScalarWidth))
    memctrl.io.start_addr := memctrl_reg.io.start
    memctrl.io.addr_step := memctrl_reg.io.step
    memctrl.io.transfer_count := memctrl_reg.io.count
    memctrl_reg.io.writeaddr := scalar_regaddr
    memctrl_reg.io.writedata := io.scalar_writedata
    memctrl_reg.io.write := io.scalar_write && (scalar_regsel === UInt(0))
    memctrl_reg.io.byteenable := io.scalar_byteenable

    memctrl.io.local_init_done := io.local_init_done
    memctrl.io.avl_waitrequest_n := io.avl_waitrequest_n
    memctrl.io.avl_readdatavalid := io.avl_readdatavalid
    memctrl.io.avl_readdata := io.avl_readdata

    io.avl_address := memctrl.io.avl_address
    io.avl_writedata := memctrl.io.avl_writedata
    io.avl_read := memctrl.io.avl_read
    io.avl_write := memctrl.io.avl_write

    io.mem_ready := memctrl.io.ready
}

class DatapathTest(c: Datapath) extends Tester(c) {

    def floatsToWords(values: Array[Float]) = {
        val numwords = values.length / c.lanes
        val words = new Array[BigInt](numwords)
        for (i <- 0 until numwords) {
            val float_group = values.slice(i * c.lanes, (i + 1) * c.lanes)
            words(i) = floatsToBigInt(float_group)
        }
        words
    }

    def setRegisterValue(regnum: Int, scalar_addr: Int, value: BigInt) {
        val full_addr = (regnum << 2) | scalar_addr

        poke(c.io.scalar_address, full_addr)
        poke(c.io.scalar_writedata, value)
        poke(c.io.scalar_byteenable, 0xf)
        poke(c.io.scalar_write, 1)
        step(1)
        poke(c.io.scalar_write, 0)
        step(1)
    }

    def setRegisterCount(regnum: Int, count: Int) =
        setRegisterValue(regnum, 2, count)

    def writeValuesToRegister(regnum: Int, values: Array[Float]) {
        val words = floatsToWords(values)

        setRegisterCount(0, words.length)
        setRegisterCount(regnum, words.length)

        poke(c.io.input_select(4), regnum)
        poke(c.io.output_select(2), regnum)
        poke(c.io.avl_waitrequest_n, 1)
        poke(c.io.avl_readdatavalid, 0)
        poke(c.io.mem_start_read, 1)
        step(1)
        poke(c.io.mem_start_read, 0)
        step(2)

        for (i <- 0 until words.length) {
            expect(c.io.avl_read, 1)
            expect(c.io.avl_address, i)
            poke(c.io.avl_readdata, words(i))
            poke(c.io.avl_readdatavalid, 1)
            step(1)
            poke(c.io.avl_waitrequest_n, 0)
            poke(c.io.avl_readdatavalid, 0)
            step(2)
            poke(c.io.avl_waitrequest_n, 1)
            step(1)
        }
        expect(c.io.avl_read, 0)
        expect(c.io.mem_ready, 1)
    }

    def checkValuesInRegister(regnum: Int, values: Array[Float]) {
        val words = floatsToWords(values)

        setRegisterCount(0, words.length)
        setRegisterCount(regnum, words.length)

        expect(c.io.mem_ready, 1)

        poke(c.io.input_select(4), regnum)
        poke(c.io.output_select(2), regnum)
        poke(c.io.avl_waitrequest_n, 1)
        poke(c.io.mem_start_write, 1)
        step(1)
        poke(c.io.mem_start_write, 0)
        step(3)

        for (i <- 0 until words.length) {
            expect(c.io.mem_ready, 0)
            expect(c.io.avl_write, 1)
            expect(c.io.avl_address, i)
            expect(c.io.avl_writedata, words(i))
            poke(c.io.avl_waitrequest_n, 0)
            step(3)
            poke(c.io.avl_waitrequest_n, 1)
            step(1)
        }

        expect(c.io.avl_write, 0)
        expect(c.io.mem_ready, 1)
    }

    def runMultiplication(areg: Int, breg: Int, resreg: Int, numwords: Int,
            square: Boolean) {
        setRegisterCount(areg, numwords)
        setRegisterCount(breg, numwords)
        setRegisterCount(resreg, numwords)

        poke(c.io.input_select(2), areg)
        poke(c.io.input_select(3), breg)
        poke(c.io.output_select(1), resreg)
        poke(c.io.mult_use_scalar, 0)
        if (square) {
            poke(c.io.mult_square, 1)
        } else {
            poke(c.io.mult_square, 0)
        }
        step(1)

        poke(c.io.mult_reset, 1)
        step(1)
        poke(c.io.mult_reset, 0)
        step(1)

        expect(c.io.mult_busy, 1)
        expect(c.io.reg_read_busy(areg), 1)
        expect(c.io.reg_read_busy(breg), 1)
        step(3)
        expect(c.io.reg_write_busy(resreg), 1)
        step(numwords)
        expect(c.io.mult_busy, 0)
    }

    def runAddition(areg: Int, breg: Int, resreg: Int, numwords: Int,
            subtract: Boolean) {
        poke(c.io.input_select(0), areg)
        poke(c.io.input_select(1), breg)
        poke(c.io.output_select(0), resreg)
        poke(c.io.adder_use_scalar, 0)
        if (subtract) {
            poke(c.io.adder_subtract, 1)
        } else {
            poke(c.io.adder_subtract, 0)
        }
        step(1)

        poke(c.io.adder_reset, 1)
        step(1)
        poke(c.io.adder_reset, 0)
        step(1)

        expect(c.io.adder_busy, 1)
        expect(c.io.reg_read_busy(areg), 1)
        expect(c.io.reg_read_busy(breg), 1)
        step(5)
        expect(c.io.reg_write_busy(resreg), 1)
        step(numwords)
        expect(c.io.adder_busy, 0)
    }

    def registerMemSet(regnum: Int, value: Float, times: Int) {
        setRegisterCount(regnum, times / 4)
        setRegisterValue(regnum, 3, floatToBigInt(value))

        poke(c.io.reg_copy_reset(regnum), 1)
        step(1)
        poke(c.io.reg_copy_reset(regnum), 0)
        step(1)
        expect(c.io.reg_write_busy(regnum), 1)
        step(times / 4)
        expect(c.io.reg_write_busy(regnum), 0)
    }

    poke(c.io.local_init_done, 1)
    poke(c.io.input_select, Array.fill(5){ BigInt(0) })
    poke(c.io.output_select, Array.fill(3){ BigInt(0) })
    step(1)

    val avalues = Array.fill(16){ rnd.nextFloat() * 10000.0f - 5000.0f }
    val bvalues = Array.fill(16){ rnd.nextFloat() * 10000.0f - 5000.0f }
    val multresvalues = (avalues zip bvalues).map { pair => pair._1 * pair._2 }
    val addresvalues = (avalues zip bvalues).map { pair => floatAdd(pair._1, pair._2) }
    val subresvalues = (avalues zip bvalues).map { pair => floatAdd(pair._1, -pair._2) }
    val squareresvalues = avalues.map { a => a * a }

    writeValuesToRegister(1, avalues)
    writeValuesToRegister(2, bvalues)

    runMultiplication(1, 2, 3, 16 / c.lanes, false)
    checkValuesInRegister(3, multresvalues)

    runMultiplication(1, 1, 3, 16 / c.lanes, true)
    checkValuesInRegister(3, squareresvalues)

    runAddition(1, 2, 3, 16 / c.lanes, false)
    checkValuesInRegister(3, addresvalues)

    runAddition(1, 2, 3, 16 / c.lanes, true)
    checkValuesInRegister(3, subresvalues)

    val scalar_val = rnd.nextFloat() * 10000.0f - 5000.0f
    registerMemSet(4, scalar_val, 16)
    checkValuesInRegister(4, Array.fill(16){ scalar_val })
}
