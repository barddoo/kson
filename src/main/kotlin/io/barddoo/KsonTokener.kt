@file:Suppress("unused")

package io.barddoo

import io.barddoo.Kson.Companion.stringToValue
import java.io.*

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
 * A io.barddoo.JSONTokener takes a source string and extracts characters and tokens from it. It is
 * used by the Kson and io.barddoo.KsonArray constructors to parse JSON source strings.
 *
 * @author JSON.org
 * @version 2014-05-03
 */
class KsonTokener(reader: Reader) {
    /**
     * Reader for the input.
     */
    private val reader: Reader

    /**
     * current read character position on the current line.
     */
    private var character: Long

    /**
     * flag to indicate if the end of the input has been found.
     */
    private var eof: Boolean

    /**
     * current read index of the input.
     */
    private var index: Long

    /**
     * current line of the input.
     */
    private var line: Long

    /**
     * previous character read from the input.
     */
    private var previous: Char

    /**
     * flag to indicate that a previous character was requested.
     */
    private var usePrevious: Boolean

    /**
     * the number of characters read in the previous line.
     */
    private var characterPreviousLine: Long

    /**
     * Construct a io.barddoo.JSONTokener from an InputStream. The caller must close the input stream.
     *
     * @param inputStream The source.
     */
    constructor(inputStream: InputStream?) : this(InputStreamReader(inputStream))

    /**
     * Construct a io.barddoo.JSONTokener from a string.
     *
     * @param s A source string.
     */
    constructor(s: String?) : this(StringReader(s))

    /**
     * Back up one character. This provides a sort of lookahead capability, so that you can test for a
     * digit or letter before attempting to parse the next number or identifier.
     *
     * @throws KsonException Thrown if trying to step back more than 1 step or if already at the start
     * of the string
     */
    @Throws(KsonException::class)
    fun back() {
        if (usePrevious || index <= 0) {
            throw KsonException("Stepping back two steps is not supported")
        }
        decrementIndexes()
        usePrevious = true
        eof = false
    }

    /**
     * Decrements the indexes for the [.back] method based on the previous character read.
     */
    private fun decrementIndexes() {
        index--
        if (previous == '\r' || previous == '\n') {
            line--
            character = characterPreviousLine
        } else if (character > 0) {
            character--
        }
    }

    /**
     * Checks if the end of the input has been reached.
     *
     * @return true if at the end of the file and we didn't step back
     */
    fun end(): Boolean {
        return eof && !usePrevious
    }

    /**
     * Determine if the source string still contains characters that next() can consume.
     *
     * @return true if not yet at the end of the source.
     * @throws KsonException thrown if there is an error stepping forward or backward while checking
     * for more data.
     */
    @Throws(KsonException::class)
    fun more(): Boolean {
        if (usePrevious) {
            return true
        }
        try {
            reader.mark(1)
        } catch (e: IOException) {
            throw KsonException("Unable to preserve stream position", e)
        }
        try {
            // -1 is EOF, but next() can not consume the null character '\0'
            if (reader.read() <= 0) {
                eof = true
                return false
            }
            reader.reset()
        } catch (e: IOException) {
            throw KsonException("Unable to read the next character from the stream", e)
        }
        return true
    }

    /**
     * Get the next character in the source string.
     *
     * @return The next character, or 0 if past the end of the source string.
     * @throws KsonException Thrown if there is an error reading the source string.
     */
    @Throws(KsonException::class)
    operator fun next(): Char {
        val c: Int
        if (usePrevious) {
            usePrevious = false
            c = previous.toInt()
        } else {
            c = try {
                reader.read()
            } catch (exception: IOException) {
                throw KsonException(exception)
            }
        }
        if (c <= 0) { // End of stream
            eof = true
            return 0.toChar()
        }
        incrementIndexes(c)
        previous = c.toChar()
        return previous
    }

