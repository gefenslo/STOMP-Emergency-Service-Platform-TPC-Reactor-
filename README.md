# STOMP Emergency Service Platform (TPC Reactor)

A multi-threaded TCP server implementing the STOMP (Streaming Text Oriented Messaging Protocol) for emergency service event coordination and reporting.

## Overview

This project provides a real-time event management system where emergency responders can:
- Connect and authenticate
- Subscribe to emergency channels (e.g., police, fire, ambulance)
- Publish and receive emergency event reports
- Generate statistical summaries by channel

## Architecture

### Server-Side (Java)
- **Reactor Pattern**: Non-blocking I/O using Java NIO `Selector`
- **TPC (Thread-Per-Client) Pattern**: Alternative blocking I/O model with one thread per connected client
- **Thread Pool**: `ActorThreadPool` preserves per-connection message ordering
- **STOMP Protocol**: Full implementation of STOMP 1.2 messaging
- **Connection Management**: Global registry tracking active clients and subscriptions

**Key Server Classes:**
- `BaseServer<T>`: Abstract base class providing common server functionality and lifecycle management (TPC)
- `Reactor<T>`: Main server loop (NIO-based)
- `NonBlockingConnectionHandler<T>`: Per-client connection state
- `BlockingConnectionHandler<T>`: Per-client connection handler (TPC model, blocking I/O)
- `StompMessagingProtocolIMPL`: STOMP command processing
- `ConnectionsIMPL`: Global connection/subscription registry
- `MessageEncoderDecoderImpl`: STOMP frame encoding/decoding

### Client-Side (C++)
- Raw TCP socket communication
- STOMP frame building and parsing
- Event data parsing from JSON
- Interactive command-line interface

**Key Client Classes:**
- `StompClient`: Main client logic
- `ConnectionHandler`: Low-level TCP operations
- `Event`: Event object representation
- `event.cpp`: JSON event file parsing

## Building

### Server
```bash
cd server
mvn clean compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.StompServer"
```

### Client
```bash
cd client
make clean
make
./bin/StompEMIClient
```

## STOMP Commands Supported

- `CONNECT` - Authenticate and establish session
- `SUBSCRIBE` - Subscribe to channel
- `UNSUBSCRIBE` - Unsubscribe from channel
- `SEND` - Publish message to channel
- `DISCONNECT` - Close session
- `RECEIPT` - Server acknowledgment

## Data Flow

**Server:**
1. Client sends bytes
2. `MessageEncoderDecoderImpl` decodes STOMP frame
3. `StompMessagingProtocolIMPL` processes command
4. `ConnectionsIMPL` routes response/broadcast
5. Response encoded and sent to client(s)

**Client:**
1. User enters command (login, join, report, etc.)
2. Command converted to STOMP frame
3. Frame sent via socket
4. Server response received and parsed
5. Output displayed to user

## Project Structure

```
StompServer/
├── server/
│   ├── src/main/java/bgu/spl/net/
│   │   ├── api/          # Protocol implementation
│   │   ├── impl/         # Server entry point
│   │   └── srv/          # Reactor & connection handling
│   └── pom.xml
├── client/
│   ├── src/
│   │   ├── StompClient.cpp
│   │   ├── event.cpp
│   │   └── ...
│   ├── include/          # Header files
│   ├── makefile
│   └── bin/              # Compiled binaries
└── eventsSummary/        # Generated reports
```

## Configuration

**Server default:** Port `7777`  
**Client connection:** `host:port username passcode`

## Event Format (JSON)

```json
{
  "channel_name": "police",
  "events": [
    {
      "city": "Liberty City",
      "name": "Grand Theft Auto",
      "time": "23/12/2024 13:40:00",
      "description": "...",
      "general_information": {...}
    }
  ]
}
```

## Performance Notes

- Non-blocking I/O scales to many concurrent clients
- Thread pool maintains FIFO ordering per client
- Buffer pooling reduces GC pressure in Reactor
- Event statistics generated on-demand


