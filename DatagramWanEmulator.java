/**
 * The MIT License (MIT)
 * Copyright (c) 2013 github.com/mucaho
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


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A convenience class for debugging UDP communication. The DatagramWanEmulator sits between
 * two specified {@link DatagramSocket}s and introduces latency, jitter and package loss 
 * between these two sockets.
 * <p>
 * In order to use the emulator, you send the {@link DatagramPacket}s directly 
 * to the emulator's {@linkplain SocketAddress} instead of sending them to the receiver 
 * {@linkplain SocketAddress}. 
 * The emulator will introduce latency, jitter and package loss. Then it will send 
 * the packets to the receiving socket.
 * <p>
 * This class can be instantiated with the respective addresses. Use the appropriate methods
 * to start and stop the emulation.
 * The specific emulation parameters can be tuned with the appropriate getters / setters. 
 * 
 * @author mucaho
 *
 */
public class DatagramWanEmulator {
	/**
	 * Internally used. Queues up delayed sending of datagram packets
	 */
	private final ScheduledThreadPoolExecutor STPE = new ScheduledThreadPoolExecutor(1);

	/** The socket address1. */
	private final InetSocketAddress socketAddress1;
	
	/** The socket address2. */
	private final InetSocketAddress socketAddress2;
	
	/** The emulator socket. */
	private DatagramSocket emulatorSocket;

	/**
	 * Construct a new DatagramWanEmulator. This will automatically create a new socket 
	 * bound to the specified emulatorAddress.
	 *
	 * @param emulatorAddress the emulator address
	 * @param hostAddress1 the host address1
	 * @param hostAddress2 the host address2
	 * @throws SocketException the socket exception
	 */
	public DatagramWanEmulator(InetSocketAddress emulatorAddress, InetSocketAddress hostAddress1, 
			InetSocketAddress hostAddress2) throws SocketException {
		this.socketAddress1 = hostAddress1;
		this.socketAddress2 = hostAddress2;
		
		emulatorSocket = new DatagramSocket(emulatorAddress);
	}
	
	/**
	 * The maximum size of incoming / outcoming datagram packets. Defaults to 1024.
	 */
	private int maxPacketLength = 1024;
	/**
	 * The amount of package loss. A value of 0.0f means no package loss, 
	 * a value of 1.0f means every packet will be lost.
	 */
	private float packageLossPercentage = 0.1f;
	/**
	 * The maximum latency between sending from one host to receiving on the other one.
	 * The latency will vary between zero and this value.
	 */
	private int maxLatency = 250;
	
	/**
	 * Gets the max packet length.
	 *
	 * @return the max packet length
	 */
	public int getMaxPacketLength() {
		return maxPacketLength;
	}

	/**
	 * Sets the max packet length.
	 *
	 * @param maxPacketLength the new max packet length
	 */
	public void setMaxPacketLength(int maxPacketLength) {
		this.maxPacketLength = maxPacketLength;
	}

	/**
	 * Gets the package loss percentage.
	 *
	 * @return the package loss percentage
	 */
	public float getPackageLossPercentage() {
		return packageLossPercentage;
	}

	/**
	 * Sets the package loss percentage.
	 *
	 * @param packageLossPercentage the new package loss percentage
	 */
	public void setPackageLossPercentage(float packageLossPercentage) {
		this.packageLossPercentage = packageLossPercentage;
	}

	/**
	 * Gets the max latency.
	 *
	 * @return the max latency
	 */
	public int getMaxLatency() {
		return maxLatency;
	}

	/**
	 * Sets the max latency.
	 *
	 * @param maxLatency the new max latency
	 */
	public void setMaxLatency(int maxLatency) {
		this.maxLatency = maxLatency;
	}

	/** The is running. */
	private volatile boolean isRunning = true;
	
	/** The running thread. */
	private Thread runningThread = null;
	
	/** The runnable. */
	private final Runnable runnable = new Runnable() {
		
		@Override
		public void run() {
			try {
				
				while(isRunning) {
					byte[] bytes = new byte[maxPacketLength];
					final DatagramPacket packet = new DatagramPacket(bytes, maxPacketLength);
					emulatorSocket.receive(packet);
					
					if (socketAddress1.equals(packet.getSocketAddress()))
						packet.setSocketAddress(socketAddress2);
					else if (socketAddress2.equals(packet.getSocketAddress()))
						packet.setSocketAddress(socketAddress1);
					
					if (Math.random() >= packageLossPercentage)
						STPE.schedule(new Runnable() {
							@Override
							public void run() {
								try {
									emulatorSocket.send(packet);
								} catch(Exception e) {e.printStackTrace();}
							}
						}, (int)(Math.random()*maxLatency), TimeUnit.MILLISECONDS);
				}
				
				
			} catch (Exception e) {
				e.printStackTrace();
				isRunning = false;
				runningThread = null;
			}
		}
	};
	
	/**
	 * Start the emulation.
	 */
	public void startEmulation() {
		if (runningThread == null) {
			runningThread = new Thread(runnable);
			runningThread.start();
		}
	}
	
	/**
	 * Let the emulation end gracefully.
	 *
	 * @throws InterruptedException the interrupted exception
	 */
	public void stopEmulation() throws InterruptedException {
		isRunning = false;
		if (runningThread != null) {
			runningThread.join();
			runningThread = null;
		}
	}
}
