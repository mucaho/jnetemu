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


import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * This class provides a simple implementation of a {@link DatagramWanEmulator network emulator}.
 * It introduces packet-latency, -jitter, -loss and -duplication to incoming packets, before relaying them.
 * <br>
 * The specific emulation parameters can be tuned with the appropriate getters / setters,
 * before {@link com.github.mucaho.jnetemu.DatagramWanEmulator#startEmulation() starting the emulation} or while emulation is already active.
 */
public class SimpleWanEmulator extends DatagramWanEmulator {

    private final static Random random = new Random();

    /**
     * Construct a new {@code SimpleWanEmulator}. This will automatically create a new socket
     * bound to the specified {@code emulatorAddress},
     * with {@code maxPacketSize} set to {@link com.github.mucaho.jnetemu.DatagramWanEmulator#MINIMUM__PACKET_SIZE MINIMUM__PACKET_SIZE}.
     *
     * @param emulatorAddress the emulator address
     * @param socketAddressA  one socket address
     * @param socketAddressB  the other socket address
     * @see DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress)
     */
    public SimpleWanEmulator(SocketAddress emulatorAddress, SocketAddress socketAddressA, SocketAddress socketAddressB) {
        super(emulatorAddress, socketAddressA, socketAddressB);
    }

    /**
     * Construct a new {@code SimpleWanEmulator}. This will automatically create a new socket
     * bound to the specified {@code emulatorAddress}.
     *
     * @param emulatorAddress the emulator address
     * @param socketAddressA  one socket address
     * @param socketAddressB  the other socket address
     * @param maxPacketSize   the maximum size of incoming / outgoing datagram packets (in bytes).
     * @see DatagramWanEmulator#DatagramWanEmulator(SocketAddress, SocketAddress, SocketAddress, int)
     */
    public SimpleWanEmulator(SocketAddress emulatorAddress, SocketAddress socketAddressA, SocketAddress socketAddressB, int maxPacketSize) {
        super(emulatorAddress, socketAddressA, socketAddressB, maxPacketSize);
    }


    //////////////////
    /// PROPERTIES ///
    //////////////////

    /**
     * The chance of a packet being lost. A value of {@code 0.00f} means no packet loss,
     * a value of {@code 1.00f} means every packet will be lost.
     * Defaults to {@code 0.10f} ({@code 10%}).
     */
    private volatile float loss = 0.1f;

    /**
     * The chance of a packet being duplicated. A value of {@code 0.00f} means no
     * packets will be duplicated, a value of {@code 1.00f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     */
    private volatile float duplication = 0.03f;

    /**
     * The base delay between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code 175 ms}.
     */
    private volatile int delay = 175;

    /**
     * The delay jitter between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code +/- 75 ms}.
     */
    private volatile int jitter = 75;

    /**
     * Get the chance of a packet being lost. A value of {@code 0.00f} means no packet loss,
     * a value of {@code 1.00f} means every packet will be lost.
     * Defaults to {@code 0.10f} ({@code 10%}).
     *
     * @return the packet loss fraction
     */
    public float loss() {
        return loss;
    }

    /**
     * Set the chance of a packet being lost. A value of {@code 0.0f} means no packet loss,
     * a value of {@code 1.0f} means every packet will be lost.
     * Defaults to {@code 0.10f} ({@code 10%}).
     *
     * @param loss the new packet loss fraction
     */
    public void loss(float loss) {
        this.loss = loss;
    }

    /**
     * Get the chance of a packet being duplicated. A value of {@code 0.00f} means no
     * packets will be duplicated, a value of {@code 1.00f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     *
     * @return the packet duplication fraction
     */
    public float duplication() {
        return duplication;
    }

    /**
     * Set the chance of a packet being duplicated. A value of {@code 0.00f} means no
     * packets will be duplicated, a value of {@code 1.00f} means every packet will
     * be duplicated infinitely.
     * Defaults to {@code 0.03f} ({@code 3%}).
     *
     * @param duplication the new packet duplication fraction
     */
    public void duplication(float duplication) {
        this.duplication = duplication;
    }


    /**
     * Get the base delay between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code 175 ms}.
     *
     * @return the base delay in ms
     */
    public int delay() {
        return delay;
    }

    /**
     * Set the base delay between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code 175 ms}.
     *
     * @param delay the new base delay in ms
     */
    public void delay(int delay) {
        this.delay = delay;
    }

    /**
     * Get the delay jitter between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code +/- 75 ms}.
     *
     * @return the delay jitter in ms
     */
    public int jitter() {
        return jitter;
    }

    /**
     * Set the delay jitter between sending from one peer to receiveing on the other one.
     * The actual delay will randomly vary between {@code baseDelay +/- jitter}.
     * Defaults to {@code +/- 75 ms}.
     *
     * @param jitter the new delay jitter in ms
     */
    public void jitter(int jitter) {
        this.jitter = jitter;
    }


    /** {@inheritDoc} */
    @Override
    protected void computeNetworkConditions(long timeNow, Collection<? extends Scheduled> scheduled, List<Long> scheduledTimes) {
        do {
            if (random.nextFloat() >= loss)
                scheduledTimes.add(timeNow + delay - jitter + random.nextInt(jitter * 2 + 1));
        } while (random.nextFloat() < duplication);
    }
}
