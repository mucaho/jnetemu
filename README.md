WAN emulation for UDP Datagrams
===============================

Java 1.6+ utility for emulating simple network characteristics found in WANs.   
The DatagramWanEmulator sits between two specified DatagramSockets and introduces delay, jitter, packet loss 
and duplication to packets relayed between these two sockets.   
Useful for debugging UDP communication, works cross platform and does not require booting into it on a dedicated emulation PC.


Source
---------------------
The code can be found inside __DatagramWanEmulator.java__.

JavaDoc
---------------------
The generated javadoc can be found on the [__Github Pages__](http://mucaho.github.io/jnetemu).

Example
---------------------
The complete, executable test can be found inside __TestWanEmulator.java__.

Here is a short pseudo-code example:
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

The debugging output looks similar to this:
```
...
[Client]: sending #92 
[Server]: recving #75 
[Server]: recving #78 
[Server]: recving #79 
[Client]: sending #93 
[Client]: sending #94 
[Server]: recving #75 
...
```

The debugging output shows the following characteristics:
* Messages get delivered __delayed__. (e.g. Client is sending #92, the Server is jut receiving #78)
* Messages get delivered __out-of-order__. (e.g. The server receives #75 after #79)
* Some messages do __not get delivered__ at all. (e.g. The whole debugging output should have 200 lines - 
100 sent + 100 received. In this case the line count was 189, that means some messages were not received)
* Some messages get __delivered multiple times__. (e.g. Server is receiving #75 twice)

_All of this parameters can be tuned on-the-fly_.
