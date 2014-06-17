package Creek

import Chisel._
import Creek.Constants.FloatSize
import Creek.Opcode._
import Creek.Instruction._

class CreekController(instr_depth: Int, nregs: Int) extends Module {
    val RealNRegs = 8
    val SelectWidth = log2Up(RealNRegs)
    val ScalarAddrSize = log2Up(RealNRegs) + 2
    val ScalarWidth = FloatSize
    val InstrWidth = 16
    val InstrAddrSize = log2Up(instr_depth)

    val io = new Bundle {
        val pause_n = Bool(INPUT)
        val local_init_done = Bool(INPUT)
        val waiting = Bool(OUTPUT)
        val instr_address = UInt(OUTPUT, InstrAddrSize)
        val instr_data = UInt(INPUT, InstrWidth)

        val input_select = Vec.fill(5) { UInt(OUTPUT, SelectWidth) }
        val output_select = Vec.fill(3) { UInt(OUTPUT, SelectWidth) }

        val reg_read_busy = Vec.fill(RealNRegs) { Bool(INPUT) }
        val reg_write_busy = Vec.fill(RealNRegs) { Bool(INPUT) }
        val reg_copy_reset = Vec.fill(RealNRegs) { Bool(OUTPUT) }

        val adder_reset = Bool(OUTPUT)
        val adder_busy = Bool(INPUT)
        val adder_use_scalar = Bool(OUTPUT)
        val adder_subtract = Bool(OUTPUT)

        val mult_reset = Bool(OUTPUT)
        val mult_busy = Bool(INPUT)
        val mult_use_scalar = Bool(OUTPUT)
        val mult_square = Bool(OUTPUT)

        val mem_ready = Bool(INPUT)
        val mem_start_read = Bool(OUTPUT)
        val mem_start_write = Bool(OUTPUT)

        val scalar_address = UInt(OUTPUT, ScalarAddrSize)
        val scalar_writedata = UInt(OUTPUT, ScalarWidth)
        val scalar_byteenable = UInt(OUTPUT, ScalarWidth / 8)
        val scalar_write = Bool(OUTPUT)

        val cur_pc = UInt(OUTPUT)
        val cur_instr = UInt(OUTPUT)
        val cur_state = UInt(OUTPUT)
    }

    val adder_use_scalar = Reg(Bool())
    val adder_subtract = Reg(Bool())

    io.adder_use_scalar := adder_use_scalar
    io.adder_subtract := adder_subtract

    val mult_use_scalar = Reg(Bool())
    val mult_square = Reg(Bool())

    io.mult_use_scalar := mult_use_scalar
    io.mult_square := mult_square

    val pc = Reg(init = UInt(0, InstrAddrSize))
    val instruction = Reg(UInt(width = InstrWidth))

    io.instr_address := pc

    val (waitInit :: instrFetch :: instrDecode ::
         reqOutputSwitch :: waitOutputSwitch ::
         reqInputSwitch:: waitInputSwitch ::
         waitReg :: writeScalar ::
         startCopy :: startMemRead :: startMemWrite ::
         setAdderB :: setAdderRes :: startAdder ::
         setMultB :: setMultRes :: startMult ::
         Nil) = Enum(UInt(), 18)
    val state = Reg(init = waitInit)
    val nextstate = Reg(init = waitInit)

    io.cur_state := state

    val byteshift = instruction(14, 13)
    val scalar_addr = instruction(12, 8)
    val scalar_value = instruction(7, 0)

    // generate byteenable by shifting 1 to the left by byteshift
    io.scalar_byteenable := UInt(1, 4) << byteshift
    io.scalar_address := scalar_addr
    // generate scalar_value by shifting immediate left by 8x byteshift
    io.scalar_writedata := scalar_value << Cat(byteshift, UInt(0, 3))
    io.scalar_write := (state === writeScalar)

    val sets = !instruction(15).toBool
    val opcode = instruction(14, 11)

    val regaddr1 = instruction(10, 8)
    val regaddr2 = instruction(7, 5)
    val regaddr3 = instruction(4, 2)

    io.waiting := (state === waitReg) && (regaddr1 === UInt(0))

    for (i <- 0 until RealNRegs) {
        io.reg_copy_reset(i) := (state === startCopy) && (regaddr1 === UInt(i))
    }

    io.adder_reset := (state === startAdder)
    io.mult_reset := (state === startMult)
    io.mem_start_read := (state === startMemRead)
    io.mem_start_write := (state === startMemWrite)

    val input_sw_ctrl = Module(new SwitchController(RealNRegs, 5))
    val output_sw_ctrl = Module(new SwitchController(RealNRegs, 3))

    io.input_select := input_sw_ctrl.io.select
    io.output_select := output_sw_ctrl.io.select

    input_sw_ctrl.io.req := (state === reqInputSwitch)
    output_sw_ctrl.io.req := (state === reqOutputSwitch)

