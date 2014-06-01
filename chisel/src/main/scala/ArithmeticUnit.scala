package Creek

import Chisel._
import Creek.Constants.FloatSize

class ArithmeticUnit(val lanes: Int) extends Module {
    val io = new Bundle {
        val reset = Bool(INPUT)
        val busy = Bool(OUTPUT)

        val a_vreg_reset = Bool(OUTPUT)
        val a_vreg_data = Bits(INPUT, FloatSize * lanes)
        val a_vreg_busy = Bool(INPUT)
        val a_scalar_data = Bits(INPUT, FloatSize)
        val a_vreg_read = Bool(OUTPUT)

        val b_vreg_reset = Bool(OUTPUT)
        val b_vreg_data = Bits(INPUT, FloatSize * lanes)
        val b_vreg_busy = Bool(INPUT)
        val b_vreg_read = Bool(OUTPUT)

        val use_scalar = Bool(INPUT)
        val double_a = Bool(INPUT)

        val res_vreg_reset = Bool(OUTPUT)
        val res_vreg_data = Bits(OUTPUT, FloatSize * lanes)
        val res_vreg_write = Bool(OUTPUT)
        val res_vreg_busy = Bool(INPUT)
    }

    val using_b = !io.use_scalar && !io.double_a
    val use_scalar_sync = Reg(Bool())
    val double_a_sync = Reg(Bool())
    val using_b_sync = !use_scalar_sync && !double_a_sync

    val scalar_reg = Reg(UInt(width = FloatSize))
    val repeated_scalar = Fill(lanes, scalar_reg)
    val actual_b_value = Mux(io.use_scalar, repeated_scalar,
        Mux(io.double_a, io.a_vreg_data, io.b_vreg_data))

    when (io.reset) {
        use_scalar_sync := io.use_scalar
        double_a_sync := io.double_a
        scalar_reg := io.a_scalar_data
    }

    io.a_vreg_reset := io.reset
    io.b_vreg_reset := io.reset && using_b
    io.a_vreg_read := Bool(true)
    io.b_vreg_read := using_b_sync

    val write_en = Reg(Bool())
    io.res_vreg_write := write_en
    io.busy := io.a_vreg_busy || io.res_vreg_busy ||
            (io.b_vreg_busy && using_b_sync)

    val results = Vec.fill(lanes) { UInt(width = FloatSize) }

    io.res_vreg_data := Cat(results.toSeq)
}
