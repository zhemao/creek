package Creek

import Chisel._
import scala.util.Random

class DummyRegister(numvalues: Int, datawidth: Int) extends Module {
    val io = new Bundle {
        val read_reset = Bool(INPUT)
        val write_reset = Bool(INPUT)
        val read = Bool(INPUT)
        val write = Bool(INPUT)
        val readdata = UInt(OUTPUT, datawidth)
        val writedata = UInt(INPUT, datawidth)
        val busy = Bool(OUTPUT)

        val correct = Bits(OUTPUT, numvalues)
    }

    val addrSize = log2Up(numvalues)
    val rnd = new Random()

    val address = Reg(UInt(width = addrSize))
    val reference = Vec.fill(numvalues) {
        UInt(rnd.nextInt(), datawidth)
    }

    io.readdata := reference(address)

    val correct = Reg(init = UInt(0, numvalues))
    io.correct := correct

    val busy = Reg(Bool())
    io.busy := busy

    when (io.read_reset || io.write_reset) {
        address := UInt(0)
        busy := Bool(true)
    } .elsewhen (io.read) {
        when (address === UInt(numvalues - 1)) {
            busy := Bool(false)
        } .otherwise {
            address := address + UInt(1)
        }
    } .elsewhen (io.write) {
        correct := correct |
            ((io.writedata === reference(address)) << address)
        when (address === UInt(numvalues - 1)) {
            busy := Bool(false)
        } .otherwise {
            address := address + UInt(1)
        }
    }
}

class DummyMemory(addrsize: Int, datawidth: Int)  extends Module {
    val io = new Bundle {
        val avl_waitrequest_n = Bool(OUTPUT)
        val avl_address = UInt(INPUT, addrsize)
        val avl_readdatavalid = Bool(OUTPUT)
        val avl_readdata = UInt(OUTPUT, datawidth)
        val avl_writedata = UInt(INPUT, datawidth)
        val avl_read = Bool(INPUT)
        val avl_write = Bool(INPUT)
    }

    val delay = 5

    val delay_counter = Reg(init = UInt(0, 3))

    val readdata = Reg(UInt(width = datawidth))
    io.avl_readdata := readdata

    val mem = Mem(UInt(width = datawidth), 1 << addrsize)
    val (idle :: reading :: writing :: finishing :: Nil) = Enum(UInt(), 4)
    val state = Reg(init = idle)

    val datavalid = Reg(init = Bool(false))

    io.avl_waitrequest_n := (state != finishing)
    io.avl_readdatavalid := datavalid

    switch (state) {
        is(idle) {
            delay_counter := UInt(delay)
            when (io.avl_read) {
                state := reading
            } .elsewhen (io.avl_write) {
                state := writing
            }
        }
        is (reading) {
            state := finishing
            readdata := mem(io.avl_address)
            datavalid := Bool(true)
        }
        is (writing) {
            state := finishing
            mem(io.avl_address) := io.avl_writedata
        }
        is (finishing) {
            datavalid := Bool(false)
            when (delay_counter === UInt(0)) {
                state := idle
            } .otherwise {
                delay_counter := delay_counter - UInt(1)
            }
        }
    }
}

class MemoryTest extends Module {
    val AddrSize = 4
    val DataWidth = 128
    val NumValues = 4

    val io = new Bundle {
        val local_init_done = Bool(INPUT)
        val correct = Bits(OUTPUT, NumValues)
        val done = Bool(OUTPUT)
    }

    val dummymem = Module(new DummyMemory(AddrSize, DataWidth))
    val memctrl = Module(new MemoryController(AddrSize, DataWidth))

    memctrl.io.local_init_done := io.local_init_done
    memctrl.io.avl_waitrequest_n := dummymem.io.avl_waitrequest_n
    dummymem.io.avl_address := memctrl.io.avl_address
    memctrl.io.avl_readdatavalid := dummymem.io.avl_readdatavalid
    memctrl.io.avl_readdata := dummymem.io.avl_readdata
    dummymem.io.avl_writedata := memctrl.io.avl_writedata
    dummymem.io.avl_read := memctrl.io.avl_read
    dummymem.io.avl_write := memctrl.io.avl_write
    memctrl.io.start_addr := UInt(0)
    memctrl.io.addr_step := UInt(1)
    memctrl.io.transfer_count := UInt(NumValues)

    val dummyreg = Module(new DummyRegister(NumValues, DataWidth))
    dummyreg.io.read_reset := memctrl.io.reg_read_reset
    dummyreg.io.write_reset := memctrl.io.reg_write_reset
    dummyreg.io.read := memctrl.io.reg_read
    dummyreg.io.write := memctrl.io.reg_write
    dummyreg.io.writedata := memctrl.io.reg_writedata
    memctrl.io.reg_readdata := dummyreg.io.readdata
    memctrl.io.reg_busy := dummyreg.io.busy
    io.correct := dummyreg.io.correct

    val (initial :: begin_write :: wait_write ::
         begin_read :: wait_read :: finished :: Nil) =
        Enum(UInt(), 6)
    val state = Reg(init = initial)

    memctrl.io.start_read := (state === begin_read)
    memctrl.io.start_write := (state === begin_write)
    io.done := (state === finished)

    val wait_counter = Reg(UInt(width = 2))

    switch (state) {
        is (initial) {
            when (memctrl.io.ready) {
                state := begin_write
            }
        }
        is (begin_write) {
            state := wait_write
            wait_counter := UInt(3)
        }
        is (wait_write) {
            when (wait_counter != UInt(0)) {
                wait_counter := wait_counter - UInt(1)
            } .elsewhen (memctrl.io.ready) {
                state := begin_read
            }
        }
        is (begin_read) {
            state := wait_read
            wait_counter := UInt(3)
        }
        is (wait_read) {
            when (wait_counter != UInt(0)) {
                wait_counter := wait_counter - UInt(1)
            } .elsewhen (!dummyreg.io.busy) {
                state := finished
            }
        }
    }
}

class MemoryTestTest(c: MemoryTest) extends Tester(c) {
    poke(c.io.local_init_done, 0)
    step(1)
    poke(c.io.local_init_done, 1)
    step(100)
    expect(c.io.done, 1)
    expect(c.io.correct, 15)
}
