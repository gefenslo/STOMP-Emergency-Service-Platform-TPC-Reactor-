package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessageEncoderDecoderImpl;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocolIMPL;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    private final Supplier<StompMessagingProtocolIMPL> protocolFactory;
    private final Supplier<MessageEncoderDecoderImpl> encdecFactory;
    private ServerSocket sock;
    private ConnectionsIMPL connections=new ConnectionsIMPL();


    public BaseServer(
            int port,
            Supplier<StompMessagingProtocolIMPL> protocolFactory,
            Supplier<MessageEncoderDecoderImpl> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
    }

    @Override
    public void serve() {

        try {
            this.sock = new ServerSocket(port);
            System.out.println("Server started");
        
            while (!Thread.currentThread().isInterrupted()) {
    Socket clientSock = sock.accept();
    System.out.println("Accepted new client: " + clientSock);
    
    // Create and initialize protocol first
    StompMessagingProtocolIMPL protocol = protocolFactory.get();
    MessageEncoderDecoderImpl encdec = encdecFactory.get();
    
    // Create handler with initialized protocol
    BlockingConnectionHandler<String> handler = new BlockingConnectionHandler<>(
            clientSock,
            encdec,
            protocol);
    
    // Add to connections and start protocol atomically
    synchronized(connections) {
        int connectionID = connections.addClientToConnections(handler);
        protocol.start(connectionID, connections);
        execute(handler);
    }
}
        } catch (IOException ex) {
            ex.printStackTrace(); // Print exception for debugging
        } finally {
            try {
                if (sock != null) {
                    sock.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
            }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<String>  handler);

}
