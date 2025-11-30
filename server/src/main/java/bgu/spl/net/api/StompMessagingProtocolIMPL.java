package bgu.spl.net.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsIMPL;

public class StompMessagingProtocolIMPL implements StompMessagingProtocol<String> {
    int connectionId;
    ConnectionsIMPL connections;



    public void start(int connectionId, ConnectionsIMPL connections){
        this.connectionId= connectionId;
        this.connections=connections;

    }
    
    public String process(String message) {   
        String[] frameLines = message.split("\n");
        String command = frameLines[0].trim();  
        
        if(command.equals("CONNECT")) {
            Map<String, String> headers = new HashMap<>();
            String receiptID = null;
            String userName = null;
            String passcode = null;
            
            for (int i = 1; i < frameLines.length; i++) {
                String line = frameLines[i].trim();
                if (line.isEmpty()) continue;
                
                String[] headerParts = line.split(":", 2);
                if (headerParts.length == 2) {
                    String key = headerParts[0].trim();
                    String value = headerParts[1].trim();
                    headers.put(key, value);
                    
                    if (key.equals("login")) {
                        userName = value;
                    } else if (key.equals("passcode")) {
                        passcode = value;
                    } else if (key.equals("receipt-id")) {
                        receiptID = value;
                    }
                }
            }
            

            // Client already has a user logged in
            if (connections.getUserPerClient().containsKey(connectionId)) {
                String res = createErrorFrame(message, receiptID, command, "User already logged in");
                return res;
            }
            // Check if user is already logged in somewhere else
            else {
                boolean userLoggedInElsewhere = false;

                
                // Check if username exists in current users map
                userLoggedInElsewhere = connections.getCurrentUsers().containsKey(userName);
                if (userLoggedInElsewhere) {
                    String res = createErrorFrame(message, receiptID, command, "User "+userName+" logged somewhere else");
                    return res;
                }
                
                // Check previous users for password validation
                if (connections.getPreviousUsers().containsKey(userName)) {
                    // Wrong password
                    if (!connections.getPreviousUsers().get(userName).equals(passcode)) {
                        String res = createErrorFrame(message, receiptID, command, "Wrong Passcode"); 
                        return res;
                    }
                }
                
                // Login successful - register the user
                connections.addToCurrUsers(userName, passcode);
                connections.addUserPerClient(connectionId, userName);
                
                StringBuilder connectedFrame = new StringBuilder();
                Map<String, String> respHeaders = new HashMap<>();
                respHeaders.put("version", "1.2");
                connectedFrame.append("CONNECTED").append("\n");
                
                
                for (Map.Entry<String, String> entry : respHeaders.entrySet()) {
                    connectedFrame.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
                }
                
                connectedFrame.append("\n"); 
                connectedFrame.append("\0"); 
                return connectedFrame.toString();
            }
        }
        else if (command.equals("SUBSCRIBE")){
            String channel=frameLines[1].split(":")[1];
            connections.addSubscription(connectionId,channel);
            return createReceiptFrame(frameLines[2].split(":")[1]);
        }

        else if (command.equals("SEND")) {
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < frameLines.length && !frameLines[i].trim().isEmpty(); i++) {
                String[] parts = frameLines[i].split(":");
                if (parts.length == 2) {
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }
            
            StringBuilder messageFrame = new StringBuilder();
            messageFrame.append("MESSAGE\n");
            
            for (Map.Entry<String, String> header : headers.entrySet()) {
                messageFrame.append(header.getKey()).append(":").append(header.getValue()).append("\n");
            }
            
            messageFrame.append("\n\u0000");
            
            String channel = headers.get("destination").substring(1);
            connections.send(channel, messageFrame.toString());
            return null;
        }
        else if (command.equals("DISCONNECT")){
            String receiptID=frameLines[1].split(":")[1];
            connections.disconnect(connectionId);
            return createReceiptFrame(receiptID);
        }
        else if (command.equals("BADCONNECTION")){
            String receiptID=frameLines[1].split(":")[1];
            String errorFrame = createErrorFrame(message, receiptID, "CONNECT", "User already logged in");
            connections.disconnect(connectionId);
            return errorFrame;
        }
        else if (command.equals("UNSUBSCRIBE")){
            String receiptID=frameLines[2].split(":")[1];
            String channel=frameLines[1].split(":")[1];
            connections.removeSubscription(connectionId,channel);
            return createReceiptFrame(receiptID);
        }
        else{
            return createErrorFrame(message,"0",command,"The command is not valid");
        }
    }
	
	/**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate(){
        return false;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public ConnectionsIMPL getConnections() {
        return connections;
    }


    private String createReceiptFrame(String receiptId) {
        StringBuilder frame = new StringBuilder();
        frame.append("RECEIPT\n");
        frame.append("receipt-id:").append(receiptId).append("\n");
        frame.append("\n");
        frame.append("\0");
        return frame.toString();
    }
    private String createErrorFrame(String message, String receiptID, String command, String errorCause) {
        StringBuilder frame = new StringBuilder();
        Map<String, String> headers = new HashMap<>();
        headers.put("receipt-id", receiptID);
        headers.put("message", command + " frame received");
    
        String body = "The message:\n----\n" + message+"\n----\n" + "\nError: " + errorCause;
        
        frame.append("ERROR");
        frame.append("\n");
    
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            frame.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
    
        frame.append("\n");
    
        frame.append(body);
    
        frame.append("\0");
    
        return frame.toString();
    }
    
    

    
}
