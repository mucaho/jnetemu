/*
 * Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.net.*;
import java.nio.ByteBuffer;

public class TestWanEmulator {

    private final static int BYTE_COUNT = Integer.SIZE / Byte.SIZE;


    public static void main(String[] args) throws Exception {
        // setup the addresses (the ports are randomly chosen)
        InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), 26010);
        InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getLocalHost(), 26011);
        InetSocketAddress emulatorAddress = new InetSocketAddress(InetAddress.getLocalHost(), 26012);

        // setup the sockets
        final DatagramSocket serverSocket = new DatagramSocket(serverAddress);
        DatagramSocket clientSocket = new DatagramSocket(clientAddress);
        // setup emulator socket
        DatagramWanEmulator emu =
            new DatagramWanEmulator(emulatorAddress, serverAddress, clientAddress);



        // start the wan emulation
        emu.startEmulation();


        // receive the packets at server
        final ByteBuffer serverBuffer = ByteBuffer.allocate(BYTE_COUNT);
        final DatagramPacket serverPacket =
            new DatagramPacket(serverBuffer.array(), BYTE_COUNT);
        final Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() { try {

                while(!Thread.currentThread().isInterrupted()) {
                    serverSocket.receive(serverPacket);
                    int i = serverBuffer.getInt();
                    System.out.printf("[Server]: recving #%02d \n", i);
                    serverBuffer.clear();
                }

            } catch (SocketException e) { /*closed */ } catch (Exception e) { e.printStackTrace(); }}
        });
        serverThread.start(); // start the newly created thread


        // send the packets from the client to the wan emulator
        ByteBuffer clientBuffer = ByteBuffer.allocate(BYTE_COUNT);
        DatagramPacket clientPacket =
            new DatagramPacket(clientBuffer.array(), BYTE_COUNT, emulatorAddress);
        for (int i=0; i<100; i++) {
            System.out.printf("[Client]: sending #%02d \n", i);
            clientBuffer.clear();
            clientBuffer.putInt(i);
            clientPacket.setData(clientBuffer.array());
            clientSocket.send(clientPacket);
            Thread.sleep(10); // just a bit of delay
        }


        // shutdown: kill all threads by interrupting and exceptionally closing their sockets
        Thread.sleep(1000);
        serverThread.interrupt();
        clientSocket.close();
        emu.stopEmulation();
        serverSocket.close();
        serverThread.join();

    }

}
