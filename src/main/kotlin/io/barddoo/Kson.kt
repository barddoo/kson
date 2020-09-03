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

import java.io.Closeable
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.regex.Pattern


/**
 * A Kson is an unordered collection of name/value pairs. Its external form is a
 * string wrapped in curly braces with colons between the names and values, and commas between the
 * values and names. The internal form is an object having `get` and `opt`
 * methods for accessing the values by name, and `put` methods for adding or replacing
 * values by name. The values can be any of these types: `Boolean`,
 * `KsonArray`, `Kson`, `Number`,
 * `String`, or the `Kson.NULL` object. A
 * Kson constructor can be used to convert an external form JSON text into an
 * internal form whose values can be retrieved with the
 * `get` and `opt` methods, or to convert values into a
 * JSON text using the `put` and `toString` methods. A
 * `get` method returns a value if one can be found, and throws an
 * exception if one cannot be found. An `opt` method returns a default value instead of
 * throwing an exception, and so is useful for obtaining optional values.
 *
 *
 * The generic `get()` methods return an object, which you can cast or query for type.
 * There are also typed `get` methods that do type checking and type
 * coercion for you. The opt methods differ from the get methods in that they do not throw. Instead,
 * they return a specified value, such as null.
 *
 *
 * The `put` methods add or replace values in an object. For example,
 *
 * `myString = new Kson().put("JSON", "Hello, World!").toString();`
 *
 * produces the string `{"JSON": "Hello, World"}`.
 *
 * The texts produced by the `toString` methods strictly conform to the JSON syntax
 * rules. The constructors are more forgiving in the texts they will accept:
 *
 *  * An extra `,` (comma) may appear just
 * before the closing brace.
 *  * Strings may be quoted with `'` (single
 * quote).
 *  * Strings do not need to be quoted at all if they do not begin with a
 * quote or single quote, and if they do not contain leading or trailing
 * spaces, and if they do not contain any of these characters:
 * `{ } [ ] / \ : , #` and if they do not look like numbers and
 * if they are not the reserved words `true`, `false`,
 * or `null`.
 *
 *
 * @author Charles Fonseca
 * @version 1.0.0
 */
class Kson() : LinkedHashMap<String, Any?>() {
    /**
     * Construct a Kson from a subset of another Kson. An array of
     * strings is used to identify the keys that should be copied. Missing keys are ignored.
     *
     * @param jo    A Kson.
     * @param names An array of strings.
     */
    constructor(jo: Kson, vararg names: String) : this(names.size) {
        for (name in names) {
            put(name, jo[name] ?: NULL)
        }
    }

