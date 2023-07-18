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

#include <windows.h>
#include <winsock2.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "nio.h"
#include "nio_util.h"
#include "net_util.h"
#include "extfunctionPtr.h"

#include "sun_nio_ch_ConnectxImpl.h"

/**
 * Returns number of bytes sent if no error (should be either 0 or len)
 * Use isConnected to determine whether socket is connected or not
 * returns < 0 IOS_ error code on error
 */
JNIEXPORT jint JNICALL
Java_sun_nio_ch_ConnectxImpl_startConnect0(JNIEnv *env, jclass clazz, jboolean preferIPv6, jobject fdo,
                                jboolean isBlocking, jobject iao, jlong ol,
                                jint port, jlong bufAddress, jint len)
{
    SOCKETADDRESS sa;
    int sa_len = 0;
    void *buf = (void *)jlong_to_ptr(bufAddress);
    SOCKET s = fdval(env, fdo);
    OVERLAPPED *lpOl = (OVERLAPPED *)jlong_to_ptr(ol);
    DWORD xfer, bytesSent = 0;

    if (NET_InetAddressToSockaddr(env, iao, port, &sa, &sa_len, preferIPv6) != 0) {
        return IOS_THROWN;
    }

    ZeroMemory((PVOID)lpOl, sizeof(OVERLAPPED));

    BOOL res = (*ConnectEx_func)(s, &sa.sa, sa_len, (PVOID)bufAddress,
                                 (DWORD)len, &bytesSent, lpOl);
    if (!res) {
        int error = GetLastError();
        if (error == ERROR_IO_PENDING) {
            res = GetOverlappedResult((HANDLE)s, lpOl, &xfer, isBlocking);
            if (res) {
                return xfer;
            } else {
                JNU_ThrowIOExceptionWithLastError(env, "ConnectEx failed");
                return IOS_THROWN;
            }
        }
        JNU_ThrowIOExceptionWithLastError(env, "ConnectEx failed");
        return IOS_THROWN;
    } else {
        // TRUE returned: means connected
        return bytesSent;
    }
}
