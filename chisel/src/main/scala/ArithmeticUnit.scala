package Creek

import Chisel._

class ArithmeticUnit(val lanes: Int, val memdepth: Int) extends Module {
    val FloatSize = 32
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

    io.a_vreg_reset := io.reset
    io.b_vreg_reset := io.reset && using_b
    io.a_vreg_read := Bool(true)
    io.b_vreg_read := using_b

    val write_en = Reg(Bool())
    io.res_vreg_write := write_en
    io.busy := io.a_vreg_busy || io.res_vreg_busy ||
            (io.b_vreg_busy && using_b)

    val scalar_reg = Reg(next = io.a_scalar_data)
    val repeated_scalar = Fill(lanes, scalar_reg)
    val actual_b_value = Mux(io.use_scalar, repeated_scalar,
        Mux(io.double_a, io.a_vreg_data, io.b_vreg_data))
    val results = Vec.fill(lanes) { UInt(width = FloatSize) }

    io.res_vreg_data := Cat(results.toSeq)
}
