package Creek

import Chisel._

class RegisterSet(depth: Int, bitwidth: Int) extends Module {
    val addr_size = log2Up(depth)
    val io = new Bundle {
        val scalar_writeaddr = UInt(INPUT, 2)
        val scalar_writedata = UInt(INPUT, addr_size)
        val scalar_write = Bool(INPUT)
        val reset = Bool(INPUT)
        val vector_readdata = UInt(OUTPUT, bitwidth)
        val vector_writedata = UInt(INPUT, bitwidth)
        val vector_write = Bool(INPUT)
        val busy = Bool(OUTPUT)
    }

    val start = Reg(UInt(width = addr_size))
    val step = Reg(UInt(width = addr_size))
    val count = Reg(UInt(width = addr_size))

    val curaddr = Reg(UInt(width = addr_size))
    val curcount = Reg(UInt(width = addr_size))

    when (io.scalar_write) {
        switch (io.scalar_writeaddr) {
            is(UInt(0)) { start := io.scalar_writedata }
            is(UInt(1)) { step  := io.scalar_writedata }
            is(UInt(2)) { count := io.scalar_writedata }
        }
    }

    io.busy := curcount != UInt(0)

    val mem = Mem(UInt(width = bitwidth), depth)

    when (io.reset) {
        curcount := count
        curaddr := start
    } .elsewhen (curcount != UInt(0)) {
        curcount := curcount - UInt(1)
        when (io.vector_write) {
            mem(curaddr) := io.vector_writedata
        }
        curaddr := curaddr + step
    }

    io.vector_readdata := mem(curaddr)
}

class RegisterSetTest(c: RegisterSet) extends Tester(c) {
    val writestart = rnd.nextInt(10)
    val writestep = rnd.nextInt(4) + 1
    val writecount = 20 + 2 * rnd.nextInt(10)

    val readstart = writestart + writestep
    val readstep = 2 * writestep
    val readcount = writecount / 2

    val writevals = (0 until writecount).map {
        i => rnd.nextInt(1 << 16)
    }.toArray

    poke(c.io.scalar_write, 1)
    poke(c.io.vector_writedata, 0)
    poke(c.io.vector_write, 0)
    poke(c.io.reset, 0)

    poke(c.io.scalar_writeaddr, 0)
    poke(c.io.scalar_writedata, writestart)
    step(1)

    poke(c.io.scalar_writeaddr, 1)
    poke(c.io.scalar_writedata, writestep)
    step(1)

    poke(c.io.scalar_writeaddr, 2)
    poke(c.io.scalar_writedata, writecount)
    step(1)

    poke(c.io.scalar_write, 0)
    poke(c.io.reset, 1)
    step(1)

    poke(c.io.vector_write, 1)
    poke(c.io.reset, 0)

    for (value <- writevals) {
        poke(c.io.vector_writedata, value)
        step(1)
        expect(c.io.busy, 1)
    }

    poke(c.io.vector_write, 0)
    step(1)
    expect(c.io.busy, 0)

    poke(c.io.scalar_write, 1)
    poke(c.io.scalar_writeaddr, 0)
    poke(c.io.scalar_writedata, readstart)
    step(1)

    poke(c.io.scalar_writeaddr, 1)
    poke(c.io.scalar_writedata, readstep)
    step(1)

    poke(c.io.scalar_writeaddr, 2)
    poke(c.io.scalar_writedata, readcount)
    step(1)

    val readvals = (0 until readcount).map {
        i => writevals(1 + 2 * i)
    }

    poke(c.io.reset, 1)
    step(1)
    poke(c.io.reset, 0)

    for (value <- readvals) {
        step(1)
        expect(c.io.busy, 1)
        expect(c.io.vector_readdata, value)
    }

    step(1)
    expect(c.io.busy, 0)
}
