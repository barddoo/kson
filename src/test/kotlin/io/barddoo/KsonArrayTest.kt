package io.barddoo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class KsonArrayTest {

    private val jsonStringTest = """
        [
          [
            "doubleKey",
            -2.345E8,
            "doubleStrKey",
            "00001.000",
            "falseKey"
          ],
          {"lorem": "jue"},
          "intKey",
          42,
          "intStrKey",
          "43",
          [
            "longKey",
            1234567890123456789,
            "longStrKey",
            "987654321098765432"
          ],
          "objectKey",
          {
            "myKey": "obj",
            "myKey1": {"obj": 4213}
          },
          "stringKey",
          "trueKey",
          true,
          "true"
        ]
        """.trimIndent()

    private val jsonTest = KsonArray(jsonStringTest)

    @Test
    fun add() {
        val json = jsonTest.clone() as KsonArray
        json.add(listOf("Lorem", "ipsum", "dolor", "sit", "amet"))
        json.add("test")
        val addedJson = json.filterIsInstance<KsonArray>().first { "Lorem" in it }
        assertTrue("test" in json)
        assertTrue("Lorem" in addedJson)
        assertTrue("ipsum" in addedJson)
        assertTrue("dolor" in addedJson)
        assertTrue("amet" in addedJson)
    }

    @Test
    fun addAll() {
        val json = jsonTest.clone() as KsonArray
        val list = listOf(11, 4213, 6546, 546452133, 54, 757, 657656786, 86574, 321)
        json.addAll(list)

        for (num in list)
            assertTrue(num in json)
    }

    @Test
    fun testAdd() {
    }

    @Test
    fun array() {
        assertNotNull(jsonTest.array(0))
        assertNull(jsonTest.array(3))
    }

    @Test
    fun json() {
        assertNotNull(jsonTest.json(1))
        assertNull(jsonTest.json(0))
    }

    @Test
    fun toKson() {
        val tojson = jsonTest.toKson(KsonArray().apply {
            addAll(listOf("obj", "kt"))
        })

        assertNotNull(tojson?.getBy<KsonArray>("obj"))
        assertNotNull(tojson?.getBy<Kson>("kt"))
    }

    @Test
    fun testToJsonArrayString() {
        val str = """[1,2,3,4,5,6,7,8,9,10,{"string":"hello"}]"""
        val array = str.toJsonArray()

        assertEquals(str, array.toString())
        assertEquals(3, array[2])
        assertEquals(
            Kson().apply { put("string", "hello") },
            array.getBy<Kson>(10)
        )
    }

    @Test
    fun testToJsonArrayCollection() {
        val collection = (1..10).toList()
        val array = collection.toJsonArray()

        assertEquals("""[1,2,3,4,5,6,7,8,9,10]""", array.toString())
        assertEquals(10, array.size)
        assertEquals(5, array[4])
    }

    @Test
    fun testToJsonArrayMap() {
        val map = mapOf(
            "str" to "0", "str1" to "2", "str2" to "2", "str15" to "15",
            "map" to mapOf("key" to "val")
        )
        val array = map.toJsonArray()

        assertEquals(
            """[{"str":"0","str1":"2","str2":"2","str15":"15","map":{"key":"val"}}]""",
            array.toString()
        )
        assertEquals(1, array.size)
        assertEquals(map.toJson(), array.getBy(0))
    }


    @Test
    fun `to string by indent factor`() {
        assertEquals(jsonTest.toString(), jsonTest.toString(0))
        assertEquals(jsonStringTest, jsonTest.toString(2))
    }

    @Test
    fun `get by`() {
        val array = KsonArray()
        array.add(null)

        array.add(99)
        array.add("avocado")

        assertNull(array.getBy<Int?>(0))
        assertEquals("""[null,99,"avocado"]""", array.toString())
        assertEquals(99, array.getBy<Int>(1))
        assertEquals("avocado", array.getBy<String>(2, "love"))
    }

    @Test
    fun `get number`() {
        val array = KsonArray().apply {
            add(14)
            add(23.3)
            add(64.8F)
            add(22L)
            add("apple")
            add("51")
            add("86.5")
        }
        assertEquals(14, array.getNumber(0))
        assertEquals(23.3, array.getNumber(1))
        assertEquals(64.8F, array.getNumber(2))
        assertEquals(22L, array.getNumber(3))
        assertNull(array.getNumber(4))
        assertEquals(51, array.getNumber(5))
        assertEquals(86.5, array.getNumber(6, 33))
    }

    @Test
    fun isNull() {
        assertTrue(jsonTest.isNull(999))
        assertFalse(jsonTest.isNull(3))
    }

    @Test
    fun `iterator by`() {
        for (str in jsonTest.iteratorBy<String?>())
            assertTrue(str is String)

        for (num in jsonTest.iteratorBy<Int?>())
            assertTrue(num is Int)
    }

    @Test
    fun `get boolean`() {
        assertTrue(jsonTest.getBoolean(11))
        assertTrue(jsonTest.getBoolean(12))
        assertTrue(jsonTest.getBoolean(9, true))
        assertFalse(jsonTest.getBoolean(0, false))
        assertFalse(jsonTest.getBoolean(1))
        assertFalse(jsonTest.getBoolean(2))
    }

    @Test
    fun `remove elements`() {
        val array = jsonTest.clone() as KsonArray
        assertTrue(array.removeAt(0) is KsonArray)

        assertTrue(array.remove("stringKey"))
        assertTrue(array.remove(true))
        assertFalse(array.remove("stringKey"))

        assertTrue(array.removeAll { it is Int })
        assertFalse(array.removeAll { it is Int })

        assertTrue(array.removeIf { it is String })
        assertFalse(array.removeIf { it is String })

        assertFalse(array.remove(Kson()))
    }

    @Test
    fun `get double`() {
        val array = KsonArray().apply {
            add(11)
            add(23.3)
            add(64.8F)
            add(22L)
            add("apple")
            add("51")
            add("86.5")
            add(-5.735E9)
            add("-2.345E8")
        }
        assertEquals(11.0, array.getDouble(0))
        assertEquals(23.3, array.getDouble(1))
        assertEquals(64.8, array.getDouble(2)!!, 0.1)
        assertEquals(22.0, array.getDouble(3)!!)
        assertNull(array.getDouble(4))
        assertEquals(33.0, array.getDouble(4, 33.0)!!)
        assertEquals(51.0, array.getDouble(5)!!)
        assertEquals(86.5, array.getDouble(6, 33.0)!!)
        assertEquals(-5.735E9, array.getDouble(7)!!)
        assertEquals(-2.345E8, array.getDouble(8)!!)
    }

    @Test
    fun `get float`() {
        val array = KsonArray().apply {
            add(14)
            add(23.3)
            add(64.8F)
            add(22L)
            add("apple")
            add("51")
            add("86.5")
        }
        assertEquals(14, array.getNumber(0))
        assertEquals(23.3, array.getNumber(1))
        assertEquals(64.8F, array.getNumber(2))
        assertEquals(22L, array.getNumber(3))
        assertNull(array.getNumber(4))
        assertEquals(51, array.getNumber(5))
        assertEquals(86.5, array.getNumber(6, 33))
    }
}
