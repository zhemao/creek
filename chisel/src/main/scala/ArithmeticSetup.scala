package Creek

import Chisel._
import ChiselFloat.FloatUtils.{floatsToBigInt, floatToBigInt}

class ArithmeticSetup(val lanes: Int, val memdepth: Int)
        extends Module {
    val FloatSize = 32
    val AddrSize = log2Up(memdepth)
    val io = new Bundle {
        val a_scalar_addr = UInt(INPUT, 2)
        val a_scalar_data = UInt(INPUT, AddrSize)
        val a_scalar_write = Bool(INPUT)
        val a_write_reset = Bool(INPUT)
        val a_vector_data = UInt(INPUT, lanes * FloatSize)
        val a_vector_write = Bool(INPUT)
        val a_scalar_val = UInt(INPUT, FloatSize)

        val b_scalar_addr = UInt(INPUT, 2)
        val b_scalar_data = UInt(INPUT, AddrSize)
        val b_scalar_write = Bool(INPUT)
        val b_write_reset = Bool(INPUT)
        val b_vector_data = UInt(INPUT, lanes * FloatSize)
        val b_vector_write = Bool(INPUT)
        val b_use_scalar = Bool(INPUT)

        val res_scalar_addr = UInt(INPUT, 2)
        val res_scalar_data = UInt(INPUT, AddrSize)
        val res_scalar_write = Bool(INPUT)
        val res_read_reset = Bool(INPUT)
        val res_vector_read = Bool(INPUT)
        val res_vector_data = UInt(OUTPUT, lanes * FloatSize)

        val unit_reset = Bool(INPUT)
        val unit_busy = Bool(OUTPUT)
    }

    val arset = Module(new RegisterSet(memdepth, lanes * FloatSize, FloatSize))
    arset.io.scalar_writeaddr := io.a_scalar_addr
    arset.io.scalar_writedata := io.a_scalar_data
    arset.io.scalar_write := io.a_scalar_write
    arset.io.write_reset := io.a_write_reset
    arset.io.vector_writedata := io.a_vector_data
    arset.io.vector_write := io.a_vector_write

    val brset = Module(new RegisterSet(memdepth, lanes * FloatSize, FloatSize))
    brset.io.scalar_writeaddr := io.b_scalar_addr
    brset.io.scalar_writedata := io.b_scalar_data
    brset.io.scalar_write := io.b_scalar_write
    brset.io.write_reset := io.b_write_reset
    brset.io.vector_writedata := io.b_vector_data
    brset.io.vector_write := io.b_vector_write

    val resrset = Module(new RegisterSet(memdepth, lanes * FloatSize, FloatSize))
    resrset.io.scalar_writeaddr := io.res_scalar_addr
    resrset.io.scalar_writedata := io.res_scalar_data
    resrset.io.scalar_write := io.res_scalar_write
    resrset.io.read_reset := io.res_read_reset
    resrset.io.vector_read := io.res_vector_read
    io.res_vector_data := resrset.io.vector_readdata

    def connectUnit(unit: ArithmeticUnit) {
        unit.io.reset := io.unit_reset
        io.unit_busy := unit.io.busy
        arset.io.read_reset := unit.io.a_vreg_reset
        unit.io.a_vreg_data := arset.io.vector_readdata
        unit.io.a_vreg_busy := arset.io.busy
        arset.io.vector_read := unit.io.a_vreg_read
        brset.io.read_reset := unit.io.b_vreg_reset
        unit.io.b_vreg_data := brset.io.vector_readdata
        unit.io.b_vreg_busy := brset.io.busy
        brset.io.vector_read := unit.io.b_vreg_read
        unit.io.a_scalar_data := io.a_scalar_val
        unit.io.use_scalar := io.b_use_scalar
        resrset.io.write_reset := unit.io.res_vreg_reset
        resrset.io.vector_writedata := unit.io.res_vreg_data
        resrset.io.vector_write := unit.io.res_vreg_write
        unit.io.res_vreg_busy := resrset.io.busy
    }
}

class ArithmeticSetupTest[T <: ArithmeticSetup](c: T, f: (Float, Float) => Float)
        extends Tester(c) {
    def randomFloats(i: Int, n: Int): Seq[Float] = {
        (0 until n).map {
            _ => rnd.nextFloat() * 10000.0f - 5000.0f
        }
    }

    poke(c.io.a_write_reset, 0)
    poke(c.io.b_write_reset, 0)
    poke(c.io.res_read_reset, 0)
    poke(c.io.a_scalar_write, 0)
    poke(c.io.b_scalar_write, 0)
    poke(c.io.res_scalar_write, 0)
    poke(c.io.unit_reset, 0)

    val num_values = 20
    val scalar_value = rnd.nextFloat() * 10000.0f - 5000.0f

    val scalar_vals = Array[BigInt](0, 1, num_values, floatToBigInt(scalar_value))

    poke(c.io.a_scalar_write, 1)
    poke(c.io.b_scalar_write, 1)
    poke(c.io.res_scalar_write, 1)

    for (i <- 0 until 4) {
        poke(c.io.a_scalar_addr, i)
        poke(c.io.b_scalar_addr, i)
        poke(c.io.res_scalar_addr, i)
        poke(c.io.a_scalar_data, scalar_vals(i))
        poke(c.io.b_scalar_data, scalar_vals(i))
        poke(c.io.res_scalar_data, scalar_vals(i))
        step(1)
    }

    poke(c.io.a_scalar_write, 0)
    poke(c.io.b_scalar_write, 0)
    poke(c.io.res_scalar_write, 0)
    step(1)

    val avalues = (0 until num_values).map(randomFloats(_, c.lanes))
    val bvalues = (0 until num_values).map(randomFloats(_, c.lanes))
    val results = (avalues zip bvalues).map {
        pair => (pair._1 zip pair._2).map {
            numpair => f(numpair._1, numpair._2)
        }
    }

    val abits = avalues.map(floatsToBigInt)
    val bbits = bvalues.map(floatsToBigInt)
    val resbits = results.map(floatsToBigInt)

    poke(c.io.a_write_reset, 1)
    poke(c.io.b_write_reset, 1)
    step(1)

    poke(c.io.a_write_reset, 0)
    poke(c.io.b_write_reset, 0)
    poke(c.io.a_vector_write, 1)
    poke(c.io.b_vector_write, 1)

    for (i <- 0 until num_values) {
        poke(c.io.a_vector_data, abits(i))
        poke(c.io.b_vector_data, bbits(i))
        step(1)
    }

    poke(c.io.a_vector_write, 0)
    poke(c.io.b_vector_write, 0)
    step(1)

    poke(c.io.unit_reset, 1)
    step(1)
    poke(c.io.unit_reset, 0)
    step(1)
    expect(c.io.unit_busy, 1)
    step(num_values + 3)
    expect(c.io.unit_busy, 0)

    poke(c.io.res_read_reset, 1)
    poke(c.io.res_vector_read, 1)
    step(1)
    poke(c.io.res_read_reset, 0)
    step(1)

    for (i <- 0 until num_values) {
        expect(c.io.res_vector_data, resbits(i))
        step(1)
    }
}
