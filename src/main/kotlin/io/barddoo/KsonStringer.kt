package io.barddoo

import java.io.StringWriter

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

/**
 * [KsonStringer] provides a quick and convenient way of producing JSON text. The texts
 * produced strictly conform to JSON syntax rules. No whitespace is added, so the results are ready
 * for transmission or storage. Each instance of [KsonStringer] can produce one JSON text.
 *
 *
 * A [KsonStringer] instance provides a `value` method for appending values to the
 * text, and a `key` method for adding keys before values in objects. There are
 * `array` and `endArray` methods that make and bound array values, and
 * `object` and `endObject` methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting cascade style. For example, <pre>
 * ```kotlin
 * val myString = KsonStringer()
 *   .objectIt()
 *   .key("JSON")
 *   .value("Hello, World!")
 *   .endObject()
 *   .toString();
 * ```
 * Which produces the string
 * `{"JSON":"Hello, World!"}`
 *
 * The first method called must be `array` or `object`. There are no methods
 * for adding commas or colons. [KsonStringer] adds them for you. Objects and arrays can be
 * nested up to 20 levels deep.
 *
 * This can sometimes be easier than using a Kson to build a string.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
class KsonStringer : KsonWriter(StringWriter()) {

    /**
     * Return the JSON text. This method is used to obtain the product of the [KsonStringer]
     * instance. It will return `null` if there was a problem in the construction of the
     * JSON text (such as the calls to `array` were not properly balanced with calls to
     * `endArray`).
     *
     * @return The JSON text.
     */
    override fun toString(): String {
        return writer.toString()
    }
}
