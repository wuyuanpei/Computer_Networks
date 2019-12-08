/** Source/Sink class
 *  This class generates packet payloads of random lengths,
 *  and receives payloads sent by a peer host.

 *  When a new payload is generated, it is passed to a Monitor object,
 *  using its send method. Similarly, packets are received from the
 *  Monitor object using its receive method.
 *  
 *  The thread is started using the start method (which calls the
 *  run method in a new thread of control). It can be stopped
 *  using the stop method; this causes the run method to terminate
 *  its main loop and print a short status report, then return.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SrcSnk implements Runnable {
	private Thread myThread;	// thread that executes run() method

	private long delta;		// time between packets in ns
	private long runLength;		// amount of time to run in ns
	private Monitor mon;		// reference to monitor module

	private int inCount;		// count of received packets
	private int outCount;		// count of sent packets
	private boolean quit;		// stop thread when true

	/** Initialize a new SrcSnk object
	 *  @param delta is a float, representing the amount of time to wait
	 *  between sending packets (in seconds)
	 *  @param runLength is a float, representing the the length of the time
	 *  interval during which packets should be sent
	 *  @param mon is a reference to a Monitor object
	 */
	SrcSnk(double delta, double runLength, Monitor mon) {
		this.delta = (long) (delta * 1000000000); // convert to ns
		this.runLength = (long) (runLength * 1000000000);
		this.mon = mon; this.quit = false;
	}

	/** Instantiate and start a thread to execute run(). */
	public void start() {
		myThread = new Thread(this); myThread.start();
	}

	/** Stop SrcSnk. */
	public void stop() throws Exception { quit = true; myThread.join(); }

	/** Run the SrcSnk thread.
	 *  This method executes a loop that generates new outgoing
	 *  payloads and receives incoming payloads. It sends packets
	 *  for a specified period of time and terminates after the stop
	 *  method is called.
	 */
	public void run() {
		String payloadString = "abracadbra feefifofum " +
					"supercalifragilisticexpialidocious";
		int psLen = payloadString.length();

		long t0 = System.nanoTime();
		long now = 0;
		long next = 1000000000;
		long stopTime = next + runLength;

		int sleeptime; // time to sleep when nothing to do
		if (delta > 0 && delta < 1000000) sleeptime = (int) delta;
		else sleeptime = 999999;

		String msg;
		while (!quit) {
			now = System.nanoTime() - t0;
			if (mon.incoming()) {
				msg = mon.receive();
			} else if (now > next && now < stopTime &&
			     	   mon.ready() && delta > 0) {
				// send an outgoing payload
				int i = (int) (Math.random() * (psLen-10));
				int j = i + (int) (Math.random() * (psLen-i));
				msg = payloadString.substring(i,j);
				mon.send(msg);
				next += delta;
			} else {
				try {
					Thread.sleep(0L,sleeptime);
				} catch(Exception e) {
					System.err.println("SrcSnk:run: "
						+ "sleep exception " + e);
					System.exit(1);
				}
			}
		}
	}
}

