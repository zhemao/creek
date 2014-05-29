package Creek

import Chisel._
import scala.math.ceil

class RegisterSet(depth: Int, vwidth: Int, swidth: Int) extends Module {
    val addr_size = log2Up(depth)
    val sbytes = ceil(swidth / 8.0).toInt

    val io = new Bundle {
        val scalar_writeaddr = UInt(INPUT, 2)
        val scalar_writedata = UInt(INPUT, swidth)
        val scalar_write = Bool(INPUT)
        val scalar_byteenable = Bits(INPUT, sbytes)
        val scalar_value = Bits(OUTPUT, swidth)
        val read_reset = Bool(INPUT)
        val write_reset = Bool(INPUT)
        val copy_reset = Bool(INPUT)
        val vector_readdata = Bits(OUTPUT, vwidth)
        val vector_writedata = Bits(INPUT, vwidth)
        val vector_write = Bool(INPUT)
        val vector_read = Bool(INPUT)
        val busy = Bool(OUTPUT)
    }

    val bitmask = Cat((0 until sbytes).map {
        i => Fill(8, io.scalar_byteenable(i))
        // need to reverse otherwise endianness will be swapped
    }.reverse)

    val start = Reg(UInt(width = swidth))
    val step = Reg(UInt(width = swidth))
    val count = Reg(UInt(width = swidth))
    val scalar = Reg(UInt(width = swidth))

    io.scalar_value := scalar

    val copy_scalar = Reg(UInt(width = swidth))
    val readaddr = Reg(UInt(width = addr_size))
    val readcount = Reg(UInt(width = addr_size))
    val readstep = Reg(UInt(width = addr_size))
    val writeaddr = Reg(UInt(width = addr_size))
    val writecount = Reg(UInt(width = addr_size))
    val writestep = Reg(UInt(width = addr_size))

    val oldvalue = MuxCase(UInt(0, swidth),
        (io.scalar_writeaddr === UInt(0), start) ::
        (io.scalar_writeaddr === UInt(1), step)  ::
        (io.scalar_writeaddr === UInt(2), count) ::
        (io.scalar_writeaddr === UInt(3), scalar) :: Nil)

    val writevalue = (oldvalue & ~bitmask) | (io.scalar_writedata & bitmask)

    when (io.scalar_write) {
        switch (io.scalar_writeaddr) {
            is(UInt(0)) { start := writevalue }
            is(UInt(1)) { step  := writevalue }
            is(UInt(2)) { count := writevalue }
            is(UInt(3)) { scalar := writevalue }
        }
    }

    io.busy := readcount != UInt(0) || writecount != UInt(0)

    val mem = Mem(Bits(width = vwidth), depth, seqRead = true)

    when (io.read_reset) {
        readcount := count(addr_size - 1, 0)
        readaddr := start(addr_size - 1, 0)
        readstep := step(addr_size - 1, 0)
    } .elsewhen (readcount != UInt(0) && io.vector_read) {
        readcount := readcount - UInt(1)
        readaddr := readaddr + readstep
    }

    io.vector_readdata := mem(readaddr)

    val copying = Reg(Bool())
    val writedata = Mux(copying,
        Fill(vwidth / swidth, copy_scalar), io.vector_writedata)

    when (io.copy_reset) {
        writecount := count(addr_size - 1, 0)
        writeaddr := start(addr_size - 1, 0)
        writestep := step(addr_size - 1, 0)
        copying := Bool(true)
        copy_scalar := scalar
    } .elsewhen (io.write_reset) {
        writecount := count(addr_size - 1, 0)
        writeaddr := start(addr_size - 1, 0)
        writestep := step(addr_size - 1, 0)
        copying := Bool(false)
    } .elsewhen (writecount != UInt(0) && (io.vector_write || copying)) {
        mem(writeaddr) := writedata
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
    poke(c.io.scalar_byteenable, 3)

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
    poke(c.io.scalar_writedata, 0)
    step(1)

    poke(c.io.scalar_byteenable, 2)
    poke(c.io.scalar_writedata, scalar_value)
    step(1)

    poke(c.io.scalar_write, 0)
    step(1)
    expect(c.io.scalar_value, scalar_value & 0xff00)

    poke(c.io.scalar_write, 1)
    poke(c.io.scalar_byteenable, 1)
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

    poke(c.io.copy_reset, 1)
    step(1)
    poke(c.io.copy_reset, 0)
    step(1)
    expect(c.io.busy, 1)
    step(writecount + 1)
    expect(c.io.busy, 0)

    poke(c.io.read_reset, 1)
    step(1)
    poke(c.io.read_reset, 0)
    poke(c.io.vector_read, 1)

    val repeated_scalar = (scalar_value.toLong << 16) | scalar_value.toLong

    for (i <- 0 until readcount) {
        step(1)
        expect(c.io.busy, 1)
        expect(c.io.vector_readdata, repeated_scalar)
    }

    poke(c.io.vector_read, 0)
    step(1)
    expect(c.io.busy, 0)
}
