using namespace std;
#include <string>
#include <fstream>;
#include <iostream>;
#include <map>;
#include <queue>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include "StompClient.h"; 
#include <iostream>
#include <thread>
#include <fcntl.h>
#include <cstring>   
#include <sys/socket.h>   
#include <netinet/in.h>  
#include <arpa/inet.h>   
#include <unistd.h>   
#include <string>    
#include "event.h"  
#include <sys/select.h> 
#include <unistd.h>    
#include <iomanip>
#include <ctime> 
using namespace std;

string ip;
string port;
int sock;
int counter=0;
queue<string> userToClientMessageQueue;  
mutex userToClientMutex;                
condition_variable cv;                  
atomic<bool> running(true);             
map <string,int> subscriptions;
int channelIDGenerator=1;
string identity;
queue<string> frames=queue<string>();
bool isConnectet=false;
int lastReciept=-1;

std::string epochToDate(const std::string& epochTimeStr) {
    std::time_t epochTime = std::stoll(epochTimeStr);
    std::tm* timeInfo = std::gmtime(&epochTime);
    if (timeInfo == nullptr) {
        throw std::runtime_error("Error converting epoch time to UTC");
    }
    std::ostringstream oss;
    oss << std::put_time(timeInfo, "%d/%m/%Y %H:%M:%S");
    return oss.str();
}

std::string extractFieldValue(const std::string& input, const std::string& fieldName) {
    
    std::string key = fieldName;

    
    size_t startPos = input.find(key);
    if (startPos == std::string::npos) {
        return "";
    }

    
    startPos += key.length();

    
    size_t endPos = input.find_first_of("\n", startPos);
    if (endPos == std::string::npos) {
        endPos = input.length();
    }

    
    std::string fieldValue = input.substr(startPos, endPos - startPos);

   
    size_t firstNonSpace = fieldValue.find_first_not_of(" \t");
    size_t lastNonSpace = fieldValue.find_last_not_of(" \t");
    if (firstNonSpace != std::string::npos) {
        fieldValue = fieldValue.substr(firstNonSpace, lastNonSpace - firstNonSpace + 1);
    }

    return fieldValue;
}

// Function for I/O thread
void ioThreadFunc(){
    while (running) {
        fd_set readfds;
        FD_ZERO(&readfds);
        FD_SET(STDIN_FILENO, &readfds); 

        struct timeval timeout;
        timeout.tv_sec = 1; 
        timeout.tv_usec = 0;

       
        int activity = select(STDIN_FILENO + 1, &readfds, nullptr, nullptr, &timeout);

        if (activity > 0 && FD_ISSET(STDIN_FILENO, &readfds)) {
            string input;
            getline(cin, input);
            {
            lock_guard<mutex> lock(userToClientMutex);
            userToClientMessageQueue.push(input);
            }
            cv.notify_one();
        }

    }
}

    

string getWordAtIndex(const string& str, int index) {

    stringstream ss(str);
    string word;
    vector<string> words;

    
    while (ss >> word) {
        words.push_back(word);
    }

   
    if (index >= 0 && index < words.size()) {
        return words[index];
    } else {
        return ""; 
    }
}


std::string createStompFrame(const std::string& command, const std::map<std::string, std::string>& headers, const std::string& body ) {
    std::ostringstream frame;

    
    frame << command << "\n";

   
    for (const auto& header : headers) {
        frame << header.first << ":" << header.second << "\n";
    }

   
    frame << "\n";

    
    frame << body;

   
    frame << '\0';

    return frame.str();
}

