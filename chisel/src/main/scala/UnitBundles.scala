package Creek

import Chisel._
import Creek.Constants.FloatSize

class UnitForwardInput(lanes: Int) extends Bundle {
    val vector_readdata = Bits(width = FloatSize * lanes)
    val busy = Bool()
    val scalar_value = Bits(width = FloatSize)

    override def clone = new UnitForwardInput(lanes).asInstanceOf[this.type]
}

class UnitBackwardInput(lanes: Int) extends Bundle {
    val read_reset = Bool()
    val vector_read = Bool()

    override def clone = new UnitBackwardInput(lanes).asInstanceOf[this.type]
}

class UnitBackwardOutput(lanes: Int) extends Bundle {
    val write_reset = Bool()
    val vector_writedata = Bits(width = FloatSize * lanes)
    val vector_write = Bool()

    override def clone = new UnitBackwardOutput(lanes).asInstanceOf[this.type]
}
