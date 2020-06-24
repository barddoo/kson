@file:Suppress("unused")

package io.barddoo

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

import io.barddoo.Kson.Companion.indent
import io.barddoo.Kson.Companion.objectToBigDecimal
import io.barddoo.Kson.Companion.objectToBigInteger
import io.barddoo.Kson.Companion.stringToNumber
import io.barddoo.Kson.Companion.testValidity
import io.barddoo.Kson.Companion.writeValue
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.lang.reflect.Array
import java.math.BigDecimal
import java.math.BigInteger


/**
 * A KsonArray is an ordered sequence of values. Its external text form is a
 * string wrapped in square brackets with commas separating the values. The
 * internal form is an object having `get` methods for accessing the values
 * by index, and `put` methods for adding or replacing values.
 *  The values can be any of these types:
 * [Boolean], [KsonArray], [Kson], [Number], [String], or the [NULL].
 *
 * The constructor can convert a JSON text into a Java object. The
 * `toString` method converts to JSON text.
 *
 * A `get` method returns a value if one can be found, and throws an
 * exception if one cannot be found. An `opt` method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 *
 * The generic `get()` methods return an object which you can cast or query for
 * type. There are also typed `get` methods that do type checking and type
 * coercion for you.
 *
 * The `getBy<T>` check type, or return `null`.
 *
 * Attention Java/Kotlin's null is not equals json's null, if you want
 * to add `null` value pay attention to `Kson.NULL`,
 * what is equal to JavaScript undefined.
 *
 * The texts produced by the `toString` methods strictly conform to
 * JSON syntax rules. The constructors are more forgiving in the texts they will
 * accept:
 *
 * An extra `,` (comma) may appear just
 * before the closing bracket.
 * The `null` value will be inserted when there is `,`
 *  (comma) elision.
 * Strings may be quoted with `'` (single
 * quote).
 * Strings do not need to be quoted at all if they do not begin with a quote
 * or single quote, and if they do not contain leading or trailing spaces, and
 * if they do not contain any of these characters:
 * `{ } [ ] / \ : , #` and if they do not look like numbers and
 * if they are not the reserved words `true`, `false`, or
 * `null`.
 *
 *
 * @author Charles Fonseca
 * @version 1.0.0
 */
class KsonArray() : ArrayList<Any?>() {
  /**
   * Construct a [KsonArray] from [KsonTokener]
   *
   * @param tokener A [KsonTokener] object
   * @throws KsonException If there is a syntax error.
   */
  constructor(tokener: KsonTokener) : this() {
    if (tokener.nextClean() != '[') {
      throw tokener.syntaxError("A [KsonArray] text must start with '['")
    }
    var nextChar = tokener.nextClean()
    if (nextChar.toInt() == 0) {
      // array is unclosed. No ']' found, instead EOF
      throw tokener.syntaxError("Expected a ',' or ']'")
    }
    if (nextChar != ']') {
      tokener.back()
      while (true) {
        if (tokener.nextClean() == ',') {
          tokener.back()
          this.add(NULL)
        } else {
          tokener.back()
          this.add(tokener.nextValue())
        }
        when (tokener.nextClean()) {
          '0' -> throw tokener.syntaxError("Expected a ',' or ']'")
          ',' -> {
            nextChar = tokener.nextClean()
            if (nextChar.toInt() == 0) {
              // array is unclosed. No ']' found, instead EOF
              throw tokener.syntaxError("Expected a ',' or ']'")
            }
            if (nextChar == ']') {
              return
            }
            tokener.back()
          }
          ']' -> return
          else -> throw tokener.syntaxError("Expected a ',' or ']'")
        }
      }
    }
  }

  /**
   * Construct a [KsonArray] from a source JSON text
   *
   * @param source A string that begins with `[` and ends with `]`
   * @throws [KsonException] If there is a syntax error
   */
  constructor(source: String) : this(KsonTokener(source))

  /**
   * Adds the specified element to the collection.
   *
   * @return `true` if the element has been added, `false` if the collection does not support duplicates
   * and the element is already contained in the collection.
   */
  override fun add(element: Any?): Boolean {
    return super.add(element ?: NULL)
  }