    /**
     * Increments the internal indexes according to the previous character read and the character
     * passed as the current character.
     *
     * @param c the current character read.
     */
    private fun incrementIndexes(c: Int) {
        if (c > 0) {
            index++
            when (c) {
                '\r'.toInt() -> {
                    line++
                    characterPreviousLine = character
                    character = 0
                }
                '\n'.toInt() -> {
                    if (previous != '\r') {
                        line++
                        characterPreviousLine = character
                    }
                    character = 0
                }
                else -> {
                    character++
                }
            }
        }
    }

    /**
     * Consume the next character, and check that it matches a specified character.
     *
     * @param c The character to match.
     * @return The character.
     * @throws KsonException if the character does not match.
     */
    fun next(c: Char): Char {
        val n = this.next()
        if (n != c) {
            if (n.toInt() > 0) {
                throw this.syntaxError("Expected '" + c + "' and instead saw '" +
                    n + "'")
            }
            throw this.syntaxError("Expected '$c' and instead saw ''")
        }
        return n
    }

    /**
     * Get the next n characters.
     *
     * @param n The number of characters to take.
     * @return A string of n characters.
     * @throws KsonException Substring bounds error if there are not n characters remaining in the
     * source string.
     */
    @Throws(KsonException::class)
    fun next(n: Int): String {
        if (n == 0) {
            return ""
        }
        val chars = CharArray(n)
        var pos = 0
        while (pos < n) {
            chars[pos] = this.next()
            if (end()) {
                throw this.syntaxError("Substring bounds error")
            }
            pos += 1
        }
        return String(chars)
    }

    /**
     * Get the next char in the string, skipping whitespace.
     *
     * @return A character, or 0 if there are no more characters.
     * @throws KsonException Thrown if there is an error reading the source string.
     */
    @Throws(KsonException::class)
    fun nextClean(): Char {
        while (true) {
            val c = this.next()
            if (c.toInt() == 0 || c > ' ') {
                return c
            }
        }
    }

    /**
     * Return the characters up to the next close quote character. Backslash processing is done. The
     * formal JSON format does not allow strings in single quotes, but an implementation is allowed to
     * accept them.
     *
     * @param quote The quoting character, either
     * `"`&nbsp;<small>(double quote)</small> or
     * `'`&nbsp;<small>(single quote)</small>.
     * @return A String.
     * @throws KsonException Unterminated string.
     */
    @Throws(KsonException::class)
    fun nextString(quote: Char): String {
        var c: Char
        val sb = StringBuilder()
        while (true) {
            c = this.next()
            when (c) {
                0.toChar(), '\n', '\r' -> throw this.syntaxError("Unterminated string")
                '\\' -> {
                    c = this.next()
                    when (c) {
                        'b' -> sb.append('\b')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        'u' -> try {
                            sb.append(this.next(4).toInt(16).toChar())
                        } catch (e: NumberFormatException) {
                            throw this.syntaxError("Illegal escape.", e)
                        }
                        '"', '\'', '\\', '/' -> sb.append(c)
                        else -> throw this.syntaxError("Illegal escape.")
                    }
                }
                else -> {
                    if (c == quote) {
                        return sb.toString()
                    }
                    sb.append(c)
                }
            }
        }
    }

    /**
     * Get the text up but not including the specified character or the end of line, whichever comes
     * first.
     *
     * @param delimiter A delimiter character.
     * @return A string.
     * @throws KsonException Thrown if there is an error while searching for the delimiter
     */
    fun nextTo(delimiter: Char): String {
        val sb = StringBuilder()
        while (true) {
            val c = this.next()
            if (c == delimiter || c.toInt() == 0 || c == '\n' || c == '\r') {
                if (c.toInt() != 0) {
                    back()
                }
                return sb.toString().trim { it <= ' ' }
            }
            sb.append(c)
        }
    }

    /**
     * Get the text up but not including one of the specified delimiter characters or the end of line,
     * whichever comes first.
     *
     * @param delimiters A set of delimiter characters.
     * @return A string, trimmed.
     * @throws KsonException Thrown if there is an error while searching for the delimiter
     */
    fun nextTo(delimiters: String): String {
        var c: Char
        val sb = StringBuilder()
        while (true) {
            c = this.next()
            if (delimiters.indexOf(c) >= 0 || c.toInt() == 0 || c == '\n' || c == '\r') {
                if (c.toInt() != 0) {
                    back()
                }
                return sb.toString().trim { it <= ' ' }
            }
            sb.append(c)
        }
    }

