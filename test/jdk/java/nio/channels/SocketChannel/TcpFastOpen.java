/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @run junit TcpFastOpen
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import static java.nio.charset.StandardCharsets.*;
import static jdk.net.ExtendedSocketOptions.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcpFastOpen {

    /**
     * Basic test of TCP_FASTOPEN_CONNECT_DATA.
     */
    @Test
    void testFastOpenConnectData() throws IOException {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            InetAddress lb = InetAddress.getLoopbackAddress();
	    // must bind on macos before setting option
            listener.bind(new InetSocketAddress(lb, 5789));
            listener.setOption(TCP_FASTOPEN, 1);

	    doTest(listener);
	    doTest(listener);
	}
    }

    void doTest(ServerSocketChannel listener) throws IOException {
            SocketChannel sc = SocketChannel.open();
	    sc.bind(null);

            String part1 = "hello";
            String part2 = "+greetings";

            ByteBuffer data = ByteBuffer.wrap(part1.getBytes(UTF_8));
            //sc.setOption(TCP_FASTOPEN, 1);
            sc.setOption(TCP_FASTOPEN_CONNECT_DATA, data);
            sc.connect(listener.getLocalAddress());
            //System.out.printf("get TCP_FASTOPEN %d\n", sc.getOption(TCP_FASTOPEN));

            ByteBuffer message = ByteBuffer.wrap(part2.getBytes(UTF_8));
            sc.write(message);

            String expected = part1 + part2;

            ByteBuffer buf = ByteBuffer.allocateDirect(100);
            int nread = 0;
            try (SocketChannel peer = listener.accept()) {
                while (nread < expected.length()) {
                    int n = peer.read(buf);
                    assertTrue(n > 0);
                    nread += n;
                }
            }
            buf.flip();
            String actual = UTF_8.decode(buf).toString();
            assertEquals(expected, actual);
    }
}