  /**
   * Adds all of the elements of the specified collection to this collection.
   *
   * @return `true` if any of the specified elements was added to the collection, `false` if the collection was not modified.
   */
  override fun addAll(elements: Collection<Any?>): Boolean {
    return super.addAll(elements.mapToJsonNull())
  }


  /**
   * Put a value, where the it will be a [KsonArray] which is produced from a [Collection].
   * Example:
   * `jsonArray.add(listOf("Lorem", "ipsum", "dolor", "sit", "amet"))`
   * Output:
   * `[["Lorem","ipsum","dolor","sit","amet"]]`
   *
   * If you intend to add each value individually, use [MutableCollection.addAll]
   * Example:
   * `jsonArray.addAll(listOf("Lorem", "ipsum", "dolor", "sit", "amet"))`
   * Output:
   * `["Lorem","ipsum","dolor","sit","amet"]`
   *
   * @param value A Collection value
   */
  fun add(value: Collection<*>): Boolean {
    testValidity(value)
    return super.add(KsonArray(value))
  }

  /**
   * Put a value in the [KsonArray], where the value will be a Kson which is produced
   * from a Map.
   *
   * @param value A Map value
   */
  fun add(value: Map<String, *>): Boolean {
    testValidity(value)
    return super.add(Kson(value))
  }

  /**
   * Put a value in the [KsonArray], where the value will be a [KsonArray] which is
   * produced from a Collection.
   *
   * @param index The subscript
   * @param elements A Collection value
   */
  override fun addAll(index: Int, elements: Collection<Any?>): Boolean {
    return super.addAll(index, KsonArray(elements.mapToJsonNull()))
  }

  override fun set(index: Int, element: Any?): Any? {
    testValidity(element)
    return super.set(index, element ?: NULL)
  }

  /**
   * Construct a [KsonArray] from a Collection.
   *
   * @param collection A Collection
   */
  constructor(collection: Collection<*>) : this() {
    this.ensureCapacity(collection.size)
    collection.mapTo(this, ::wrap)
  }

  /**
   * Construct a [KsonArray] from an [Array], [Collection], [Map], or [Iterable]
   *
   * @param any [Array], [Collection], [Map], or [Iterable]
   * @throws KsonException If object is invalid
   */
  constructor(any: Any) : this() {
    when {
      any.javaClass.isArray -> {
        val length = Array.getLength(any)
        this.ensureCapacity(length)
        var i = 0
        while (i < length) {
          this.add(wrap(Array.get(any, i)))
          i += 1
        }
      }
      any is Iterable<*> -> {
        for (elem: Any? in any) {
          this.add(wrap(elem))
        }
      }
      any is Iterator<*> -> {
        for (elem in any) {
          this.add(wrap(elem))
        }
      }
      any is Map<*, *> -> {
        add(wrap(any))
      }
      else -> {
        throw KsonException(
          "KsonArray initial value should be a string or collection or array."
        )
      }
    }
  }

  /**
   * Determine if the value is `null`.
   *
   * @param index The index must be between 0 and size - 1.
   * @return true if the value at the index is `null`, or if there is no value.
   */
  fun isNull(index: Int): Boolean {
    if (index > size)
      return true
    val any = get(index)
    return any == NULL
  }

  /**
   * Get the optional object value associated with an index.
   *
   * @param index The index must be between 0 and size - 1
   * @return An object value, or null if there is no object at that index
   */
  override operator fun get(index: Int): Any? {
    val any = super.get(index)
    return if (any != NULL) any
    else null
  }

  /**
   * Get the optional boolean value associated with an index. It returns the defaultValue if there
   * is no value at that index or if it is not a Boolean or the String "true" or "false" (case
   * insensitive).
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default in case the value is not found
   * @return The truth
   */
  @JvmOverloads
  fun getBoolean(index: Int, defaultValue: Boolean = false): Boolean {
    val any = this[index]
    when {
      any == false || any is String && any
        .equals("false", ignoreCase = true) -> {
        return false
      }
      any == true || any is String && any
        .equals("true", ignoreCase = true) -> {
        return true
      }
    }
    return defaultValue
  }

