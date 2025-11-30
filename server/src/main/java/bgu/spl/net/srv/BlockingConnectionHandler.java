package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessageEncoderDecoderImpl;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocolIMPL;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final StompMessagingProtocolIMPL protocol;
    private final MessageEncoderDecoderImpl encdec;
    private final Socket sock;
    private static final int BUFFER_ALLOCATION_SIZE = 1 << 13; //8k
    private static final ConcurrentLinkedQueue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoderImpl reader, StompMessagingProtocolIMPL protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            System.out.println("Handler started for: " + sock);
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            
            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                String nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    System.out.println("Received message: " + nextMessage);
                    String response = protocol.process(nextMessage);
                    if (response != null) {
                        out.write(encdec.encode(response));
                        out.flush();
                    }
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Handler error: " + ex.getMessage());
        }/*
        finally {
            try {
                System.out.println("Closing client socket: " + sock);
                sock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/
    }
    

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }
    @Override
    public void send(String msg) {
        System.out.println("Sending message: " + msg);
        byte[] encodedMsg = encdec.encode(msg);
        try {
            sock.getOutputStream().write(encodedMsg);
            System.out.println("Message sent successfully, bytes written: " + encodedMsg.length);
        } catch (IOException ex) {
            System.err.println("Failed to send message: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    public StompMessagingProtocolIMPL getProtocol(){
        return protocol;
    }
}
