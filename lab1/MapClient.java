/****************************************************************************
 Name: Richard Wu
 Student ID: 464493
 Date: 2019/9/1
 This program system includes a client program(MapClient) and a server
 program (MapServer). The server will store a set of key and value pairs and
 the client can send instructions to server to get, put, or remove any pair
 stored. The format of using the client program is listed as follows: the
 first argument is the name of the host; the second argument is the server's
 port number; the third argument is either "get", "put", or "remove"; if the
 instruction is "get", the fourth argument is the key; if the instruction is
 "put", the fourth argument is the key and the fifth argument is the value; 
 if the instruction is "remove", the fourth argument is the key. For the
 server side, the default port number used is 30123 unless the first argument
 specifies the port number. When it receives an instruction, it will send
 back a feedback to the client.
****************************************************************************/
import java.net.*;

public class MapClient {

	public static void main(String args[]) throws Exception {
		// Check the argument number
		if (args.length < 4) {
			System.out.println("The number of argument is incorrect");
			return;
		}
		// Get the name of the post
		InetAddress serverAdr = InetAddress.getByName(args[0]);
		int port = Integer.parseInt(args[1]);
		// Create a UDP datagram socket
		DatagramSocket sock = new DatagramSocket();
		// Build the instruction string
		String outString = null;
		// get or remove
		if(args.length == 4) {
			outString = args[2] + ":" + args[3];
		} else if(args.length == 5) { // put
			outString = args[2] + ":" + args[3] + ":" + args[4];
		}
		// Build the output packet
		byte[] outBuf = outString.getBytes();  
		DatagramPacket outPkt = new DatagramPacket(outBuf,outBuf.length,
												serverAdr,port);
		sock.send(outPkt); // Send packet to server
		// Prepare for the input packet
		byte[] inBuf = new byte[1000];
		DatagramPacket inPkt = new DatagramPacket(inBuf,inBuf.length);
		sock.receive(inPkt); // Wait for reply
		// Print the data in the input packet
		String reply = new String(inBuf, 0, inPkt.getLength(), "US-ASCII");
		System.out.println(reply);
		sock.close(); // Close the socket
	}
}
