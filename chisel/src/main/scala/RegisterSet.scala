package Creek

import Chisel._

class RegisterSet(depth: Int, bitwidth: Int) extends Module {
    val addr_size = log2Up(depth)
    val io = new Bundle {
        val scalar_writeaddr = UInt(INPUT, 2)
        val scalar_writedata = UInt(INPUT, addr_size)
        val scalar_write = Bool(INPUT)
        val scalar_value = Bits(OUTPUT, bitwidth)
        val read_reset = Bool(INPUT)
        val write_reset = Bool(INPUT)
        val vector_readdata = Bits(OUTPUT, bitwidth)
        val vector_writedata = Bits(INPUT, bitwidth)
        val vector_write = Bool(INPUT)
        val vector_read = Bool(INPUT)
        val busy = Bool(OUTPUT)
    }

    val start = Reg(UInt(width = addr_size))
    val step = Reg(UInt(width = addr_size))
    val count = Reg(UInt(width = addr_size))
    val scalar = Reg(UInt(width = addr_size))

    io.scalar_value := scalar

    val readaddr = Reg(UInt(width = addr_size))
    val readcount = Reg(UInt(width = addr_size))
    val readstep = Reg(UInt(width = addr_size))
    val writeaddr = Reg(UInt(width = addr_size))
    val writecount = Reg(UInt(width = addr_size))
    val writestep = Reg(UInt(width = addr_size))

    when (io.scalar_write) {
        switch (io.scalar_writeaddr) {
            is(UInt(0)) { start := io.scalar_writedata }
            is(UInt(1)) { step  := io.scalar_writedata }
            is(UInt(2)) { count := io.scalar_writedata }
            is(UInt(3)) { scalar := io.scalar_writedata }
        }
    }

    io.busy := readcount != UInt(0) || writecount != UInt(0)

    val mem = Mem(Bits(width = bitwidth), depth, seqRead = true)

    when (io.read_reset) {
        readcount := count
        readaddr := start
        readstep := step
    } .elsewhen (readcount != UInt(0) && io.vector_read) {
        readcount := readcount - UInt(1)
        readaddr := readaddr + readstep
    }

    io.vector_readdata := mem(readaddr)

    when (io.write_reset) {
        writecount := count
        writeaddr := start
        writestep := step
    } .elsewhen (writecount != UInt(0) && io.vector_write) {
        mem(writeaddr) := io.vector_writedata
        writeaddr := writeaddr + writestep
        writecount := writecount - UInt(1)
    }
}

class RegisterSetTest(c: RegisterSet) extends Tester(c) {
    val writestart = rnd.nextInt(10)
    val writestep = rnd.nextInt(3) + 1
    val writecount = 20 + 2 * rnd.nextInt(10)

    val readstart = writestart + writestep
    val readstep = 2 * writestep
    val readcount = writecount / 2
    val scalar_value = rnd.nextInt(1 << 16)

    val writevals = (0 until writecount).map {
        i => rnd.nextInt(1 << 16)
    }.toArray

    poke(c.io.scalar_write, 1)
    poke(c.io.vector_writedata, 0)
    poke(c.io.vector_write, 0)
    poke(c.io.read_reset, 0)
    poke(c.io.write_reset, 0)

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
    poke(c.io.write_reset, 1)
    step(1)

    poke(c.io.vector_write, 1)
    poke(c.io.write_reset, 0)

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

    poke(c.io.scalar_writeaddr, 3)
    poke(c.io.scalar_writedata, scalar_value)
    step(1)

    poke(c.io.scalar_write, 0)
    step(1)

    expect(c.io.scalar_value, scalar_value)

    val readvals = (0 until readcount).map {
        i => writevals(1 + 2 * i)
    }

    poke(c.io.read_reset, 1)
    step(1)
    poke(c.io.read_reset, 0)
    poke(c.io.vector_read, 1)

    for (value <- readvals) {
        step(1)
        expect(c.io.busy, 1)
        expect(c.io.vector_readdata, value)
    }

    poke(c.io.vector_read, 0)
    step(1)
    expect(c.io.busy, 0)
}
