package Creek

import Chisel._

class SwitchController(nleft: Int, nbottom: Int) extends Module {
    val LeftWidth = log2Up(nleft)
    // +1 because of weirdness with reverse select
    val BottomWidth = log2Up(nbottom + 1)

    val io = new Bundle {
        val select = Vec.fill(nbottom){ UInt(OUTPUT, LeftWidth) }
        val req = Bool(INPUT)
        val ack = Bool(OUTPUT)
        val left_sel = UInt(INPUT, LeftWidth)
        val bottom_sel = UInt(INPUT, BottomWidth)

        val left_busy = Vec.fill(nleft){ Bool(INPUT) }
        val bottom_busy = Vec.fill(nbottom){ Bool(INPUT) }
    }

    val select_reg = Vec.fill(nbottom) { Reg(UInt(width = LeftWidth)) }
    // we have to reserve 0 here as a sentinel value meaning unassigned
    // so we +1 going in and -1 coming out
    val reverse_reg = Vec.fill(nleft) { Reg(UInt(width = BottomWidth)) }

    val left_reg = Reg(UInt(width = LeftWidth))
    val bottom_reg = Reg(UInt(width = BottomWidth))

    io.select := select_reg

    val (ready :: waiting :: acking :: Nil) = Enum(UInt(), 3)
    val state = Reg(init = ready)

    val busy = io.left_busy(left_reg) || io.bottom_busy(bottom_reg)

    val reverse_bottom = reverse_reg(left_reg)
    val reverse_left = select_reg(bottom_reg)

    io.ack := (state === acking)

    switch (state) {
        is(ready) {
            when (io.req) {
                left_reg := io.left_sel
                bottom_reg := io.bottom_sel
                state := waiting
            }
        }
        is (waiting) {
            when (!busy) {
                when (reverse_bottom != UInt(0)) {
                    select_reg(reverse_bottom - UInt(1)) := UInt(0)
                }
                reverse_reg(reverse_left) := UInt(0)
                state := acking
            }
        }
        is (acking) {
            select_reg(bottom_reg) := left_reg
            reverse_reg(left_reg) := bottom_reg + UInt(1)
            state := ready
        }
    }
}

class SwitchControllerTest(c: SwitchController) extends Tester(c) {
    poke(c.io.left_sel, 2)
    poke(c.io.bottom_sel, 0)
    poke(c.io.req, 1)
    step(1)
    poke(c.io.req, 0)
    step(2)
    expect(c.io.ack, 1)
    step(1)
    expect(c.io.ack, 0)
    expect(c.io.select, Array[BigInt](2, 0))

    poke(c.io.left_busy(2), 1)
    poke(c.io.bottom_busy(0), 1)
    step(1)

    poke(c.io.left_sel, 1)
    poke(c.io.bottom_sel, 1)
    poke(c.io.req, 1)
    step(1)
    poke(c.io.req, 0)
    step(2)
    expect(c.io.ack, 1)
    step(1)
    expect(c.io.ack, 0)
    expect(c.io.select, Array[BigInt](2, 1))

    poke(c.io.left_busy(1), 1)
    poke(c.io.bottom_busy(1), 1)
    step(1)
    poke(c.io.left_sel, 2)
    poke(c.io.bottom_sel, 1)
    poke(c.io.req, 1)
    step(1)
    poke(c.io.req, 0)
    step(2)
    expect(c.io.ack, 0)

    poke(c.io.left_busy(2), 0)
    poke(c.io.bottom_busy(1), 0)
    step(2)
    expect(c.io.ack, 1)
    step(1)
    expect(c.io.select, Array[BigInt](0, 2))

    poke(c.io.left_sel, 1)
    poke(c.io.bottom_sel, 0)
    poke(c.io.req, 1)
    step(1)
    poke(c.io.req, 0)
    step(2)
    expect(c.io.ack, 0)

    poke(c.io.left_busy(1), 0)
    poke(c.io.bottom_busy(0), 0)
    step(2)
    expect(c.io.ack, 1)
    step(1)
    expect(c.io.select, Array[BigInt](1, 2))

    poke(c.io.left_sel, 1)
    poke(c.io.bottom_sel, 1)
    poke(c.io.req, 1)
    step(1)
    poke(c.io.req, 0)
    step(2)
    expect(c.io.ack, 1)
    step(1)
    expect(c.io.select, Array[BigInt](0, 1))
}
