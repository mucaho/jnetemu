/*
 * The MIT License (MIT)
 * Copyright (c) 2013-2016 mucaho (https://github.com/mucaho)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal 
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN 
 * THE SOFTWARE.
 */

package com.github.mucaho.jnetemu;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A convenience class for debugging UDP communication. The DatagramWanEmulator sits between
 * two specified {@link java.net.DatagramSocket}s and emulates various network conditions.
 * <p>
 * In order to use the emulator, you send the {@link java.net.DatagramPacket}s directly
 * to the emulator's {@link java.net.SocketAddress} instead of sending them to the receiver
 * {@link java.net.SocketAddress}.
 * The emulator will then apply various network conditions,
 * before sending those packets to the receiver {@link java.net.SocketAddress}.
 * <p>
 * This class can be partially instantiated using the respective
 * {@link com.github.mucaho.jnetemu.DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress) constructor}.
 * Use the appropriate methods to {@link com.github.mucaho.jnetemu.DatagramWanEmulator#startEmulation() start} and
 * {@link com.github.mucaho.jnetemu.DatagramWanEmulator#stopEmulation() stop} the emulation.
 * The actual computation of {@link #computeNetworkConditions(long, Collection, List) network conditions}
 * is given by subclasses of this abstract class.
 * <p>
 * Internally, this emulator uses a single thread for all emulator instances
 * by leveraging {@link java.nio NIO} selectors &amp; non-blocking channels.
 * <p>
 * There are multiple emulator configuration options available,
 * some possibilities are illustrated in the following figures:
 * <br>
 * Emulator sitting on PeerA, emulating network conditions for outgoing traffic only.
 * <pre>{@literal
 * ╔══════════╗      ╔═════╗      ╔═════════╗
 * ║ A -> EMU ║ ---> ║     ║ ---> ║    A    ║
 * ║ P        ║      ║ ??? ║      ║    P    ║
 * ║ P  <--   ║ <--- ║     ║ <--- ║    P    ║
 * ╚══════════╝      ╚═════╝      ╚═════════╝
 *    PeerA          Network         PeerB
 * }</pre>
 * <br>
 * Emulator sitting on PeerA, emulating network conditions for both outgoing and incoming traffic.
 * <pre>{@literal
 * ╔═════════╗      ╔═════╗      ╔═════════╗
 * ║ A     E ║      ║     ║      ║    A    ║
 * ║ P <-> M ║ <--> ║ ??? ║ <--> ║    P    ║
 * ║ P     U ║      ║     ║      ║    P    ║
 * ╚═════════╝      ╚═════╝      ╚═════════╝
 *    PeerA         Network         PeerB
 * }</pre>
 * <br>
 * One emulator sitting on each peer, emulating network conditions for its outgoing traffic respectively.
 * <pre>{@literal
 * ╔══════════╗      ╔═════╗      ╔══════════╗
 * ║ A -> EMU ║ ---> ║     ║ ---> ║    --> A ║
 * ║ P        ║      ║ ??? ║      ║        P ║
 * ║ P <--    ║ <--- ║     ║ <--- ║ EMU <- P ║
 * ╚══════════╝      ╚═════╝      ╚══════════╝
 *    PeerA          Network         PeerB
 * }</pre>
 *
 */
public abstract class DatagramWanEmulator {

    /////////////////////
    /// STATIC FIELDS ///
    /////////////////////

    /**
     * Minimum payload size in bytes of an UDP datagram that will not cause fragmentation in the IP layer.
     * <p>
     * That's the minimum supported MTU for IPv4 ({@code 576} B), minus the maximum IPv4 header size ({@code 60} B)
     * and minus the UDP header size ({@code 8} B). Defaults to {@value} B.
     */
    public final static int MINIMUM__PACKET_SIZE = 576 - 60 - 8;
    /**
     * Size in bytes of the usually supported MTU for IPv4. Defaults to {@value} B.
     */
    public final static int DEFAULT_PACKET_SIZE = 1500;
    // incremented on instance creation; decremented on instance destruction
    private final static AtomicInteger emulatorCount = new AtomicInteger(0);
    // setup on first instance creation; torn-down on last instance destruction
    private static Thread selectionThread;
    private static Selector selector;




    ///////////////////////
    /// INSTANCE FIELDS ///
    ///////////////////////

    // setup on instance creation
    private final Queue<ScheduledPacket> scheduledPackets = new PriorityQueue<ScheduledPacket>(16);
    private final Queue<ByteBuffer> writableBuffers = new LinkedList<ByteBuffer>();
    // setup in constructor
    private final SocketAddress socketAddressA;
    private final SocketAddress socketAddressB;
    private final SocketAddress emulatorAddress;
    private final int maxPacketSize;
    // setup/torn-down on start/end emulation
    private DatagramChannel datagramChannel;
    private SelectionKey selectionKey;





    /**
     * Construct a new {@code DatagramWanEmulator}. This will automatically create a new socket
     * bound to the specified {@code emulatorAddress},
     * with {@code maxPacketSize} set to {@link #MINIMUM__PACKET_SIZE MINIMUM__PACKET_SIZE}.
     *
     * @param emulatorAddress the emulator address
     * @param socketAddressA  one socket address
     * @param socketAddressB  the other socket address
     */
    public DatagramWanEmulator(SocketAddress emulatorAddress, SocketAddress socketAddressA,
                               SocketAddress socketAddressB) {
        this(emulatorAddress, socketAddressA, socketAddressB, MINIMUM__PACKET_SIZE);
    }

    /**
     * Construct a new {@code DatagramWanEmulator}. This will automatically create a new socket
     * bound to the specified {@code emulatorAddress}.
     *
     * @param emulatorAddress the emulator address
     * @param socketAddressA  one socket address
     * @param socketAddressB  the other socket address
     * @param maxPacketSize the maximum size of incoming / outgoing datagram packets (in bytes).
     */
    public DatagramWanEmulator(SocketAddress emulatorAddress, SocketAddress socketAddressA,
                               SocketAddress socketAddressB, int maxPacketSize) {
        this.socketAddressA = socketAddressA;
        this.socketAddressB = socketAddressB;
        this.emulatorAddress = emulatorAddress;
        this.maxPacketSize = maxPacketSize;

        allocateBuffers(16);
    }

    /**
     * Expand the amount of available buffers to write to.
     */
    private void allocateBuffers(int amount) {
        do {
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxPacketSize);
            writableBuffers.offer(buffer);
        } while (--amount > 0);
    }


    ////////////////////////
    /// INSTANCE METHODS ///
    ////////////////////////

    /**
     * Compute network conditions. Actual implementation given in subclasses.
     * <br>
     * This method will be called once per incoming packet and computes the network conditions for the outgoing packet.
     * At any time it is guaranteed to be called from a single thread only,
     * thus implementing classes should not worry about threading issues.
     *
     * @param timeNow the current low-resolution system time in {@code ms},
     *                that should be used by all time-dependent computations.
     *                It represents the difference between the current time
     *                and midnight, 01.01.1970 UTC.
     * @param scheduled a read-only ordered collection containing already scheduled packets.
     *                  Note that packets being duplicated are represented multiple times in this collection.
     *                  This is provided as additional information for computing network conditions.
     * @param scheduledTimes a writable list that should be filled with zero or more scheduled times
     *                       at which the incoming packet will be relayed.
     *                       Scheduled times are given as the difference between current time ({@code timeNow})
     *                       and midnight, 01.01.1970 UTC.
     *                       <ul>
     *                       <li>A zero-length list indicates packet gets dropped.</li>
     *                       <li>A list with a single element indicates at which time to relay the incoming packet.</li>
     *                       <li>A list with {@code n} elements indicates that the packet will be duplicated {@code n-1} times.
     *                       These duplicates will be sent according to the time values provided.</li>
     *                       </ul>
     */
    protected abstract void computeNetworkConditions(long timeNow,
                                                     Collection<? extends Scheduled> scheduled,
                                                     List<Long> scheduledTimes);

    private final List<Long> scheduledTimes = new ArrayList<Long>();
    private final Collection<? extends Scheduled> scheduledOut = Collections.unmodifiableCollection(scheduledPackets);
    private List<Long> computeNetworkConditions(long timeNow) {
        scheduledTimes.clear();
        computeNetworkConditions(timeNow, scheduledOut, scheduledTimes);
        return scheduledTimes;
    }

    /**
     * Read from channel to buffers.
     * @param timeNow current time in ms
     * @return {@code true} if something was read, {@code false} otherwise
     */
    private boolean read(long timeNow) throws IOException {
        ByteBuffer buffer = writableBuffers.poll();
        if (buffer == null) {
            allocateBuffers(16);
            buffer = writableBuffers.poll();
        }

        SocketAddress senderAddress = datagramChannel.receive(buffer);
        if (senderAddress == null) { // if there was no datagram to receive yet
            buffer.clear();
            writableBuffers.offer(buffer);
            return false;
        }
        buffer.flip();

        SocketAddress receiverAddress;
        if (socketAddressA.equals(senderAddress))
            receiverAddress = socketAddressB;
        else if (socketAddressB.equals(senderAddress))
            receiverAddress = socketAddressA;
        else
            receiverAddress = null;
        if (receiverAddress == null) { // if datagram was received from 3rd party
            buffer.clear();
            writableBuffers.offer(buffer);
            return true;
        }

        PacketId packetId = new PacketId();
        for (Long scheduledTime : computeNetworkConditions(timeNow)) {
            packetId.count++;
            ScheduledPacket scheduled = new ScheduledPacket(buffer, receiverAddress, packetId, scheduledTime);
            scheduledPackets.offer(scheduled);
        }

        if (packetId.count == 0) {
            buffer.clear();
            writableBuffers.offer(buffer);
        }

        return true;
    }

    /**
     * Check if there are buffers ready to be written.
     * @param timeNow the current time in ms
     * @return {@code true} if something was written, {@code false} otherwise
     */
    private boolean needsWriting(long timeNow) {
        ScheduledPacket scheduled = scheduledPackets.peek();
        return scheduled != null && scheduled.isReady(timeNow);
    }

    /**
     * Write from ready buffers to channel.
     * @param timeNow current time in ms
     * @return {@code true} if something was written, {@code false} otherwise
     */
    private boolean write(long timeNow) throws IOException {
        ScheduledPacket scheduled = scheduledPackets.peek();
        if (scheduled == null || !scheduled.isReady(timeNow)) { // if there is no ready packet yet
            return false;
        }
        scheduled = scheduledPackets.poll();
        ByteBuffer buffer = scheduled.buffer;

        buffer.rewind();
        int bytesSent = datagramChannel.send(buffer, scheduled.receiverAddress);
        if (bytesSent <= 0) { // if datagram could not be sent yet
            buffer.rewind();
            scheduledPackets.offer(scheduled);
            return false;
        }

        if (--scheduled.packetId.count == 0) {
            buffer.clear();
            writableBuffers.offer(buffer);
        }

        return true;
    }



    ////////////////////////
    /// SELECTION THREAD ///
    ////////////////////////

    private final static Runnable runSelection = new Runnable() {
        @Override
        public void run() {
            try {
                // run the selection loop until interrupted
                while(!Thread.currentThread().isInterrupted()) {
                    long timeNow = System.currentTimeMillis();

                    try {
                        // check which buffers are ready to be written
                        for (SelectionKey key : selector.keys()) {
                            DatagramWanEmulator emulator = (DatagramWanEmulator) key.attachment();
                            if (emulator.needsWriting(timeNow))
                                // add OP_WRITE as interestOp
                                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                        }

                        // do reading and writing on ready channels
                        selector.selectNow();
                        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();

                            DatagramWanEmulator emulator = (DatagramWanEmulator) key.attachment();
                            if (key.isReadable()) {
                                boolean hasMore;
                                do {
                                    hasMore = emulator.read(timeNow);
                                } while (hasMore);
                            }
                            if (key.isWritable()) {
                                boolean hasMore;
                                do {
                                    hasMore = emulator.write(timeNow);
                                } while (hasMore);

                                // remove OP_WRITE as interestOp
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            }

                            keyIterator.remove();
                        }
                    } catch (CancelledKeyException e) {
                        // key cancelled from other thread; safe to ignore
                    } catch (ClosedChannelException e) {
                        // channel closed from other thread; safe to ignore
                    }

                    Thread.yield();
                }
            } catch (IOException e) {
                // some not-handleable error occurred
                e.printStackTrace();
            }
        }
    };


    /////////////////////////////////
    /// INSTANCE SETUP & TEARDOWN ///
    /////////////////////////////////

    /**
     * Start the emulation between the two sockets.
     *
     * @throws ClosedChannelException if the current instance has already been stopped by
     * {@link #stopEmulation() stopEmulation()}. Create a new
     * {@link com.github.mucaho.jnetemu.DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress) instance} instead.
     * @throws java.io.IOException if there was some other I/O error
     */
    public void startEmulation() throws IOException {
        if (datagramChannel != null && !datagramChannel.isOpen())
            throw new ClosedChannelException();

        // setup up a new selectionThread if this is the first emulator instance
        synchronized (DatagramWanEmulator.class) {
            if (emulatorCount.getAndIncrement() == 0) {
                // open a selector
                selector = Selector.open();

                selectionThread = new Thread(runSelection);
                selectionThread.start();
            }
        }

        // register this instance to the selectionThread
        datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        datagramChannel.socket().bind(emulatorAddress);
        selectionKey = datagramChannel.register(selector, SelectionKey.OP_READ, this);
    }

    /**
     * Stop the emulation between the two sockets and close the associated emulator socket.
     *
     * @throws java.lang.InterruptedException if the current instance is interrupted while shutting down last emulation
     * @throws java.io.IOException if there was some other I/O error
     */
    public void stopEmulation() throws InterruptedException, IOException {
        // destroy the old selectionThread if this is was the last emulator instance
        synchronized (DatagramWanEmulator.class) {
            if (emulatorCount.decrementAndGet() == 0) {
                selectionThread.interrupt();
                selectionThread.join();
                selectionThread = null;

                // close the selector
                selector.close();
                selector = null;
            }
        }

        // deregister this instance from the selectionThread
        selectionKey.cancel();
        datagramChannel.close();
    }

    //////////////////////
    /// HELPER CLASSES ///
    //////////////////////

    private final static class PacketId {
        private int count = 0;
    }

    private final static class ScheduledPacket implements Scheduled {
        private final ByteBuffer buffer;
        private final SocketAddress receiverAddress;
        private final long finishedTime;
        private final PacketId packetId;

        private ScheduledPacket(ByteBuffer buffer, SocketAddress receiverAddress,
                                PacketId packetId, long finishedTime) {
            this.buffer = buffer;
            this.receiverAddress = receiverAddress;
            this.finishedTime = finishedTime;
            this.packetId = packetId;
        }

        @Override
        public long getScheduledTime() {
            return finishedTime;
        }

        @Override
        public boolean isReady(long timeNow) {
            return finishedTime <= timeNow;
        }

        @Override
        public int compareTo(Scheduled other) {
            long thisDelay = this.getScheduledTime();
            long otherDelay = other.getScheduledTime();
            return (int) (thisDelay - otherDelay);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ScheduledPacket that = (ScheduledPacket) o;

            if (finishedTime != that.finishedTime) return false;
            if (!buffer.equals(that.buffer)) return false;
            if (!receiverAddress.equals(that.receiverAddress)) return false;
            return packetId.equals(that.packetId);

        }

        @Override
        public int hashCode() {
            int result = buffer.hashCode();
            result = 31 * result + receiverAddress.hashCode();
            result = 31 * result + (int) (finishedTime ^ (finishedTime >>> 32));
            result = 31 * result + packetId.hashCode();
            return result;
        }
    }
}
