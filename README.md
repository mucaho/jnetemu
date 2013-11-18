Java datagrams in emulated WAN
======================

Java utility for emulating simple network characteristics found in WANs (delay, jitter, package loss).   
This is a convenience class for debugging UDP communication. The DatagramWanEmulator sits between
two specified DatagramSockets and introduces latency, jitter and package loss 
between these two sockets.

Source
---------------------
The code can be found inside __DatagramWanEmulator.java__

JavaDoc
---------------------
The generated javadoc can be found on the [__Github Pages__](http://mucaho.github.io/Java-UDP-WAN-Emulator)

Example
---------------------
Here is a short pseudo-code example, the debugging output shows the following characteristics.
_All of this parameters can be tuned:_
* Messages get delivered delayed.
* Messages get delivered out-of-order.
* Some messages do not get delivered at all.   



The complete, executable test can be found inside __TestWanEmulator.java__.
```java
		// setup the sockets
		DatagramSocket serverSocket = new DatagramSocket(new InetSocketAddress(...));
		DatagramSocket clientSocket = new DatagramSocket(new InetSocketAddress(...));
		
		// setup emulator socket
		InetSocketAddress emulatorAddress = new InetSocketAddress(...);
		DatagramWanEmulator emulator = new DatagramWanEmulator(emulatorAddress, ...);
		
		// start the wan emulation
		emulator.startEmulation();

		// send the packets from the client to the wan emulator
		DatagramPacket clientPacket = new DatagramPacket(..., emulatorAddress);
		for (int i=0; i<100; i++) {
		  clientPacket.setData(i);
			System.out.printf("[Client]: sending #%02d \n", i);
			clientSocket.send(clientPacket);
		}
		
		// receive the packets at server
		DatagramPacket serverPacket = new DatagramPacket(...);
		while(true) {
			serverSocket.receive(serverPacket);
			System.out.printf("[Server]: recving #%02d \n", serverPacket.getData(...));
		}
```



