package Creek

import Chisel._
import ChiselCrossbar.CrossbarSwitch
import Creek.Constants.FloatSize

class Datapath(lanes: Int, regdepth: Int, nregs: Int, memaddrsize: Int)
        extends Module {
    val io = new Bundle {
        val local_init_done = Bool(INPUT)
        val avl_waitrequest_n = Bool(INPUT)
        val avl_address = UInt(OUTPUT, addrsize)
        val avl_readdatavalid = Bool(INPUT)
        val avl_readdata = UInt(INPUT, datawidth)
        val avl_writedata = UInt(OUTPUT, datawidth)
        val avl_read = Bool(OUTPUT)
        val avl_write = Bool(OUTPUT)
    }

    val input_fwidth = new UnitForwardInput(lanes).getWidth
    val input_bwidth = new UnitBackwardInput(lanes).getWidth
    val output_fwidth = 1
    val output_bwidth = new UnitBackwardOutput(lanes).getWidth

    val in_switch = Module(new CrossbarSwitch(
        input_fwidth, input_bwidth, nregs, 5))
    val out_switch = Module(new CrossbarSwitch(
        output_fwidth, output_bwidth, nregs, 3))

    for (i <- 0 until nregs) {
        val reg = new RegisterSet(regdepth, FloatSize * lanes, FloatSize)
        val ufi = new UnitForwardInput(lanes)
        val ubi = new UnitBackwardInput(lanes)
        val ubo = new UnitBackwardOutput(lanes)

        ufi.vector_readdata := reg.io.vector_readdata
        ufi.busy := reg.io.busy
        ufi.scalar_value := reg.io.scalar_value
        in_switch.io.fw_left(i) := ufi.toBits

        ubi.fromBits(in_switch.io.bw_left(i))
        reg.io.read_reset := ubi.read_reset
        reg.io.vector_read := ubi.vector_read

        in_switch.io.fw_left(i) := reg.io.busy

        ubo.fromBits(out_switch.io.bw_left(i))
        reg.io.write_reset := ubo.write_reset
        reg.io.vector_writedata := ubo.vector_writedata
        reg.io.vector_write := ubo.vector_write
    }

    def connectArithmeticUnit(unit: ArithmeticUnit, ai: Int, bi: Int, ri: Int) {
        val a_ufi = new UnitForwardInput(lanes)
        val a_ubi = new UnitBackwardInput(lanes)
        val b_ufi = new UnitForwardInput(lanes)
        val b_ubi = new UnitBackwardInput(lanes)
        val res_ubo = new UnitBackwardOutput(lanes)

        a_ufi.fromBits(in_switch.io.fw_bottom(ai))
        unit.io.a_vreg_data := a_ufi.vector_readdata
        unit.io.a_vreg_busy := a_ufi.busy
        unit.io.a_scalar_data := a_ufi.scalar_value

        a_ubi.read_reset := unit.io.a_vreg_reset
        a_ubi.vector_read := unit.io.a_vreg_read
        in_switch.io.bw_bottom(ai) := a_ubi.toBits

        b_ufi.fromBits(in_switch.io.fw_bottom(bi))
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
    val multiplier = Module(new MultiplierUnit(lanes))
    val memctrl = Module(new MemoryController(memaddrsize, FloatSize * lanes))
    
    connectArithmeticUnit(adder, 0, 1, 0)
    connectArithmeticUnit(multiplier, 2, 3, 1)

    val mem_ufi = new UnitForwardInput(lanes)
    val mem_ubi = new UnitBackwardInput(lanes)
    val mem_ubo = new UnitBackwardOutput(lanes)

    mem_ufi.fromBits(in_switch.io.fw_bottom(4))
    memctrl.io.reg_readdata := mem_ufi.vector_readdata
    memctrl.io.reg_busy := mem_ufi.busy

    mem_ubi.read_reset := memctrl.io.reg_read_reset
    mem_ubi.vector_read := memctrl.io.reg_read
    in_switch.io.bw_bottom(4) := mem_ubi.toBits

    mem_ubo.vector_writedata := memctrl.io.reg_writedata
    mem_ubo.vector_write := memctrl.io.reg_write
    mem_ubo.write_reset := memctrl.io.reg_write_reset
    out_switch.io.bw_bottom(2) := mem_ubo.toBits

    memctrl.io.local_init_done := io.local_init_done
    memctrl.io.avl_waitrequest_n := io.avl_waitrequest_n
    memctrl.io.avl_readdatavalid := io.avl_readdatavalid
    memctrl.io.avl_readdata := io.avl_readdata

    io.avl_address := memctrl.io.avl_address
    io.avl_writedata := memctrl.io.avl_writedata
    io.avl_read := memctrl.io.avl_read
    io.avl_write := memctrl.io.avl_write
}
