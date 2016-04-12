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

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A convenience class for debugging UDP communication. The DatagramWanEmulator sits between
 * two specified {@link DatagramSocket}s and introduces packet-latency, -jitter, -loss and -duplication
 * between these two sockets.
 * <p>
 * In order to use the emulator, you send the {@link DatagramPacket}s directly 
 * to the emulator's {@link java.net.SocketAddress} instead of sending them to the receiver 
 * {@link java.net.SocketAddress}.
 * The emulator will then apply packet latency, jitter, loss and duplication.
 * before sending those packets to the receiver {@link java.net.SocketAddress}.
 * <p>
 * This class can be instantiated using the respective
 * {@link DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress) constructor}.
 * Use the appropriate methods to {@link DatagramWanEmulator#startEmulation() start} and
 * {@link DatagramWanEmulator#stopEmulation() stop} the emulation.
 * The specific emulation parameters can be tuned with the appropriate getters / setters,
 * before starting the emulation or while emulation is already active.
 * <p>
 * Internally, this emulator uses a single thread for all emulator instances
 * by leveraging {@link java.nio NIO} selectors &amp; non-blocking channels.
 * <p>
 * There are multiple emulator configuration options available,
 * some possibilities are illustrated in the following figures:
 * <br>
 * Emulator sitting on PeerA, emulating network conditions for outgoing traffic only.
 * <pre>
 * ╔══════════╗      ╔═════╗      ╔═════════╗
 * ║ A -> EMU ║ ---> ║     ║ ---> ║    A    ║
 * ║ P        ║      ║ ??? ║      ║    P    ║
 * ║ P  <--   ║ <--- ║     ║ <--- ║    P    ║
 * ╚══════════╝      ╚═════╝      ╚═════════╝
 *    PeerA          Network         PeerB
 * </pre>
 * <br>
 * Emulator sitting on PeerA, emulating network conditions for both outgoing and incoming traffic.
 * <pre>
 * ╔═════════╗      ╔═════╗      ╔═════════╗
 * ║ A     E ║      ║     ║      ║    A    ║
 * ║ P <-> M ║ <--> ║ ??? ║ <--> ║    P    ║
 * ║ P     U ║      ║     ║      ║    P    ║
 * ╚═════════╝      ╚═════╝      ╚═════════╝
 *    PeerA         Network         PeerB
 * </pre>
 * <br>
 * One emulator sitting on each peer, emulating network conditions for its outgoing traffic respectively.
 * <pre>
 * ╔══════════╗      ╔═════╗      ╔══════════╗
 * ║ A -> EMU ║ ---> ║     ║ ---> ║    --> A ║
 * ║ P        ║      ║ ??? ║      ║        P ║
 * ║ P <--    ║ <--- ║     ║ <--- ║ EMU <- P ║
 * ╚══════════╝      ╚═════╝      ╚══════════╝
 *    PeerA          Network         PeerB
 * </pre>
 *
 * @author mucaho
 *
 */
public class DatagramWanEmulator {

    /////////////////////
    /// STATIC FIELDS ///
    /////////////////////

    /**
     * Minimum payload size in bytes of an UDP datagram that will not cause fragmentation in the IP layer.
     * <p>
     * That's the minimum supported MTU for IPv4 ({@code 576} B), minus the maximum IPv4 header size ({@code 60} B)
     * and minus the UDP header size ({@code 8} B).
     */
    public static int MINIMUM__PACKET_SIZE = 576 - 60 - 8;
    /**
     * Size in bytes of the usually supported MTU for IPv4.
     */
    public static int DEFAULT_PACKET_SIZE = 1500;
    // incremented on instance creation; decremented on instance destruction
    private final static AtomicInteger emulatorCount = new AtomicInteger(0);
    // setup on first instance creation; torn-down on last instance destruction
    private static Thread selectionThread;
    private static Selector selector;




    ///////////////////////
    /// INSTANCE FIELDS ///
    ///////////////////////

    // setup on instance creation
    private final Queue<QueuedPacket> queuedPackets = new PriorityQueue<QueuedPacket>(16);
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




    //////////////////
    /// PROPERTIES ///
    //////////////////

    /**
     * The chance of a packet being lost. A value of {@code 0.0f} means no packet loss,
     * a value of {@code 1.0f} means every packet will be lost.
     * Defaults to {@code 0.1f} ({@code 10%}).
     */
    private volatile float loss = 0.1f;

    /**
     * The chance of a packet being duplicated. A value of {@code 0.0f} means no
     * packets will be duplicated, a value of {@code 1.0f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     */
    private volatile float duplication = 0.03f;

    /**
     * The maximum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 250 ms}.
     */
    private volatile int maxLatency = 250;

    /**
     * The minimum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 100 ms}.
     */
    private volatile int minLatency = 100;


    /**
     * Get the chance of a packet being lost. A value of {@code 0.0f} means no packet loss,
     * a value of {@code 1.0f} means every packet will be lost.
     * Defaults to {@code 0.1f} ({@code 10%}).
     *
     * @return the packet loss fraction
     */
    public float getLoss() {
        return loss;
    }

    /**
     * Set the chance of a packet being lost. A value of {@code 0.0f} means no packet loss,
     * a value of {@code 1.0f} means every packet will be lost.
     * Defaults to {@code 0.1f} ({@code 10%}).
     *
     * @param loss the new packet loss fraction
     */
    public void setLoss(float loss) {
        this.loss = loss;
    }

    /**
     * Get the chance of a packet being duplicated. A value of {@code 0.0f} means no
     * packets will be duplicated, a value of {@code 1.0f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     *
     * @return the packet duplication fraction
     */
    public float getDuplication() {
        return duplication;
    }

    /**
     * Set the chance of a packet being duplicated. A value of {@code 0.0f} means no
     * packets will be duplicated, a value of {@code 1.0f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     *
     * @param duplication the new packet duplication fraction
     */
    public void setDuplication(float duplication) {
        this.duplication = duplication;
    }

    /**
     * Get the maximum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 250 ms}.
     *
     * @return the max latency in ms
     */
    public int getMaxLatency() {
        return maxLatency;
    }

    /**
     * Set the maximum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 250 ms}.
     *
     * @param maxLatency the new max latency in ms
     */
    public void setMaxLatency(int maxLatency) {
        this.maxLatency = maxLatency;
    }


    /**
     * Get the minimum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 100 ms}.
     *
     * @return the min latency in ms
     */
    public int getMinLatency() {
        return minLatency;
    }

    /**
     * Set the minimum latency between sending from one peer to receiving on the other one.
     * The latency will randomly vary between {@code minLatency} and {@code maxLatency}.
     * Defaults to {@code 100 ms}.
     *
     * @param minLatency the new min latency in ms
     */
    public void setMinLatency(int minLatency) {
        this.minLatency = minLatency;
    }




    ////////////////////////
    /// INSTANCE METHODS ///
    ////////////////////////


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
        do {
            if (Math.random() >= loss) {
                packetId.count++;
                QueuedPacket queued = new QueuedPacket(buffer, receiverAddress, packetId,
                        timeNow + minLatency + (int) (Math.random() * (maxLatency - minLatency)));
                queuedPackets.offer(queued);
            }
        } while (Math.random() < duplication);

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
        QueuedPacket queued = queuedPackets.peek();
        return queued != null && queued.isReady(timeNow);
    }

    /**
     * Write from ready buffers to channel.
     * @param timeNow current time in ms
     * @return {@code true} if something was written, {@code false} otherwise
     */
    private boolean write(long timeNow) throws IOException {
        QueuedPacket queued = queuedPackets.peek();
        if (queued == null || !queued.isReady(timeNow)) { // if there is no ready packet yet
            return false;
        }
        queued = queuedPackets.poll();
        ByteBuffer buffer = queued.buffer;

        buffer.rewind();
        int bytesSent = datagramChannel.send(buffer, queued.receiverAddress);
        if (bytesSent <= 0) { // if datagram could not be sent yet
            buffer.rewind();
            queuedPackets.offer(queued);
            return false;
        }

        if (--queued.packetId.count == 0) {
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
                        for (SelectionKey key: selector.keys()) {
                            DatagramWanEmulator emulator = (DatagramWanEmulator) key.attachment();
                            if (emulator.needsWriting(timeNow))
                                // add OP_WRITE as interestOp
                                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                        }

                        // do reading and writing on ready channels
                        selector.selectNow();
                        Iterator<SelectionKey>keyIterator = selector.selectedKeys().iterator();
                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();

                            DatagramWanEmulator emulator = (DatagramWanEmulator) key.attachment();
                            if (key.isReadable()) {
                                while (emulator.read(timeNow)) ;
                            }
                            if (key.isWritable()) {
                                while (emulator.write(timeNow)) ;
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
     * {@link DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress) instance} instead.
     * @throws IOException if there was some other I/O error
     */
    public void startEmulation() throws IOException {
        if (datagramChannel != null && !datagramChannel.isOpen())
            throw new ClosedChannelException();

        // setup up a new selectionThread if this is the first emulator instance
        synchronized (DatagramWanEmulator.emulatorCount) {
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
     * @throws InterruptedException if the current instance is interrupted while shutting down last emulation
     * @throws IOException if there was some other I/O error
     */
    public void stopEmulation() throws InterruptedException, IOException {
        // destroy the old selectionThread if this is was the last emulator instance
        synchronized (DatagramWanEmulator.emulatorCount) {
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

    private final static class QueuedPacket implements Comparable<QueuedPacket> {
        private final ByteBuffer buffer;
        private final SocketAddress receiverAddress;
        private final long finishedTime;
        private final PacketId packetId;

        private QueuedPacket(ByteBuffer buffer, SocketAddress receiverAddress,
                             PacketId packetId, long finishedTime) {
            this.buffer = buffer;
            this.receiverAddress = receiverAddress;
            this.finishedTime = finishedTime;
            this.packetId = packetId;
        }

        @Override
        public int compareTo(QueuedPacket other) {
            long thisDelay = this.finishedTime;
            long otherDelay = other.finishedTime;
            return (int) (thisDelay - otherDelay);
        }

        private boolean isReady(long timeNow) {
            return finishedTime <= timeNow;
        }
    }
}
