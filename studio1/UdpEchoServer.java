import java.io.*;
import java.net.*;
import java.util.Arrays;

public class UdpEchoServer {
	public static void main(String args[]) throws Exception {

		InetAddress myIp = null; int port = 37777;
		if (args.length > 0) myIp = InetAddress.getByName(args[0]);
		if (args.length > 1) port = Integer.parseInt(args[1]);

		DatagramSocket sock = new DatagramSocket(port,myIp);

		// create the packet
		byte[] buf = new byte[1000];
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);

		while (true) {
			// wait for incoming packet
			sock.receive(pkt);
			
			// record the meaningful length
			int length = pkt.getLength();
			
			// turn the data into uppercase
			String received = new String(buf,"UTF-16");
			received = received.toUpperCase();
			buf = received.getBytes("UTF-16");
			
			// reset the data and length
			pkt.setData(buf);
			pkt.setLength(length);
			
			// and send it back
			sock.send(pkt);
			
			// restore the original length
			pkt.setLength(buf.length);
		}
	}
}
