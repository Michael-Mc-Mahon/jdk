/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* @test
 * @bug 4842706 8024695 8361587 8362429
 * @summary Test some file operations with empty path
 * @requires os.family == "windows"
 * @run junit InvalidPaths
 */

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InvalidPaths {
    static File DIR;

    static void deletePath(File path) {
        File[] files = path.listFiles();
        if (files != null) {
            for (File f : files)
                deletePath(f);
        }
        path.delete();
    }

    @BeforeAll
    public static void init() {
        DIR = new File("foo");
        deletePath(DIR);
        if (!DIR.mkdir())
            throw new RuntimeException("could not create dir");
    }

    @Test
    public void test() {
        shouldThrow("boo ");
        shouldThrow("boo.");
        shouldThrow("boo .");
        shouldThrow("foo\\boo ");
        shouldThrow("foo\\boo.");
        shouldThrow("foo\\boo .");

        shouldSucceed("moo");
        shouldSucceed("foo\\moo");
    }

    @AfterAll
    public static void cleanup() {
        deletePath(DIR);
    }

    public void shouldThrow(String path) {
        Exception e;
        e = assertThrows(IOException.class, () -> {
            File f = new File(path);
            f.createNewFile();
        }, "Foo bar");
        assertTrue(e.getMessage().contains("Invalid file path"));

        e =assertThrows(IOException.class, () -> {
            File f = new File(path).getAbsoluteFile();;
            f.createNewFile();
        }, "Foo bar");
        assertTrue(e.getMessage().contains("Invalid file path"));
    }

    public void shouldSucceed(String path) {
        try {
            File f = new File(path);
            f.createNewFile();
        } catch (IOException e) {
            fail(path, e);
        }
    }
}
