package io.barddoo

import java.util.*

/*
Copyright (c) 2002 JSON.org
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
 * Convert an HTTP header to a Kson and back.
 *
 * @author Charles Fonseca
 * @version 1.0.0
 */
object Http {
    /**
     * Carriage return/line feed.
     */
    private const val crlf = "\r\n"

    /**
     * Convert an HTTP header string into a Kson. It can be a request header or a response
     * header. A request header will contain
     * ```
     * {
     * Method: "POST" (for example),
     * "Request-URI": "/" (for example),
     * "HTTP-Version": "HTTP/1.1" (for example)
     * }
     * ```
     * A response header will contain
     * ```
     * {
     * "HTTP-Version": "HTTP/1.1" (for example),
     * "Status-Code": "200" (for example),
     * "Reason-Phrase": "OK" (for example)
     * }
     * ```
     * In addition, the other parameters in the header will be captured, using
     * the HTTP field names as JSON names, so that
     * ```
     * Date: Sun, 26 May 2002 18:06:04 GMT
     * Cookie: Q=q2=PPEAsg--; B=677gi6ouf29bn=2=s
     * Cache-Control: no-cache
     * ```
     * become
     * ```
     * {...
     * Date: "Sun, 26 May 2002 18:06:04 GMT",
     * Cookie: "Q=q2=PPEAsg--; B=677gi6ouf29bn=2=s",
     * "Cache-Control": "no-cache",
     * ...}
     * ```
     * It does no further checking or conversion. It does not parse dates. It does not do '%'
     * transforms on URLs.
     *
     * @param string An HTTP header string.
     * @return A Kson containing the elements and attributes of the XML string.
     */
    fun json(string: String): Kson {
        val json = Kson()
        val x = HttpTokener(string)
        val token: String
        token = x.nextToken()
        if (token.toUpperCase(Locale.ROOT).startsWith("HTTP")) {

            // Response
            json["HTTP-Version"] = token
            json["Status-Code"] = x.nextToken()
            json["Reason-Phrase"] = x.nextTo('\u0000')
            x.next()
        } else {

            // Request
            json["Method"] = token
            json["Request-URI"] = x.nextToken()
            json["HTTP-Version"] = x.nextToken()
        }

        // Fields
        while (x.more()) {
            val name = x.nextTo(':')
            x.next(':')
            json[name] = x.nextTo('\u0000')
            x.next()
        }
        return json
    }

    /**
     * Convert a [Kson] into an [Http] header. A request header must contain
     * ```
     * {
     * Method: "POST" (for example),
     * "Request-URI": "/" (for example),
     * "HTTP-Version": "HTTP/1.1" (for example)
     * }
     * ```
     * A response header must contain
     * ```
     * {
     * "HTTP-Version": "HTTP/1.1" (for example),
     * "Status-Code": "200" (for example),
     * "Reason-Phrase": "OK" (for example)
     * }
     * ```
     * Any other members of the [Kson] will be output as HTTP fields. The result will end with two
     * CRLF pairs.
     *
     * @param json A [Kson]
     * @return An HTTP header string.
     * @throws KsonException if the object does not contain enough information.
     */
    fun toString(json: Kson): String = buildString {
        when {
            json.has("Status-Code") && json.has("Reason-Phrase") -> {
                append(json.getString("HTTP-Version"))
                append(' ')
                append(json.getString("Status-Code"))
                append(' ')
                append(json.getString("Reason-Phrase"))
            }
            json.has("Method") && json.has("Request-URI") -> {
                append(json.getString("Method"))
                append(' ')
                append('"')
                append(json.getString("Request-URI"))
                append('"')
                append(' ')
                append(json.getString("HTTP-Version"))
            }
            else -> {
                throw KsonException("Not enough material for an HTTP header.")
            }
        }
        append(crlf)
        for (key in json.keys) {
            val value = json.getString(key)
            if ("HTTP-Version" != key && "Status-Code" != key
                && "Reason-Phrase" != key && "Method" != key
                && "Request-URI" != key && value != NULL.toString()
            ) {
                append(key)
                append(": ")
                append(json.getString(key))
                append(crlf)
            }
        }
        append(crlf)
    }
}
