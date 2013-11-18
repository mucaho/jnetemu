Java-UDP-WAN-Simulator
======================

Java utility for simulating simple network characteristics found in WANs (delay, jitter, package loss).
___________________
   
This is a convenience class for debugging UDP communication. The DatagramWanEmulator sits between
two specified DatagramSockets and introduces latency, jitter and package loss 
between these two sockets.

In order to use the emulator, you send the DatagramPackets directly 
to the emulator's SocketAddress instead of sending them to the receiver 
SocketAddress.
The emulator will introduce latency, jitter and package loss. Then it will send 
the packets to the receiving socket.

This class can be instantiated with the respective addresses. Use the appropriate methods
to start and stop the emulation.
The specific emulation parameters can be tuned with the appropriate getters / setters. 
