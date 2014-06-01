package Creek

import Chisel._
import ChiselFloat.FPMult32
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt}
import Creek.Constants.FloatSize

class MultiplierUnit(lanes: Int)
        extends ArithmeticUnit(lanes) {
    val reset_buffer = Reg(next = io.reset)
    io.res_vreg_reset := reset_buffer

    when (reset_buffer) {
        write_en := Bool(true)
    } .elsewhen (!io.a_vreg_busy) {
        write_en := Bool(false)
    }

    for (i <- 0 until lanes) {
        val multiplier = Module(new FPMult32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        multiplier.io.a := io.a_vreg_data(start_pos, end_pos)
        multiplier.io.b := actual_b_value(start_pos, end_pos)
        results(i) := multiplier.io.res
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
    poke(c.io.double_a, 0)
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
    expect(c.io.a_vreg_reset, 0)
    expect(c.io.b_vreg_reset, 0)

    for (i <- 1 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        poke(c.io.b_vreg_data, bbits(i))
        step(1)

        expect(c.io.busy, 1)
        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_reset, 0)
        expect(c.io.a_vreg_reset, 0)
        expect(c.io.b_vreg_reset, 0)
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

    val ascalar = rnd.nextFloat() * 10000.0f - 5000.0f
    val results2 = avalues.map {
        valueset => valueset.map {
            value => value * ascalar
        }
    }

    val ascalbits = floatToBigInt(ascalar)
    val resbits2 = results2.map(floatsToBigInt)

    poke(c.io.reset, 1)
    poke(c.io.use_scalar, 1)
    poke(c.io.a_vreg_data, 0)
    poke(c.io.b_vreg_data, 0)
    poke(c.io.a_scalar_data, ascalbits)
    step(1)

    expect(c.io.a_vreg_reset, 1)
    expect(c.io.b_vreg_reset, 0)

    poke(c.io.reset, 0)
    poke(c.io.a_vreg_busy, 1)
    poke(c.io.a_vreg_data, abits(0))
    step(1)

    expect(c.io.busy, 1)
    expect(c.io.res_vreg_reset, 1)
    expect(c.io.res_vreg_write, 0)
    expect(c.io.a_vreg_reset, 0)
    expect(c.io.b_vreg_reset, 0)

    for (i <- 1 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        step(1)

        expect(c.io.busy, 1)
        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_reset, 0)
        expect(c.io.a_vreg_reset, 0)
        expect(c.io.b_vreg_reset, 0)
        expect(c.io.res_vreg_data, resbits2(i - 1))
    }

    poke(c.io.a_vreg_busy, 0)
    poke(c.io.b_vreg_busy, 0)

    step(1)
    expect(c.io.res_vreg_write, 1)
    expect(c.io.res_vreg_data, resbits2(num_values - 1))

    step(1)
    expect(c.io.res_vreg_write, 0)
    poke(c.io.res_vreg_busy, 0)
    step(1)
    expect(c.io.busy, 0)

    val results3 = avalues.map {
        valueset => valueset.map {
            value => value * value
        }
    }
    val resbits3 = results3.map(floatsToBigInt)

    poke(c.io.reset, 1)
    poke(c.io.use_scalar, 0)
    poke(c.io.double_a, 1)
    poke(c.io.a_vreg_busy, 1)
    poke(c.io.a_vreg_data, 0)
    step(1)

    expect(c.io.a_vreg_reset, 1)
    expect(c.io.b_vreg_reset, 0)
    expect(c.io.a_vreg_read, 1)
    expect(c.io.b_vreg_read, 0)

    poke(c.io.reset, 0)
    poke(c.io.a_vreg_busy, 1)
    poke(c.io.a_vreg_data, abits(0))
    step(1)

    for (i <- 1 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        step(1)

        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_data, resbits3(i - 1))
    }

    poke(c.io.a_vreg_busy, 0)
    step(1)
    expect(c.io.res_vreg_write, 1)
    expect(c.io.res_vreg_data, resbits3(num_values - 1))

    step(1)
    expect(c.io.res_vreg_write, 0)
    poke(c.io.res_vreg_busy, 0)
    step(1)
    expect(c.io.busy, 0)
}