    /**
     * Get the next value. The value can be a Boolean, Double, Integer, io.barddoo.KsonArray,
     * Kson, Long, or String, or the NULL object.
     *
     * @return An object.
     * @throws KsonException If syntax error.
     */
    @Throws(KsonException::class)
    fun nextValue(): Any? {
        var c = nextClean()
        val string: String
        when (c) {
            '"', '\'' -> return nextString(c)
            '{' -> {
                back()
                return Kson(this)
            }
            '[' -> {
                back()
                return KsonArray(this)
            }
        }

        /*
         * Handle unquoted text. This could be the values true, false, or
         * null, or it can be a number. An implementation (such as this one)
         * is allowed to also accept non-standard forms.
         *
         * Accumulate characters until we reach the end of the text or a
         * formatting character.
         */
        val sb = StringBuilder()
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c)
            c = this.next()
        }
        if (!eof) {
            back()
        }
        string = sb.toString().trim { it <= ' ' }
        if ("" == string) {
            throw this.syntaxError("Missing value")
        }
        return stringToValue(string)
    }

    /**
     * Skip characters until the next character is the requested character. If the requested character
     * is not found, no characters are skipped.
     *
     * @param to A character to skip to.
     * @return The requested character, or zero if the requested character is not found.
     * @throws KsonException Thrown if there is an error while searching for the to character
     */
    @Throws(KsonException::class)
    fun skipTo(to: Char): Char {
        var c: Char
        try {
            val startIndex = index
            val startCharacter = character
            val startLine = line
            reader.mark(1000000)
            do {
                c = this.next()
                if (c.toInt() == 0) {
                    // in some readers, reset() may throw an exception if
                    // the remaining portion of the input is greater than
                    // the mark size (1,000,000 above).
                    reader.reset()
                    index = startIndex
                    character = startCharacter
                    line = startLine
                    return 0.toChar()
                }
            } while (c != to)
            reader.mark(1)
        } catch (exception: IOException) {
            throw KsonException(exception)
        }
        back()
        return c
    }

    /**
     * Make a io.barddoo.JSONException to signal a syntax error.
     *
     * @param message The error message.
     * @return A io.barddoo.JSONException object, suitable for throwing
     */
    fun syntaxError(message: String): KsonException {
        return KsonException(message + this.toString())
    }

    /**
     * Make a io.barddoo.JSONException to signal a syntax error.
     *
     * @param message  The error message.
     * @param causedBy The throwable that caused the error.
     * @return A io.barddoo.JSONException object, suitable for throwing
     */
    fun syntaxError(message: String, causedBy: Throwable): KsonException {
        return KsonException(message + this.toString(), causedBy)
    }

    /**
     * Make a printable string of this io.barddoo.JSONTokener.
     *
     * @return " at {index} [character {character} line {line}]"
     */
    override fun toString(): String {
        return " at " + index + " [character " + character + " line " +
            line + "]"
    }

    companion object {
        /**
         * Get the hex value of a character (base16).
         *
         * @param c A character between '0' and '9' or between 'A' and 'F' or between 'a' and 'f'.
         * @return An int between 0 and 15, or -1 if c was not a hex digit.
         */
        fun dehexchar(c: Char): Int {
            if (c in '0'..'9') {
                return c - '0'
            }
            if (c in 'A'..'F') {
                return c.toInt() - ('A'.toInt() - 10)
            }
            return if (c in 'a'..'f') {
                c.toInt() - ('a'.toInt() - 10)
            } else -1
        }
    }

    /**
     * Construct a [KsonTokener] from a Reader. The caller must close the Reader.
     *
     */
    init {
        this.reader = if (reader.markSupported()) reader else BufferedReader(reader)
        eof = false
        usePrevious = false
        previous = 0.toChar()
        index = 0
        character = 1
        characterPreviousLine = 0
        line = 1
    }
}