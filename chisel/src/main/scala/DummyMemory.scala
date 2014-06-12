package Creek

import Chisel._

class DummyMemory(addrsize: Int, datawidth: Int)  extends Module {
    val io = new Bundle {
        val avl_ready = Bool(OUTPUT)
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

    io.avl_ready := (state != finishing)
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
