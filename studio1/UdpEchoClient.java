import java.io.*;
import java.net.*;

/**
 * usage: UdpEchoClient serverName port string
 *
 * Send a packet to the named server:port containing the given string.
 * Wait for reply packet and print its contents.
**/

public class UdpEchoClient {
	public static void main(String args[]) throws Exception {
		// get server address using first command-line argument
		InetAddress serverAdr = InetAddress.getByName(args[0]);
		int port = Integer.parseInt(args[1]);

		// open datagram socket
		DatagramSocket sock = new DatagramSocket();

		// build packet addressed to server containing second argument,
		// encoded using US-ASCII Charset, then send it
		byte[] outBuf = args[2].getBytes("UTF-16");
		DatagramPacket outPkt = new DatagramPacket(outBuf,outBuf.length,
							   serverAdr,port);
		sock.send(outPkt);	// send packet to server

		// create buffer and packet for reply, then receive it
		byte[] inBuf = new byte[1000];
		DatagramPacket inPkt = new DatagramPacket(inBuf,inBuf.length);
		sock.receive(inPkt);	// wait for reply

		// print buffer contents and close socket
		String reply = new String(inBuf,0,inPkt.getLength(),"UTF-16");
		System.out.println(reply);
		sock.close();
	}
}
