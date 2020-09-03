package io.barddoo.data

import java.io.StringReader


/**
 * Used in testing when Bean behavior is needed
 */
interface MyBean {
    val intKey: Int?
    val doubleKey: Double?
    val stringKey: String?
    val escapeStringKey: String?
    val isTrueKey: Boolean?
    val isFalseKey: Boolean?
    val stringReaderKey: StringReader?
}