  /**
   * Get the optional double value associated with an index. The defaultValue is returned if there
   * is no value for the index, or if the value is not a number and cannot be converted to a
   * number.
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default in case the value is not found
   * @return The value
   */
  @JvmOverloads
  fun getDouble(index: Int, defaultValue: Double? = null): Double? {
    return getNumber(index, defaultValue)?.toDouble()
  }

  /**
   * Get the optional float value associated with an index. The defaultValue is returned if there is
   * no value for the index, or if the value is not a number and cannot be converted to a number.
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default in case the value is not found
   * @return The value
   */
  @JvmOverloads
  fun getFloat(index: Int, defaultValue: Float? = null): Float? {
    return getNumber(index, defaultValue)?.toFloat()
  }

  /**
   * Get the optional int value associated with an index. The defaultValue is returned if there is
   * no value for the index, or if the value is not a number and cannot be converted to a number.
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default in case the value is not found.
   * @return The value
   */
  @JvmOverloads
  fun getInt(index: Int, defaultValue: Int? = null): Int? {
    return getNumber(index, defaultValue)?.toInt()
  }

  /**
   * Get the optional [BigInteger] value associated with an index. The
   * defaultValue is returned if there is no value for the index, or if the
   * value is not a number and cannot be converted to a number.
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default value
   * @return The value
   */
  @JvmOverloads
  fun getBigInteger(index: Int, defaultValue: BigInteger? = null): BigInteger? {
    return objectToBigInteger(this[index], defaultValue)
  }

  /**
   * Get the optional [BigDecimal] value associated with an index. The
   * defaultValue is returned if there is no value for the index, or if the
   * value is not a number and cannot be converted to a number. If the value
   * is float or double, the the [BigDecimal]
   * constructor will be used. See notes on the constructor for conversion
   * issues that may arise.
   *
   * @param index The index must be between 0 and size - 1
   * @param defaultValue The default value
   * @return The value
   */
  @JvmOverloads
  fun getBigDecimal(index: Int, defaultValue: BigDecimal? = null): BigDecimal? {
    return objectToBigDecimal(this[index], defaultValue)
  }

  /**
   * Get the optional long value associated with an index. The defaultValue is returned if there is
   * no value for the index, or if the value is not a number and cannot be converted to a number.
   *
   * @param index The index must be between 0 and size - 1.
   * @param defaultValue The default in case the value is not found.
   * @return The value.
   */
  @JvmOverloads
  fun getLong(index: Int, defaultValue: Long? = null): Long? {
    return getNumber(index, defaultValue)?.toLong()
  }

  /**
   * Get an optional [Number] value associated with a key, or the default if there is no such
   * key or if the value is not a number. If the value is a string, an attempt will be made to
   * evaluate it as a number ([BigDecimal]). This method would be used in cases where type
   * coercion of the number value is unwanted.
   *
   * @param index The index must be between 0 and size - 1.
   * @param defaultValue The default in case the value is not found.
   * @return An object which is the value.
   */
  @JvmOverloads
  fun getNumber(index: Int, defaultValue: Number? = null): Number? {
    val any = get(index)
    if (NULL == any || any == null) {
      return defaultValue
    }
    if (any is Number) {
      return any
    }
    return if (any is String) {
      try {
        stringToNumber((any as String?)!!)
      } catch (e: Exception) {
        defaultValue
      }
    } else defaultValue
  }

  /**
   * Get the optional string associated with an index. The defaultValue is returned if the key is
   * not found.
   *
   * @param index The index must be between 0 and size - 1.
   * @param defaultValue The default in case the value is not found.
   * @return A [String].
   */
  @JvmOverloads
  fun getString(index: Int, defaultValue: String? = null): String? {
    val any = get(index)
    return if (NULL != any && any != null)
      any.toString() else defaultValue
  }

  /**
   * Get the enum value associated with a key.
   *
   * @param E Enum Type
   * @param clazz The type of enum to retrieve.
   * @param index The index must be between 0 and size - 1.
   * @param defaultValue The default in case the value is not found.
   * @return The enum value at the index location or [defaultValue] if the value is not found or
   * cannot be assigned to clazz
   */
  inline fun <reified E : Enum<E>?> getEnum(clazz: Class<E>, index: Int, defaultValue: E? = null): E? {
    val any = this[index]
    if (NULL == any || any == null) {
      return defaultValue
    }
    if (clazz.isAssignableFrom(any.javaClass) and (any is E)) {
      return any as E
    }

    return java.lang.Enum.valueOf(clazz, any.toString()) ?: defaultValue
  }

