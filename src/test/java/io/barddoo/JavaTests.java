package io.barddoo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class JavaTests {

    @Test
    public void testGet() {
        KsonArray objects = new KsonArray();
        objects.add("123");
        objects.add((Object) null);
        assertNull(objects.get(1));
    }
}
