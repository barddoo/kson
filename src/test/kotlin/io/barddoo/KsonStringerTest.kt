package io.barddoo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KsonStringerTest {

    @Test
    fun create_object() {

        val myString = KsonStringer()
            .objectIt()
            .key("JSON")
            .value("Hello, World!")
            .endObject()
            .toString()
        val kson = Kson()
        kson["JSON"] = "Hello, World!"
        assertEquals(myString, kson.toString())
    }

    @Test
    fun create_array() {
        val str = KsonStringer()
            .array()
            .value(1)
            .value(2)
            .value(3)
            .endArray()
            .toString()
        val kson = KsonArray()
        kson.addAll(1, 2, 3)
        assertEquals(str, kson.toString())
    }

    @Test
    fun create() {
        val str = KsonStringer()
            .array().value(1).value(2).value(3)
            .objectIt()
            .key("hello").value("world")
            .endObject()
            .endArray()
            .toString()
        val kson = KsonArray()
        kson.addAll(1, 2, 3)
        val json = Kson()
        json["hello"] = "world"
        kson.add(json)
        assertEquals(str, kson.toString())
    }
}