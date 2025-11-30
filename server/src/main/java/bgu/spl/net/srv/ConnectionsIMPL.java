package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedList;

public class ConnectionsIMPL implements Connections <String> {

ConcurrentHashMap<Integer,ConnectionHandler> clientsMap;
Integer counter;
ConcurrentHashMap<String,LinkedList<Integer>> subscriptions;
ConcurrentHashMap<Integer,LinkedList<String>> subscriptionsPerClient;
ConcurrentHashMap<Integer,String> userPerClient;
ConcurrentHashMap<String,String> previousUsers;
ConcurrentHashMap<String,String> currentUsers;




public ConnectionsIMPL(){
    clientsMap=new ConcurrentHashMap<Integer,ConnectionHandler>();
    counter=0;
    subscriptions=new ConcurrentHashMap<String,LinkedList<Integer>>();
    subscriptionsPerClient=new ConcurrentHashMap<Integer,LinkedList<String>>() ;
    userPerClient= new ConcurrentHashMap<Integer,String>();
    previousUsers= new ConcurrentHashMap<String,String>();
    currentUsers= new ConcurrentHashMap<String,String>();
}


public boolean send(int connectionId, String msg){
    ConnectionHandler<String> handler=(ConnectionHandler<String>)clientsMap.get(connectionId);
    if(handler==null){
        return false;
    }
    else{
    handler.send(msg);
    return true;
    }
}

public void send(String channel, String msg){
    if(channel!=null){
        if(subscriptions.get(channel)!=null){
            synchronized(subscriptions.get(channel)){
                LinkedList <Integer>list= subscriptions.get(channel);
                for(int i=0;i<list.size();i++){
                    send((int)(list.get(i)),msg);
                }
            }

        }
    }
    }

public void disconnect(int connectionId){
    String username=userPerClient.get(connectionId);
    userPerClient.remove(connectionId);
    String passcode=currentUsers.get(username);
    previousUsers.put(username,passcode);
    currentUsers.remove(username);
    LinkedList<String> list=subscriptionsPerClient.get(connectionId);
    if(list!=null){
    for(int i=0; i<list.size();i++){
        String topic=list.get(i);
        synchronized(subscriptions.get(topic)){
            subscriptions.get(topic).remove(connectionId);
        }
    }
}
    }

public void addUserPerClient(int connectionId,String username){
    userPerClient.put(connectionId, username);
}

public Integer addClientToConnections(ConnectionHandler<String> handler){
    synchronized(counter){
        clientsMap.put(counter,handler);
        Integer connectionID = counter;
        counter++;
        return connectionID ;
    }
}
public void addSubscription(int connectionId, String topic){
    if(topic!=null){
        if(subscriptions.get(topic)==null){
            subscriptions.put(topic,new LinkedList<>());
        }
        synchronized(subscriptions.get(topic)){
        subscriptions.get(topic).add(connectionId);
        }
        if(subscriptionsPerClient.get(connectionId)==null){
            subscriptionsPerClient.put(connectionId,new LinkedList<>());
        }
        subscriptionsPerClient.get(connectionId).add(topic);
    }
}

public void removeSubscription(int connectionId, String topic) {
    if (topic != null && subscriptions.containsKey(topic)) {
        synchronized (subscriptions.get(topic)) {
            subscriptions.get(topic).remove((Integer) connectionId);
            if (subscriptions.get(topic).isEmpty()) {
                subscriptions.remove(topic);
            }
        }
    }

    if (subscriptionsPerClient.containsKey(connectionId)) {
        subscriptionsPerClient.get(connectionId).remove(topic);
        if (subscriptionsPerClient.get(connectionId).isEmpty()) {
            subscriptionsPerClient.remove(connectionId);
        }
    }
}

public void addToCurrUsers(String username, String passcode){
    currentUsers.put(username, passcode);
}

public ConcurrentHashMap<Integer, String> getUserPerClient() {
    return userPerClient;
}

public ConcurrentHashMap<String, String> getPreviousUsers() {
    return previousUsers;
}
public ConcurrentHashMap<Integer, ConnectionHandler> getClientsMap() {
    return clientsMap;
}



public ConcurrentHashMap<String, String> getCurrentUsers() {
    return currentUsers;
}

}
