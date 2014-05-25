package Creek

import Chisel._

class MemoryController(addrsize: Int, datawidth: Int) extends Module {
    val io = new Bundle {
        val local_init_done = Bool(INPUT)
        val avl_waitrequest_n = Bool(INPUT)
        val avl_address = UInt(OUTPUT, addrsize)
        val avl_readdatavalid = Bool(INPUT)
        val avl_readdata = UInt(INPUT, datawidth)
        val avl_writedata = UInt(OUTPUT, datawidth)
        val avl_read = Bool(OUTPUT)
        val avl_write = Bool(OUTPUT)

        val reg_read_reset = Bool(OUTPUT)
        val reg_write_reset = Bool(OUTPUT)
        val reg_readdata = UInt(INPUT, datawidth)
        val reg_writedata = UInt(OUTPUT, datawidth)
        val reg_read = Bool(OUTPUT)
        val reg_write = Bool(OUTPUT)
        val reg_busy = Bool(INPUT)

        val start_read = Bool(INPUT)
        val start_write = Bool(INPUT)
        val start_addr = UInt(INPUT, addrsize)
        val addr_step = UInt(INPUT, addrsize)
        val transfer_count = UInt(INPUT, addrsize)
    }

    val avl_address = Reg(UInt(width = addrsize))
    val avl_writedata = Reg(UInt(width = datawidth))
    val avl_readdata = Reg(UInt(width = datawidth))

    io.reg_writedata := avl_readdata
    io.avl_writedata := avl_writedata
    io.avl_address := avl_address

    val addr_step = Reg(UInt(width = addrsize))
    val transfers_left = Reg(UInt(width = addrsize))

    val (idle :: ready :: startRegWrite :: startMemRead :: waitMemRead ::
        checkReadData :: writeReg :: startRegRead :: readReg ::
        startMemWrite :: waitMemWrite :: checkRegReadContinue :: Nil) =
            Enum(UInt(), 12)

    val state = Reg(init = idle)

    io.avl_read := (state === startMemRead) || (state === waitMemRead)
    io.avl_write := (state === waitMemWrite)
    io.reg_read_reset := (state === startRegRead)
    io.reg_write_reset := (state === startRegWrite)
    io.reg_read := (state === readReg)
    io.reg_write := (state === writeReg)

    switch (state) {
        is(idle) {
            when (io.local_init_done) {
                state := ready
            }
        }
        is (ready) {
            when (io.start_read && !io.reg_busy) {
                state := startRegWrite
                avl_address := io.start_addr
                addr_step := io.addr_step
                transfers_left := io.transfer_count
            } .elsewhen (io.start_write && !io.reg_busy) {
                state := startRegRead
                avl_address := io.start_addr
                addr_step := io.addr_step
                transfers_left := io.transfer_count
            }
        }
        is (startRegWrite) {
            state := startMemRead
        }
        is (startMemRead) {
            state := waitMemRead
        }
        is (waitMemRead) {
            when (io.avl_waitrequest_n) {
                state := checkReadData
            }
        }
        is (checkReadData) {
            when (io.avl_readdatavalid) {
                state := writeReg
                avl_readdata := io.avl_readdata
                transfers_left := transfers_left - UInt(1)
            } .otherwise {
                state := startMemRead
            }
        }
        is (writeReg) {
            when (transfers_left === UInt(0)) {
                state := ready
            } .otherwise {
                state := startMemRead
                avl_address := avl_address + addr_step
            }
        }
        is (startRegRead) {
            state := readReg
        }
        is (readReg) {
            state := startMemWrite
        }
        is (startMemWrite) {
            state := waitMemWrite
            avl_writedata := io.reg_readdata
        }
        is (waitMemWrite) {
            when (io.avl_waitrequest_n) {
                transfers_left := transfers_left - UInt(1)
                state := checkRegReadContinue
            }
        }
        is (checkRegReadContinue) {
            when (transfers_left === UInt(0)) {
                state := ready
            } .otherwise {
                avl_address := avl_address + addr_step
                state := readReg
            }
        }
    }
}