    /**
     * Construct a Kson from a JSONTokener.
     *
     * @param x A JSONTokener object containing the source string.
     */
    constructor(x: KsonTokener) : this() {
        var c: Char
        var key: String
        if (x.nextClean() != '{') {
            throw x.syntaxError("A Kson text must begin with '{'")
        }
        while (true) {
            c = x.nextClean()
            key = when (c) {
                '0' -> throw x.syntaxError("A Kson text must end with '}'")
                '}' -> return
                else -> {
                    x.back()
                    x.nextValue().toString()
                }
            }

            // The key is followed by ':'.
            c = x.nextClean()
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key")
            }

            // Use syntaxError(..) to include error location
            // Check if key exists
            if (get(key) != null) {
                // key already exists
                throw x.syntaxError("Duplicate key '$key'")
            }
            // Only add value if non-null
            val value: Any? = x.nextValue()
            if (value != null) {
                this[key] = value
            }
            when (x.nextClean()) {
                ';', ',' -> {
                    if (x.nextClean() == '}') {
                        return
                    }
                    x.back()
                }
                '}' -> return
                else -> throw x.syntaxError("Expected a ',' or '}'")
            }
        }
    }

    /**
     * Construct a [Kson] from a Map.
     *
     * @param map A map object that can be used to initialize the contents of the [Kson]. If
     * the key null, it will be ignored.
     */
    constructor(map: Map<*, *>) : this() {
        for ((key, value) in map) {
            if (key != null)
                this[key.toString()] = wrap(value)
            else throw KsonException("json Key must not be null")
        }
    }

    /**
     * Construct a Kson from an Object using bean getters. It reflects on all of the
     * public methods of the object. For each of the methods with no parameters and a name starting
     * with `"get"` or
     * `"is"` followed by an uppercase letter, the method is invoked,
     * and a key and the value returned from the getter method are put into the new
     * Kson.
     *
     *
     * The key is formed by removing the `"get"` or `"is"` prefix. If the second
     * remaining character is not upper case, then the first character is converted to lower case.
     *
     *
     * Methods that are `static`, return `void`, have parameters, or are
     * "bridge" methods, are ignored.
     *
     *
     * For example, if an object has a method named `"getName"`, and if the result of
     * calling `object.getName()` is
     * `"Larry Fine"`, then the Kson will contain
     * `"name": "Larry Fine"`.
     *
     *
     * The [KsonPropertyName] annotation can be used on a bean getter to override key name used
     * in the Kson. For example, using the object above with the `getName`
     * method, if we annotated it with:
     * <pre>
     * &#64;JSONPropertyName("FullName")
     * public String getName() { return this.name; }
     * </pre>
     * The resulting JSON object would contain `"FullName": "Larry Fine"`
     *
     * Similarly, the [KsonPropertyName] annotation can be used on non-
     * `get` and `is` methods. We can also override key
     * name used in the Kson as seen below even though the field would normally be
     * ignored:
     * <pre>
     * &#64;JSONPropertyName("FullName")
     * public String fullName() { return this.name; }
    </pre> *
     * The resulting JSON object would contain `"FullName": "Larry Fine"`
     *
     *
     * The [KsonPropertyIgnore] annotation can be used to force the bean property to not be
     * serialized into JSON. If both [KsonPropertyIgnore] and [KsonPropertyName] are
     * defined on the same method, a depth comparison is performed and the one closest to the concrete
     * class being serialized is used. If both annotations are at the same level, then the [ ] annotation takes precedent and the field is not serialized. For example,
     * the following declaration would prevent the `getName` method from being serialized:
     * &#64;JSONPropertyName("FullName")
     * &#64;JSONPropertyIgnore
     * public String getName() { return this.name; }
     *
     * @param bean An object that has getter methods that should be used to make a
     * Kson.
     */
    constructor(bean: Any) : this() {
        populateMap(bean)
    }

    /**
     * Construct a Kson from an Object, using reflection to find the public members.
     * The resulting Kson's keys will be the strings from the names array, and the
     * values will be the field values associated with those keys in the object. If a key is not found
     * or not visible, then it will not be copied into the new Kson.
     *
     * @param `object` An object that has fields that should be used to make a Kson.
     * @param names  An array of strings, the names of the fields to be obtained from the object.
     */
    constructor(other: Any, vararg names: String?) : this(names.size) {
        val c: Class<*> = other::class.java
        var i = 0
        while (i < names.size) {
            val name = names[i]
            try {
                if (name != null) {
                    put(name, c.getField(name)[other])
                }
            } catch (ignore: Exception) {
            }
            i += 1
        }
    }

    /**
     * Construct a Kson from a source JSON text string. This is the most commonly used
     * Kson constructor.
     *
     * @param source A string beginning with `{` (left brace) and
     * ending with `}`  (right brace).
     */
    constructor(source: String) : this(KsonTokener(source))

    /**
     * Construct a Kson from a ResourceBundle.
     *
     * @param baseName The ResourceBundle base name.
     * @param locale   The Locale to load the ResourceBundle for.
     * @throws KsonException If any JSONExceptions are detected.
     */
    constructor(baseName: String, locale: Locale) : this() {
        val bundle = ResourceBundle.getBundle(
            baseName, locale,
            Thread.currentThread().contextClassLoader
        )

        // Iterate through the keys in the bundle.
        val keys = bundle.keys
        while (keys.hasMoreElements()) {
            val key: Any? = keys.nextElement()
            if (key != null) {

                // Go through the path, ensuring that there is a nested Kson for each
                // segment except the last. Add the value using the last segment's name into
                // the deepest nested Kson.
                val path = (key as String).split("\\.".toRegex()).toTypedArray()
                val last = path.size - 1
                var target = this
                var i = 0
                while (i < last) {
                    val segment = path[i]
                    var nextTarget = target.json(segment)
                    if (nextTarget == null) {
                        nextTarget = Kson()
                        target[segment] = nextTarget
                    }
                    target = nextTarget
                    i += 1
                }
                target[path[last]] = bundle.getString(key)
            }
        }
    }

    /**
     * Accumulate values under a key. It is similar to the put method except that if there is already
     * an object stored under the key then a KsonArray is stored under the key to hold all of
     * the accumulated values. If there is already a KsonArray, then the new value is
     * appended to it. In contrast, the put method replaces the previous value.
     *
     *
     * If only one value is accumulated that is not a KsonArray, then the result will be the
     * same as using put. But if multiple values are accumulated, then the result will be like
     * append.
     *
     * @param key   A key string.
     * @param value An object to be accumulated under the key.
     * @return this.
     */
    fun accumulate(key: String, value: Any): Kson {
        testValidity(value)
        when (val any = get(key)) {
            null -> {
                this[key] = if (value is KsonArray) KsonArray().add(value) else value
            }
            is KsonArray -> {
                any.add(value)
            }
            else -> {
                this[key] = KsonArray().apply {
                    add(any)
                    add(value)
                }
            }
        }
        return this
    }

    /**
     * Append values to the array under a key. If the key does not exist in the Kson,
     * then the key is put in the Kson with its value being a KsonArray
     * containing the value parameter. If the key was already associated with a KsonArray,
     * then the value parameter is appended to it.
     *
     * @param key   A key string.
     * @param value An object to be accumulated under the key.
     * @return this.
     * @throws KsonException        If the value is non-finite number or if the current value
     * associated with the key is not a KsonArray.
     */
    fun append(key: String, value: Any): Kson {
        testValidity(value)
        when (val any = get(key)) {
            null -> {
                this[key] = KsonArray().add(value)
            }
            is KsonArray -> {
                this[key] = any.add(value)
            }
            else -> {
                throw wrongValueFormatException(key, "KsonArray")
            }
        }
        return this
    }

    /**
     * Determine if the Kson contains a specific key.
     *
     * @param key A key string.
     * @return true if the key exists in the Kson.
     */
    fun has(key: String): Boolean {
        return this.containsKey(key)
    }

    /**
     * Increment a property of a Kson. If there is no such property, create one with a
     * value of 1 (Integer). If there is such a property, and if it is an Integer, Long, Double,
     * Float, BigInteger, or BigDecimal then add one to it. No overflow bounds checking is performed,
     * so callers should initialize the key prior to this call with an appropriate type that can
     * handle the maximum expected value.
     *
     * @param key A key string.
     * @return this.
     * Long, Double, or Float.
     */
    fun increment(key: String): Kson {
        when (val value = get(key)) {
            null -> {
                this[key] = 1 as Any
            }
            is Int -> {
                this[key] = value.toInt() + 1
            }
            is Long -> {
                this[key] = value.toLong() + 1L
            }
            is BigInteger -> {
                this[key] = value.add(BigInteger.ONE)
            }
            is Float -> {
                this[key] = value.toFloat() + 1.0F
            }
            is Double -> {
                this[key] = value.toDouble() + 1.0
            }
            is BigDecimal -> {
                this[key] = value.add(BigDecimal.ONE)
            }
            else -> {
                throw KsonException("Unable to increment [" + quote(key) + "].")
            }
        }
        return this
    }

    /**
     * Determine if the value associated with the key is `null` or if there is no value.
     *
     * @param key A key string.
     * @return true if there is no value associated with the key or if the value is the
     * [NULL] object.
     */
    fun isNull(key: String): Boolean {
        return NULL == get(key)
    }

    /**
     * Produce a KsonArray containing the names of the elements of this Kson.
     *
     * @return A KsonArray containing the key strings
     *
     */
    fun names(): KsonArray {
        return KsonArray(this.keys)
    }

    inline fun <reified E> getBy(key: String, defaultValue: E? = null): E? {
        val any = this[key]
        return if (any is E) any else defaultValue
    }

    inline fun <reified E> iteratorBy(): Iterator<E> {
        return this.values.filterIsInstance<E>().iterator()
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param <E>          Enum Type
     * @param clazz        The type of enum to retrieve.
     * @param key          A key string.
     * @param defaultValue The default in case the value is not found
     * @return The enum value associated with the key or defaultValue if the value is not found or
     * cannot be assigned to `clazz`
    </E> */
    @JvmOverloads
    inline fun <reified E : Enum<E>?> getEnum(clazz: Class<E>, key: String, defaultValue: E? = null): E? {
        return try {
            val any = get(key)
            if (NULL == any) {
                return defaultValue
            }
            if (clazz.isAssignableFrom(any!!.javaClass)) {
                // we just checked it!
                if (any is E) {
                    return any
                }
            }
            return java.lang.Enum.valueOf(clazz, any.toString())
        } catch (e: IllegalArgumentException) {
            defaultValue
        } catch (e: NullPointerException) {
            defaultValue
        }
    }

    /**
     * Get the BigDecimal value associated with a key. If the value is float or
     * double, the the [BigDecimal] constructor will
     * be used. See notes on the constructor for conversion issues that may
     * arise.
     *
     * @param key A key string.
     * @return The numeric value.
     *
     */
    @JvmOverloads
    fun getBigDecimal(key: String, defaultValue: BigDecimal? = null): BigDecimal? {
        return objectToBigDecimal(get(key), defaultValue)
    }

    @JvmOverloads
    fun getBigInteger(key: String, defaultValue: BigInteger? = null): BigInteger? {
        return objectToBigInteger(get(key), defaultValue)
    }

    /**
     * Get an optional long value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     * A key string.
     * @param defaultValue
     * The default.
     * @return An object which is the value.
     */
    @JvmOverloads
    fun getLong(key: String, defaultValue: Long? = null): Long? {
        return this.getNumber(key, null)?.toLong() ?: return defaultValue
    }

    /**
     * Get an optional double associated with a key, or NaN if there is no such
     * key or if its value is not a number. If the value is a string, an attempt
     * will be made to evaluate it as a number.
     *
     * @param key
     *            A string which is the key.
     * @return An object which is the value.
     */
    @JvmOverloads
    fun getDouble(key: String, defaultValue: Double? = null): Double? {
        return this.getNumber(key)?.toDouble() ?: return defaultValue
    }

    /**
     * Get the string associated with a key.
     *
     * @param key A key string.
     * @return A string which is the value.
     * @throws KsonException if there is no string value for the key.
     */
    @JvmOverloads
    fun getString(key: String, defaultValue: String? = null): String? {
        return getBy<String>(key) ?: defaultValue
    }

    /**
     * Get the int value associated with a key.
     *
     * @param key
     * A key string.
     * @return The integer value.
     * @throws KsonException
     * if the key is not found or if the value cannot be converted
     * to an integer.
     */
    @JvmOverloads
    fun getInt(key: String, defaultValue: Int? = null): Int? {
        return this.getNumber(key, null)?.toInt() ?: return defaultValue
    }

    /**
     * Get the optional double value associated with an index. The defaultValue
     * is returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param key
     * A key string.
     * @param defaultValue
     * The default value.
     * @return The value.
     */
    @JvmOverloads
    fun getFloat(key: String, defaultValue: Float? = null): Float? {
        return this.getNumber(key)?.toFloat() ?: return defaultValue
    }

    /**
     * Get an optional boolean associated with a key. It returns false if there is no such key, or if
     * the value is not Boolean.TRUE or the String "true".
     *
     * @param key A key string.
     * @return The truth.
     */
    @JvmOverloads
    fun getBoolean(key: String, defaultValue: Boolean? = null): Boolean? {
        return when (val any = get(key)) {
            is Boolean -> {
                any
            }
            is String -> {
                when {
                    "true".equals(any, ignoreCase = true) -> true
                    "false".equals(any, ignoreCase = true) -> false
                    else -> null
                }
            }
            else -> defaultValue
        }
    }

    /**
     * Get an optional KsonArray associated with a key. It returns null if there is no such
     * key, or if its value is not a KsonArray.
     *
     * @param key A key string.
     * @return A KsonArray which is the value.
     */
    fun array(key: String): KsonArray? {
        return getBy<KsonArray>(key)
    }

    /**
     * Get an optional Kson associated with a key. It returns null if there is no such
     * key, or if its value is not a Kson.
     *
     * @param key A key string.
     * @return A Kson which is the value.
     */
    fun json(key: String): Kson? {
        return getBy<Kson>(key)
    }

    /**
     * Get an optional [Number] value associated with a key, or `null` if there is no
     * such key or if the value is not a number. If the value is a string, an attempt will be made to
     * evaluate it as a number ([BigDecimal]). This method would be used in cases where type
     * coercion of the number value is unwanted.
     *
     * @param key A key string.
     * @return An object which is the value.
     */
    @JvmOverloads
    fun getNumber(key: String, defaultValue: Number? = null): Number? {
        val any = get(key)
        if (NULL == any) {
            return defaultValue
        }
        return if (any is Number) {
            any
        } else try {
            stringToNumber(any.toString())
        } catch (e: Throwable) {
            defaultValue
        }
    }

    /**
     * Populates the internal map of the Kson with the bean properties. The bean can
     * not be recursive.
     *
     * @param bean the bean
     * @see Kson
     */
    private fun populateMap(bean: Any) {
        val klass: Class<*> = bean.javaClass

        // If klass is a System class then set includeSuperClass to false.
        val includeSuperClass = klass.classLoader != null
        val methods = if (includeSuperClass) klass.methods else klass.declaredMethods
        for (method in methods) {
            val modifiers = method.modifiers
            if (Modifier.isPublic(modifiers)
                && !Modifier.isStatic(modifiers)
                && method.parameterTypes.isEmpty() && !method.isBridge
                && method.returnType != Void.TYPE && isValidMethodName(method.name)
            ) {
                val key = getKeyNameFromMethod(method)
                if (key != null && key.isNotEmpty()) {
                    try {
                        val result = method.invoke(bean)
                        if (result != null) {
                            this[key] = wrap(result)
                            // we don't use the result anywhere outside of wrap
                            // if it's a resource we should be sure to close it
                            // after calling toString
                            if (result is Closeable) {
                                try {
                                    result.close()
                                } catch (ignore: IOException) {
                                }
                            }
                        }
                    } catch (ignore: IllegalAccessException) {
                    } catch (ignore: IllegalArgumentException) {
                    } catch (ignore: InvocationTargetException) {
                    }
                }
            }
        }
    }

    /**
     * Put a key/value pair in the Kson, where the value will be a KsonArray
     * which is produced from a Collection.
     *
     * @param key   A key string.
     * @param value A Collection value.
     * @return this
     * @throws KsonException        If the value is non-finite number.
     */
    fun <T> put(key: String, value: Collection<T?>?): Any? {
        return super.put(key, KsonArray(value?.mapToJsonNull() ?: NULL))
    }

    /**
     * Put a key/value pair in the Kson, where the value will be a Kson
     * which is produced from a Map.
     *
     * @param key   A key string.
     * @param value A Map value.
     * @return this.
     * @throws KsonException        If the value is non-finite number.
     */
    fun put(key: String, value: Map<*, *>): Any? {
        return super.put(key, Kson(value))
    }

    override fun put(key: String, value: Any?): Any? {
        testValidity(value)
        return super.put(key, value ?: NULL)
    }

    /**
     * Queries and returns a value from this object using `ksonPointer`, or
     * returns null if the query fails due to a missing key.
     *
     * @param ksonPointer the string representation of the JSON pointer
     * @return the queried value or `null`
     * @throws IllegalArgumentException if `ksonPointer` has invalid syntax
     */
    fun query(jsonPointer: String): Any? {
        return query(KsonPointer(jsonPointer))
    }

    /**
     * Queries and returns a value from this object using `ksonPointer`, or
     * returns null if the query fails due to a missing key.
     *
     * @param ksonPointer The JSON pointer
     * @return the queried value or `null`
     * @throws IllegalArgumentException if `ksonPointer` has invalid syntax
     */
    fun query(ksonPointer: KsonPointer): Any? {
        return try {
            ksonPointer.queryFrom(this)
        } catch (e: KsonPointerException) {
            null
        }
    }

    /**
     * Determine if two Ksons are similar. They must contain the same set of names which must be
     * associated with similar values.
     *
     * @param other The other Kson
     * @return true if they are equal
     */
    fun similar(other: Any?): Boolean {
        if (other !is Kson) {
            return false
        }
        if (keys != other.keys) {
            return false
        }
        for ((name, valueThis) in entries) {
            val valueOther = other[name]
            if (valueThis === valueOther) {
                continue
            }
            when {
                valueThis is Kson -> {
                    if (!valueThis.similar(valueOther)) {
                        return false
                    }
                }
                valueThis is KsonArray -> {
                    if (!valueThis.similar(valueOther)) {
                        return false
                    }
                }
                valueThis != valueOther -> {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Produce a KsonArray containing the values of the members of this Kson.
     *
     * @param names A KsonArray containing a list of key strings. This determines the
     * sequence of the values in the result.
     * @return A KsonArray of values.
     * @throws KsonException If any of the values are non-finite numbers.
     */
    fun toKsonArray(names: KsonArray?): KsonArray? {
        val ja = KsonArray()
        if (names == null) return null
        for (elem in names) ja.add(getString(elem.toString())!!)
        return ja
    }

    /**
     * Make a JSON text of this Kson. For compactness, no whitespace is added. If this
     * would not result in a syntactically correct JSON text, then null will be returned instead.
     *
     * **
     * Warning: This method assumes that the data structure is acyclical.
     ** *
     *
     * @return a printable, displayable, portable, transmittable representation of the object,
     * beginning with `{` (left brace) and ending with
     * `}` (right brace).
     */
    override fun toString(): String {
        return this.toString(0)
    }

    /**
     * Make a pretty-printed JSON text of this Kson.
     *
     *
     * If `indentFactor > 0` and the [Kson]
     * has only one key, then the object will be output on a single line:
     * <pre>`{"key": 1}`</pre>
     *
     *
     * If an object has 2 or more keys, then it will be output across
     * multiple lines: `<pre>{
     * "key1": 1,
     * "key2": "value 2",
     * "key3": 3
     * }</pre>`
     *
     * **
     * Warning: This method assumes that the data structure is acyclical.
     ** *
     *
     * @param indentFactor The number of spaces to add to each level of indentation.
     * @return a printable, displayable, portable, transmittable representation of the object,
     * beginning with `{` (left brace) and ending with
     * `}` (right brace).
     * @throws KsonException If the object contains an invalid number.
     */
    fun toString(indentFactor: Int): String {
        val w = StringWriter()
        synchronized(w.buffer) { return write(w, indentFactor, 0).toString() }
    }

    /**
     * Write the contents of the Kson as JSON text to a writer.
     *
     *
     * If `indentFactor > 0` and the [Kson]
     * has only one key, then the object will be output on a single line:
     * <pre>`{"key": 1}`</pre>
     *
     *
     * If an object has 2 or more keys, then it will be output across
     * multiple lines: `<pre>{
     * "key1": 1,
     * "key2": "value 2",
     * "key3": 3
     * }</pre>`
     *
     * **
     * Warning: This method assumes that the data structure is acyclical.
     *
     *
     * @param writer       Writes the serialized JSON
     * @param indentFactor The number of spaces to add to each level of indentation.
     * @param indent       The indentation of the top level.
     * @return The writer.
     * @throws KsonException
     */
    @JvmOverloads
    fun write(writer: Writer, indentFactor: Int = 0, indent: Int = 0): Writer {
        var needsComma = false
        writer.write("{")
        when {
            size == 1 -> {
                val entry: Map.Entry<String, *> = entries.iterator().next()
                val key = entry.key
                writer.write(quote(key))
                writer.write(":")
                if (indentFactor > 0) {
                    writer.write(" ")
                }
                try {
                    writeValue(writer, entry.value, indentFactor, indent)
                } catch (e: Throwable) {
                    throw KsonException("Unable to write Kson value for key: $key", e)
                }
            }
            size != 0 -> {
                val newIndent = indent + indentFactor
                for ((key, value) in entries) {
                    if (needsComma) {
                        writer.write(",")
                    }
                    if (indentFactor > 0) {
                        writer.write("\n")
                    }
                    indent(writer, newIndent)
                    writer.write(quote(key))
                    writer.write(":")
                    if (indentFactor > 0) {
                        writer.write(" ")
                    }
                    try {
                        writeValue(writer, value, indentFactor, newIndent)
                    } catch (e: Throwable) {
                        throw KsonException("Unable to write Kson value for key: $key", e)
                    }
                    needsComma = true
                }
                if (indentFactor > 0) {
                    writer.write("\n")
                }
                indent(writer, indent)
            }
        }
        writer.write("}")
        return writer
    }

    /**
     * Returns a java.util.Map containing all of the entries in this object. If an entry in the object
     * is a KsonArray or Kson it will also be converted.
     *
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.Map containing the entries of this object
     */
    fun toMap(): Map<String, Any?> {
        val results: MutableMap<String, Any?> = HashMap()
        for ((key, value1) in entries) {
            val value: Any? = when {
                NULL == value1 -> {
                    null
                }
                value1 is Kson -> {
                    value1.toMap()
                }
                value1 is KsonArray -> {
                    value1.toList()
                }
                else -> {
                    value1
                }
            }
            results[key] = value
        }
        return results
    }

    companion object {
        /**
         * Regular Expression Pattern that matches JSON Numbers. This is primarily used for output to
         * guarantee that we are always writing valid JSON.
         */
        val NUMBER_PATTERN: Pattern = Pattern
            .compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")

        /**
         * Produce a string from a double. The string "null" will be returned if the number is not
         * finite.
         *
         * @param d A double.
         * @return A String.
         */
        @JvmStatic
        fun doubleToString(d: Double): String {
            if (java.lang.Double.isInfinite(d) || java.lang.Double.isNaN(d)) {
                return "null"
            }

            // Shave off trailing zeros and decimal point, if possible.
            var string = d.toString()
            if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string.indexOf('E') < 0) {
                while (string.endsWith("0")) {
                    string = string.substring(0, string.length - 1)
                }
                if (string.endsWith(".")) {
                    string = string.substring(0, string.length - 1)
                }
            }
            return string
        }

        /**
         * Get an array of field names from a Kson.
         *
         * @param jo JSON object
         * @return An array of field names, or null if there are no names.
         */
        @JvmStatic
        fun getNames(jo: Kson): Array<String?>? {
            return if (jo.isEmpty()) {
                null
            } else jo.keys.toTypedArray()
        }

        /**
         * Get an array of public field names from an Object.
         *
         * @param any object to read
         * @return An array of field names, or null if there are no names.
         */
        @JvmStatic
        fun getNames(any: Any?): Array<String?>? {
            if (any == null) {
                return null
            }
            val klass: Class<*> = any.javaClass
            val fields = klass.fields
            val length = fields.size
            if (length == 0) {
                return null
            }
            val names = arrayOfNulls<String>(length)
            var i = 0
            while (i < length) {
                names[i] = fields[i].name
                i += 1
            }
            return names
        }

        /**
         * Produce a string from a Number.
         *
         * @param number A Number
         * @return A String.
         * @throws KsonException If n is a non-finite number.
         */
        @JvmStatic
        fun numberToString(number: Number): String {
            testValidity(number)

            // Shave off trailing zeros and decimal point, if possible.
            var string = number.toString()
            if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string.indexOf('E') < 0) {
                while (string.endsWith("0")) {
                    string = string.substring(0, string.length - 1)
                }
                if (string.endsWith(".")) {
                    string = string.substring(0, string.length - 1)
                }
            }
            return string
        }

        /**
         * @param val          value to convert
         * @param defaultValue default value to return is the conversion doesn't work or is null.
         * @return BigDecimal conversion of the original value, or the defaultValue if unable to convert.
         */
        @JvmStatic
        fun objectToBigDecimal(`val`: Any?, defaultValue: BigDecimal?): BigDecimal? {
            if (NULL == `val`) {
                return defaultValue
            }
            if (`val` is BigDecimal) {
                return `val`
            }
            if (`val` is BigInteger) {
                return BigDecimal(`val` as BigInteger?)
            }
            if (`val` is Double || `val` is Float) {
                val d: Double = (`val` as Number).toDouble()
                return if (java.lang.Double.isNaN(d)) {
                    defaultValue
                } else BigDecimal(`val`.toDouble())
            }
            return if (`val` is Long || `val` is Int
                || `val` is Short || `val` is Byte
            ) {
                BigDecimal((`val` as Number).toLong())
            } else try {
                BigDecimal(`val`.toString())
            } catch (e: Throwable) {
                defaultValue
            }
            // don't check if it's a string in case of unchecked Number subclasses
        }

        /**
         * @param val          value to convert
         * @param defaultValue default value to return is the conversion doesn't work or is null.
         * @return BigInteger conversion of the original value, or the defaultValue if unable to convert.
         */
        @JvmStatic
        fun objectToBigInteger(`val`: Any?, defaultValue: BigInteger? = null): BigInteger? {
            if (NULL == `val`) {
                return defaultValue
            }
            if (`val` is BigInteger) {
                return `val`
            }
            if (`val` is BigDecimal) {
                return `val`.toBigInteger()
            }
            if (`val` is Double || `val` is Float) {
                val d: Double = (`val` as Number).toDouble()
                return if (java.lang.Double.isNaN(d)) {
                    defaultValue
                } else BigDecimal(d).toBigInteger()
            }
            return if (`val` is Long || `val` is Int
                || `val` is Short || `val` is Byte
            ) {
                BigInteger.valueOf((`val` as Number).toLong())
            } else try {
                // the other opt functions handle implicit conversions, i.e.
                // jo.put("double",1.1d);
                // jo.optInt("double"); -- will return 1, not an error
                // this conversion to BigDecimal then to BigInteger is to maintain
                // that type cast support that may truncate the decimal.
                val valStr = `val`.toString()
                if (isDecimalNotation(valStr)) {
                    BigDecimal(valStr).toBigInteger()
                } else BigInteger(valStr)
            } catch (e: Throwable) {
                defaultValue
            }
            // don't check if it's a string in case of unchecked Number subclasses
        }

        private fun isValidMethodName(name: String): Boolean {
            return "getClass" != name && "getDeclaringClass" != name
        }

        private fun getKeyNameFromMethod(method: Method): String? {
            val ignoreDepth = getAnnotationDepth(method, KsonPropertyIgnore::class.java)
            if (ignoreDepth > 0) {
                val forcedNameDepth = getAnnotationDepth(method, KsonPropertyName::class.java)
                if (forcedNameDepth < 0 || ignoreDepth <= forcedNameDepth) {
                    // the hierarchy asked to ignore, and the nearest name override
                    // was higher or non-existent
                    return null
                }
            }
            val annotation = getAnnotation(method, KsonPropertyName::class.java)
            if (annotation?.value != null && annotation.value.isNotEmpty()) {
                return annotation.value
            }
            var key: String
            val name = method.name
            key = when {
                name.startsWith("get") && name.length > 3 -> {
                    name.substring(3)
                }
                name.startsWith("is") && name.length > 2 -> {
                    name.substring(2)
                }
                else -> {
                    return null
                }
            }
            // if the first letter in the key is not uppercase, then skip.
            // This is to maintain backwards compatibility before PR406
            // (https://github.com/stleary/JSON-java/pull/406/)
            if (Character.isLowerCase(key[0])) {
                return null
            }
            if (key.length == 1) {
                key = key.toLowerCase(Locale.ROOT)
            } else if (!Character.isUpperCase(key[1])) {
                key = key.substring(0, 1).toLowerCase(Locale.ROOT) + key.substring(1)
            }
            return key
        }

        /**
         * Searches the class hierarchy to see if the method or it's super implementations and interfaces
         * has the annotation.
         *
         * @param A type of the annotation
         * @param m method to check
         * @param annotationClass annotation to look for
         * @return the [Annotation] if the annotation exists on the current method or one of it's
         * super class definitions
        </A> */
        private fun <A : Annotation?> getAnnotation(
            m: Method?,
            annotationClass: Class<A>?,
        ): A? {
            // if we have invalid data the result is null
            if (m == null || annotationClass == null) {
                return null
            }
            if (m.isAnnotationPresent(annotationClass)) {
                return m.getAnnotation(annotationClass)
            }

            // if we've already reached the Object class, return null;
            val c = m.declaringClass
            if (c.superclass == null) {
                return null
            }

            // check directly implemented interfaces for the method being checked
            for (i in c.interfaces) {
                return try {
                    val im = i.getMethod(m.name, *m.parameterTypes)
                    getAnnotation(im, annotationClass)
                } catch (ex: SecurityException) {
                    continue
                } catch (ex: NoSuchMethodException) {
                    continue
                }
            }
            return try {
                getAnnotation(
                    c.superclass.getMethod(m.name, *m.parameterTypes),
                    annotationClass
                )
            } catch (ex: SecurityException) {
                null
            } catch (ex: NoSuchMethodException) {
                null
            }
        }

        /**
         * Searches the class hierarchy to see if the method or it's super implementations and interfaces
         * has the annotation. Returns the depth of the annotation in the hierarchy.
         *
         * @param <A>             type of the annotation
         * @param m               method to check
         * @param annotationClass annotation to look for
         * @return Depth of the annotation or -1 if the annotation is not on the method.
        </A> */
        private fun getAnnotationDepth(
            m: Method?,
            annotationClass: Class<out Annotation>?,
        ): Int {
            // if we have invalid data the result is -1
            if (m == null || annotationClass == null) {
                return -1
            }
            if (m.isAnnotationPresent(annotationClass)) {
                return 1
            }

            // if we've already reached the Object class, return -1;
            val c = m.declaringClass
            if (c.superclass == null) {
                return -1
            }

            // check directly implemented interfaces for the method being checked
            for (i in c.interfaces) {
                try {
                    val im = i.getMethod(m.name, *m.parameterTypes)
                    val d = getAnnotationDepth(im, annotationClass)
                    if (d > 0) {
                        // since the annotation was on the interface, add 1
                        return d + 1
                    }
                } catch (ex: SecurityException) {
                    continue
                } catch (ex: NoSuchMethodException) {
                    continue
                }
            }
            return try {
                val d = getAnnotationDepth(
                    c.superclass.getMethod(m.name, *m.parameterTypes),
                    annotationClass
                )
                if (d > 0) {
                    // since the annotation was on the superclass, add 1
                    d + 1
                } else -1
            } catch (ex: SecurityException) {
                -1
            } catch (ex: NoSuchMethodException) {
                -1
            }
        }

        /**
         * Produce a string in double quotes with backslash sequences in all the right places. A backslash
         * will be inserted within  </, producing  <\/, allowing JSON text to be delivered in HTML. In
         * JSON text, a string cannot contain a control character or an unescaped quote or backslash.
         *
         * @param string A String
         * @return A String correctly formatted for insertion in a JSON text.
         */
        fun quote(string: String?): String {
            val sw = StringWriter()
            synchronized(sw.buffer) {
                return try {
                    quote(string, sw).toString()
                } catch (ignored: IOException) {
                    // will never happen - we are writing to a string writer
                    ""
                }
            }
        }

        fun quote(string: String?, w: Writer): Writer {
            if (string == null || string.isEmpty()) {
                w.write("\"\"")
                return w
            }
            var b: Char
            var c = 0.toChar()
            var hhhh: String
            val len = string.length
            w.write('"'.toInt())
            var i = 0
            while (i < len) {
                b = c
                c = string[i]
                when (c) {
                    '\\', '"' -> {
                        w.write('\\'.toInt())
                        w.write(c.toInt())
                    }
                    '/' -> {
                        if (b == '<') {
                            w.write('\\'.toInt())
                        }
                        w.write(c.toInt())
                    }
                    '\b' -> w.write("\\b")
                    '\t' -> w.write("\\t")
                    '\n' -> w.write("\\n")
                    '\r' -> w.write("\\r")
                    else -> if (c < ' ' || c in '\u0080'..''
                        || c in '\u2000'..'⃿'
                    ) {
                        w.write("\\u")
                        hhhh = Integer.toHexString(c.toInt())
                        w.write("0000", 0, 4 - hhhh.length)
                        w.write(hhhh)
                    } else {
                        w.write(c.toInt())
                    }
                }
                i += 1
            }
            w.write('"'.toInt())
            return w
        }

        /**
         * Tests if the value should be tried as a decimal. It makes no test if there are actual digits.
         *
         * @param val value to test
         * @return true if the string is "-0" or if it contains '.', 'e', or 'E', false otherwise.
         */
        fun isDecimalNotation(`val`: String): Boolean {
            return `val`.indexOf('.') > -1 || `val`.indexOf('e') > -1 || `val`.indexOf('E') > -1 || "-0" == `val`
        }

        /**
         * Converts a string to a number using the narrowest possible type. Possible returns for this
         * function are BigDecimal, Double, BigInteger, Long, and Integer. When a Double is returned, it
         * should always be a valid Double and not NaN or +-infinity.
         *
         * @param val value to convert
         * @return Number representation of the value.
         * @throws NumberFormatException thrown if the value is not a valid number. A public caller should
         * catch this and wrap it in a [KsonException] if applicable.
         */
        @JvmStatic
        fun stringToNumber(`val`: String): Number {
            val initial = `val`[0]
            if (initial in '0'..'9' || initial == '-') {
                // decimal representation
                if (isDecimalNotation(`val`)) {
                    // quick dirty way to see if we need a BigDecimal instead of a Double
                    // this only handles some cases of overflow or underflow
                    if (`val`.length > 14) {
                        return BigDecimal(`val`)
                    }
                    val d = java.lang.Double.valueOf(`val`)
                    return if (d.isInfinite() || d.isNaN()) {
                        // if we can't parse it as a double, go up to BigDecimal
                        // this is probably due to underflow like 4.32e-678
                        // or overflow like 4.65e5324. The size of the string is small
                        // but can't be held in a Double.
                        BigDecimal(`val`)
                    } else d
                }
                // integer representation.
                // This will narrow any values to the smallest reasonable Object representation
                // (Integer, Long, or BigInteger)

                // string version
                // The compare string length method reduces GC,
                // but leads to smaller integers being placed in larger wrappers even though not
                // needed. i.e. 1,000,000,000 -> Long even though it's an Integer
                // 1,000,000,000,000,000,000 -> BigInteger even though it's a Long
                //if(val.length()<=9){
                //    return Integer.valueOf(val);
                //}
                //if(val.length()<=18){
                //    return Long.valueOf(val);
                //}
                //return new BigInteger(val);

                // BigInteger version: We use a similar bitLength compare as
                // BigInteger#intValueExact uses. Increases GC, but objects hold
                // only what they need. i.e. Less runtime overhead if the value is
                // long lived. Which is the better tradeoff? This is closer to what's
                // in stringToValue.
                val bi = BigInteger(`val`)
                if (bi.bitLength() <= 31) {
                    return Integer.valueOf(bi.toInt())
                }
                return if (bi.bitLength() <= 63) {
                    java.lang.Long.valueOf(bi.toLong())
                } else bi
            }
            throw NumberFormatException("val [$`val`] is not a valid number.")
        }

        /**
         * Try to convert a string into a number, boolean, or null. If the string can't be converted,
         * return the string.
         *
         * @param string A String. can not be null.
         * @return A simple JSON value.
         */
        // Changes to this method must be copied to the corresponding method in
        // the XML class to keep full support for Android
        @JvmStatic
        fun stringToValue(string: String): Any {
            if ("" == string) {
                return string
            }

            // check JSON key words true/false/null
            if ("true".equals(string, ignoreCase = true)) {
                return java.lang.Boolean.TRUE
            }
            if ("false".equals(string, ignoreCase = true)) {
                return java.lang.Boolean.FALSE
            }
            if ("null".equals(string, ignoreCase = true)) {
                return NULL
            }

            /*
             * If it might be a number, try converting it. If a number cannot be
             * produced, then the value will just be a string.
             */
            val initial = string[0]
            if (initial in '0'..'9' || initial == '-') {
                try {
                    // if we want full Big Number support the contents of this
                    // `try` block can be replaced with:
                    // return stringToNumber(string);
                    if (isDecimalNotation(string)) {
                        val d = java.lang.Double.valueOf(string)
                        if (!d.isInfinite() && !d.isNaN()) {
                            return d
                        }
                    } else {
                        val myLong = java.lang.Long.valueOf(string)
                        if (string == myLong.toString()) {
                            return if (myLong.toLong() == myLong.toInt().toLong()) {
                                Integer.valueOf(myLong.toInt())
                            } else myLong
                        }
                    }
                } catch (ignore: Exception) {
                }
            }
            return string
        }

        /**
         * Throw an exception if the object is a NaN or infinite number.
         *
         * @param o The object to test.
         * @throws KsonException If o is a non-finite number.
         */
        @JvmStatic
        fun testValidity(o: Any?) {
            if (o != null) {
                when (o) {
                    is Double -> {
                        if (o.isInfinite() || o.isNaN()) {
                            throw KsonException(
                                "JSON does not allow non-finite numbers."
                            )
                        }
                    }
                    is Float -> {
                        if (o.isInfinite() || o.isNaN()) {
                            throw KsonException(
                                "JSON does not allow non-finite numbers."
                            )
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun writeValue(
            writer: Writer, value: Any?, indentFactor: Int,
            indent: Int,
        ): Writer {
            when {
                value == null -> {
                    writer.write("null")
                }
                value is KsonString -> {
                    writer.write(value.toJson())
                }
                value is Number -> {
                    // not all Numbers may match actual JSON Numbers. i.e. fractions or Imaginary
                    val numberAsString = numberToString(value)
                    if (NUMBER_PATTERN.matcher(numberAsString).matches()) {
                        writer.write(numberAsString)
                    } else {
                        // The Number value is not a valid JSON number.
                        // Instead we will quote it as a string
                        quote(numberAsString, writer)
                    }
                }
                value is Boolean -> {
                    writer.write(value.toString())
                }
                value is Enum<*> -> {
                    writer.write(quote(value.name))
                }
                value is Kson -> {
                    value.write(writer, indentFactor, indent)
                }
                value is KsonArray -> {
                    value.write(writer, indentFactor, indent)
                }
                value is Collection<*> || value::class.java.isArray ||
                    value is Map<*, *> -> {
                    KsonArray(value).write(writer, indentFactor, indent)
                }
                value is String -> {
                    quote(value, writer)
                }
                else -> {
                    writer.write(wrap(value).toString())
                }
            }
            return writer
        }

        @JvmStatic
        fun indent(writer: Writer, indent: Int) {
            var i = 0
            while (i < indent) {
                writer.write(' '.toInt())
                i += 1
            }
        }

        /**
         * Create a new JSONException in a common format for incorrect conversions.
         *
         * @param key       name of the key
         * @return JSONException that can be thrown.
         */
        private fun wrongValueFormatException(key: String): KsonException {
            return KsonException("Kson[" + quote(key) + "] is not a valid")
        }

        /**
         * Create a new [KsonException] in a common format for incorrect conversions.
         *
         * @param key name of the key
         * @param valueType the type of value being coerced to
         * @return JSONException that can be thrown.
         */
        private fun wrongValueFormatException(
            key: String?,
            valueType: String,
        ): KsonException {
            return KsonException("Kson[" + quote(key) + "] is not a " + valueType)
        }
    }
}

/**
 * [NULL] is equivalent to the value that JavaScript calls null, whilst Java's
 * null is equivalent to the value that JavaScript calls undefined.
 */
object NULL : Cloneable {
    /**
     * There is only intended to be a single instance of the NULL object, so the clone method
     * returns itself.
     *
     * @return NULL.
     */
    override fun clone(): Any {
        return this
    }

    /**
     * A Null object is equal to the null value and to itself.
     *
     * @param other An object to test for nullness.
     * @return true if the object parameter is the Kson.NULL object or null.
     */
    override fun equals(other: Any?): Boolean {
        return other == null || other === this
    }

    /**
     * A Null object is equal to the null value and to itself.
     *
     * @return always returns 0
     */
    override fun hashCode(): Int {
        return 0
    }

    /**
     * Get the "null" string value.
     *
     * @return The string "null"
     */
    override fun toString(): String {
        return "null"
    }
}

/**
 * Wrap an object, if necessary. If the object is `null`, return the NULL object. If it
 * is an array or collection, wrap it in a [KsonArray]. If it is a map, wrap it in a
 * [Kson]. If it is a standard property (Double, String, et al) then it is already
 * wrapped. Otherwise, if it comes from one of the java packages, turn it into a string. And if it
 * doesn't, try to wrap it in a [Kson]. If the wrapping fails, then null is
 * returned.
 *
 * @param any The object to wrap
 * @return The wrapped value
 */
fun wrap(any: Any?): Any {
    if (any == null) {
        return NULL
    }
    if (any is Kson || any is KsonArray
        || any is NULL || any is KsonString
        || any is Byte || any is Char
        || any is Short || any is Int
        || any is Long || any is Boolean
        || any is Float || any is Double
        || any is String || any is BigInteger
        || any is BigDecimal || any is Enum<*>
    ) {
        return any
    }
    if (any is Collection<*>) {
        return KsonArray(any)
    }
    if (any.javaClass.isArray) {
        return KsonArray(any)
    }
    if (any is Map<*, *>) {
        return Kson(any)
    }
    val objectPackage = any.javaClass.getPackage()
    val objectPackageName = if (objectPackage != null) objectPackage
        .name else ""
    return if (objectPackageName.startsWith("java.")
        || objectPackageName.startsWith("javax.")
        || objectPackageName.startsWith("kotlin.")
        || objectPackageName.startsWith("kotlinx.")
        || any.javaClass.classLoader == null
    ) {
        any.toString()
    } else Kson(any)
}
