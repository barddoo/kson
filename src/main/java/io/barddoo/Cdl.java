package io.barddoo;
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
 * This provides static methods to convert comma delimited text into a io.barddoo.KsonArray, and to
 * convert a io.barddoo.KsonArray into comma delimited text. Comma delimited text is a very popular
 * format for data interchange. It is understood by most database, spreadsheet, and organizer
 * programs.
 * <p>
 * Each row of text represents a row in a table or a data record. Each row ends with a NEWLINE
 * character. Each row contains one or more values. Values are separated by commas. A value can
 * contain any character except for comma, unless is is wrapped in single quotes or double quotes.
 * <p>
 * The first row usually contains the names of the columns.
 * <p>
 * A comma delimited list can be converted into a io.barddoo.KsonArray of Ksons. The names for
 * the elements in the Ksons can be taken from the names in the first row.
 *
 * @author JSON.org
 * @version 2016-05-01
 */
public class Cdl {

    /**
     * Get the next value. The value can be wrapped in quotes. The value can be empty.
     *
     * @param x A io.barddoo.JSONTokener of the source text.
     * @return The value string, or null if empty.
     * @throws KsonException if the quoted string is badly formed.
     */
    private static String getValue(KsonTokener x) throws KsonException {
        char c;
        char q;
        StringBuilder sb;
        do {
            c = x.next();
        } while (c == ' ' || c == '\t');
        switch (c) {
            case 0:
                return null;
            case '"':
            case '\'':
                q = c;
                sb = new StringBuilder();
                for (; ; ) {
                    c = x.next();
                    if (c == q) {
                        //Handle escaped double-quote
                        char nextC = x.next();
                        if (nextC != '\"') {
                            // if our quote was the end of the file, don't step
                            if (nextC > 0) {
                                x.back();
                            }
                            break;
                        }
                    }
                    if (c == 0 || c == '\n' || c == '\r') {
                        throw x.syntaxError("Missing close quote '" + q + "'.");
                    }
                    sb.append(c);
                }
                return sb.toString();
            case ',':
                x.back();
                return "";
            default:
                x.back();
                return x.nextTo(',');
        }
    }

    /**
     * Produce a io.barddoo.KsonArray of strings from a row of comma delimited values.
     *
     * @param x A io.barddoo.JSONTokener of the source text.
     * @return A io.barddoo.KsonArray of strings.
     */
    public static KsonArray rowToKsonArray(KsonTokener x) throws KsonException {
        KsonArray ja = new KsonArray();
        for (; ; ) {
            String value = getValue(x);
            char c = x.next();
            if (value == null ||
                (ja.size() == 0 && value.length() == 0 && c != ',')) {
                return null;
            }
            ja.add(value);
            while (c != ',') {
                if (c != ' ') {
                    if (c == '\n' || c == '\r' || c == 0) {
                        return ja;
                    }
                    throw x.syntaxError("Bad character '" + c + "' (" +
                        (int) c + ").");
                }
                c = x.next();
            }
        }
    }

    /**
     * Produce a Kson from a row of comma delimited text, using a parallel io.barddoo.KsonArray of
     * strings to provides the names of the elements.
     *
     * @param names A io.barddoo.KsonArray of names. This is commonly obtained from the first row of a
     *              comma delimited text file using the rowToKsonArray method.
     * @param x     A io.barddoo.JSONTokener of the source text.
     * @return A Kson combining the names and values.
     */
    public static Kson rowToKson(KsonArray names, KsonTokener x)
        throws KsonException {
        KsonArray ja = rowToKsonArray(x);
        return ja != null ? ja.toKson(names) : null;
    }

