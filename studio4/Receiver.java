/** Datagram receiver.
 *
 *  This class provides a receive interface to a datagram socket.
 *  Specifically, it enables one to test for the presence of an
 *  incoming packet before attempting a (potentially blocking)
 *  receive operation.
 */  

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Receiver implements Runnable {
	private Thread myThread;	// thread that executes run() method
	private Sender sndr;

	private DatagramSocket sock;
	private ArrayBlockingQueue<Packet> rcvq;
	private InetSocketAddress peerAdr;
	private boolean debug;

	Receiver(DatagramSocket sock, InetSocketAddress peerAdr,
		 Sender sndr, boolean debug) {
		this.sock = sock; this.peerAdr = peerAdr;
		this.sndr = sndr; this.debug = debug;

		// initialize queue for received packets
		// stores both the packet and socket address of the sender
		rcvq = new ArrayBlockingQueue<Packet>(1000,true);
	}

	/** Instantiate run() thread and start it running. */
	public void start() {
		myThread = new Thread(this); myThread.start();
	}

	/** Wait for thread to quit. */
	public void join() throws Exception { myThread.join(); }

	/** Receive thread places incoming packet in a queue.
	 *  This method is run by a separate thread. It simply receives
	 *  packets from the datagram socket and places them in a queue.
	 *  If the queue is full when a packet arrives, it is discarded.
	 */
	public void run() {
		Packet p;
		byte[] buf = new byte[2000];
                DatagramPacket dg = new DatagramPacket(buf, buf.length);
		long now = System.nanoTime(); long eventTime = 0;

		// run until nothing has happened for 5 seconds
		while (eventTime == 0 || now < eventTime + 5000000000L) {
			now = System.nanoTime();
	                try {
	                        sock.receive(dg);
	                } catch(SocketTimeoutException e) {
	                        continue; // check for termination, then retry
	                } catch(Exception e) {
	                        System.err.println("Receiver: receive "
						    + "exception: " + e);
	                        System.exit(1);
			}
			eventTime = now;
			// set peerAdr if not yet initialized
			// otherwise, that it's the same peer
			if (peerAdr == null) {
				peerAdr = (InetSocketAddress)
						dg.getSocketAddress();
				sndr.setPeerAdr(peerAdr);
			} else if (!dg.getSocketAddress().equals(peerAdr)) {
	                        System.err.println("Receiver: received "
					+ "packet from unexpected sender: "
					+ dg.getSocketAddress());
	                        System.exit(1);
			}
			p = new Packet();
			if (!p.unpack(dg.getData(), dg.getLength())) {
                        	System.err.println("Receiver: error while "
						   + "unpacking packet");
                       		System.exit(1);
                	}
	                if (debug) {
	                        System.out.println(sock.getLocalSocketAddress()
	                                + " received packet from " 
	                                + dg.getSocketAddress() + "\n" + p);
	                        System.out.flush();
	                }
			rcvq.offer(p); // discard if rcvq is full
		}
	}

	/** Receive a packet.
	 *  @return the next packet that has been received on the socket;
	 *  will block if no packets available
	 */
	public Packet receive() {
		Packet p = null;
		try {
			p = rcvq.take();
		} catch(Exception e) {
			System.err.println("Receiver:receive: exception " + e);
			System.exit(1);
		}
		return p;
	}

	/** 
	 *  Check whether there is incoming packet
	 */
	public boolean incoming() { return rcvq.size() > 0; }
}
