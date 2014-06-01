package Creek

import Chisel._
import Creek.Constants.FloatSize

class UnitForwardInput(lanes: Int) extends Bundle {
    val vector_readdata = Bits(width = FloatSize * lanes)
    val busy = Bool()
    val scalar_value = Bits(width = FloatSize)
}

class UnitBackwardInput(lanes: Int) extends Bundle {
    val read_reset = Bool()
    val vector_read = Bool()
}

class UnitBackwardOutput(lanes: Int) extends Bundle {
    val write_reset = Bool()
    val vector_writedata = Bits(width = FloatSize * lanes)
    val vector_write = Bool()
}