    val input_sw_ack = input_sw_ctrl.io.ack
    val output_sw_ack = output_sw_ctrl.io.ack

    input_sw_ctrl.io.left_busy := io.reg_read_busy
    input_sw_ctrl.io.bottom_busy(0) := io.adder_busy
    input_sw_ctrl.io.bottom_busy(1) := io.adder_busy
    input_sw_ctrl.io.bottom_busy(2) := io.mult_busy
    input_sw_ctrl.io.bottom_busy(3) := io.mult_busy
    input_sw_ctrl.io.bottom_busy(4) := !io.mem_ready

    output_sw_ctrl.io.left_busy := io.reg_write_busy
    output_sw_ctrl.io.bottom_busy(0) := io.adder_busy
    output_sw_ctrl.io.bottom_busy(1) := io.mult_busy
    output_sw_ctrl.io.bottom_busy(2) := !io.mem_ready

    val input_left_sel = Reg(UInt(width = log2Up(RealNRegs)))
    val input_bottom_sel = Reg(UInt(width = 3))
    input_sw_ctrl.io.left_sel := input_left_sel
    input_sw_ctrl.io.bottom_sel := input_bottom_sel

    val output_left_sel = Reg(UInt(width = log2Up(RealNRegs)))
    val output_bottom_sel = Reg(UInt(width = 2))
    output_sw_ctrl.io.left_sel := output_left_sel
    output_sw_ctrl.io.bottom_sel := output_bottom_sel

    val reg_busy = io.reg_read_busy(regaddr1) || io.reg_write_busy(regaddr1)

    val cur_pc = Reg(UInt(width = InstrAddrSize))

    io.cur_pc := cur_pc
    io.cur_instr := instruction
    io.cur_state := state

    switch (state) {
        is (waitInit) {
            when (io.local_init_done) {
                state := instrFetch
            }
        }
        is (instrFetch) {
            when (io.pause_n) {
                instruction := io.instr_data
                pc := pc + UInt(1)
                cur_pc := pc
                state := instrDecode
            }
        }
        is (instrDecode) {
            when (sets) {
                state := writeScalar
            } .otherwise {
                switch (opcode) {
                    is (UInt(LOAD)) {
                        output_left_sel := regaddr1
                        output_bottom_sel := UInt(2)
                        state := reqOutputSwitch
                        nextstate := startMemRead
                    }
                    is (UInt(STORE)) {
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(4)
                        state := reqInputSwitch
                        nextstate := startMemWrite
                    }
                    is (UInt(COPY)) {
                        state := startCopy
                    }
                    is (UInt(ADDV)) {
                        adder_use_scalar := Bool(false)
                        adder_subtract := Bool(false)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(0)
                        state := reqInputSwitch
                        nextstate := setAdderB
                    }
                    is (UInt(ADDS)) {
                        adder_use_scalar := Bool(true)
                        adder_subtract := Bool(false)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(0)
                        state := reqInputSwitch
                        nextstate := setAdderRes
                    }
                    is (UInt(SUBV)) {
                        adder_use_scalar := Bool(false)
                        adder_subtract := Bool(true)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(0)
                        state := reqInputSwitch
                        nextstate := setAdderB
                    }
                    is (UInt(SUBS)) {
                        adder_use_scalar := Bool(true)
                        adder_subtract := Bool(true)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(0)
                        state := reqInputSwitch
                        nextstate := setAdderRes
                    }
                    is (UInt(MULTV)) {
                        mult_use_scalar := Bool(false)
                        mult_square := Bool(false)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(2)
                        state := reqInputSwitch
                        nextstate := setMultB
                    }
                    is (UInt(MULTS)) {
                        mult_use_scalar := Bool(true)
                        mult_square := Bool(false)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(2)
                        state := reqInputSwitch
                        nextstate := setMultRes
                    }
                    is (UInt(SQUARE)) {
                        mult_use_scalar := Bool(false)
                        mult_square := Bool(true)
                        input_left_sel := regaddr1
                        input_bottom_sel := UInt(2)
                        state := reqInputSwitch
                        nextstate := setMultRes
                    }
                    is (UInt(WAIT)) {
                        state := waitReg
                    }
                }
            }
        }
        is (reqOutputSwitch) {
            state := waitOutputSwitch
        }
        is (waitOutputSwitch) {
            when (output_sw_ack) {
                state := nextstate
            }
        }
        is (reqInputSwitch) {
            state := waitInputSwitch
        }
        is (waitInputSwitch) {
            when (input_sw_ack) {
                state := nextstate
            }
        }
        is (setAdderB) {
            input_left_sel := regaddr2
            input_bottom_sel := UInt(1)
            state := reqInputSwitch
            nextstate := setAdderRes
        }
        is (setAdderRes) {
            output_left_sel := regaddr3
            output_bottom_sel := UInt(0)
            state := reqOutputSwitch
            nextstate := startAdder
        }
        is (setMultB) {
            input_left_sel := regaddr2
            input_bottom_sel := UInt(3)
            state := reqInputSwitch
            nextstate := setMultRes
        }
        is (setMultRes) {
            output_left_sel := regaddr3
            output_bottom_sel := UInt(1)
            state := reqOutputSwitch
            nextstate := startMult
        }
        is (startAdder, startMult, 
            startMemRead, startMemWrite,
            startCopy, writeScalar) {

            state := instrFetch
        }
        is (waitReg) {
            when (!reg_busy) {
                state := instrFetch
            }
        }
    }
}

