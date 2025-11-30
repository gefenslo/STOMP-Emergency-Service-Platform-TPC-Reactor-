package bgu.spl.net.impl.stomp;

import java.util.Arrays;
import java.util.function.Supplier;

import bgu.spl.net.api.MessageEncoderDecoderImpl;
import bgu.spl.net.api.StompMessagingProtocolIMPL;
import bgu.spl.net.srv.Server;

public class StompServer {
    public static void main(String[] args) {
        if (args.length < 2) {
            return;
        }
        try {
            int port = Integer.parseInt(args[0]);
            String serverType = args[1];

            if (serverType.equalsIgnoreCase("reactor")) {
                Server.reactor(
                    Runtime.getRuntime().availableProcessors(), 
                    port,
                    StompMessagingProtocolIMPL::new, 
                    MessageEncoderDecoderImpl::new 
                ).serve();
            } else if (serverType.equalsIgnoreCase("tpc")) {
                Server.threadPerClient(
                    port,
                    StompMessagingProtocolIMPL::new,
                    MessageEncoderDecoderImpl::new           
                ).serve();
            } else {
                System.out.println("Unknown server type: " + serverType);
                System.out.println("Valid types are: reactor, thread-per-client");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
