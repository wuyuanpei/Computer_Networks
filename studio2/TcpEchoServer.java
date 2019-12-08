/** TCP echo server - echo lines from a remote client
 *  Jon Turner - 7/2013
 * 
 *  usage: TcpEchoServer [ localIp [ localPort ] ]
 * 
 *  The localIp argument specifies the IP address to bind the
 *  server socket to. If it is omitted, the wildcard address is used.
 *  The localPort argument specifies the port number to bind the
 *  server socket to. If it is omitted, it defaults to 6789.
 * 
 *  If localPort is specified, localIp must also be specified.
 * 
 *  The connection to a remote client remains open until the remote
 *  client closes it.
 */
import java.io.*;
import java.net.*;

public class TcpEchoServer {
	public static void main(String args[]) throws Exception {
		// process arguments
		int port = 6789;
		if (args.length > 1) port = Integer.parseInt(args[1]);
		InetAddress bindAdr = null;
		if (args.length > 0) bindAdr = InetAddress.getByName(args[0]);

		// create and bind listening socket
		ServerSocket listenSock = new ServerSocket(port,0,bindAdr);

		byte[] buf = new byte[1000];

		while (true) {
			// wait for incoming connection request and
			// create new socket to handle it
			Socket connSock = listenSock.accept();
	
			// create buffered versions of socket's in/out streams
			BufferedInputStream   in = new BufferedInputStream(
						   connSock.getInputStream());
			BufferedOutputStream out = new BufferedOutputStream(
						   connSock.getOutputStream());

			while (true) {
				int nbytes = in.read(buf, 0, buf.length);
				if (nbytes < 0) break;
				out.write(buf,0,nbytes); out.flush();
			}
			connSock.close();
		}
	}
}
