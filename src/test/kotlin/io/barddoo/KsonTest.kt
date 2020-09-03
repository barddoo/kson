package io.barddoo

import io.barddoo.data.Button
import io.barddoo.data.Fraction
import io.barddoo.data.MyNumber
import io.barddoo.data.MyNumberContainer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.math.BigInteger
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class KsonTest {

    private val jsonStringTest = """
        {
          "arrayKey": [
            0,
            1,
            2
          ],
          "doubleKey": -23.45e7,
          "doubleStrKey": "00001.000",
          "falseKey": false,
          "falseStrKey": "false",
          "intKey": 42,
          "intStrKey": "43",
          "longKey": 1234567890123456789,
          "longStrKey": "987654321098765432",
          "objectKey": {
            "myKey": "myVal"
          },
          "stringKey": "hello world!",
          "trueKey": true,
          "trueStrKey": "true"
        }
        """.trimIndent()

    private val jsonTest = Kson(jsonStringTest)

    @Test
    fun names() {
        val str = """
        {
          "falseKey": false,
          "stringKey": "hello world!",
          "trueKey": true,
          "obj": {
            "key1": "hello",
            "key2": "value1",
            "random": "hello",
            "random1": "from",
            "random2": "kson",
            "key3": "test"
          }
        }
        """.trimIndent()
        val jsonObject = Kson(str)
        val jsonArray = jsonObject.names()

        assertEquals(4, jsonArray.size)
        assertEquals(6, jsonObject.json("obj")?.names()?.size)
        assertEquals(0, Kson().names().size)
        assertTrue("obj" in jsonArray)
        assertTrue("stringKey" in jsonArray)
    }

    @Test
    fun getLong() {
        assertEquals(1234567890123456789L, jsonTest.getLong("longKey"))
        assertEquals(987654321098765432L, jsonTest.getLong("longStrKey"))
        assertNull(jsonTest.getLong("nonKey"))
        assertNull(jsonTest.getLong("stringKey"))
    }

    @Test
    fun getDouble() {
        assertNull(jsonTest.getDouble("nonKey"))
        assertNull(jsonTest.getDouble("stringKey"))
    }

    @Test
    fun getString() {
        assertNull(jsonTest.getString("nonKey"))
        assertNull(jsonTest.getString("trueKey"))
    }

    @Test
    fun getInt() {
        assertNull(jsonTest.getInt("nonKey"))
        assertNull(jsonTest.getInt("stringKey"))
    }

    @Test
    fun testGetInt() {
    }

    @Test
    fun getFloat() {
    }

    @Test
    fun testGetFloat() {
    }

    @Test
    fun getBoolean() {
        assertFalse(jsonTest.getBoolean("nonKey", false)!!)

        assertNull(jsonTest.getBoolean("stringKey"))
    }

    @Test
    fun array() {
        assertNull(jsonTest.array("nonKey"))
        assertNull(jsonTest.array("stringKey"))

    }

    @Test
    fun json() {
        assertNull(jsonTest.json("nonKey"))
        assertNull(jsonTest.json("stringKey"))
    }

    /**
     * Tests Number serialization.
     */
    @Test
    fun verifyNumberOutput() {
        /**
         * MyNumberContainer is a POJO, so call Kson(bean),
         * which builds a map of getter names/values
         * The only getter is getMyNumber (key=myNumber),
         * whose return value is MyNumber. MyNumber extends Number,
         * but is not recognized as such by wrap() per current
         * implementation, so wrap() returns the default new Kson(bean).
         * The only getter is getNumber (key=number), whose return value is
         * BigDecimal(42).
         */
        val myNumber = MyNumberContainer()
        var jsonObject = Kson(myNumber)
        var actual: String = jsonObject.toString()
        var expected = """{"myNumber":{"number":42}}"""
        assertEquals(expected, actual, "Equal")
        /**
         * Kson.put() handles objects differently than the
         * bean constructor. Where the bean ctor wraps objects before
         * placing them in the map, put() inserts the object without wrapping.
         * In this case, a MyNumber instance is the value.
         * The MyNumber.toString() method is responsible for
         * returning a reasonable value: the string '42'.
         */
        jsonObject = Kson()
        jsonObject["myNumber"] = MyNumber()
        actual = jsonObject.toString()
        expected = """{"myNumber":42}"""
        assertEquals(expected, actual, "Equal")
        /**
         * Calls the Kson(Map) ctor, which calls wrap() for values.
         * AtomicInteger is a Number, but is not recognized by wrap(), per
         * current implementation. However, the type is
         * 'java.util.concurrent.atomic', so due to the 'java' prefix,
         * wrap() inserts the value as a string. That is why 42 comes back
         * wrapped in quotes.
         */
        jsonObject = Kson(Collections.singletonMap("myNumber", AtomicInteger(42)))
        actual = jsonObject.toString()
        expected = """{"myNumber":"42"}"""
        assertEquals(expected, actual, "Equal")
        /**
         * Kson.put() inserts the AtomicInteger directly into the
         * map not calling wrap(). In toString()->write()->writeValue(),
         * AtomicInteger is recognized as a Number, and converted via
         * numberToString() into the unquoted string '42'.
         */
        jsonObject = Kson()
        jsonObject["myNumber"] = AtomicInteger(42)
        actual = jsonObject.toString()
        expected = """{"myNumber":42}"""
        assertEquals(actual, expected, "Equal")
        /**
         * Calls the Kson(Map) ctor, which calls wrap() for values.
         * Fraction is a Number, but is not recognized by wrap(), per
         * current implementation. As a POJO, Fraction is handled as a
         * bean and inserted into a contained Kson. It has 2 getters,
         * for numerator and denominator.
         */
        jsonObject = Kson(mapOf("myNumber" to Fraction(4, 2)))
        assertEquals(1, jsonObject.size)
        assertEquals(2, (jsonObject["myNumber"] as Kson).size)
        assertEquals(2, jsonObject.getBy<Kson>("myNumber")?.size)
        assertEquals(BigInteger.valueOf(4), jsonObject.query("/myNumber/numerator"), "Numerator")
        assertEquals(BigInteger.valueOf(2), jsonObject.query("/myNumber/denominator"), "Denominator")
        /**
         * Kson.put() inserts the Fraction directly into the
         * map not calling wrap(). In toString()->write()->writeValue(),
         * Fraction is recognized as a Number, and converted via
         * numberToString() into the unquoted string '4/2'. But the
         * BigDecimal sanity check fails, so writeValue() defaults
         * to returning a safe JSON quoted string. Pretty slick!
         */
        jsonObject = Kson()
        jsonObject.put("myNumber", Fraction(4, 2))
        actual = jsonObject.toString()
        expected = """{"myNumber":"4/2"}""" // valid JSON, bug fixed
        assertEquals(expected, actual, "Equal")
    }

    /**
     * Verifies that the put Collection has backwards compatibility with RAW types pre-java5.
     */
    @Test
    fun verifyPutCollection() {
        val expected = Kson("""{"myCollection":[10]}""")
        val collectionAny1 = setOf(10)
        val jsonObject1 = Kson()
        jsonObject1["myCollection"] = collectionAny1
        val collectionAny2 = setOf(10 as Any)
        val jsonObject2 = Kson()
        jsonObject2["myCollection"] = collectionAny2
        val collectionInt = setOf(10)
        val jaInt = Kson()
        jaInt["myCollection"] = collectionInt
        assertEquals(expected.toString(), jsonObject1.toString(), "The RAW Collection should give me the same as the Typed Collection")
        assertEquals(expected.toString(), jsonObject2.toString(), "The RAW Collection should give me the same as the Typed Collection")
        assertEquals(expected.toString(), jaInt.toString(), "The RAW Collection should give me the same as the Typed Collection")
    }

    @Test
    fun testLongFromString() {
        val str = "26315000000253009"
        val json = Kson()
        json["key"] = str
        val actualKey = json["key"]
        assertTrue(str == actualKey) {
            "Incorrect key value. Got $actualKey expected $str"
        }
        val actualLong = json.getLong("key")
        assertTrue(actualLong != 0L) { "Unable to extract long value for string $str" }
        assertTrue(26315000000253009L == actualLong) {
            ("Incorrect key value. Got "
                + actualLong + " expected " + str)
        }
        val actualString = json.getString("key")
        assertTrue(str == actualString) {
            ("Incorrect key value. Got "
                + actualString + " expected " + str)
        }
    }

    @Test
    fun put() {
        val expected = Kson("""{"myMap":{"myKey":10}}""")
        val myRawC = mapOf("myKey" to 10)
        val jaRaw = Kson()
        jaRaw.put("myMap", myRawC)
        val myCStrObj = mapOf("myKey" to 10 as Any)
        val jaStrObj = Kson()
        jaStrObj.put("myMap", myCStrObj)
        val myCStrInt = mapOf("myKey" to 10)
        val jaStrInt = Kson()
        jaStrInt.put("myMap", myCStrInt)
        val myCObjObj = mapOf("myKey" to 10 as Any)
        val jaObjObj = Kson()
        jaObjObj.put("myMap", myCObjObj)
        assertTrue(expected.similar(jaRaw), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaStrObj), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaStrInt), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaObjObj), "The RAW Collection should give me the same as the Typed Collection")
    }

    @Test
    fun `put 1`() {
        val jsonT = Kson()
        jsonT.put("key1", 9999)
        jsonT.put("key2", 51221321512425214L)
        jsonT.put("key3", 521424.4251232)
        jsonT.put("key4", 189489.321F)
        jsonT.put("key5", "value1")
        jsonT.put("key6", null as Any?)
        val nil: String? = null
        jsonT.put("key7", nil)
        assertEquals(9999, jsonT["key1"])
        assertEquals(51221321512425214L, jsonT["key2"])
        assertEquals(521424.4251232, jsonT["key3"])
        assertEquals(189489.321F, jsonT["key4"])
        assertEquals("value1", jsonT["key5"])
        assertTrue(jsonT["key6"] is NULL)
        assertTrue(jsonT["key7"] is NULL)
    }

    @Test
    fun `put 2`() {
        val expected = Kson("""{"myCollection":[10]}""")
        val collectionAny1: Collection<*> = setOf(10)
        val jsonObject1 = Kson()
        jsonObject1.put("myCollection", collectionAny1)
        val collectionAny2: Collection<Any> = setOf(10 as Any)
        val jsonObject2 = Kson()
        jsonObject2.put("myCollection", collectionAny2)
        val collectionInt: Collection<Int> = setOf(10)
        val jaInt = Kson()
        jaInt.put("myCollection", collectionInt)
        assertTrue(expected.similar(jsonObject1), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jsonObject2), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaInt), "The RAW Collection should give me the same as the Typed Collection")
    }

    @Test
    fun query() {
        assertNull(Kson().query("/a/b"))
    }

    @Test
    fun query1() {
    }

    @Test
    fun remove() {
    }

    @Test
    fun similar() {
        val string1 = "HasSameRef"
        val obj1 = Kson().apply {
            put("key1", "abc")
            put("key2", 2)
            put("key3", string1)
        }
        val obj2 = Kson().apply {
            put("key1", "abc")
            put("key2", 3)
            put("key3", string1)
        }
        val obj3 = Kson().apply {
            put("key1", "abc")
            put("key2", 2)
            put("key3", string1)
        }
        assertFalse(obj1.similar(obj2), "Should eval to false")
        assertTrue(obj1.similar(obj3), "Should eval to true")
    }

    /**
     * The JSON parser is permissive of unambiguous unquoted keys and values.
     * Such JSON text should be allowed, even if it does not strictly conform
     * to the spec. However, after being parsed, toString() should emit strictly
     * conforming JSON text.
     */
    @Test
    fun unquotedText() {
        val str = "{key1:value1, key2:42}"
        val jsonObject = Kson(str)
        val textStr = jsonObject.toString()
        assertTrue(textStr.contains(""""key1""""), "expected key1")
        assertTrue(textStr.contains(""""value1""""), "expected value1")
        assertTrue(textStr.contains(""""key2""""), "expected key2")
        assertTrue(textStr.contains("42"), "expected 42")
    }

    @Test
    fun toKsonArray() {
        assertEquals(null, Kson().toKsonArray(null), "toKsonArray() with null names should be null")
    }

    @Test
    fun `test constructor`() {
        val expected = Kson("""{"myKey":10}""")
        val jaRaw = Kson(Collections.singletonMap("myKey", Integer.valueOf(10)))
        val jaStrObj = Kson(Collections.singletonMap("myKey", Integer.valueOf(10)))
        val jaStrInt = Kson(Collections.singletonMap("myKey", Integer.valueOf(10)))
        val myCObjObj = Collections.singletonMap("myKey", Integer.valueOf(10))
        val jaObjObj = Kson(myCObjObj)
        assertTrue(expected.similar(jaRaw), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaStrObj), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaStrInt), "The RAW Collection should give me the same as the Typed Collection")
        assertTrue(expected.similar(jaObjObj), "The RAW Collection should give me the same as the Typed Collection")
    }

    @Test
    fun write() {
        val str = """{"key1":"value1","key2":[1,2,3]}"""
        val jsonObject = Kson(str)
        val stringWriter = StringWriter()

        stringWriter.use {
            val actualStr = jsonObject.write(it).toString()
            assertEquals(str, actualStr)
        }
    }

    @Test
    fun stringToValue() {
        val str = ""
        val valueStr = Kson.stringToValue(str) as String
        assertEquals("", valueStr)
    }

    @Test
    fun invalidEscapeSequence() {
        val json = """
        {
         "\url": "value"
        }
        """.trimIndent()
        assertThrows(KsonException::class.java) {
            Kson(json)
        }
    }

    @Test
    fun `test insert java objects`() {
        val json = Kson()
        json["button"] = Button("blue")
        assertEquals("""{"button":{"color":"blue"}}""", json.toString())
    }
}