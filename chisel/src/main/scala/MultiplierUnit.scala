package Creek

import Chisel._
import ChiselFloat.FPMult32
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt}
import Creek.Constants.FloatSize

class MultiplierUnit(lanes: Int) extends ArithmeticUnit(lanes, 3) {
    for (i <- 0 until lanes) {
        val multiplier = Module(new FPMult32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        multiplier.io.a := a_value_reg(start_pos, end_pos)
        multiplier.io.b := b_value_reg(start_pos, end_pos)
        results(i) := multiplier.io.res
    }
}

class MultiplierUnitTest(c: MultiplierUnit) extends ArithmeticUnitTest(c) {
    val num_values = 20
    val latency = 3

    val avalues = Array.fill(num_values){ randomFloats(c.lanes) }
    val bvalues = Array.fill(num_values){ randomFloats(c.lanes) }

    val results = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => numpair._1 * numpair._2
        }
    }

    val abits = avalues.map(floatsToBigInt)
    val bbits = bvalues.map(floatsToBigInt)
    val resbits = results.map(floatsToBigInt)

    poke(c.io.use_scalar, 0)
    poke(c.io.double_a, 0)

    checkOperation(abits, bbits, resbits, latency)

    val ascalar = rnd.nextFloat() * 10000.0f - 5000.0f
    val results2 = avalues.map {
        valueset => valueset.map {
            value => value * ascalar
        }
    }

    val ascalbits = floatToBigInt(ascalar)
    val resbits2 = results2.map(floatsToBigInt)

    poke(c.io.use_scalar, 1)
    poke(c.io.a_scalar_data, ascalbits)

    checkOperation(abits, bbits, resbits2, latency)

    val results3 = avalues.map {
        valueset => valueset.map {
            value => value * value
        }
    }
    val resbits3 = results3.map(floatsToBigInt)

    poke(c.io.use_scalar, 0)
    poke(c.io.double_a, 1)

    checkOperation(abits, bbits, resbits3, latency)
}
