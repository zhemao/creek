package Creek

import Chisel._
import ChiselFloat.FPAdd32
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt, floatAdd}

class AdderUnit(lanes: Int, memdepth: Int)
        extends ArithmeticUnit(lanes, memdepth) {

    val adder_latency = 3

    val reset_shift = Reg(Bits(width = adder_latency))
    reset_shift := Cat(reset_shift(adder_latency - 2, 0), io.reset)
    io.res_vreg_reset := reset_shift(adder_latency - 1)

    val busy_shift = Reg(Bits(width = adder_latency - 1))
    busy_shift := Cat(busy_shift(adder_latency - 3, 0), io.a_vreg_busy)

    when (reset_shift(adder_latency - 1).toBool()) {
        write_en := Bool(true)
    } .elsewhen (!busy_shift(adder_latency - 2).toBool()) {
        write_en := Bool(false)
    }

    for (i <- 0 until lanes) {
        val adder = Module(new FPAdd32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        adder.io.a := io.a_vreg_data(start_pos, end_pos)
        adder.io.b := actual_b_value(start_pos, end_pos)
        results(i) := adder.io.res
    }
}

class AdderUnitTest(c: AdderUnit) extends Tester(c) {
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
            numpair => floatAdd(numpair._1, numpair._2)
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

    for (i <- 0 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        poke(c.io.b_vreg_data, bbits(i))
        step(1)

        expect(c.io.busy, 1)
        expect(c.io.a_vreg_reset, 0)
        expect(c.io.b_vreg_reset, 0)

        if (i == 2) {
            expect(c.io.res_vreg_reset, 1)
            poke(c.io.res_vreg_busy, 1)
            expect(c.io.res_vreg_write, 0)
        } else if (i >= 3) {
            expect(c.io.res_vreg_write, 1)
            expect(c.io.res_vreg_data, resbits(i - 3))
            expect(c.io.res_vreg_reset, 0)
        } else {
            expect(c.io.res_vreg_write, 0)
            expect(c.io.res_vreg_reset, 0)
        }
    }

    poke(c.io.a_vreg_busy, 0)
    poke(c.io.b_vreg_busy, 0)

    for (i <- 0 until 3) {
        val index = num_values - 3 + i
        step(1)
        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_data, resbits(index))
    }

    step(1)
    expect(c.io.res_vreg_write, 0)
    poke(c.io.res_vreg_busy, 0)
    step(1)
    expect(c.io.busy, 0)

    val ascalar = rnd.nextFloat() * 10000.0f - 5000.0f
    val results2 = avalues.map {
        valueset => valueset.map {
            value => floatAdd(value, ascalar)
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

    for (i <- 0 until num_values) {
        poke(c.io.a_vreg_data, abits(i))
        step(1)

        expect(c.io.busy, 1)
        expect(c.io.a_vreg_reset, 0)
        expect(c.io.b_vreg_reset, 0)

        if (i == 2) {
            expect(c.io.res_vreg_reset, 1)
            poke(c.io.res_vreg_busy, 1)
            expect(c.io.res_vreg_write, 0)
        } else if (i >= 3) {
            expect(c.io.res_vreg_write, 1)
            expect(c.io.res_vreg_data, resbits2(i - 3))
            expect(c.io.res_vreg_reset, 0)
        } else {
            expect(c.io.res_vreg_write, 0)
            expect(c.io.res_vreg_reset, 0)
        }
    }

    poke(c.io.a_vreg_busy, 0)
    poke(c.io.b_vreg_busy, 0)

    for (i <- 0 until 3) {
        val index = num_values - 3 + i
        step(1)
        expect(c.io.res_vreg_write, 1)
        expect(c.io.res_vreg_data, resbits2(index))
    }

    step(1)
    expect(c.io.res_vreg_write, 0)
    poke(c.io.res_vreg_busy, 0)
    step(1)
    expect(c.io.busy, 0)
}
