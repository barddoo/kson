@file:Suppress("unused")

package io.barddoo

/*
Copyright (c) 2006 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import io.barddoo.Kson.Companion.numberToString
import io.barddoo.Kson.Companion.quote
import java.io.IOException

/**
 * JSONWriter provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. No whitespace is
 * added, so the results are ready for transmission or storage. Each instance of
 * JSONWriter can produce one JSON text.
 * A JSONWriter instance provides a `value` method for appending
 * values to the
 * text, and a `key`
 * method for adding keys before values in objects. There are `array`
 * and `endArray` methods that make and bound array values, and
 * `object` and `endObject` methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting a cascade style. For example in Java,
 * ```java
 * new JSONWriter(myWriter)
 *     .objectIt()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject();</pre> which writes <pre>
 * {"JSON":"Hello, World!"}</pre>
 * ```
 * In Kotlin,
 * ```kotlin
 * JSONWriter(myWriter)
 *     .objectIt()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject();</pre> which writes <pre>
 * {"JSON":"Hello, World!"}
 * ```
 *
 * The first method called must be `array` or `object`.
 * There are no methods for adding commas or colons. JSONWriter adds them for
 * you. Objects and arrays can be nested up to 200 levels deep.
 *
 * This can sometimes be easier than using a Kson to build a string.
 * @author Charles Fonseca
 * @version 1.0.0
 */
open class KsonWriter(w: Appendable) {

    /**
     * The comma flag determines if a comma should be output before the next value.
     */
    private var comma = false

    /**
     * The current mode. Values: 'a' (array), 'd' (done), 'i' (initial), 'k' (key), 'o' (object).
     */
    @JvmField
    protected var mode = 'i'

    /**
     * The object/array stack.
     */
    private val stack: Array<Kson?>

    /**
     * The stack top index. A value of 0 indicates that the stack is empty.
     */
    private var top: Int

    /**
     * The writer that will receive the output.
     */
    @JvmField
    protected var writer: Appendable

    /**
     * Make a fresh JSONWriter. It can be used to build one JSON text.
     */
    init {
        stack = arrayOfNulls(maxdepth)
        top = 0
        writer = w
    }

    /**
     * Append a value.
     *
     * @param string A string value.
     * @return [KsonWriter]
     * @throws KsonException If the value is out of sequence.
     */
    @Throws(KsonException::class)
    private fun append(string: String): KsonWriter {
        if (mode == 'o' || mode == 'a') {
            try {
                if (comma && mode == 'a') {
                    writer.append(',')
                }
                writer.append(string)
            } catch (e: IOException) {
                // Android as of API 25 does not support this exception constructor
                // however we won't worry about it. If an exception is happening here
                // it will just throw a "Method not found" exception instead.
                throw KsonException(e)
            }
            if (mode == 'o') {
                mode = 'k'
            }
            comma = true
            return this
        }
        throw KsonException("Value out of sequence.")
    }

    /**
     * Begin appending a new array. All values until the balancing
     * `endArray` will be appended to this array. The
     * `endArray` method must be called to mark the array's end.
     *
     * @return [KsonWriter]
     * @throws KsonException If the nesting is too deep, or if the object is started in the wrong
     * place (for example as a key or after the end of the outermost array or
     * object).
     */
    @Throws(KsonException::class)
    fun array(): KsonWriter {
        if (mode == 'i' || mode == 'o' || mode == 'a') {
            push(null)
            this.append("[")
            comma = false
            return this
        }
        throw KsonException("Misplaced array.")
    }

    /**
     * End something.
     *
     * @param m Mode
     * @param c Closing character
     * @return [KsonWriter]
     * @throws KsonException If unbalanced.
     */
    private fun end(m: Char, c: Char): KsonWriter {
        if (mode != m) {
            throw KsonException(if (m == 'a') "Misplaced endArray." else "Misplaced endObject.")
        }
        pop(m)
        try {
            writer.append(c)
        } catch (e: IOException) {
            // Android as of API 25 does not support this exception constructor
            // however we won't worry about it. If an exception is happening here
            // it will just throw a "Method not found" exception instead.
            throw KsonException(e)
        }
        comma = true
        return this
    }

    /**
     * End an array. This method most be called to balance calls to
     * `array`.
     *
     * @return [KsonWriter]
     * @throws KsonException If incorrectly nested.
     */
    @Throws(KsonException::class)
    fun endArray(): KsonWriter {
        return end('a', ']')
    }

    /**
     * End an object. This method most be called to balance calls to
     * `object`.
     *
     * @return [KsonWriter]
     * @throws KsonException If incorrectly nested.
     */
    @Throws(KsonException::class)
    fun endObject(): KsonWriter {
        return end('k', '}')
    }