void communicationFunc() {
    while (running) {
        char buffer[4096];
        fd_set readfds;
        
        while (true && userToClientMessageQueue.empty()) {
            FD_ZERO(&readfds);
            FD_SET(sock, &readfds);
            struct timeval timeout;
            timeout.tv_sec = 0;
            timeout.tv_usec = 20000;

            int activity = select(sock + 1, &readfds, NULL, NULL, &timeout);  
            if (activity > 0 && FD_ISSET(sock, &readfds)) {
                int error = 0;
                socklen_t len = sizeof(error);
                if (getsockopt(sock, SOL_SOCKET, SO_ERROR, &error, &len) == 0 && error != 0) {
                    std::cerr << "Socket error: " << strerror(error) << std::endl;
                    close(sock);
                    return;
                }
                int bytesReceived = recv(sock, buffer, sizeof(buffer) - 1, 0);
                if (bytesReceived > 0) {
                    buffer[bytesReceived] = '\0';
                    cout << buffer << endl;
                    if (bytesReceived > 0 && buffer[0] == 'E' && buffer[1] == 'R') {
                        close(sock); 
                        running = false;
                        return; 
                    } else if (buffer[0] == 'C' && buffer[6] == 'T') {
                        isConnectet = true;
                    } else if (buffer[0] == 'M' && buffer[6] == 'E') {
                        string frame = buffer;
                        frames.push(frame);
                    } else if (buffer[0] == 'R' && buffer[6] == 'T') {
                        string frame = buffer;
                        frame = extractFieldValue(frame, "receipt-id:");
                        if (stoi(frame) == lastReciept) {
                            subscriptions = map<string, int>();
                            channelIDGenerator = 1;
                            counter = 0;
                            frames = queue<string>(); 
                            userToClientMessageQueue = queue<string>(); 
                            close(sock);
                        }              
                    }                
                } else if (bytesReceived == 0) {
                    std::cerr << "recv() failed, errno: " << errno << " (" << strerror(errno) << ")" << std::endl;
                    close(sock);  
                    running = false;
                    return;
                } else {
                    break;
            }
        }
    }
    string messageToSend;

            {
    unique_lock<mutex> lock(userToClientMutex);
    cv.wait(lock, [] { return !userToClientMessageQueue.empty() || !running; });

    if (!running) break;

    messageToSend = userToClientMessageQueue.front();
    userToClientMessageQueue.pop();
    }
    string command = getWordAtIndex(messageToSend,0);
    if (command=="login"){
        if (isConnectet){
        cout<<"already connected"<<endl;
        std::map<std::string, std::string> headerMap ;
        headerMap["receipt"] = to_string(counter);
        string badConnectFrame=createStompFrame("BADCONNECTION",headerMap,"");
        send(sock, badConnectFrame.c_str(), badConnectFrame.length(), 0);  
        }
        else {
      string hostPort = getWordAtIndex(messageToSend,1);
        int  pos = hostPort.find(':');
        string host = hostPort.substr(0, pos);
        int port=std::stoi (hostPort.substr(pos+1,hostPort.length()-1));
        string userName= getWordAtIndex(messageToSend,2);
        identity=userName;
        string passcode = getWordAtIndex(messageToSend,3);
        if(!(hostPort.empty()||host.empty()||userName.empty()||passcode.empty())){   
            std::map<std::string, std::string> headerMap ;
            headerMap["accept-version"] = "1.2";
            headerMap["host"] = "stomp.cs.bgu.ac.il";
            headerMap["login"] = userName;
            headerMap["passcode"]=passcode;
            headerMap["receipt-id"]=std::to_string(counter);
            counter++;
            string connectFrame=createStompFrame("CONNECT",headerMap,"");
            sock = socket(AF_INET, SOCK_STREAM, 0);
            struct sockaddr_in serverAddr;
            memset(&serverAddr, 0, sizeof(serverAddr));  
            serverAddr.sin_family = AF_INET;
            serverAddr.sin_port = htons(port);  
            inet_pton(AF_INET, host.c_str(), &serverAddr.sin_addr);  

            if (!(connect(sock, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == -1)){
                        send(sock, connectFrame.c_str(), connectFrame.length(), 0);  
            }
            else {
            cout<<"Cannot connect to "+host+" please try again"<< endl;
            }
            }
        else {
        cout<<"use this format: {host:port} {userName} {passcode}"<<endl; 

        }
        }
    }
    else if (command== "join") {
        std::map<std::string, std::string> headerMap ;
        string destination = getWordAtIndex(messageToSend,1);
        headerMap["destination"]=destination;
        int id= subscriptions[destination];
        if (id>0){
            cout<< "already subscribed to this channel"<<endl;
            break;
        }
        else{
            string id= to_string(channelIDGenerator);
            headerMap["id"]=id;
            subscriptions[destination]=channelIDGenerator;
            channelIDGenerator++;
            headerMap["receipt"]= to_string(counter);
            counter++;
            string connectFrame=createStompFrame("SUBSCRIBE",headerMap,"");
            send(sock, connectFrame.c_str(), connectFrame.length(), 0);  
        }
    }
    else if (command=="exit"){
        std::map<std::string, std::string> headerMap ;
        string destination = getWordAtIndex(messageToSend,1);
        headerMap["destination"]=destination;
        if(subscriptions [destination]<=0){
            cout << "you are not subscribed to channel "+destination<<endl;
        }
        else{
            cout<< "exit frame was recieved"<<endl;
            string id= to_string(subscriptions [destination]);
            headerMap["id"]=id;
            headerMap["receipt"]= to_string(counter);
            counter++;
            subscriptions[destination]=0;
             string connectFrame=createStompFrame("UNSUBSCRIBE",headerMap,"");
            send(sock, connectFrame.c_str(), connectFrame.length(), 0);  
        }
    }
    else if (command=="report"){
        std::string file_path= getWordAtIndex(messageToSend,1);
        names_and_events  events1 =parseEventsFile(std::string(file_path));
        vector<Event> eventsVector=  events1.events;
        cout<<"events size: "+to_string(eventsVector.size())<<endl;
        string channel_name = events1.channel_name;
        for (Event event2: eventsVector){
            std::map<std::string, std::string> headerMap ;
            headerMap["destination"]="/"+channel_name;
            headerMap["user"]= identity;
            headerMap["city"]=event2.Event::get_city();
            headerMap["event name"]=event2.Event::get_name();
            headerMap["date time"]=to_string(event2.Event::get_date_time());  
            map <string,string> general_information= event2.Event::get_general_information();
            for (const auto& entry : general_information) {
                headerMap[entry.first]=entry.second;
            }
            headerMap["description"]=event2.Event::get_description();
            headerMap["receipt"]= to_string(counter);
            counter++;
            string sendFrame=createStompFrame("SEND",headerMap,"");
            send(sock, sendFrame.c_str(), sendFrame.length(), 0);  
            for (int checkCount = 0; checkCount < 3; checkCount++) {
                fd_set checkfds;
                FD_ZERO(&checkfds);
                FD_SET(sock, &checkfds);
                
                struct timeval checkTimeout;
                checkTimeout.tv_sec = 0;
                checkTimeout.tv_usec = 30000; // 30ms check
                
                if (select(sock + 1, &checkfds, NULL, NULL, &checkTimeout) > 0) {
                    if (FD_ISSET(sock, &checkfds)) {
                        char recvBuffer[4096];
                        int bytes = recv(sock, recvBuffer, sizeof(recvBuffer) - 1, 0);
                        if (bytes > 0) {
                            recvBuffer[bytes] = '\0';
                            cout << recvBuffer << endl;
                            
                            if (recvBuffer[0] == 'M' && recvBuffer[6] == 'E') {
                                string frame = recvBuffer;
                                frames.push(frame);
                            }
                        }
                    }
                }
        }
        
        }
    }
    else if (command=="summary"){
    std::ofstream outFile(getWordAtIndex(messageToSend,3), std::ios::app);
    string channel= getWordAtIndex(messageToSend,1);
    string userFromTerminal= getWordAtIndex(messageToSend,2);
    outFile << "Channel "+getWordAtIndex(messageToSend,1) << endl;       
    outFile << "Stats: "<< endl;       
    outFile << "Total: " + to_string(frames.size())<< endl;       
        string result="";
        int active=0;
        int forces_arrival_at_scene=0;
        int size=frames.size();
        for(int i=0;i<size;i++){
            string frame=frames.front();
            frames.pop();
            frames.push(frame);
            string channelFromFrame=extractFieldValue(frame,"destination:").substr(1); 
            string user = extractFieldValue(frame,"user:");
            if (channel==channelFromFrame&&user==userFromTerminal){
            result=result+"Report_"+to_string(i+1)+":\n";
            string city=extractFieldValue(frame,"city:");
            result=result+"city: "+city+":\n";
            string time=extractFieldValue(frame,"date time:");
            time=epochToDate(time);
            result=result+"date time: "+time+"\n";
            string event=extractFieldValue(frame,"event name:");
            result=result+"event name: "+event+"\n";
            string description=extractFieldValue(frame,"description:");
            result=result+"summary: "+description.substr(0,27);            
            if(description.length()>27){
                result=result+"...";
            }
            result=result+"\n\n";
            if(extractFieldValue(frame,"active:")=="true"){
                active++;
            }
            if(extractFieldValue(frame,"forces_arrival_at_scene:")=="true"){
                forces_arrival_at_scene++;
            }
            
        }
      
       }
            result=result+"\n";
            outFile << "active: " + to_string(active)<< endl;       
            outFile << "forces_arrival_at_scene: " + to_string(forces_arrival_at_scene)<< endl; 
            outFile << ""<< endl; 
            outFile << "Event Reports:" << endl;      
            outFile << ""<< endl; 
            outFile << result << endl; 
            outFile.close();
    
}

        else if(command=="logout"){
        std::map<std::string, std::string> headerMap ;
        headerMap["receipt"] = to_string(counter);
        lastReciept=counter;
        string disconnectFrame=createStompFrame("DISCONNECT",headerMap,"");       
        send(sock, disconnectFrame.c_str(), disconnectFrame.length(), 0); 
        isConnectet=false;   
    }
    }
}  

int main(int argc, char *argv[]) {
    thread ioThread(ioThreadFunc);
    thread communicationThread(communicationFunc);

    ioThread.join();
    communicationThread.join();

	return 0;
}

