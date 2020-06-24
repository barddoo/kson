package io.barddoo;

/*
Copyright (c) 2008 JSON.org

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
 * This provides static methods to convert an {@link Xml} text into a {@link KsonArray} or
 * Kson, and to covert a {@link KsonArray} or Kson into an {@link Xml} text using the
 * JsonML transform.
 *
 * @author JSON.org
 * @version 2016-01-30
 */
public class KsonML {

    /**
     * Parse {@link Xml} values and store them in a {@link KsonArray}.
     *
     * @param x           The {@link XmlTokener} containing the source string.
     * @param arrayForm   true if array form, false if object form.
     * @param ja          The {@link KsonArray} that is containing the current tag or null if we are
     *                    at the outermost level.
     * @param keepStrings Don't type-convert text nodes and attribute values
     * @return A {@link KsonArray} if the value is the outermost tag, otherwise null.
     */
    private static Object parse(
        XmlTokener x,
        boolean arrayForm,
        KsonArray ja,
        boolean keepStrings
    ) throws KsonException {
        String attribute;
        char c;
        String closeTag;
        int i;
        KsonArray newja;
        Kson newjo;
        Object token;
        String tagName;

        // Test for and skip past these forms:
        //      <!-- ... -->
        //      <![  ... ]]>
        //      <!   ...   >
        //      <?   ...  ?>

        while (true) {
            if (!x.more()) {
                throw x.syntaxError("Bad {@link XML}");
            }
            token = x.nextContent();
            if (token == Xml.LT) {
                token = x.nextToken();
                if (token instanceof Character) {
                    if (token == Xml.SLASH) {

                        // Close tag </

                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw new KsonException(
                                "Expected a closing name instead of '" +
                                    token + "'.");
                        }
                        if (x.nextToken() != Xml.GT) {
                            throw x.syntaxError("Misshaped close tag");
                        }
                        return token;
                    } else if (token == Xml.BANG) {

                        // <!

                        c = x.next();
                        if (c == '-') {
                            if (x.next() == '-') {
                                x.skipPast("-->");
                            } else {
                                x.back();
                            }
                        } else if (c == '[') {
                            token = x.nextToken();
                            if (token.equals("CDATA") && x.next() == '[') {
                                if (ja != null) {
                                    ja.add(x.nextCDATA());
                                }
                            } else {
                                throw x.syntaxError("Expected 'CDATA['");
                            }
                        } else {
                            i = 1;
                            do {
                                token = x.nextMeta();
                                if (token == null) {
                                    throw x.syntaxError("Missing '>' after '<!'.");
                                } else if (token == Xml.LT) {
                                    i += 1;
                                } else if (token == Xml.GT) {
                                    i -= 1;
                                }
                            } while (i > 0);
                        }
                    } else if (token == Xml.QUEST) {

                        // <?

                        x.skipPast("?>");
                    } else {
                        throw x.syntaxError("Misshaped tag");
                    }

                    // Open tag <

                } else {
                    if (!(token instanceof String)) {
                        throw x.syntaxError("Bad tagName '" + token + "'.");
                    }
                    tagName = (String) token;
                    newja = new KsonArray();
                    newjo = new Kson();
                    if (arrayForm) {
                        newja.add(tagName);
                        if (ja != null) {
                            ja.add(newja);
                        }
                    } else {
                        newjo.put("tagName", tagName);
                        if (ja != null) {
                            ja.add(newjo);
                        }
                    }
                    token = null;
                    for (; ; ) {
                        if (token == null) {
                            token = x.nextToken();
                        }
                        if (token == null) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        if (!(token instanceof String)) {
                            break;
                        }

                        // attribute = value

                        attribute = (String) token;
                        if (!arrayForm && ("tagName".equals(attribute) || "childNode".equals(attribute))) {
                            throw x.syntaxError("Reserved attribute.");
                        }
                        token = x.nextToken();
                        if (token == Xml.EQ) {
                            token = x.nextToken();
                            if (!(token instanceof String)) {
                                throw x.syntaxError("Missing value");
                            }
                            newjo.accumulate(attribute,
                                keepStrings ? ((String) token) : Xml.stringToValue((String) token));
                            token = null;
                        } else {
                            newjo.accumulate(attribute, "");
                        }
                    }
                    if (arrayForm && newjo.size() > 0) {
                        newja.add(newjo);
                    }

                    // Empty tag <.../>

                    if (token == Xml.SLASH) {
                        if (x.nextToken() != Xml.GT) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        if (ja == null) {
                            if (arrayForm) {
                                return newja;
                            }
                            return newjo;
                        }

                        // Content, between <...> and </...>

                    } else {
                        if (token != Xml.GT) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        closeTag = (String) parse(x, arrayForm, newja, keepStrings);
                        if (closeTag != null) {
                            if (!closeTag.equals(tagName)) {
                                throw x.syntaxError("Mismatched '" + tagName +
                                    "' and '" + closeTag + "'");
                            }
                            tagName = null;
                            if (!arrayForm && newja.size() > 0) {
                                newjo.put("childNodes", newja);
                            }
                            if (ja == null) {
                                if (arrayForm) {
                                    return newja;
                                }
                                return newjo;
                            }
                        }
                    }
                }
            } else {
                if (ja != null) {
                    ja.add(token instanceof String
                        ? keepStrings ? Xml.unescape((String) token) : Xml.stringToValue((String) token)
                        : token);
                }
            }
        }
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a {@link KsonArray}
     * using the JsonML transform. Each {@link Xml} tag is represented as a {@link KsonArray} in
     * which the first element is the tag name. If the tag has attributes, then the second element
     * will be Kson containing the name/value pairs. If the tag contains children, then strings
     * and KsonArrays will represent the child tags. Comments, prologs, DTDs, and <code>&lt;[ [
     * ]]&gt;</code> are ignored.
     *
     * @param string The source string.
     * @return A {@link KsonArray} containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a {@link KsonArray}
     */
    public static KsonArray toKsonArray(String string) throws KsonException {
        return (KsonArray) parse(new XmlTokener(string), true, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a {@link KsonArray}
     * using the JsonML transform. Each {@link Xml} tag is represented as a {@link KsonArray} in
     * which the first element is the tag name. If the tag has attributes, then the second element
     * will be Kson containing the name/value pairs. If the tag contains children, then strings
     * and KsonArrays will represent the child tags. As opposed to toKsonArray this method does not
     * attempt to convert any text node or attribute value to any type but just leaves it as a string.
     * Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param string      The source string.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A {@link KsonArray} containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a {@link KsonArray}
     */
    public static KsonArray toKsonArray(String string, boolean keepStrings) throws KsonException {
        return (KsonArray) parse(new XmlTokener(string), true, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a {@link KsonArray}
     * using the JsonML transform. Each {@link Xml} tag is represented as a {@link KsonArray} in
     * which the first element is the tag name. If the tag has attributes, then the second element
     * will be Kson containing the name/value pairs. If the tag contains children, then strings
     * and KsonArrays will represent the child content and tags. As opposed to toKsonArray this method
     * does not attempt to convert any text node or attribute value to any type but just leaves it as
     * a string. Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param x           An {@link XmlTokener}.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A {@link KsonArray} containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a {@link KsonArray}
     */
    public static KsonArray toKsonArray(XmlTokener x, boolean keepStrings) throws KsonException {
        return (KsonArray) parse(x, true, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a {@link KsonArray}
     * using the JsonML transform. Each {@link Xml} tag is represented as a {@link KsonArray} in
     * which the first element is the tag name. If the tag has attributes, then the second element
     * will be Kson containing the name/value pairs. If the tag contains children, then strings
     * and KsonArrays will represent the child content and tags. Comments, prologs, DTDs, and
     * <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param x An {@link XmlTokener}.
     * @return A {@link KsonArray} containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a {@link KsonArray}
     */
    public static KsonArray toKsonArray(XmlTokener x) throws KsonException {
        return (KsonArray) parse(x, true, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson using
     * the JsonML transform. Each {@link Xml} tag is represented as a Kson with a "tagName"
     * property. If the tag has attributes, then the attributes will be in the Kson as
     * properties. If the tag contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML Ksons.
     * <p>
     * Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param string The {@link Xml} source text.
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a Kson
     */
    public static Kson toKson(String string) throws KsonException {
        return (Kson) parse(new XmlTokener(string), false, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson using
     * the JsonML transform. Each {@link Xml} tag is represented as a Kson with a "tagName"
     * property. If the tag has attributes, then the attributes will be in the Kson as
     * properties. If the tag contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML Ksons.
     * <p>
     * Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param string      The {@link Xml} source text.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a Kson
     */
    public static Kson toKson(String string, boolean keepStrings) throws KsonException {
        return (Kson) parse(new XmlTokener(string), false, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson using
     * the JsonML transform. Each {@link Xml} tag is represented as a Kson with a "tagName"
     * property. If the tag has attributes, then the attributes will be in the Kson as
     * properties. If the tag contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML Ksons.
     * <p>
     * Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param x An {@link XmlTokener} of the {@link Xml} source text.
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a Kson
     */
    public static Kson toKson(XmlTokener x) throws KsonException {
        return (Kson) parse(x, false, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) {@link Xml} string into a Kson using
     * the JsonML transform. Each {@link Xml} tag is represented as a Kson with a "tagName"
     * property. If the tag has attributes, then the attributes will be in the Kson as
     * properties. If the tag contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML Ksons.
     * <p>
     * Comments, prologs, DTDs, and <code>&lt;[ [ ]]&gt;</code> are ignored.
     *
     * @param x           An {@link XmlTokener} of the {@link Xml} source text.
     * @param keepStrings If true, then values will not be coerced into boolean or numeric values and
     *                    will instead be left as strings
     * @return A Kson containing the structured data from the {@link Xml} string.
     * @throws KsonException Thrown on error converting to a Kson
     */
    public static Kson toKson(XmlTokener x, boolean keepStrings) throws KsonException {
        return (Kson) parse(x, false, null, keepStrings);
    }


    /**
     * Reverse the {@link KsonML} transformation, making an {@link Xml} text from a
     * {@link KsonArray}.
     *
     * @param ja A {@link KsonArray}.
     * @return An {@link Xml} string.
     * @throws KsonException Thrown on error converting to a string
     */
    public static String toString(KsonArray ja) throws KsonException {
        int i;
        Kson jo;
        int length;
        Object object;
        StringBuilder sb = new StringBuilder();
        String tagName;

        // Emit <tagName

        tagName = ja.getString(0);
        Xml.noSpace(tagName);
        tagName = Xml.escape(tagName);
        sb.append('<');
        sb.append(tagName);

        object = ja.get(1);
        if (object instanceof Kson) {
            i = 2;
            jo = (Kson) object;

            // Emit the attributes

            // Don't use the new entrySet API to maintain Android support
            for (final String key : jo.keySet()) {
                final Object value = jo.get(key);
                Xml.noSpace(key);
                if (value != null) {
                    sb.append(' ');
                    sb.append(Xml.escape(key));
                    sb.append('=');
                    sb.append('"');
                    sb.append(Xml.escape(value.toString()));
                    sb.append('"');
                }
            }
        } else {
            i = 1;
        }

        // Emit content in body

        length = ja.size();
        if (i >= length) {
            sb.append('/');
            sb.append('>');
        } else {
            sb.append('>');
            do {
                object = ja.get(i);
                i += 1;
                if (object != null) {
                    if (object instanceof String) {
                        sb.append(Xml.escape(object.toString()));
                    } else if (object instanceof Kson) {
                        sb.append(toString((Kson) object));
                    } else if (object instanceof KsonArray) {
                        sb.append(toString((KsonArray) object));
                    } else {
                        sb.append(object.toString());
                    }
                }
            } while (i < length);
            sb.append('<');
            sb.append('/');
            sb.append(tagName);
            sb.append('>');
        }
        return sb.toString();
    }

    /**
     * Reverse the {@link KsonML} transformation, making an {@link Xml} text from a Kson. The
     * Kson must contain a "tagName" property. If it has children, then it must have a
     * "childNodes" property containing an array of objects. The other properties are attributes with
     * string values.
     *
     * @param jo A Kson.
     * @return An {@link Xml} string.
     * @throws KsonException Thrown on error converting to a string
     */
    public static String toString(Kson jo) throws KsonException {
        StringBuilder sb = new StringBuilder();
        int i;
        KsonArray ja;
        int length;
        Object object;
        String tagName;
        Object value;

        //Emit <tagName

        tagName = jo.getString("tagName");
        if (tagName == null) {
            return Xml.escape(jo.toString());
        }
        Xml.noSpace(tagName);
        tagName = Xml.escape(tagName);
        sb.append('<');
        sb.append(tagName);

        //Emit the attributes

        // Don't use the new entrySet API to maintain Android support
        for (final String key : jo.keySet()) {
            if (!"tagName".equals(key) && !"childNodes".equals(key)) {
                Xml.noSpace(key);
                value = jo.get(key);
                if (value != null) {
                    sb.append(' ');
                    sb.append(Xml.escape(key));
                    sb.append('=');
                    sb.append('"');
                    sb.append(Xml.escape(value.toString()));
                    sb.append('"');
                }
            }
        }

        //Emit content in body

        ja = jo.array("childNodes");
        if (ja == null) {
            sb.append('/');
            sb.append('>');
        } else {
            sb.append('>');
            length = ja.size();
            for (i = 0; i < length; i += 1) {
                object = ja.get(i);
                if (object != null) {
                    if (object instanceof String) {
                        sb.append(Xml.escape(object.toString()));
                    } else if (object instanceof Kson) {
                        sb.append(toString((Kson) object));
                    } else if (object instanceof KsonArray) {
                        sb.append(toString((KsonArray) object));
                    } else {
                        sb.append(object.toString());
                    }
                }
            }
            sb.append('<');
            sb.append('/');
            sb.append(tagName);
            sb.append('>');
        }
        return sb.toString();
    }
}