  /**
   * Get the [KsonArray] associated with an index.
   *
   * @param E type to consider on iterating
   * @param index The index must be between 0 and size - 1.
   * @param defaultValue The default in case the value is not found.
   * @return An object value of [E] type, or [defaultValue] if there is no object at that index.
   */
  inline fun <reified E> getBy(index: Int, defaultValue: E? = null): E? {
    val any = this[index]
    return if (any is E) any else defaultValue
  }

  /**
   * Get the optional KsonArray associated with an index.
   *
   * @param index subscript
   * @return A KsonArray value, or null if the index has no value, or if the value is not a KsonArray.
   */
  fun array(index: Int): KsonArray? {
    return getBy<KsonArray>(index)
  }

  /**
   * Get the optional [Kson] associated with an index. Null is returned if
   * the key is not found, or null if the index has no value, or if the value
   * is not a Kson.
   *
   * @param index The index must be between 0 and size - 1.
   * @return A Kson value.
   */
  fun json(index: Int): Kson? {
    return getBy<Kson>(index)
  }

  /**
   * Get a typed iterator.
   *
   * @param E type to consider on iterating
   * @return An iterator by given type.
   */
  inline fun <reified E> iteratorBy(): Iterator<E> {
    return this.filterIsInstance<E>().iterator()
  }

  /**
   * Creates a [KsonPointer] using an initialization string and tries to
   * match it to an item within this [KsonArray]. For example, given a
   * KsonArray initialized with this document:
   * ```json
   * [{"b":"c"}]
   * ```
   * and this [KsonPointer] string: "/0/b"
   * Then this method will return the String "c"
   * A KsonPointerException may be thrown from code called by this method.
   *
   * @param jsonPointer string that can be used to create a KsonPointer
   * @return the item matched by the KsonPointer, otherwise null
   */
  fun query(jsonPointer: String): Any {
    return query(KsonPointer(jsonPointer))
  }

  /**
   * Uses a user initialized KsonPointer  and tries to
   * match it to an item within this KsonArray. For example, given a
   * KsonArray initialized with this document:
   * <pre>
   * [
   *     {"b":"c"}
   * ]
   * </pre>
   * and this KsonPointer:
   * <pre>
   * "/0/b"
   * </pre>
   * Then this method will return the String "c"
   * A KsonPointerException may be thrown from code called by this method.
   *
   * @param ksonPointer string that can be used to create a KsonPointer
   * @return the item matched by the KsonPointer, otherwise null
   */
  fun query(ksonPointer: KsonPointer): Any {
    return ksonPointer.queryFrom(this)
  }

  /**
   * Queries and returns a value from this object using `jsonPointer`, or returns null if the
   * query fails due to a missing key.
   *
   * @param jsonPointer the string representation of the JSON pointer
   * @return the queried value or `null`
   * @throws IllegalArgumentException if `jsonPointer` has invalid syntax
   */
  fun optQuery(jsonPointer: String): Any? {
    return optQuery(KsonPointer(jsonPointer))
  }

  /**
   * Queries and returns a value from this object using `jsonPointer`, or returns null if the
   * query fails due to a missing key.
   *
   * @param ksonPointer The JSON pointer
   * @return the queried value or `null`
   * @throws IllegalArgumentException if `jsonPointer` has invalid syntax
   */
  fun optQuery(ksonPointer: KsonPointer): Any? {
    return try {
      ksonPointer.queryFrom(this)
    } catch (e: KsonPointerException) {
      null
    }
  }

  /**
   * Determine if two [KsonArray] are similar. They must contain similar sequences.
   *
   * @param other The [KsonArray] to compare
   * @return true if they are equal
   */
  fun similar(other: Any?): Boolean {
    if (other !is KsonArray) {
      return false
    }
    val len = size
    if (len != other.size) {
      return false
    }
    var i = 0
    while (i < len) {
      val valueThis = this[i]
      val valueOther = other[i]
      if (valueThis === valueOther) {
        i += 1
        continue
      }
      if (valueThis is Kson) {
        if (!valueThis.similar(valueOther)) {
          return false
        }
      } else if (valueThis is KsonArray) {
        if (!valueThis.similar(valueOther)) {
          return false
        }
      } else if (valueThis != valueOther) {
        return false
      }
      i += 1
    }
    return true
  }

