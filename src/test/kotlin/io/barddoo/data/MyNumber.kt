package io.barddoo.data


/**
 * Number override for testing. Number overrides should always override
 * toString, hashCode, and Equals.
 *
 * @see [The
 * Numbers Classes](https://docs.oracle.com/javase/tutorial/java/data/numberclasses.html)
 *
 * @see [Formatting
 * Numeric Print Output](https://docs.oracle.com/javase/tutorial/java/data/numberformat.html)
 *
 *
 * @author John Aylward
 */
class MyNumber : Number() {
    /**
     * @return number!
     */
    val number: Number = 42.toBigDecimal()


    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + number.hashCode()
        return result
    }

    override fun toByte(): Byte {
        return number.toByte()
    }

    override fun toChar(): Char {
        return number.toChar()
    }

    override fun toDouble(): Double {
        return number.toDouble()
    }

    override fun toFloat(): Float {
        return number.toFloat()
    }

    override fun toInt(): Int {
        return number.toInt()
    }

    override fun toLong(): Long {
        return number.toLong()
    }

    override fun toShort(): Short {
        return number.toShort()

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (other !is MyNumber) {
            return false
        }
        return number == other.number
    }

    override fun toString(): String {
        return number.toString()
    }
}
