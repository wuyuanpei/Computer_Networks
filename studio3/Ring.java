/** Node in a simple ring network.
 *  
 *  usage: Ring ringId myIp [ debug ] [ predIp  predPort ]
 *  
 *  ringId	is a ring identifier for this node (each node has unique id)
 *  myIp	is the IP address to use for this server's socket
 *  debug	is an optional argument; if present it is the literal string
 *		"debug"; when debug is present, a copy of every packet
 *		received and sent is printed on System.out.
 *  predIp	is an optional argument specifying the IP address of the
 *		predecessor node for this node in the ring; is required for
 *		all but the first node to start
 *  predPort	is an optional argument specifying the port number of the
 *		predecessor node for this node in the ring;
 *		is required for all but the first node
 *  
 *  All ring packets are UDP packets containing ASCII text. For example,
 *  to join the ring, a prospective ring node sends a join packet to some
 *  node already in the ring. The format of the packet is:
 *  
 *  type:join
 *  
 *  An example of a reply packet is:
 *  
 *  type:accept
 *  succIp:123.45.67.89
 *  succPort:54321
 *  
 *  The succIp and succPort fields specify the IP address and port number of
 *  the new node's successor in the ring.
 *  
 *  Once a node is part of the ring, it periodically sends a "ring test" packet,
 *  which takes the form
 *  
 *  type:ring test
 *  sender:5
 *  
 *  Here, sender is the ringId of the sending node. Anytime a node receives
 *  a ring test packet, it checks to see if it was the sender of the packet.
 *  If so, it removes the packet from the ring. If not, it forwards the
 *  packet to its successor.
 *  
 *  Each node should send a new test packet every ten seconds. If, when
 *  it's ready to send a test packet, it has not received the previous
 *  packet that it sent, then it prints a message saying
 *  
 *  ring node X detected break in ring
 *  
 *  where X is the node's ring id.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Ring {
	private static int ringId;

	private static InetAddress myIp;
	private static DatagramSocket sock;
	private static InetSocketAddress predAdr;
	private static InetSocketAddress succAdr;

	private static boolean gotRingReply;
	private static long ringTestTime;

	private static boolean debug;

	/** Main method for Ring node.
	 *  Processes command line arguments, initializes data, joins ring,
	 *  then starts running ring tests.
	 */
	public static void main(String[] args) {
		// process command-line arguments
		if (args.length < 2) {
			System.err.println("usage: Ring ringId myIp " +
					   "[ debug ] [ predIp  predPort ]");
			System.exit(1);
		}
		ringId = Integer.parseInt(args[0]);
		try {	
			myIp = InetAddress.getByName(args[1]);
		} catch(Exception e) {
			System.err.println("usage: Ring ringId myIp " +
					   "[ debug ] [ predIp  predPort ]");
			System.exit(1);
		}

		debug = false; int nextArg = 2;
		if (args.length > nextArg && args[nextArg].equals("debug")) {
			debug = true; nextArg++;
		}
		predAdr = null;
		if (args.length > nextArg) {
			if (args.length != nextArg+2) {
				System.err.println("usage: Ring ringId myIp " +
					   "[ debug ] [ predIp  predPort ]");
				System.exit(1);
			}
			predAdr = new InetSocketAddress(args[nextArg],
					Integer.parseInt(args[nextArg+1]));
		}

		// open socket and bind to specified IP address
		sock = null;
		int port = 0;
		try {
			sock = new DatagramSocket(0,myIp);
			port = sock.getLocalPort();
			sock.setSoTimeout(1000); // 1 second timeout
		} catch(Exception e) {
			System.out.println("unable to create socket");
			System.exit(1);
		}
		System.out.println("using port " + port);
		succAdr = new InetSocketAddress(myIp,port);
		if (predAdr != null) join(predAdr);

		gotRingReply = false;
		Packet testPkt = new Packet();
		testPkt.type = "ring test";
		testPkt.sender = ringId;
		testPkt.send(sock,succAdr,debug);
		ringTestTime = System.nanoTime();
		long tenSec = 10000000000L;

		Pair<Packet,InetSocketAddress> pp;
		Packet p = new Packet();
		InetSocketAddress senderAdr;
		while (true) {
			senderAdr = p.receive(sock,debug);
			if (senderAdr != null) handlePacket(p,senderAdr);
			long now = System.nanoTime();
			// TODO - detect ring break
			// It's time to resend the testPkt
			if(now >= tenSec + ringTestTime) {
				ringTestTime = System.nanoTime();
				// If not received
				if(!gotRingReply) {
					System.out.println("ring node "+ ringId +" detected break in ring");
					System.exit(2);
				}else {
					gotRingReply = false;
					testPkt.send(sock,succAdr,debug);
				}
			}
		}
	}

	/** Join the ring.
	 *  @param predAdr is the socket address of some ring node
	 *  that will become the predecessor of this node.
	 *  The method sends a join packet, then waits for a reply,
	 *  that is expected to include the succesor's IP and port.
	 */
	public static void join(InetSocketAddress predAdr) {
		// TODO
		Packet joinPkt = new Packet();
		joinPkt.type = "join";
		joinPkt.send(sock,predAdr,debug);
		Packet replyPkt = new Packet();
		replyPkt.receive(sock,debug);
		succAdr = new InetSocketAddress(replyPkt.succIp,replyPkt.succPort);
	}
	
	/** Handle a join packet from a ring node.
	 *  @param p is the received join packet
	 *  @param joinerAdr is the socket address of the host that
	 *  sent the join packet (the new successor)
	 */
	public static void handleJoin(Packet p, InetSocketAddress joinerAdr) {
		// TODO
		Packet replyPkt = new Packet();
		replyPkt.type = "accept";
		replyPkt.succIp = succAdr.getAddress();
		replyPkt.succPort = succAdr.getPort();
		replyPkt.send(sock,joinerAdr,debug);
		succAdr = joinerAdr;
	}
	
	/** Handle a ring test packet.
	 *  If this is a packet that we sent, set gotRingReply.
	 *  Otherwise, just pass it on.
	 */
	public static void handleRingTest(Packet p) {
		// TODO
		if(p.sender == ringId) {
			gotRingReply = true;
		} else {
			p.send(sock,succAdr,debug);
		}
	}

	/** Handle packets received from another ring node.
	 *  @param p is a packet
	 *  @param senderAdr is the address (ip:port) of the sender
	 */
	public static void handlePacket(Packet p, InetSocketAddress senderAdr) {
		if (p.type.equals("join")) {
			handleJoin(p,senderAdr);
		} else if (p.type.equals("ring test")) {
			handleRingTest(p);
		}
	}
}