    /**
     * Append a key. The key will be associated with the next value. In an object, every value must be
     * preceded by a key.
     *
     * @param string A key string.
     * @return [KsonWriter]
     * @throws KsonException If the key is out of place. For example, keys do not belong in arrays or
     * if the key is null.
     */
    @Throws(KsonException::class)
    fun key(string: String): KsonWriter {
        if (mode == 'k') {
            return try {
                val topObject = stack[top - 1]
                // don't use the built in putOnce method to maintain Android support
                if (topObject!!.has(string)) {
                    throw KsonException("Duplicate key \"$string\"")
                }
                topObject[string] = true
                if (comma) {
                    writer.append(',')
                }
                writer.append(quote(string))
                writer.append(':')
                comma = false
                mode = 'o'
                this
            } catch (e: IOException) {
                // Android as of API 25 does not support this exception constructor
                // however we won't worry about it. If an exception is happening here
                // it will just throw a "Method not found" exception instead.
                throw KsonException(e)
            }
        }
        throw KsonException("Misplaced key.")
    }

    /**
     * Begin appending a new object. All keys and values until the balancing
     * `endObject` will be appended to this object. The
     * `endObject` method must be called to mark the object's end.
     *
     * @return [KsonWriter]
     * @throws KsonException If the nesting is too deep, or if the object is started in the wrong
     * place (for example as a key or after the end of the outermost array or
     * object).
     */
    @Throws(KsonException::class)
    fun objectIt(): KsonWriter {
        if (mode == 'i') {
            mode = 'o'
        }
        if (mode == 'o' || mode == 'a') {
            this.append("{")
            push(Kson())
            comma = false
            return this
        }
        throw KsonException("Misplaced object.")
    }

    /**
     * Pop an array or object scope.
     *
     * @param c The scope to close.
     * @throws KsonException If nesting is wrong.
     */
    @Throws(KsonException::class)
    private fun pop(c: Char) {
        if (top <= 0) {
            throw KsonException("Nesting error.")
        }
        val m = if (stack[top - 1] == null) 'a' else 'k'
        if (m != c) {
            throw KsonException("Nesting error.")
        }
        top -= 1
        mode = if (top == 0) 'd' else if (stack[top - 1] == null) 'a' else 'k'
    }

    /**
     * Push an array or object scope.
     *
     * @param jo The scope to open.
     * @throws KsonException If nesting is too deep.
     */
    @Throws(KsonException::class)
    private fun push(jo: Kson?) {
        if (top >= maxdepth) {
            throw KsonException("Nesting too deep.")
        }
        stack[top] = jo
        mode = if (jo == null) 'a' else 'k'
        top += 1
    }

    /**
     * Append an object value.
     *
     * @param any The object to append. It can be null, or a Boolean, Number, String, Kson,
     * or KsonArray, or an object that implements KsonString.
     * @return [KsonWriter]
     * @throws KsonException If the value is out of sequence.
     */
    @Throws(KsonException::class)
    fun value(any: Any?): KsonWriter {
        return this.append(valueToString(any))
    }

    companion object {
        private const val maxdepth = 200

        /**
         * Make a JSON text of an Object value. If the object has an value.toJson() method, then
         * that method will be used to produce the JSON text. The method is required to produce a strictly
         * conforming text. If the object does not contain a toJson method (which is the most common
         * case), then a text will be produced by other means. If the value is an array or Collection,
         * then a KsonArray will be made from it and its toJson method will be called. If
         * the value is a MAP, then a Kson will be made from it and its toJson method will be
         * called. Otherwise, the value's toString method will be called, and the result will be quoted.
         *
         * Warning: This method assumes that the data structure is acyclical.
         *
         * @param value The value to be serialized.
         * @return a printable, displayable, transmittable representation of the object, beginning with
         * `{` (left brace) and ending with `}` (right
         * brace).
         * @throws KsonException If the value is or contains an invalid number.
         */
        fun valueToString(value: Any?): String {
            return when {
                value == null -> "null"

                value is String -> quote(value)

                value is KsonString -> try {
                    value.toJson()
                } catch (e: Throwable) {
                    throw KsonException(e)
                }

                value is Number -> {
                    // not all Numbers may match actual JSON Numbers. i.e. Fractions or Complex
                    val numberAsString = numberToString((value as Number?)!!)
                    if (Kson.NUMBER_PATTERN.matcher(numberAsString).matches()) {
                        // Close enough to a JSON number that we will return it unquoted
                        numberAsString
                    } else quote(numberAsString)
                    // The Number value is not a valid JSON number.
                    // Instead we will quote it as a string
                }
                value is Boolean || value is Kson
                    || value is KsonArray ->
                    value.toString()

                value is Map<*, *> -> Kson(value).toString()

                value is Collection<*> -> KsonArray(value).toString()

                value.javaClass.isArray -> KsonArray(value).toString()

                value is Enum<*> -> quote(value.name)

                else -> quote(value.toString())
            }
        }
    }
}
