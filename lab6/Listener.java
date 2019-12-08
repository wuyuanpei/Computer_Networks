/*
 *	This program will be used to test the firewall. This program will listen
 *	for UDP packets and TCP connections.
 *	
 *	
 */
 
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
 
public class Listener {
	public static void main(String args[]) {
		//Port for the TCP listening socket
		int tcpPort = 11313;
		
		//Create a TCP listening 
		ServerSocket tcpSock = null;
		try {
			tcpSock = new ServerSocket(tcpPort);
		}catch (Exception e) {
			System.err.println("Failed to create TCP listening socket: " + e);
			System.exit(1);
		}
		
		//Port for the UDP listening socket
		int udpPort = 30123;
		
		//Create a UDP listening socket
		DatagramSocket udpSock = null;
		try {
			udpSock = new DatagramSocket(udpPort);
		}catch (Exception e) {
			System.err.println("Failed to create UDP listening socket: " + e);
			System.exit(1);
		}
		
		long startTime = System.nanoTime();
		
		//Make new threads for the TCP and UDP receivers
		Thread tcpThread = new Thread(new testTcp(tcpSock, startTime));
		tcpThread.start();
		
		Thread udpThread = new Thread(new testUdp(udpSock, startTime));
		udpThread.start();		
	}
	
	public static class testTcp implements Runnable {
		ServerSocket sock;
		long startTime;
		
		public testTcp (ServerSocket tcpSock, long start) {
			sock = tcpSock;
			startTime = start;
		}
		
		@Override
		public void run() {
			Socket conSock = null;
			
			//If no packet is received for 5 seconds, then stop
			try {
				sock.setSoTimeout(2000);
			}catch (Exception e) {
				System.err.println("Failed setting timeout on TCP socket: " +
									e);
				System.exit(1);
			}
			
			while (true) {
				try {
					conSock = sock.accept();
				}catch (SocketTimeoutException e) {
					break;
				}catch (Exception e) {
					System.out.println("Error receiving TCP connection: " + e);
					System.exit(1);
				}
				
				//Make a new thread for this TCP connection
				Thread conThread = new Thread(new handleTcpCon(conSock, startTime));
				conThread.start();
			}
		}
	}
	
	public static class handleTcpCon implements Runnable {
		Socket sock;
		long startTime;
		
		public handleTcpCon(Socket conSock, long start) {
			sock = conSock;
			startTime = start;
		}
		
		@Override
		public void run() {
			//Get input from the socket
			BufferedReader readBuf = null;
			try {
				readBuf = new BufferedReader(new InputStreamReader
							(sock.getInputStream()));
			} catch (Exception e) {
				System.err.println("Error reading TCP packet: " + e);
				System.exit(1);
			}
			
			//The connection will end if there is a 2 second delay between 
			//two packets
			long endTime = System.nanoTime() + 2000000000L;
			while (true) {
				try {
					if (readBuf.ready()) {
						//Print out the contents of the packet and set
						//endTime
						System.out.println(readBuf.readLine() + " at " +
										((System.nanoTime() - startTime) /
										1000000000.0) + " seconds");
						endTime = System.nanoTime() + 2000000000L;
					}
					else if (endTime < System.nanoTime())
						break;
				}catch (Exception e) {
					System.err.println("Error reading buffer: " + e);
					System.exit(1);
				}
			}
			try {
				sock.close();
			}catch (Exception e) {
				System.err.println("Failed to close TCP connection: " + e);
				System.exit(1);
			}
		}
	}
	
	public static class testUdp implements Runnable {
		DatagramSocket sock;
		long startTime;
		
		//Packet for receiving packets
		byte[] buf = new byte[1000];
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		
		public testUdp(DatagramSocket udpSock, long start) {
			sock = udpSock;
			startTime = start;
		}
		
		@Override
		public void run() {
			//The port will stop listening if there is a 2 second delay between
			//two packets
			try{
				sock.setSoTimeout(2000);
			}catch (Exception e) {
				System.err.println("Error setting timeout on UDP socket: " +
									e);
				System.exit(1);
			}
			while (true) {
				//Wait for the socket to receive a packet
				try {
					sock.receive(pkt);
				}catch (SocketTimeoutException e) {
					//If no packet is received, then break out of the loop
					break;
				}catch (Exception e) {
					System.out.println("Error receiving UDP packet: " + e);
					System.exit(1);
				}
				
				try {
					System.out.println(new String(buf,0,pkt.getLength(),
						"US-ASCII") + " at " +
						((System.nanoTime() - startTime) / 1000000000.0) +
						" seconds");
				}catch (Exception e) {
					System.err.println("Failed to read UDP packet: " + e);
					System.exit(1);
				}
			}
		}
	}
}