# KSON
[![Build Status](https://travis-ci.com/barddoo/kson.svg?branch=master)](https://travis-ci.com/barddoo/kson)
[![](https://jitpack.io/v/barddoo/kson.svg)](https://jitpack.io/#barddoo/kson)
<img src="https://img.shields.io/liberapay/patrons/barddoo.svg?logo=liberapay">

### *KSON* is a Kotlin friendly fork of [org.json](https://github.com/stleary/JSON-java) 

JSON is a light-weight, language independent, data interchange format.
See http://www.JSON.org/

The files in this package implement JSON encoders/decoders in JVM.
It also includes the capability to convert between JSON and XML, HTTP
headers, Cookies, and CDL.

This is a reference implementation. There is a large number of JSON packages
in JVM. Perhaps someday the Java and Kotlin community will standardize on one. Until
then, choose carefully.

## Usage
- Add jitpack repo:
  - Gradle
    ```gradle
    maven { url 'https://jitpack.io' }
    dependencies {
        implementation 'com.github.barddoo:kson:1.0.0'
    }
    ```
  - Maven
    ```xml
    <repositories>
    	<repository>
    	    <id>jitpack.io</id>
    	    <url>https://jitpack.io</url>
    	</repository>
    </repositories>

    <dependency>
        <groupId>com.github.barddoo</groupId>
        <artifactId>kson</artifactId>
        <version>1.0.0</version>
    </dependency>
    ```
- GitHub Package:

First [setup your github registry](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-apache-maven-for-use-with-github-packages)
  - Gralde
    ```gradle
    dependencies {
        implementation 'io.barddoo:kson:1.0.0'
    }
    ```
  - Maven
    ```gradle
    <dependency>
        <groupId>io.barddoo</groupId>
        <artifactId>kson</artifactId>
        <version>1.0.0</version>
    </dependency>
    ```

### Breaking Changes from [org.json](https://github.com/stleary/JSON-java)

Improved interoperability with standard library.

All `opt()` are now `get()`

All `put()` are now `add()`


`getKson()`/`optKson()` = `json()`

`getKsonArray()`/`optKsonArray()` = `array()`

### Recommendations

- `iteratorBy<T>()` to iterate over KsonArray/Kson by type

```kotlin
val json = KsonArray()
json.add("awsome")
json.add(78)
json.add("Java/Kotlin")
json.add(84)

for(element: Int in json.iteratorBy<Int>()) {
    println(element)
}

//Only integers will be printed
```

- `getBy<T>()` to get element by type

```kotlin
val json = Kson()
json.add("person", Person(name = "King Arthur", year = 964))

val person: Person? = json.getBy<Person>("person")
// Type checked
```

Numeric types in this package comply with
[ECMA-404: The JSON Data Interchange Format](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf) and
[RFC 8259: The JavaScript Object Notation (JSON) Data Interchange Format](https://tools.ietf.org/html/rfc8259#section-6).
This package fully supports `Integer`, `Long`, and `Double` Java types. Partial support
for `BigInteger` and `BigDecimal` values in `Kson` and `KsonArray` objects is provided
in the form of `get()`, and `add()` API methods.

In compliance with RFC8259 page 10 section 9, the parser is more lax with what is valid
JSON than the Generator. For Example, the tab character (U+0009) is allowed when reading
JSON Text strings, but when output by the Generator, tab is properly converted to \t in
the string. Other instances may occur where reading invalid JSON text does not cause an
error to be generated. Malformed JSON Texts such as missing end " (quote) on strings or
invalid number formats (1.2e6.3) will cause errors as such documents can not be read
reliably.

Some notible exceptions that the JSON Parser in this library accepts are:
* Unquoted keys `{ key: "value" }`
* Unquoted values `{ "key": value }`
* Unescaped literals like "tab" in string values `{ "key": "value   with an unescaped tab" }`
* Numbers out of range for `Double` or `Long` are parsed as strings
