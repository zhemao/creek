package Creek

import java.lang.Float.floatToRawIntBits
import java.math.BigInteger

object TestUtils {
    def floatsToBigInt(floats: Seq[Float]): BigInt = {
        // extra '0' byte in front
        val float_array = floats.toArray
        var byte_array = new Array[Byte](1 + float_array.length * 4)

        byte_array(0) = 0

        for (i <- 0 until float_array.length) {
            val start_index = 1 + i * 4
            val rawint = floatToRawIntBits(float_array(i))

            byte_array(start_index) = ((rawint >> 24) & 0xff).toByte
            byte_array(start_index + 1) = ((rawint >> 16) & 0xff).toByte
            byte_array(start_index + 2) = ((rawint >> 8) & 0xff).toByte
            byte_array(start_index + 3) = (rawint & 0xff).toByte
        }

        BigInt(new BigInteger(byte_array))
    }
}
