package Creek

import Chisel._
import scala.math.ceil

class MemoryControllerRegisters(regwidth: Int) extends Module {
    val RegAddrSize = 2
    val NumBytes = ceil(regwidth / 8.0).toInt

    val io = new Bundle {
        val writeaddr = UInt(INPUT, RegAddrSize)
        val writedata = UInt(INPUT, regwidth)
        val write = Bool(INPUT)
        val byteenable = UInt(INPUT, NumBytes)

        val start = UInt(OUTPUT, regwidth)
        val step = UInt(OUTPUT, regwidth)
        val count = UInt(OUTPUT, regwidth)
    }

    val bitmask = Cat((0 until NumBytes).map {
        i => Fill(8, io.byteenable(i))
        // need to reverse otherwise endianness will be swapped
    }.reverse)

    val start = Reg(init = UInt(0, regwidth))
    val step = Reg(init = UInt(1, regwidth))
    val count = Reg(init = Fill(regwidth, UInt(1, 1)))

    val oldvalue = MuxCase(UInt(0, regwidth),
        (io.writeaddr === UInt(0), start) ::
        (io.writeaddr === UInt(1), step) ::
        (io.writeaddr === UInt(2), count) :: Nil)
    val writevalue = (oldvalue & ~bitmask) | (io.writedata & bitmask)

    when (io.write) {
        switch (io.writeaddr) {
            is(UInt(0)) { start := writevalue }
            is(UInt(1)) { step  := writevalue }
            is(UInt(2)) { count := writevalue }
        }
    }

    io.start := start
    io.step  := step
    io.count := count
}

class MemoryControllerRegistersTest(c: MemoryControllerRegisters)
        extends Tester(c) {

    val startval = rnd.nextInt(1 << 16)
    val stepval  = rnd.nextInt(1 << 16)
    val countval = rnd.nextInt(1 << 16)

    poke(c.io.write, 1)
    poke(c.io.writeaddr, 0)
    poke(c.io.writedata, startval)
    poke(c.io.byteenable, 2)
    step(1)

    poke(c.io.writeaddr, 1)
    poke(c.io.writedata, stepval)
    poke(c.io.byteenable, 1)
    step(1)

    poke(c.io.writeaddr, 2)
    poke(c.io.writedata, countval)
    poke(c.io.byteenable, 3)
    step(1)

    poke(c.io.write, 0)
    step(1)

    expect(c.io.start, startval & 0xff00)
    expect(c.io.step, stepval & 0x00ff)
    expect(c.io.count, countval)
}