  /**
   * Produce a [Kson] by combining a [KsonArray] of names with the values of this
   * [KsonArray].
   *
   * @param names A [KsonArray] containing a list of key strings. These will be paired with
   * the values.
   * @return A [Kson], or null if there are no names or if this [KsonArray] has no
   * values.
   */
  fun toKson(names: KsonArray): Kson? {
    if (names.isEmpty() || this.isEmpty()) {
      return null
    }
    val jo = Kson(names.size)
    var i = 0
    while (i < names.size) {
      jo[names.getString(i)!!] = get(i)!!
      i += 1
    }
    return jo
  }

  /**
   * Make a JSON text of this [KsonArray]. For compactness, no unnecessary
   * whitespace is added. If it is not possible to produce a syntactically
   * correct JSON text then null will be returned instead. This could occur if
   * the array contains an invalid number.
   *
   * Warning: This method assumes that the data structure is acyclical.
   *
   *
   * @return a printable, displayable, transmittable representation of the array.
   */
  override fun toString(): String {
    return this.toString(0)
  }

  /**
   * Make a pretty-printed JSON text of this KsonArray.
   *
   * If <code>indentFactor > 0</code> and the {@link KsonArray} has only
   * one element, then the array will be output on a single line:
   * <pre>{@code [1]}</pre>
   *
   * If an array has 2 or more elements, then it will be output across
   * multiple lines:
   * ```json
   * {
   *   [
   *     1,
   *     "value 2",
   *     3
   *   ]
   * }
   * ```
   *
   * Warning: This method assumes that the data structure is acyclical.
   *
   *
   * @param indentFactor
   *            The number of spaces to add to each level of indentation.
   * @return a printable, displayable, transmittable representation of the
   *         object, beginning with <code>[</code> (left
   *         bracket) and ending with <code>]</code>
   *          (right bracket).
   * @throws KsonException
   */
  fun toString(indentFactor: Int): String {
    val sw = StringWriter()
    synchronized(sw.buffer) { return write(sw, indentFactor, 0).toString() }
  }

  /**
   * Write the contents of the KsonArray as JSON text to a writer.
   *
   * If `indentFactor > 0` and the [KsonArray] has only
   * one element, then the array will be output on a single line:
   * <pre>{@code [1]}</pre>
   *
   * If an array has 2 or more elements, then it will be output across
   * multiple lines:
   * ```json
   * [1, "value 2", 3]
   * ```
   *
   * Warning: This method assumes that the data structure is acyclical.
   *
   * @param writer Writes the serialized JSON
   * @param indentFactor The number of spaces to add to each level of indentation.
   * @param indent
   *            The indentation of the top level.
   * @return The writer.
   * @throws KsonException
   */
  @JvmOverloads
  fun write(writer: Writer, indentFactor: Int = 0, indent: Int = 0): Writer {
    return try {
      var needsComma = false
      val length = size
      writer.write('['.toInt())
      if (length == 1) {
        try {
          writeValue(
            writer, this[0],
            indentFactor, indent
          )
        } catch (e: Exception) {
          throw KsonException("Unable to write [KsonArray] value at index: 0", e)
        }
      } else if (length != 0) {
        val newIndent = indent + indentFactor
        var i = 0
        while (i < length) {
          if (needsComma) {
            writer.write(','.toInt())
          }
          if (indentFactor > 0) {
            writer.write('\n'.toInt())
          }
          indent(writer, newIndent)
          try {
            writeValue(
              writer, this[i],
              indentFactor, newIndent
            )
          } catch (e: Exception) {
            throw KsonException("Unable to write [KsonArray] value at index: $i", e)
          }
          needsComma = true
          i += 1
        }
        if (indentFactor > 0) {
          writer.write('\n'.toInt())
        }
        indent(writer, indent)
      }
      writer.write(']'.toInt())
      writer
    } catch (e: IOException) {
      throw KsonException(e)
    }
  }
}
