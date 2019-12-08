/*
 *
 *	java Sender receiverIP tcpPort udpPort
 *
 *	This program will send TCP and UDP packets to another host on ONL.
 *      The packets are spaced apart by (approximately) 50ms.
 *	This program takes two three arguments:
 *
 *	receiverIP	the IP address of the host that you wish to send test
 *				packets to
 *	tcpPort		the port on the receiving host that you wish to send tcp
 *				packets to
 *	udpPort		the port on the receiving host that you wish to send udp
 *				packets to
 */
 
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Sender {
	public static void main(String[] args) {
		//Process command-line arguments
		if (args.length != 4) {
			System.out.println("Incorrect number of arguments");
			System.exit(1);
		}
		//Assign variables
		InetAddress recvAddr = null;
		try {
			recvAddr = InetAddress.getByName(args[0]);
		}catch (Exception e) {
			System.err.println("Failed to find IP address: " + e);
			System.exit(1);
		}
		int tcpPort = Integer.parseInt(args[1]);
		int udpPort = Integer.parseInt(args[2]);
		String host = args[3];
		
		//Send TCP packets
		Thread tcpThread = new Thread(new tcpTest(recvAddr, tcpPort, host));
		tcpThread.start();
		
		//Send UDP packets
		Thread udpThread = new Thread(new udpTest(recvAddr, udpPort, host));
		udpThread.start();
	}
	
	public static class tcpTest implements Runnable {
		InetAddress rAddr;
		int port;
		String host;
		
		public tcpTest (InetAddress recvAddr, int tcpPort, String hst) {
			rAddr = recvAddr;
			port = tcpPort;
			host = hst;
		}
		
		@Override
		public void run () {
			//Create TCP socket
			Socket sock = null;
			try {
				sock = new Socket(rAddr, port);
			}catch (Exception e) {
				System.exit(1);
			}
			
			//Create a buffer to write to the socket
			BufferedWriter writeBuf = null;
			try {
				writeBuf = new BufferedWriter(new OutputStreamWriter
							   (sock.getOutputStream()));
			}catch (Exception e) {
				System.err.println("Failed to create TCP buffer: " + e);
				System.exit(1);
			}
			
			//Send 10 packets
			for (int x=0; x<10; x++) {
				//Create a message
				String msg = "Received TCP packet " + x + " from " + host;
				
				//Send the message to the receiving host
				try {
					writeBuf.write(msg, 0, msg.length());
					writeBuf.newLine();
					writeBuf.flush();
				}catch (Exception e) {
					System.err.println("Failed to send TCP packet: " + e);
					System.exit(1);
				}
				
				//Wait .05 seconds
				try {
					Thread.sleep(50);
				}catch (Exception e) {
					System.err.println("Failed to sleep: " + e);
					System.exit(1);
				}
			}
			
			//Close the socket
			try {
				sock.close();
			}catch (Exception e) {
				System.err.println("Failed to close TCP socket: " + e);
				System.exit(1);
			}
		}
	}
	
	public static class udpTest implements Runnable {
		InetAddress rAddr;
		int port;
		String host;
		
		public udpTest (InetAddress recvAddr, int udpPort, String hst) {
			rAddr = recvAddr;
			port = udpPort;
			host = hst;
		}
		
		@Override
		public void run () {
			//Create a socket and a packet
			DatagramSocket sock = null;
			try {
				sock = new DatagramSocket();
			}catch (Exception e) {
				System.out.println("Failed to create UDP socket: " + e);
				System.exit(1);
			}
			
			//Send 10 packets
			for (int x=0; x<10; x++) {
				//Create a message
				String message = "Received UDP packet " + x + " from " + host;
				byte[] msg = message.getBytes();
				
				//Put the message into the packet
				DatagramPacket pkt = new DatagramPacket
									 (msg, msg.length, rAddr, port);
				
				//Send the packet
				try {
					sock.send(pkt);
				}catch (Exception e) {
					System.err.println("Failed to send UDP packet: " + e);
					System.exit(1);
				}
				
				//Wait .05 seconds
				try {
					Thread.sleep(50);
				}catch (Exception e) {
					System.err.println("Failed to sleep: " + e);
					System.exit(1);
				}
			}
			
			//Close the socket
			sock.close();
		}
	}
}
