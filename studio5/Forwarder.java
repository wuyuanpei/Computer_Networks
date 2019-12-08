import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Forwarder for an overlay IP router.
 *
 *  This class implements a basic packet forwarder for a simplified
 *  overlay IP router. It runs as a separate thread.
 *
 *  An application layer thread provides new packet payloads to be
 *  sent using the provided send() method, and retrieves newly arrived
 *  payloads with the receive() method. Each application layer payload
 *  is sent as a separate packet, where each packet includes a protocol
 *  field, a ttl, a source address and a destination address.
 */
public class Forwarder implements Runnable {
	private int myIp;	// this node's ip address in overlay
	private int debug;	// controls amount of debugging output
	private Substrate sub;	// Substrate object for packet IO
	private double now;	// current time in ns
	private final double sec = 1000000000; // # of ns in a second

	// queues for communicating with SrcSnk
	private ArrayBlockingQueue<Packet> fromSrc;
	private ArrayBlockingQueue<Packet> toSnk;

	private Thread myThread;
	private boolean quit;

	/** Initialize a new Forwarder object.
	 *  @param myIp is this node's IP address in the overlay network,
	 *  expressed as a raw integer.
	 *  @param sub is a reference to the Substrate object that this object
	 *  uses to handle the socket IO
	 *  @param debug controls the amount of debugging output
	 */
	Forwarder(int myIp, Substrate sub, int debug) {
		this.myIp = myIp; this.sub = sub; this.debug = debug;

		// create queues for SrcSnk
		fromSrc = new ArrayBlockingQueue<Packet>(1000,true);
		toSnk = new ArrayBlockingQueue<Packet>(1000,true);

		quit = false;
	}

	/** Start the Forwarder running. */
	public void start() throws Exception {
		myThread = new Thread(this); myThread.start();
	}

	/** Terminate the Forwarder.  */
	public void stop() throws Exception { quit = true; myThread.join(); }

	/** This is the main thread for the Forwarder object.
	 *
	 *  It inserts payloads received from the application layer into
	 *  packets, which it sends to the substrate, using a random
	 *  link number in the set {0,1,2}.
	 *
	 *  In addition, it receives packets from the Substrate and
	 *  forwards them either to the application layer, or forwards
	 *  them on a random outgoing link.
	 */
	public void run() {
		now = 0; double t0 = System.nanoTime()/sec;
		Packet p = null;

		while (!quit) {
			now = System.nanoTime()/sec - t0;
			// TODO
			// if the substrate has an incoming packet,
			//	if it's for this node, pass payload up to SrcSnk
			// 	else 
			//		decrement ttl, if ttl==0, discard and
			//		print "expired ttl" if debug>0
			//		if ttl>0, forward packet to a random
			// 		link, but not the one on which it
			//		arrived
			// else if there is a new SrcSnk payload to send,
			//	put it in a packet and send it to a random link
			if(sub.incoming()) {
				Pair<Packet,Integer> pair = sub.receive();
				p = pair.left;
				int link = pair.right.intValue();
				if(p.destAdr == myIp) {
					try {
						toSnk.put(p);
					} catch(Exception e) {
						System.err.println("Forwarder:run: put exception" + e);
						System.exit(1);
					}
				}
				else {
					p.ttl--;
					if(p.ttl == 0) {
						if (debug > 0)
							System.out.println("expired ttl");
					}
					else {
						int rlink;
						do {
							rlink = new Random().nextInt(3);
						}while(rlink == link);
						sub.send(p,rlink);
					}
				}
			}
			else if(fromSrc.size() > 0) {
				try {
					p = fromSrc.take();
				} catch(Exception e) {
					System.err.println("Forwarder:run: take exception" +e);
					System.exit(1);
				}
				int rlink = new Random().nextInt(3);
				sub.send(p,rlink);
			}
		}
	}

	/** Send a message to another overlay host.
	 *  @param message is a string to be sent to the peer
	 */
	public void send(String payload, String destAdr) {
		Packet p = new Packet();
		p.srcAdr = myIp; p.destAdr = Util.string2ip(destAdr);
		p.ttl = 15; p.payload = payload;
		try {
			fromSrc.put(p);
		} catch(Exception e) {
			System.err.println("Forwarder:send: put exception" + e);
			System.exit(1);
		}
	}
		
	/** Test if Forwarder is ready to send a message.
	 *  @return true if Forwarder is ready
	 */
	public boolean ready() { return fromSrc.remainingCapacity() > 0; }

	/** Get an incoming message.
	 *  @return next message
	 */
	public Pair<String,String> receive() {
		Packet p = null;
		try {
			p = toSnk.take();
		} catch(Exception e) {
			System.err.println("Forwarder:send: take exception" +e);
			System.exit(1);
		}
		return new Pair<String,String>(
				p.payload,Util.ip2string(p.srcAdr));
	}
	
	/** Test for the presence of an incoming message.
	 *  @return true if there is an incoming message
	 */
	public boolean incoming() { return toSnk.size() > 0; }
}
