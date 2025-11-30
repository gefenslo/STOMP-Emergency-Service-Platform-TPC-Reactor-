package bgu.spl.net.api;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MessageEncoderDecoderImpl implements MessageEncoderDecoder<String> {
    private static final char NULL_CHAR = '\0'; // Null character for STOMP frame termination
    private static final int INITIAL_BUFFER_SIZE = 1024; // Initial buffer size

    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
    private int length = 0;

    @Override
    public String decodeNextByte(byte nextByte) {
        if (nextByte == NULL_CHAR) { // Message ends when null character is encountered
            String message = new String(buffer, 0, length, StandardCharsets.UTF_8);
            length = 0; // Reset buffer
            return message; // Return the full message
        } else {
            pushByte(nextByte); // Add byte to buffer
            return null; // Message not complete yet
        }
    }

    @Override
    public byte[] encode(String message) {
        // Append a null character to terminate the STOMP frame
        return (message + NULL_CHAR).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Helper method to add a byte to the buffer.
     * Expands the buffer size if necessary.
     */
    private void pushByte(byte nextByte) {
        if (length >= buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length * 2); // Double the buffer size
        }
        buffer[length++] = nextByte;
    }
}
