package Creek

import Chisel._
import ChiselFloat.FPAdd32
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt, floatAdd}
import Creek.Constants.FloatSize

class AdderUnit(lanes: Int)
        extends ArithmeticUnit(lanes) {

    val adder_latency = 3

    val reset_shifted = ShiftRegister(io.reset, adder_latency)
    val busy_shifted = ShiftRegister(io.a_vreg_busy, adder_latency - 1)

    io.res_vreg_reset := reset_shifted

    when (reset_shifted) {
        write_en := Bool(true)
    } .elsewhen (!busy_shifted) {
        write_en := Bool(false)
    }

    val bmask = Cat(io.invert_b, UInt(0, FloatSize - 1))

    for (i <- 0 until lanes) {
        val adder = Module(new FPAdd32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        adder.io.a := actual_a_value(start_pos, end_pos)
        adder.io.b := actual_b_value(start_pos, end_pos) ^ bmask
        results(i) := adder.io.res
    }
}

class AdderUnitTest(c: AdderUnit) extends Tester(c) {
    def randomFloats(i: Int, n: Int): Seq[Float] = {
        (0 until n).map {
            _ => rnd.nextFloat() * 10000.0f - 5000.0f
        }
    }

    def checkOperation(abits: IndexedSeq[BigInt], bbits: IndexedSeq[BigInt],
            resbits: IndexedSeq[BigInt]) {

        val num_values = abits.length

        poke(c.io.reset, 1)
        poke(c.io.a_vreg_busy, 0)
        poke(c.io.b_vreg_busy, 0)
        poke(c.io.res_vreg_busy, 0)
        step(1)

        poke(c.io.reset, 0)
        poke(c.io.a_vreg_busy, 1)
        poke(c.io.b_vreg_busy, 1)

        for (i <- 0 until num_values) {
            poke(c.io.a_vreg_data, abits(i))
            poke(c.io.b_vreg_data, bbits(i))
            step(1)

            expect(c.io.busy, 1)

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

    poke(c.io.use_scalar, 0)

    checkOperation(abits, bbits, resbits)

    val ascalar = rnd.nextFloat() * 10000.0f - 5000.0f
    val results2 = avalues.map {
        valueset => valueset.map {
            value => floatAdd(value, ascalar)
        }
    }

    val ascalbits = floatToBigInt(ascalar)
    val resbits2 = results2.map(floatsToBigInt)

    poke(c.io.use_scalar, 1)
    poke(c.io.a_scalar_data, ascalbits)

    checkOperation(abits, bbits, resbits2)

    val results3 = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => floatAdd(numpair._1, -numpair._2)
        }
    }
    val resbits3 = results3.map(floatsToBigInt)

    poke(c.io.use_scalar, 0)
    poke(c.io.invert_b, 1)

    checkOperation(abits, bbits, resbits3)
}
