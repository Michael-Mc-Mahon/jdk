/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <poll.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <string.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <limits.h>
#include <stdio.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_Net.h"
#include "net_util.h"
#include "net_util_md.h"
#include "nio_util.h"
#include "nio.h"

#ifdef _AIX
#include <stdlib.h>
#include <sys/utsname.h>
#endif

/**
 * Returns number of bytes sent if no error (should be either 0 or len)
 * Use isConnected to determine whether socket is connected or not
 */
JNIEXPORT jint JNICALL
Java_sun_nio_ch_NetMd_connectx0(JNIEnv *env, jclass clazz, jboolean preferIPv6, jobject fdo,
                              jboolean unused, jobject iao, jint port,
                              jlong bufAddress, jint len)
{
    SOCKETADDRESS sa;
    int sa_len = 0;
    void *buf = (void *)jlong_to_ptr(bufAddress);

    if (file == NULL) {
	file = fopen("/tmp/foo.txt", "a");
    }

    if (NET_InetAddressToSockaddr(env, iao, port, &sa, &sa_len, preferIPv6) != 0) {
        return IOS_THROWN;
    }

#if defined(__linux__)

    // TBD - what if sendto is interrupted (EINTR), is initial data lost?

    ssize_t n = (int) sendto(fdval(env, fdo), buf, len, MSG_FASTOPEN, &sa.sa, sa_len);
    fprintf(file, "XXX: %d %s \n", (int)n, strerror(errno));
    if (n < 0) {
        if (errno == EMSGSIZE) {
            JNU_ThrowIOException(env, "TFO data too large");
            return IOS_THROWN;
        } else if (errno == EINPROGRESS) {
            /* non-blocking TCP fast connect
             * where no cookie is available. This means
             * zero bytes were written and user needs
             * to write the data after the socket is connected
             */
            return 0;
        } else {
            return handleSocketError(env, errno);
        }
    }
    return n; // fast open bytes written or queued (cookie available)

#elif defined(__APPLE__)

    sa_endpoints_t endpoints;
    struct iovec iov;
    size_t nsent;

    endpoints.sae_srcif = 0;
    endpoints.sae_srcaddr = NULL;
    endpoints.sae_srcaddrlen = 0;
    endpoints.sae_dstaddr = (struct sockaddr *) &sa;
    endpoints.sae_dstaddrlen = sa_len;

    iov.iov_base = buf;
    iov.iov_len = len;

    // TBD - what if connectx is interrupted (EINTR), is nsent set?

    int n = connectx(fdval(env, fdo), &endpoints, 0, CONNECT_DATA_IDEMPOTENT, &iov, 1, &nsent, NULL);
    fprintf(file, "XXX: %d %s %d\n", n, strerror(errno), (int)nsent);
    fflush(file);
    if (n < 0) {
        if (errno == EMSGSIZE) {
            JNU_ThrowIOException(env, "TFO data too large");
            return IOS_THROWN;
        } else if (errno == EINPROGRESS) {
            /* non-blocking TCP fast connect where no cookie is available. This means
             * zero bytes were written and user needs to write the data after the 
             * socket is connected. Also can happen if greater number of bytes
             * to be written than will fit in initial SYN.
             */
            return nsent;
        } else {
            return handleSocketError(env, errno);
        }
    }
    return nsent; // fast open bytes written or queued (cookie available)
#else
    JNU_ThrowInternalError(env, "should not reach here");
    return IOS_THROWN;
#endif
}
