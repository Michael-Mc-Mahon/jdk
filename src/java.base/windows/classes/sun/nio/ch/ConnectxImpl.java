/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.io.IOException;
import jdk.internal.misc.Unsafe;

/**
 * Wrapper around connectx. On Windows a cleanup method is needed after connectx returns
 */
class ConnectxImpl {
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final int addressSize = unsafe.addressSize();

    private static int dependsArch(int value32, int value64) {
        return (addressSize == 4) ? value32 : value64;
    }

    private static Map<FileDescriptor,Long> olbufs =
        Collections.synchronizedMap(new HashMap<>());

    /*
     * typedef struct _OVERLAPPED {
     *     DWORD  Internal;
     *     DWORD  InternalHigh;
     *     DWORD  Offset;
     *     DWORD  OffsetHigh;
     *     HANDLE hEvent;
     * } OVERLAPPED;
     */
    private static final int SIZEOF_OVERLAPPED = dependsArch(20, 32);

    static int startConnect(boolean preferIPv6, FileDescriptor fd, boolean isBlocking,
                            InetAddress remote, int remotePort, long dataAddress,
                            int dataLen) throws IOException
    {
        long ol = 0L;
        int ret = -1;
        try {
            ol = unsafe.allocateMemory(SIZEOF_OVERLAPPED);
            ret = startConnect0(preferIPv6, fd, isBlocking, remote, ol, remotePort,
                                dataAddress, dataLen);
            if (ret >= 0) {
               olbufs.put(fd, ol);
            }
            return ret;
        } finally {
            if (ret < 0) {
                unsafe.freeMemory(ol);
            }
        }

    }

    // TODO: synchronize?

    // OVERLAPPED buffer must be freed for any non-blocking connectx call
    // which succeeded.

    static void finishConnect(FileDescriptor fd) {
        Long ol = olbufs.remove(fd);
        if (ol != null) {
            unsafe.freeMemory(ol.longValue());
        }
	finishConnect0(fd);
    }

    static native int startConnect0(boolean preferIPv6,
                                    FileDescriptor fd,
                                    boolean isBlocking,
                                    InetAddress remote,
                                    long ol,
                                    int remotePort,
                                    long dataAddress,
                                    int dataLen) throws IOException;

    static native void finishConnect0(FileDescriptor fd);
}
