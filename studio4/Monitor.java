/** Monitor class.
 *  This class implements a thread object that Monitors an upper level
 *  application as it sends and receives packet. It counts the number
 *  of packets and chars sent, and includes these counts in each packet
 *  sent to a peer. The peer uses these values to detect data loss and
 *  prints a report at the end of the run. Losses are monitored in both
 *  directions
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Monitor implements Runnable {
	private Substrate sub;

	private ArrayBlockingQueue<String> fromSrc;
	private ArrayBlockingQueue<String> toSnk;

	private Thread myThread;
	private boolean quit;

	// statistics
	private int pktsSent;
	private int pktsRcvd;
	private int charsSent;
	private int charsRcvd;
	private int pktsReported;
	private int charsReported;

	/** Initialize a new Monitor object.
	 *  @param sub is a reference to the Substrate object that this object
	 *  uses to handle the socket IO
	 */
	Monitor(Substrate sub) {
		this.sub = sub;

		// create queues for application layer interface
		fromSrc = new ArrayBlockingQueue<String>(1000,true);
		toSnk = new ArrayBlockingQueue<String>(1000,true);
		quit = false;
	}

	/** Start the Monitor running. */
	public void start() throws Exception {
		myThread = new Thread(this); myThread.start();
	}

	/** Stop the Monitor.  */
	public void stop() throws Exception { quit = true; myThread.join(); }

	/** Main thread for the Monitor object.
	 *  Inserts payloads received from the application layer into
	 *  packets, and sends them to the substrate. The packets include
	 *  the number of packets and chars sent so far (including the
	 *  current packet).
	 */
	public void run() {
		pktsSent = charsSent = 0;
		pktsRcvd = charsRcvd = 0;
		pktsReported = charsReported = 0;
		
		while (!quit) {
			if (sub.incoming()) {
				// substrate has an incoming packet
				// TODO - process it
				//Get information out of the packet
				Packet p = sub.receive();
				String msg = p.payload;
				pktsRcvd += 1;
				charsRcvd += msg.length();
				
				toSnk.offer(msg);
				
			} else if (fromSrc.size() > 0 && sub.ready()) {
				// there is a message to send to substrate
				// TODO - form next packet and send to substrate
				//Extract the message from the queue
				String msg = fromSrc.poll();
				pktsSent += 1;
				charsSent += msg.length();
				
				Packet p = new Packet();
				p.charCount = charsSent;
				p.pktCount = pktsSent;
				p.payload = msg;
				
				sub.send(p);
			} else {
				// TODO - sleep for a millisecond
				try {
					Thread.sleep(1);
				}catch(Exception e) {}
			}
			
		}
		// TODO - print a report
		System.out.println("MONITOR REPORT");
		System.out.println("pktsSent = " + pktsSent);
		System.out.println("pktsRcvd = " + pktsRcvd);
		System.out.println("charsSent = " + charsSent);
		System.out.println("charsRcvd = " + charsRcvd);
	}

	/** Send a message to peer.
	 *  @param message is a string to be sent to the peer
	 */
	public void send(String message) {
		try {
			fromSrc.put(message);
		} catch(Exception e) {
			System.err.println("Monitor:send: put exception" + e);
			System.exit(1);
		}
	}
		
	/** Test if Monitor is ready to send a message.
	 *  @return true if Monitor is ready
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
			System.err.println("Monitor:send: put exception" + e);
			System.exit(1);
		}
		return s;
	}
	
	/** Test for the presence of an incoming message.
	 *  @return true if there is an incoming message
	 */
	public boolean incoming() { return toSnk.size() > 0; }
}
