package fr.uge.tcp.element;

import java.util.Objects;

public record MessagePub(int opcode, String servername, String username, String message) {
    public MessagePub{
        Objects.requireNonNull(servername);
        Objects.requireNonNull(username);
        Objects.requireNonNull(message);
        if(opcode != 4){
            throw new IllegalArgumentException("the packet must be a public message packet");
        }
    }
    public MessagePub(String servername,String username,String message) {
        this(4,servername,username,message);
    }

}
