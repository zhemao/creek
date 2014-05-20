package Creek

import Chisel._
import ChiselFloat.FPMult32
import ChiselFloat.FloatUtils.floatsToBigInt

class MultiplierUnit(val lanes: Int, val memdepth: Int) extends Module {
    val FloatSize = 32
    val io = new Bundle {
        val reset = Bool(INPUT)
        val busy = Bool(OUTPUT)

        val a_vreg_reset = Bool(OUTPUT)
        val a_vreg_data = Bits(INPUT, FloatSize * lanes)
        val a_vreg_busy = Bool(INPUT)

        val b_vreg_reset = Bool(OUTPUT)
        val b_vreg_data = Bits(INPUT, FloatSize * lanes)
        val b_vreg_busy = Bool(INPUT)
        val b_scalar_data = Bits(INPUT, FloatSize)
        val use_scalar = Bool(INPUT)

        val res_vreg_reset = Bool(OUTPUT)
        val res_vreg_data = Bits(OUTPUT, FloatSize * lanes)
        val res_vreg_write = Bool(OUTPUT)
        val res_vreg_busy = Bool(INPUT)
    }

    io.a_vreg_reset := io.reset
    io.b_vreg_reset := io.reset && !io.use_scalar

    val reset_buffer = Reg(next = io.reset)
    io.res_vreg_reset := reset_buffer

    val write_en = Reg(Bool())

    when (reset_buffer) {
        write_en := Bool(true)
    } .elsewhen (!io.a_vreg_busy) {
        write_en := Bool(false)
    }

    io.res_vreg_write := write_en
    io.busy := io.a_vreg_busy || io.res_vreg_busy ||
            (io.b_vreg_busy && !io.use_scalar)

    val repeated_b_scalar = (1 until lanes).foldLeft(io.b_scalar_data) {
        (group, _) => Cat(group, io.b_scalar_data)
    }

    val actual_b_value = Mux(io.use_scalar, repeated_b_scalar, io.b_vreg_data)
    val results = Vec.fill(lanes) { UInt(width = FloatSize) }

    for (i <- 0 until lanes) {
        val multiplier = Module(new FPMult32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        multiplier.io.a := io.a_vreg_data(start_pos, end_pos)
        multiplier.io.b := actual_b_value(start_pos, end_pos)
        results(i) := multiplier.io.res
    }

    io.res_vreg_data := (1 until lanes).foldLeft(results(0)) {
        (group, i) => Cat(group, results(i))
    }
}

class MultiplierUnitTest(c: MultiplierUnit) extends Tester(c) {
    def randomFloats(i: Int, n: Int): Seq[Float] = {
        (0 until n).map {
            _ => rnd.nextFloat() * 10000.0f - 5000.0f
        }
    }
    val num_values = 20

    val avalues = (0 until num_values).map(randomFloats(_, c.lanes))
    val bvalues = (0 until num_values).map(randomFloats(_, c.lanes))

    val results = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => numpair._1 * numpair._2
        }
    }

    val abits = avalues.map(floatsToBigInt)
    val bbits = bvalues.map(floatsToBigInt)
    val resbits = results.map(floatsToBigInt)

    poke(c.io.reset, 1)
    poke(c.io.use_scalar, 0)
    poke(c.io.a_vreg_busy, 0)
    poke(c.io.b_vreg_busy, 0)
    poke(c.io.res_vreg_busy, 0)
    poke(c.io.a_vreg_data, 0)
    poke(c.io.b_vreg_data, 0)
    step(1)

    expect(c.io.a_vreg_reset, 1)
    expect(c.io.b_vreg_reset, 1)

    poke(c.io.reset, 0)
    poke(c.io.a_vreg_busy, 1)
    poke(c.io.b_vreg_busy, 1)

    poke(c.io.a_vreg_data, abits(0))
    poke(c.io.b_vreg_data, bbits(0))
    step(1)

    expect(c.io.busy, 1)
    expect(c.io.res_vreg_reset, 1)
    expect(c.io.res_vreg_write, 0)

    for (i <- 1 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        poke(c.io.b_vreg_data, bbits(i))
        step(1)

        expect(c.io.busy, 1)
        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_reset, 0)
        expect(c.io.res_vreg_data, resbits(i - 1))
    }

    poke(c.io.a_vreg_busy, 0)
    poke(c.io.b_vreg_busy, 0)

    step(1)
    expect(c.io.res_vreg_write, 1)
    expect(c.io.res_vreg_data, resbits(num_values - 1))

    step(1)
    expect(c.io.res_vreg_write, 0)
    poke(c.io.res_vreg_busy, 0)
    step(1)
    expect(c.io.busy, 0)
}
