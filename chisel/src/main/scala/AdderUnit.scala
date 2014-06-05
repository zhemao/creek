package Creek

import Chisel._
import ChiselFloat.FPAdd32
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt, floatAdd}
import Creek.Constants.FloatSize

class AdderUnit(lanes: Int) extends ArithmeticUnit(lanes, 5) {
    val bmask = Cat(invert_b_sync, UInt(0, FloatSize - 1))

    for (i <- 0 until lanes) {
        val adder = Module(new FPAdd32())
        val start_pos = FloatSize * (lanes - i) - 1
        val end_pos = FloatSize * (lanes - i - 1)
        adder.io.a := a_value_reg(start_pos, end_pos)
        adder.io.b := b_value_reg(start_pos, end_pos) ^ bmask
        results(i) := adder.io.res
    }
}

class AdderUnitTest(c: AdderUnit) extends ArithmeticUnitTest(c) {
    val num_values = 20

    val avalues = Array.fill(num_values){ randomFloats(c.lanes) }
    val bvalues = Array.fill(num_values){ randomFloats(c.lanes) }

    val results = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => floatAdd(numpair._1, numpair._2)
        }
    }

    val abits = avalues.map(floatsToBigInt)
    val bbits = bvalues.map(floatsToBigInt)
    val resbits = results.map(floatsToBigInt)

    poke(c.io.use_scalar, 0)

    val latency = 5

    checkOperation(abits, bbits, resbits, latency)

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

    checkOperation(abits, bbits, resbits2, latency)

    val results3 = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => floatAdd(numpair._1, -numpair._2)
        }
    }
    val resbits3 = results3.map(floatsToBigInt)

    poke(c.io.use_scalar, 0)
    poke(c.io.invert_b, 1)

    checkOperation(abits, bbits, resbits3, latency)
}
