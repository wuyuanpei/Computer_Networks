/** Reliable Data Transport class.
 *
 *  This class implements a reliable data transport service.
 *  It uses a go-back-N sliding window protocol on a packet basis.
 *
 *  An application layer thread provides new packet payloads to be
 *  sent using the provided send() method, and retrieves newly arrived
 *  payloads with the receive() method. Each application layer payload
 *  is sent as a separate UDP packet, along with a sequence number and
 *  a type flag that identifies a packet as a data packet or an
 *  acknowledgment. The sequence numbers are 15 bits.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Rdt implements Runnable {
	private int wSize;	// protocol window size
	private long timeout;	// retransmission timeout in ns
	private Substrate sub;	// Substrate object for packet IO

	private ArrayBlockingQueue<String> fromSrc;
	private ArrayBlockingQueue<String> toSnk;

	// Sending structures and necessary information
	private Packet[] sendBuf; // not yet acked packets
	private short sendBase = 0;	// seq# of first packet in send window
	private short sendSeqNum = 0;	// next available seq# in send window
	private short dupAcks = 0; // should only happen for sendBase-1 packet

	// Receiving structures and necessary information
	private Packet[] recvBuf; // undelivered packets
	private short recvBase = 0;  // seq# of oldest undelivered packet (to application)
	private short expSeqNum = 0;	// seq# of packet we expect to receive (from substrate)
	private short lastRcvd = -1; // last packet received properly

	// Time keeping variabels
	private long now = 0;		// current time (relative to t0)
	private long sendAgain = 0;	// time when we send all unacked packets

	private Thread myThread;
	private boolean quit;

	/** Initialize a new Rdt object.
	 *  @param wSize is the window size used by protocol; the sequence #
	 *  space is twice the window size
	 *  @param timeout is the time to wait before retransmitting
	 *  @param sub is a reference to the Substrate object that this object
	 *  uses to handle the socket IO
	 */
	Rdt(int wSize, double timeout, Substrate sub) 
	{
		this.wSize = Math.min(wSize,(1 << 14) - 1);
		this.timeout = ((long) (timeout * 1000000000)); // sec to ns
		this.sub = sub;

		// create queues for application layer interface
		fromSrc = new ArrayBlockingQueue<String>(1000,true);
		toSnk = new ArrayBlockingQueue<String>(1000,true);
		quit = false;

		sendBuf = new Packet[2*wSize];
		recvBuf = new Packet[2*wSize];
	}

	/** Start the Rdt running. */
	public void start() throws Exception {
		myThread = new Thread(this); myThread.start();
	}

	/** Stop the Rdt.  */
	public void stop() throws Exception { quit = true; myThread.join(); }

	/** Increment sequence number, handling wrap-around.
	 *  @param x is a sequence number
	 *  @return next sequence number after x
	 */
	private short incr(short x) {
		x++; return (x < 2*wSize ? x : 0);
	}

	/** Compute the difference between two sequence numbers,
	 *  accounting for "wrap-around"
	 *  @param x is a sequence number
	 *  @param y is another sequence number
	 *  @return difference, assuming x is "clockwise" from y
	 */
	private int diff(short x, short y) {
		return (x >= y ? x-y : (x + 2*wSize) - y);
	}

	/** Main thread for the Rdt object.
	 *
	 *  Inserts payloads received from the application layer into
	 *  packets, and sends them to the substrate. The packets include
	 *  a type and a seq num fields. It also takes packets received 
	 *  from the substrate and sends the extracted payloads
	 *  up to the application layer. To ensure that packets are
	 *  delivered reliably and in-order, using a sliding
	 *  window protocol with the go-back-N feature.
	 */
	public void run() {
		long t0 = System.nanoTime();
		long now = 0;		// current time (relative to t0)
		/* run the loop if quit is false or we still have un-acked packets */
		while (!quit || diff(sendSeqNum, sendBase) != 0 ) {
			now = System.nanoTime() - t0;
			// TODO
			// if receive buffer has a packet that can be
			//    delivered, deliver it to sink
			if(recvBuf[recvBase] != null) {
				// Put the payload of the packet at the tail of toSnk
				try {
					toSnk.put(recvBuf[recvBase].payload);
				} catch(Exception e) {
					System.err.println("Rdt:run toSnk exception " + e);
					System.exit(1);
				}
				recvBuf[recvBase] = null;
				recvBase = incr(recvBase);
			}
			// else if the substrate has an incoming packet
			//      get the packet from the substrate and process it
			else if(sub.incoming()) {
				Packet p = sub.receive();
			// 	if it's a data packet, ack it and add it
			//	   to receive buffer as appropriate
				if(p.type == 0) {
					// If this packet is as expected
					if(p.seqNum == expSeqNum) {
						recvBuf[expSeqNum] = p;
						lastRcvd = expSeqNum;
						expSeqNum = incr(expSeqNum);
					}
					// Send ACK
					Packet ack_p = new Packet();
					ack_p.type = 1;
					ack_p.seqNum = lastRcvd;
					sub.send(ack_p);
					
				}
			//	if it's an ack, update the send buffer and
			//	   related data as appropriate
			//	   reset the timer if necessary
				else {
					short seqNum = p.seqNum;
					// If it's not a duplicated ACK
					if(incr(seqNum) != sendBase) {
						sendBase = incr(seqNum);
						dupAcks = 0;
						// reset timer
						sendAgain = now + timeout;
					}
					else {
						dupAcks += 1;
					}
					// Three duplicated ACKs (plus one correctly received)
					// Retransmission
					if(dupAcks == 3) {
						// reset timer 
						// that's the only way to do retransmission again
						// Only until ACK is received, dupAcks will be reset
						sendAgain = now + timeout;
						for(short i = sendBase; i != sendSeqNum; i = incr(i)) {
							sub.send(sendBuf[i]);
						}
					}
				}
			}
			// else if the resend timer has expired, re-send all
			//      packets in the window and reset the timer
			else if(now >= sendAgain) {
				sendAgain = now + timeout;
				for(short i = sendBase; i != sendSeqNum; i = incr(i)) {
					sub.send(sendBuf[i]);
				}
			}
			// else if there is a message from the source waiting
			//      to be sent and the send window is not full
			//	and the substrate can accept a packet
			//      create a packet containing the message,
			//	and send it, after updating the send buffer
			//	and related data
			else if(fromSrc.size() > 0 && 
					diff(sendSeqNum, sendBase) < wSize &&
					sub.ready()) {
				Packet p = new Packet();
				p.type = 0;
				p.seqNum = sendSeqNum;
				p.payload = fromSrc.poll();
				sub.send(p);
				sendBuf[sendSeqNum] = p;
				sendSeqNum = incr(sendSeqNum);
				// If this is the first packet, set timer
				if(diff(sendSeqNum, sendBase) == 1) {
					sendAgain = now + timeout;
				}
			}
			// else nothing to do, so sleep for 1 ms
			else {
				try {
					Thread.sleep(1);
				}catch(Exception e) {
					System.err.println("Rdt:run: "
							+ "sleep exception " + e);
						System.exit(1);
				}
			}
		}
	}

	/** Send a message to peer.
	 *  @param message is a string to be sent to the peer
	 */
	public void send(String message) {
		try {
			fromSrc.put(message);
		} catch(Exception e) {
			System.err.println("Rdt:send: put exception" + e);
			System.exit(1);
		}
	}
		
	/** Test if Rdt is ready to send a message.
	 *  @return true if Rdt is ready
	 */
	public boolean ready() { return fromSrc.remainingCapacity() > 0; }

	/** Get an incoming message.
	 *  @return next message
	 */
	public String receive() {
		String s = null;
		try {
			s = toSnk.take();
		} catch(Exception e) {
			System.err.println("Rdt:send: take exception" + e);
			System.exit(1);
		}
		return s;
	}
	
	/** Test for the presence of an incoming message.
	 *  @return true if there is an incoming message
	 */
	public boolean incoming() { return toSnk.size() > 0; }
}