class CreekControllerTest(c: CreekController) extends Tester(c) {
    poke(c.io.local_init_done, 1)
    poke(c.io.pause_n, 1)
    step(1)

    // SETS 0 1 32
    poke(c.io.mem_ready, 1)
    poke(c.io.instr_data, constructSetsInstr(0, 1, 32))
    step(3)
    expect(c.io.scalar_address, 1)
    expect(c.io.scalar_writedata, 32)
    expect(c.io.scalar_byteenable, 1)
    expect(c.io.scalar_write, 1)

    // LOAD 2
    poke(c.io.instr_data, constructInstr(0, 2, 0, 0))
    step(6)
    expect(c.io.output_select(2), 2)
    expect(c.io.mem_start_read, 1)

    // STORE 3
    poke(c.io.instr_data, constructInstr(1, 3, 0, 0))
    step(6)
    expect(c.io.input_select(4), 3)
    expect(c.io.mem_start_write, 1)

    // COPYS 1
    poke(c.io.instr_data, constructInstr(2, 1, 0, 0))
    step(3)
    expect(c.io.reg_copy_reset(1), 1)

    // ADDV 1 2 3
    poke(c.io.instr_data, constructInstr(4, 1, 2, 3))
    step(14)
    expect(c.io.adder_reset, 1)
    expect(c.io.adder_use_scalar, 0)
    expect(c.io.adder_subtract, 0)
    expect(c.io.input_select(0), 1)
    expect(c.io.input_select(1), 2)
    expect(c.io.output_select(0), 3)

    // ADDS 2 4
    poke(c.io.instr_data, constructInstr(5, 2, 0, 4))
    step(10)
    expect(c.io.adder_reset, 1)
    expect(c.io.adder_use_scalar, 1)
    expect(c.io.adder_subtract, 0)
    expect(c.io.input_select(0), 2)
    expect(c.io.output_select(0), 4)

    // SUBV 2 3 4
    poke(c.io.instr_data, constructInstr(6, 2, 3, 4))
    step(14)
    expect(c.io.adder_reset, 1)
    expect(c.io.adder_use_scalar, 0)
    expect(c.io.adder_subtract, 1)
    expect(c.io.input_select(0), 2)
    expect(c.io.input_select(1), 3)
    expect(c.io.output_select(0), 4)

    // SUBS 3 1
    poke(c.io.instr_data, constructInstr(7, 3, 0, 1))
    step(10)
    expect(c.io.adder_reset, 1)
    expect(c.io.adder_use_scalar, 1)
    expect(c.io.adder_subtract, 1)
    expect(c.io.input_select(0), 3)
    expect(c.io.output_select(0), 1)

    // MULTV 5 3 1
    poke(c.io.instr_data, constructInstr(8, 5, 3, 1))
    step(14)
    expect(c.io.mult_reset, 1)
    expect(c.io.mult_use_scalar, 0)
    expect(c.io.mult_square, 0)
    expect(c.io.input_select(2), 5)
    expect(c.io.input_select(3), 3)
    expect(c.io.output_select(1), 1)

    // MULTS 6 2
    poke(c.io.instr_data, constructInstr(9, 6, 0, 2))
    step(10)
    expect(c.io.mult_reset, 1)
    expect(c.io.mult_use_scalar, 1)
    expect(c.io.mult_square, 0)
    expect(c.io.input_select(2), 6)
    expect(c.io.output_select(1), 2)

    // SQUARE 1 5
    poke(c.io.instr_data, constructInstr(10, 1, 0, 5))
    step(10)
    expect(c.io.mult_reset, 1)
    expect(c.io.mult_use_scalar, 0)
    expect(c.io.mult_square, 1)
    expect(c.io.input_select(2), 1)
    expect(c.io.output_select(1), 5)

    expect(c.io.instr_address, 11)

    // WAIT 3
    poke(c.io.instr_data, constructInstr(12, 2, 0, 0))
    poke(c.io.reg_read_busy(2), 1)
    step(2)
    expect(c.io.instr_address, 12)
    step(4)
    expect(c.io.instr_address, 12)
    poke(c.io.reg_read_busy(2), 0)
    step(3)
    expect(c.io.instr_address, 13)
}
