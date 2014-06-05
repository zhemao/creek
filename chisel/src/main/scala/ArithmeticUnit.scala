package Creek

import Chisel._
import Creek.Constants.FloatSize

class ArithmeticUnit(val lanes: Int, val latency: Int) extends Module {
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
        val invert_b = Bool(INPUT)

        val res_vreg_reset = Bool(OUTPUT)
        val res_vreg_data = Bits(OUTPUT, FloatSize * lanes)
        val res_vreg_write = Bool(OUTPUT)
        val res_vreg_busy = Bool(INPUT)
    }

    val using_b = !io.use_scalar && !io.double_a
    val use_scalar_sync = Reg(Bool())
    val double_a_sync = Reg(Bool())
    val invert_b_sync = Reg(Bool())
    val using_b_sync = !use_scalar_sync && !double_a_sync

    val scalar_reg = Reg(UInt(width = FloatSize))
    val repeated_scalar = Fill(lanes, scalar_reg)
    val actual_a_value = Mux(use_scalar_sync, repeated_scalar, io.a_vreg_data)
    val actual_b_value = Mux(using_b_sync, io.b_vreg_data, io.a_vreg_data)

    val a_value_reg = Reg(next = actual_a_value)
    val b_value_reg = Reg(next = actual_b_value)

    when (io.reset) {
        use_scalar_sync := io.use_scalar
        double_a_sync := io.double_a
        invert_b_sync := io.invert_b
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
    val res_value_reg = Reg(next = Cat(results.toSeq))

    io.res_vreg_data := res_value_reg

    val reset_shifted = ShiftRegister(io.reset, latency)
    val busy_shifted = ShiftRegister(io.a_vreg_busy, latency - 1)

    io.res_vreg_reset := reset_shifted

    when (reset_shifted) {
        write_en := Bool(true)
    } .elsewhen (!busy_shifted) {
        write_en := Bool(false)
    }
}

class ArithmeticUnitTest[T <: ArithmeticUnit](c: T) extends Tester(c) {
    def randomFloats(n: Int): Seq[Float] = {
        Array.fill(n){ rnd.nextFloat() * 10000.0f - 5000.0f }
    }

    def checkOperation(abits: IndexedSeq[BigInt], bbits: IndexedSeq[BigInt],
            resbits: IndexedSeq[BigInt], latency: Int) {

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

            if (i == latency - 1) {
                expect(c.io.res_vreg_reset, 1)
                poke(c.io.res_vreg_busy, 1)
                expect(c.io.res_vreg_write, 0)
            } else if (i >= latency) {
                expect(c.io.res_vreg_write, 1)
                expect(c.io.res_vreg_data, resbits(i - latency))
                expect(c.io.res_vreg_reset, 0)
            } else {
                expect(c.io.res_vreg_write, 0)
                expect(c.io.res_vreg_reset, 0)
            }
        }

        poke(c.io.a_vreg_busy, 0)
        poke(c.io.b_vreg_busy, 0)

        for (i <- 0 until latency) {
            val index = num_values - latency + i
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
}
