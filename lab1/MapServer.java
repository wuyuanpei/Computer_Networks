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
import java.util.*;

public class MapServer {
	
	public static void main(String args[]) throws Exception {
		// Default port number
		int port = 30123;
		// Set an optional port number
		if (args.length > 0) port = Integer.parseInt(args[0]);
		// Create the UDP socket
		DatagramSocket sock = new DatagramSocket(port,null);
		// Create the receiver packet
		byte[] receiverBuf = new byte[1000];
		DatagramPacket receiverPkt = new DatagramPacket(receiverBuf,
														receiverBuf.length);
		// Create HashMap to store the data
		HashMap<String, String> map = new HashMap<>();
		// Start receiving data
		while (true) {
			// Wait for incoming packet
			sock.receive(receiverPkt);
			// Turn the data into string
			String dataString = new String(receiverPkt.getData(), 0, 
										receiverPkt.getLength(), "US-ASCII");
			// Manipulate the data
			String[] dataStrings = dataString.split(":");
			// Return string
			String returnString;
			// get instruction
			if("get".equals(dataStrings[0]) && 
					dataStrings.length == 2) {
				String value = map.get(dataStrings[1]);
				if(value == null) {
					returnString = "no match";
				} else {
					returnString = "ok:"+value;
				}
			}
			// put instruction
			else if("put".equals(dataStrings[0]) && 
					dataStrings.length == 3) {
				String previous = map.put(dataStrings[1], dataStrings[2]);
				if(previous == null) {
					returnString = "Ok";
				} else {
					returnString = "updated:"+dataStrings[1];
				}
			}
			// remove instruction
			else if("remove".equals(dataStrings[0]) &&
					dataStrings.length == 2) {
				String removed = map.remove(dataStrings[1]);
				if(removed == null) {
					returnString = "no match";
				} else {
					returnString = "Ok";
				}
			} else { // invalid instruction
				returnString = "Error:unrecognizable\ninput:"+dataString;
			}
			// Create the sender packet
			DatagramPacket senderPkt = new DatagramPacket(
											returnString.getBytes(),
											returnString.length(), 
											receiverPkt.getAddress(),
											receiverPkt.getPort());
			
			// Send the packet
			sock.send(senderPkt);
		}
	}
}
