package io.barddoo;
/*
Copyright (c) 2015 JSON.org

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

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

/**
 * This provides static methods to convert an {@link Xml} text into a Kson, and to covert a
 * Kson into an {@link Xml} text.
 *
 * @author JSON.org
 * @version 2016-08-10
 */
@SuppressWarnings("boxing")
public class Xml {

    /**
     * The Character '&amp;'.
     */
    public static final Character AMP = '&';

    /**
     * The Character '''.
     */
    public static final Character APOS = '\'';

    /**
     * The Character '!'.
     */
    public static final Character BANG = '!';

    /**
     * The Character '='.
     */
    public static final Character EQ = '=';

    /**
     * The Character '&gt;'.
     */
    public static final Character GT = '>';

    /**
     * The Character '&lt;'.
     */
    public static final Character LT = '<';

    /**
     * The Character '?'.
     */
    public static final Character QUEST = '?';

    /**
     * The Character '"'.
     */
    public static final Character QUOT = '"';

    /**
     * The Character '/'.
     */
    public static final Character SLASH = '/';

    /**
     * Null attribute name
     */
    public static final String NULL_ATTR = "xsi:nil";

    /**
     * Creates an iterator for navigating Code Points in a string instead of characters. Once Java7
     * support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href= "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    private static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private final int length = string.length();
                    private int nextIndex = 0;

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Replace special characters with {@link Xml} escapes:
     *
     * <pre>
     * &amp;(ampersand) is replaced by &amp;amp;
     * &lt;(less than) is replaced by &amp;lt;
     * &gt;(greater than) is replaced by &amp;gt;
     * &quot;(double quote) is replaced by &amp;quot;
     * (single quote / apostrophe) is replaced by &amp;apos;
     * </pre>
     *
     * @param string The string to be escaped.
     * @return The escaped string
     */
    public static String escape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (final int cp : codePointIterator(string)) {
            switch (cp) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    if (mustEscape(cp)) {
                        sb.append("&#x");
                        sb.append(Integer.toHexString(cp));
                        sb.append(';');
                    } else {
                        sb.appendCodePoint(cp);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * @param cp code point to test
     * @return true if the code point is not valid for an {@link Xml}
     */
    private static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
            && cp != 0x9
            && cp != 0xA
            && cp != 0xD
        ) || !(
            // valid the range of acceptable characters that aren't control
            (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
        )
            ;
    }

    /**
     * Removes {@link Xml} escapes from the string.
     *
     * @param string string to remove escapes from
     * @return string with converted entities
     */
    public static String unescape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            char c = string.charAt(i);
            if (c == '&') {
                final int semic = string.indexOf(';', i);
                if (semic > i) {
                    final String entity = string.substring(i + 1, semic);
                    sb.append(XmlTokener.unescapeEntity(entity));
                    // skip past the entity we just parsed.
                    i += entity.length() + 1;
                } else {
                    // this shouldn't happen in most cases since the parser
                    // errors on unclosed entries.
                    sb.append(c);
                }
            } else {
                // not part of an entity
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Throw an exception if the string contains whitespace. Whitespace is not allowed in tagNames and
     * attributes.
     *
     * @param string A string.
     * @throws KsonException Thrown if the string contains whitespace or is empty.
     */
    public static void noSpace(String string) throws KsonException {
        int i, length = string.length();
        if (length == 0) {
            throw new KsonException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new KsonException("'" + string
                    + "' contains a space character.");
            }
        }
    }

    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x       The {@link XmlTokener} containing the source string.
     * @param context The Kson that will include the new material.
     * @param name    The tag name.
     * @return true if the close tag is processed.
     */
    private static boolean parse(XmlTokener x, Kson context, String name,
        XmlParserConfiguration config)
        throws KsonException {
        char c;
        int i;
        Kson kson;
        String string;
        String tagName;
        Object token;

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();

        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.cDataTagName, string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        } else {
            tagName = (String) token;
            token = null;
            kson = new Kson();
            boolean nilAttributeFound = false;
            for (; ; ) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.convertNilAttributeToNull
                            && NULL_ATTR.equals(string)
                            && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        } else if (!nilAttributeFound) {
                            kson.accumulate(string,
                                config.keepStrings
                                    ? ((String) token)
                                    : stringToValue((String) token));
                        }
                        token = null;
                    } else {
                        kson.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (nilAttributeFound) {
                        context.accumulate(tagName, NULL.INSTANCE);
                    } else if (kson.size() > 0) {
                        context.accumulate(tagName, kson);
                    } else {
                        context.accumulate(tagName, "");
                    }
                    return false;

                } else if (token == GT) {
                    // Content, between <...> and </...>
                    for (; ; ) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                kson.accumulate(config.cDataTagName,
                                    config.keepStrings ? string : stringToValue(string));
                            }

                        } else if (token == LT) {
                            // Nested element
                            if (parse(x, kson, tagName, config)) {
                                if (kson.size() == 0) {
                                    context.accumulate(tagName, "");
                                } else if (kson.size() == 1
                                    && kson.get(config.cDataTagName) != null) {
                                    context.accumulate(tagName, kson.get(config.cDataTagName));
                                } else {
                                    context.accumulate(tagName, kson);
                                }
                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * This method is the same as {@link Kson#stringToValue(String)}.
     *
     * @param string String to convert
     * @return JSON value of this string or the string
     */
    // To maintain compatibility with the Android API, this method is a direct copy of
    // the one in Kson. Changes made here should be reflected there.
    public static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        }
        if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (string.equalsIgnoreCase("null")) {
            return NULL.INSTANCE;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                // if we want full Big Number support this block can be replaced with:
                // return stringToNumber(string);
                if (string.indexOf('.') > -1 || string.indexOf('e') > -1
                    || string.indexOf('E') > -1 || "-0".equals(string)) {
                    Double d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {
                        return d;
                    }
                } else {
                    long myLong = Long.parseLong(string);
                    if (string.equals(Long.toString(myLong))) {
                        if (myLong == (int) myLong) {
                            return (int) myLong;
                        }
                        return myLong;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return string;
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param string The source string.
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(String string) throws KsonException {
        return toKson(string, XmlParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param reader The {@link Xml} source reader.
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(Reader reader) throws KsonException {
        return toKson(reader, XmlParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     * <p>
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to numbers but will
     * instead be the exact value as seen in the {@link Xml} document.
     *
     * @param reader      The {@link Xml} source reader.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(Reader reader, boolean keepStrings) throws KsonException {
        if (keepStrings) {
            return toKson(reader, XmlParserConfiguration.KEEP_STRINGS);
        }
        return toKson(reader, XmlParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     * <p>
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to numbers but will
     * instead be the exact value as seen in the @link XML} document.
     *
     * @param reader The {@link Xml} source reader.
     * @param config Configuration options for the parser
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(Reader reader, XmlParserConfiguration config)
        throws KsonException {
        Kson jo = new Kson();
        XmlTokener x = new XmlTokener(reader);
        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                parse(x, jo, null, config);
            }
        }
        return jo;
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     * <p>
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to numbers but will
     * instead be the exact value as seen in the {@link Xml} document.
     *
     * @param string      The source string.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(String string, boolean keepStrings) throws KsonException {
        return toKson(new StringReader(string), keepStrings);
    }

    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson. Some
     * information may be lost in this transformation because JSON is a data format and {@link Xml}
     * is a document format. {@link Xml} uses elements, attributes, and content text, while JSON uses
     * unordered collections of name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar elements are represented as
     * KsonArrays. Content text may be placed in a "content" member. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     * <p>
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to numbers but will
     * instead be the exact value as seen in the {@link Xml} document.
     *
     * @param string The source string.
     * @param config Configuration options for the parser.
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown if there is an errors while parsing the string
     */
    public static Kson toKson(String string, XmlParserConfiguration config)
        throws KsonException {
        return toKson(new StringReader(string), config);
    }

    /**
     * Convert a Kson into a well-formed, element-normal {@link Xml} string.
     *
     * @param object A Kson.
     * @return A string.
     * @throws KsonException Thrown if there is an error parsing the string
     */
    public static String toString(Object object) throws KsonException {
        return toString(object, null, XmlParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a Kson into a well-formed, element-normal {@link Xml} string.
     *
     * @param object  A Kson.
     * @param tagName The optional name of the enclosing tag.
     * @return A string.
     * @throws KsonException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName) {
        return toString(object, tagName, XmlParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a Kson into a well-formed, element-normal {@link Xml} string.
     *
     * @param object  A Kson.
     * @param tagName The optional name of the enclosing tag.
     * @param config  Configuration that can control output to {@link Xml}.
     * @return A string.
     * @throws KsonException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName,
        final XmlParserConfiguration config)
        throws KsonException {
        StringBuilder sb = new StringBuilder();
        KsonArray ja;
        Kson jo;
        String string;

        if (object instanceof Kson) {

            // Emit <tagName>
            if (tagName != null) {
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
            }

            // Loop thru the keys.
            // don't use the new entrySet accessor to maintain Android Support
            jo = (Kson) object;
            for (final String key : jo.keySet()) {
                Object value = jo.get(key);
                if (value == null) {
                    value = "";
                } else if (value.getClass().isArray()) {
                    value = new KsonArray(value);
                }

                // Emit content in body
                if (key.equals(config.cDataTagName)) {
                    if (value instanceof KsonArray) {
                        ja = (KsonArray) value;
                        int jaLength = ja.size();
                        // don't use the new iterator API to maintain support for Android
                        for (int i = 0; i < jaLength; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            Object val = ja.get(i);
                            sb.append(escape(val.toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }

                    // Emit an array of similar keys

                } else if (value instanceof KsonArray) {
                    ja = (KsonArray) value;
                    // don't use the new iterator API to maintain support for Android
                    for (Object val : ja) {
                        if (val instanceof KsonArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(val, null, config));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(val, key, config));
                        }
                    }
                } else if ("".equals(value)) {
                    sb.append('<');
                    sb.append(key);
                    sb.append("/>");

                    // Emit a new tag <k>

                } else {
                    sb.append(toString(value, key, config));
                }
            }
            if (tagName != null) {

                // Emit the </tagName> close tag
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
            }
            return sb.toString();

        }

        if (object != null && (object instanceof KsonArray || object.getClass().isArray())) {
            if (object.getClass().isArray()) {
                ja = new KsonArray(object);
            } else {
                ja = (KsonArray) object;
            }
            // don't use the new iterator API to maintain support for Android
            for (Object val : ja) {
                // {@link XML} does not have good support for arrays. If an array
                // appears in a place where {@link XML} is lacking, synthesize an
                // <array> element.
                sb.append(toString(val, tagName == null ? "array" : tagName, config));
            }
            return sb.toString();
        }

        string = (object == null) ? "null" : escape(object.toString());
        return (tagName == null) ? "\"" + string + "\""
            : (string.length() == 0) ? "<" + tagName + "/>" : "<" + tagName
            + ">" + string + "</" + tagName + ">";
    }
}
