@file:Suppress("unused")

package io.barddoo

fun String.toJsonArray(): KsonArray {
    return KsonArray(this)
}

fun Collection<*>.toJsonArray(): KsonArray {
    return KsonArray(this)
}

fun Map<*, *>.toJsonArray(): KsonArray {
    return KsonArray(this)
}

fun Collection<Any?>.mapToJsonNull(): Collection<Any> {
    val notNullable: ArrayList<Any> = arrayListOf()
    notNullable.ensureCapacity(this.size)
    for (elem in this)
        notNullable += elem ?: NULL
    return notNullable
}

fun String.toJson(): Kson {
    return Kson(this)
}

fun Map<String, *>.toJson(): Kson {
    return Kson(this)
}
