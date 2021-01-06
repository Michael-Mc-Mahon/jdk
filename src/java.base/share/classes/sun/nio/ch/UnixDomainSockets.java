/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.net.BindException;
import java.net.NetPermission;
import java.net.ProtocolFamily;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import sun.nio.fs.AbstractFileSystemProvider;

import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;

import static java.net.StandardProtocolFamily.UNIX;

class UnixDomainSockets {
    private UnixDomainSockets() { }

    private static final JavaIOFileDescriptorAccess fdAccess =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    static final UnixDomainSocketAddress UNNAMED = UnixDomainSocketAddress.of("");

    private static final boolean supported;

    private static final String tempDir = UnixDomainSocketsUtil.getTempDir();

    private static final NetPermission accessUnixDomainSocket =
            new NetPermission("accessUnixDomainSocket");

    static boolean isSupported() {
        return supported;
    }

    static void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(accessUnixDomainSocket);
    }

    static UnixDomainSocketAddress getRevealedLocalAddress(SocketAddress sa) {
        UnixDomainSocketAddress addr = (UnixDomainSocketAddress) sa;
        try {
            checkPermission();
            // Security check passed
        } catch (SecurityException e) {
            // Return unnamed address only if security check fails
            addr = UNNAMED;
        }
        return addr;
    }

    static UnixDomainSocketAddress localAddress(FileDescriptor fd) throws IOException {
        String path = new String(localAddress0(fd), UnixDomainSocketsUtil.getCharset());
        return UnixDomainSocketAddress.of(path);
    }

    private static native byte[] localAddress0(FileDescriptor fd) throws IOException;

    static String getRevealedLocalAddressAsString(SocketAddress sa) {
        return (System.getSecurityManager() != null) ? sa.toString() : "";
    }

    static UnixDomainSocketAddress checkAddress(SocketAddress sa) {
        if (sa == null)
            throw new NullPointerException();
        if (!(sa instanceof UnixDomainSocketAddress))
            throw new UnsupportedAddressTypeException();
        return (UnixDomainSocketAddress) sa;
    }

    static byte[] getPathBytes(Path path) {
        FileSystemProvider provider = FileSystems.getDefault().provider();
        return ((AbstractFileSystemProvider) provider).getSunPathForSocketFile(path);
    }

    static FileDescriptor socket() throws IOException {
        return IOUtil.newFD(socket0());
    }

    static void bind(FileDescriptor fd, Path addr) throws IOException {
        byte[] path = getPathBytes(addr);
        if (path.length == 0) {
            throw new BindException("Server socket cannot bind to unnamed address");
        }
        bind0(fd, path);
    }

    private static Random getRandom() {
        try {
            return SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom(); // This should not fail
        }
    }

    private static final Random random = getRandom();

    /**
     * Return a possible temporary name to bind to, which is different for each call
     * Name is of the form <temp dir>/socket_<random>
     */
    static UnixDomainSocketAddress generateTempName() throws IOException {
        String dir = UnixDomainSockets.tempDir;
        if (dir == null)
            throw new BindException("Could not locate temporary directory for sockets");
        int rnd = random.nextInt(Integer.MAX_VALUE);
        try {
            Path path = Path.of(dir, "socket_" + rnd);
            return UnixDomainSocketAddress.of(path);
        } catch (InvalidPathException e) {
            throw new BindException("Invalid temporary directory");
        }
    }

    static int connect(FileDescriptor fd, SocketAddress sa) throws IOException {
        return UnixDomainSockets.connect(fd, ((UnixDomainSocketAddress) sa).getPath());
    }

    static int connect(FileDescriptor fd, Path path) throws IOException {
        return connect0(fd, getPathBytes(path));
    }

    static int accept(FileDescriptor fd, FileDescriptor newfd, String[] paths)
        throws IOException
    {
        Object[] array  = new Object[1];
        int n = accept0(fd, newfd, array);
        if (n > 0) {
            byte[] bytes = (byte[]) array[0];
            paths[0] = new String(bytes, UnixDomainSocketsUtil.getCharset());
        }
        return n;
    }

    private static final int MAX_SEND_FDS = SocketDispatcher.maxsendfds();

    /**
     * return the protocol family of remote if it is not null,
     * or the protocol family of local if remote is null
     */
    private static ProtocolFamily inetFamilyOf(SocketAddress local, SocketAddress remote) {
        assert local != null && remote != null;
        assert local instanceof InetSocketAddress;
        assert remote instanceof InetSocketAddress;

        InetSocketAddress isa = (InetSocketAddress) (remote != null ? remote : local);;
        ProtocolFamily family = isa.getAddress() instanceof Inet4Address ?
            StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
        return family;
    }

    static int read(SelectorProvider provider, FileDescriptor fd, ByteBuffer bb, 
                    LinkedList<SendableChannel> receiveQueue, NativeDispatcher nd)
        throws IOException
    {
        int[] newfds = new int[MAX_SEND_FDS];
        for (int i=0; i<newfds.length; i++)
            newfds[i] = -1;

        int nbytes = IOUtil.recvmsg(fd, bb, (SocketDispatcher)nd, newfds);

        int fd1 = newfds.length == 0 ? -1 : newfds[0];

        for (int i=0; fd1 != -1; fd1 = newfds[++i]) {
            FileDescriptor newfd = new FileDescriptor();
            fdAccess.set(newfd, newfds[i]);
            SocketAddress laddr = Net.localAddress(newfd);
            SocketAddress raddr = Net.remoteAddress(newfd);
            SendableChannel chan;

            if (laddr instanceof UnixDomainSocketAddress) {
                if (raddr == null) {
                    chan = new ServerSocketChannelImpl(provider, UNIX, newfd, true);
                } else {
                    chan = new SocketChannelImpl(provider, UNIX, newfd, raddr);
                }
                addToChannelList(receiveQueue, chan);
            } else if (laddr instanceof InetSocketAddress) {
                ProtocolFamily family = inetFamilyOf(laddr, raddr);
                InetSocketAddress isa = (InetSocketAddress) raddr;
                if (raddr == null) {
                    chan = new ServerSocketChannelImpl(provider, family, newfd, true);
                } else {
                    chan = new SocketChannelImpl(provider, family, newfd, isa);
                }
                addToChannelList(receiveQueue, chan);
            }
        }
        return nbytes;
    }

    public static int write(FileDescriptor fd, ByteBuffer src, Set<SendableChannel> sendQueue,
                         NativeDispatcher nd)
        throws IOException
    {
        FileDescriptor[] sendfds = null;
        SendableChannel[] chans = {};
        synchronized (sendQueue) {
            if (!sendQueue.isEmpty()) {
                int l = sendQueue.size();
                sendfds = new FileDescriptor[l];
                chans = new SendableChannel[l];
                int i=0;
                for (SendableChannel sendee : sendQueue) {
                    if (!sendee.isOpen()) {
                        throw new IOException("Target channel for send is closed");
                    }
                    if (sendee.isRegistered()) {
                        throw new IOException("Target channel for send is registered with selector");
                    }
                    sendfds[i] = sendee.getFD();
                    chans[i] = sendee;
                    i++;
                };
                sendQueue.clear();
            }
        }
        int nbytes = IOUtil.sendmsg(fd, src, (SocketDispatcher)nd, sendfds);
        for (SendableChannel chan : chans) {
            try {chan.close(); } catch (IOException e) {}
        }
        return nbytes;
    }

    private static void addToChannelList(LinkedList<SendableChannel> list, SendableChannel c)
        throws IOException
    {
        addToChannelList(list, c, Integer.MAX_VALUE);
    }

    private static void addToChannelList(LinkedList<SendableChannel> list, SendableChannel c, int maxlistlen)
        throws IOException
    {
        synchronized (list) {
            if (list.size() >= maxlistlen) {
                throw new IOException("Too many entries in queue");
            }
            list.add(c);
        }
    }

    private static native boolean socketSupported();

    private static native int socket0() throws IOException;

    private static native void bind0(FileDescriptor fd, byte[] path)
        throws IOException;

    private static native int connect0(FileDescriptor fd, byte[] path)
        throws IOException;

    private static native int accept0(FileDescriptor fd, FileDescriptor newfd, Object[] array)
        throws IOException;

    static {
        // Load all required native libs
        IOUtil.load();
        supported = socketSupported();
    }
}
