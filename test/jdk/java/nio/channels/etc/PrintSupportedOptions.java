/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /test/lib
 * @requires (os.family == "linux" | os.family == "mac" | os.family == "windows")
 * @bug 8209152
 * @run main PrintSupportedOptions
 * @run main/othervm -Djava.net.preferIPv4Stack=true PrintSupportedOptions
 */

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

import jdk.test.lib.net.IPSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PrintSupportedOptions {

    @FunctionalInterface
    interface NetworkChannelSupplier<T extends NetworkChannel> {
        T get() throws IOException;
    }

    public static void main(String[] args) throws IOException {
        IPSupport.throwSkippedExceptionIfNonOperational();

        test(() -> SocketChannel.open());
        test(() -> ServerSocketChannel.open());
        test(() -> DatagramChannel.open());

        test(() -> AsynchronousSocketChannel.open());
        test(() -> AsynchronousServerSocketChannel.open());
    }

    static final Set<String> READ_ONLY_OPTS = Set.of("SO_INCOMING_NAPI_ID");

    // key is name of option that can only be written. Value is the value to set
    // instead of trying to read a value
    static final Map<String,Object> WRITE_ONLY_OPTS =
        Map.of("TCP_FASTOPEN_CONNECT_DATA", ByteBuffer.wrap("hello world".getBytes(UTF_8)));

    // Anything to do to channel before test

    static interface PrepFunction<T> {
        public void run(T t) throws IOException;
    }

    static final Map<String,PrepFunction<NetworkChannel>> prep =
        Map.of("TCP_FASTOPEN", (ch) -> ch.bind(null));

    @SuppressWarnings("unchecked")
    static <T extends NetworkChannel>
    void test(NetworkChannelSupplier<T> supplier) throws IOException {
        try (T ch = supplier.get()) {
            System.out.println(ch);
            for (SocketOption<?> opt : ch.supportedOptions()) {
                if (prep.containsKey(opt.name())) {
                    prep.get(opt.name()).run(ch);
                }

                Object value;
                if (!WRITE_ONLY_OPTS.containsKey(opt.name())) {
                    value = ch.getOption(opt);
                } else {
                    value = WRITE_ONLY_OPTS.get(opt.name());
                }
                System.out.format(" %s -> %s%n", opt.name(), value);
                if (!READ_ONLY_OPTS.contains(opt.name())) {
                    if (value != null)
                        ch.setOption((SocketOption<Object>) opt, value);
                }
            }
        }
    }
}
