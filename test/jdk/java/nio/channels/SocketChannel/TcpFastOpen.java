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
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static java.nio.charset.StandardCharsets.*;
import static jdk.net.ExtendedSocketOptions.*;

import jdk.net.ExtendedSocketOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcpFastOpen {

    public static void main(String[] args) throws Exception {
	testFastOpenConnectData();
    }

    /**
     * Basic test of TCP_FASTOPEN_CONNECT_DATA.
     */
    @Test
    static void testFastOpenConnectData() throws IOException, InterruptedException {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            InetAddress lb = InetAddress.getLoopbackAddress();
            // must bind on macos before setting option
            listener.bind(new InetSocketAddress(lb, 5789));
            listener.setOption(TCP_FASTOPEN, 1);

            doTest(listener, true);
            doTest(listener, false);
        }
    }

    static void doTest(ServerSocketChannel listener, boolean block) throws IOException, InterruptedException {
            SocketChannel sc = SocketChannel.open();
            sc.bind(null);
            sc.configureBlocking(block);

            String part1 = new StringBuilder().repeat("X", 50).toString();
            String part2 = "+greetings";

            ByteBuffer data = ByteBuffer.wrap(part1.getBytes(UTF_8));
            //sc.setOption(TCP_FASTOPEN, 1);
            sc.setOption(TCP_FASTOPEN_CONNECT_DATA, data);
            sc.connect(listener.getLocalAddress());
            //System.out.printf("get TCP_FASTOPEN %d\n", sc.getOption(TCP_FASTOPEN));

	    if (!sc.isBlocking()) {
	        Selector sel = Selector.open();
	        sc.register(sel, SelectionKey.OP_CONNECT, null);
	        int t = sel.select();
	    }
            //Thread.sleep(Duration.ofSeconds(2));
            sc.finishConnect();
            ByteBuffer remaining = sc.getOption(TCP_FASTOPEN_CONNECT_DATA);
	    System.out.printf("returned BB = %s\n", remaining);
            while (remaining.remaining() > 0) {
                sc.write(remaining);
            }
            ByteBuffer message = ByteBuffer.wrap(part2.getBytes(UTF_8));
            sc.write(message);

            String expected = part1 + part2;

            ByteBuffer buf = ByteBuffer.allocateDirect(1000000);
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

    static void assertTrue(boolean b) {
	if (!b)
		throw new RuntimeException();
    }

    static void assertEquals(Object o1, Object o2) {
	if (!o1.equals(o2)) {
		throw new RuntimeException("");
	}
    }
}
