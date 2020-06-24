package io.barddoo.data

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * basic fraction class, no frills.
 * @author John Aylward
 */
class Fraction(numerator: BigInteger, denominator: BigInteger) : Number(), Comparable<Fraction> {
    /**
     * value as a big decimal.
     */
    private var bigDecimal: BigDecimal

    /**
     * @return the denominator
     */
    /**
     * value of the denominator.
     */
    val denominator: BigInteger

    /**
     * value of the numerator.
     */
    val numerator: BigInteger

    /**
     * @param numerator
     * numerator
     * @param denominator
     * denominator
     */
    constructor(numerator: Long, denominator: Long) : this(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))

    /**
     * @return the decimal
     */
    fun bigDecimalValue(): BigDecimal? {
        return bigDecimal
    }

    override fun compareTo(other: Fraction): Int {
        // .equals call this, so no .equals compare allowed

        // if they are the same reference, just return equals
        if (this == other) {
            return 0
        }

        // if my denominators are already equal, just compare the numerators
        if (denominator.compareTo(other.denominator) == 0) {
            return numerator.compareTo(other.numerator)
        }

        // get numerators of common denominators
        // a    x     ay   xb
        // --- --- = ---- ----
        // b    y     by   yb
        val thisN = numerator.multiply(other.denominator)
        val otherN = other.numerator.multiply(denominator)
        return thisN.compareTo(otherN)
    }

    override fun toDouble(): Double {
        return bigDecimal.toDouble()
    }

    /**
     * @see Object.equals
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (this.javaClass != other.javaClass) {
            return false
        }
        val other1 = other as Fraction
        return this.compareTo(other1) == 0
    }

    override fun toFloat(): Float {
        return bigDecimal.toFloat()
    }

    /**
     * @see Object.hashCode
     */
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + bigDecimal.hashCode()
        return result
    }

    override fun toByte(): Byte {
        return bigDecimal.toByte()
    }

    override fun toChar(): Char {
        return bigDecimal.toChar()
    }

    override fun toInt(): Int {
        return bigDecimal.toInt()
    }

    override fun toLong(): Long {
        return bigDecimal.toLong()
    }

    override fun toShort(): Short {
        return bigDecimal.toShort()
    }

    /**
     * @see Object.toString
     */
    override fun toString(): String {
        return "$numerator/$denominator"
    }

    companion object {
        /**
         * serial id.
         */
        private const val serialVersionUID = 1L
    }

    init {
        require(denominator.compareTo(BigInteger.ZERO) != 0) { "Divide by zero" }
        val n: BigInteger
        val d: BigInteger
        // normalize fraction
        if (denominator.signum() < 0) {
            n = numerator.negate()
            d = denominator.negate()
        } else {
            n = numerator
            d = denominator
        }
        this.numerator = n
        this.denominator = d
        bigDecimal = when {
            n.compareTo(BigInteger.ZERO) == 0 -> {
                BigDecimal.ZERO
            }
            n.compareTo(d) == 0 -> { // i.e. 4/4, 10/10
                BigDecimal.ONE
            }
            else -> {
                BigDecimal(this.numerator).divide(
                    BigDecimal(this.denominator),
                    RoundingMode.HALF_EVEN
                )
            }
        }
    }
}