    /**
     * Produce a comma delimited text row from a io.barddoo.KsonArray. Values containing the comma
     * character will be quoted. Troublesome characters may be removed.
     *
     * @param ja A io.barddoo.KsonArray of strings.
     * @return A string ending in NEWLINE.
     */
    public static String rowToString(KsonArray ja) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ja.size(); i += 1) {
            if (i > 0) {
                sb.append(',');
            }
            Object object = ja.get(i);
            if (object != null) {
                String string = object.toString();
                if (string.length() > 0 && (string.indexOf(',') >= 0 ||
                    string.indexOf('\n') >= 0 || string.indexOf('\r') >= 0 ||
                    string.indexOf(0) >= 0 || string.charAt(0) == '"')) {
                    sb.append('"');
                    int length = string.length();
                    for (int j = 0; j < length; j += 1) {
                        char c = string.charAt(j);
                        if (c >= ' ' && c != '"') {
                            sb.append(c);
                        }
                    }
                    sb.append('"');
                } else {
                    sb.append(string);
                }
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Produce a io.barddoo.KsonArray of Ksons from a comma delimited text string, using the first
     * row as a source of names.
     *
     * @param string The comma delimited text.
     * @return A io.barddoo.KsonArray of Ksons.
     */
    public static KsonArray toKsonArray(String string) throws KsonException {
        return toKsonArray(new KsonTokener(string));
    }

    /**
     * Produce a io.barddoo.KsonArray of Ksons from a comma delimited text string, using the first
     * row as a source of names.
     *
     * @param x The io.barddoo.JSONTokener containing the comma delimited text.
     * @return A io.barddoo.KsonArray of Ksons.
     */
    public static KsonArray toKsonArray(KsonTokener x) throws KsonException {
        return toKsonArray(rowToKsonArray(x), x);
    }

    /**
     * Produce a io.barddoo.KsonArray of Ksons from a comma delimited text string using a supplied
     * io.barddoo.KsonArray as the source of element names.
     *
     * @param names  A io.barddoo.KsonArray of strings.
     * @param string The comma delimited text.
     * @return A io.barddoo.KsonArray of Ksons.
     */
    public static KsonArray toKsonArray(KsonArray names, String string)
        throws KsonException {
        return toKsonArray(names, new KsonTokener(string));
    }

    /**
     * Produce a io.barddoo.KsonArray of Ksons from a comma delimited text string using a supplied
     * io.barddoo.KsonArray as the source of element names.
     *
     * @param names A io.barddoo.KsonArray of strings.
     * @param x     A io.barddoo.JSONTokener of the source text.
     * @return A io.barddoo.KsonArray of Ksons.
     */
    public static KsonArray toKsonArray(KsonArray names, KsonTokener x)
        throws KsonException {
        if (names == null || names.size() == 0) {
            return null;
        }
        KsonArray ja = new KsonArray();
        for (; ; ) {
            Kson jo = rowToKson(names, x);
            if (jo == null) {
                break;
            }
            ja.add(jo);
        }
        if (ja.size() == 0) {
            return null;
        }
        return ja;
    }


    /**
     * Produce a comma delimited text from a io.barddoo.KsonArray of Ksons. The first row will be
     * a list of names obtained by inspecting the first Kson.
     *
     * @param ja A io.barddoo.KsonArray of Ksons.
     * @return A comma delimited text.
     */
    public static String toString(KsonArray ja) throws KsonException {
        Kson jo = ja.json(0);
        if (jo != null) {
            KsonArray names = jo.names();
            if (names != null) {
                return rowToString(names) + toString(names, ja);
            }
        }
        return null;
    }

    /**
     * Produce a comma delimited text from a io.barddoo.KsonArray of Ksons using a provided list
     * of names. The list of names is not included in the output.
     *
     * @param names A io.barddoo.KsonArray of strings.
     * @param ja    A io.barddoo.KsonArray of Ksons.
     * @return A comma delimited text.
     */
    public static String toString(KsonArray names, KsonArray ja)
        throws KsonException {
        if (names == null || names.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ja.size(); i += 1) {
            Kson jo = ja.json(i);
            if (jo != null) {
                sb.append(rowToString(jo.toKsonArray(names)));
            }
        }
        return sb.toString();
    }
}
