/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.nio.fs;

import java.nio.file.InvalidPathException;

/**
 * Path parsing utilities shared with java/io/File
 */

public class ParseUtil {

    // Illegal characters at end of path segment
    private static final char DOT = '.';
    private static final char SP = ' ';

    /**
     * Assumes the current char is a slash, then checks if the last char
     * is illegal ('.' or ' ') except if the entire segment is "." or ".."
     * prevC is the previous character to lastC
     */
    public static boolean isInvalidSeq(char lastC, char prevC, int segmentLen) {
        assert segmentLen > 0;
        if (lastC != DOT && lastC != SP)
            return false; // valid: Not a . or SP
        if (lastC == DOT && segmentLen == 1) {
            return false; // valid: . on its own
        }
        if (segmentLen == 2) {
            return !(lastC == DOT && prevC == DOT); // valid: .. on its own
        }
        return true;
    }
}
