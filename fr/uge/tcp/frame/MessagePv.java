package fr.uge.tcp.frame;

import java.util.Objects;

public record MessagePv(int opcode, String servername_src, String servername_dst, String username_src, String username_dst, String message) implements Frame {
    public MessagePv {
        Objects.requireNonNull(servername_src);
        Objects.requireNonNull(servername_dst);
        Objects.requireNonNull(username_src);
        Objects.requireNonNull(username_dst);
        Objects.requireNonNull(message);
    }
    public MessagePv(String servername_src, String servername_dst, String username_src, String username_dst, String message){
        this(5,servername_src, servername_dst, username_src, username_dst, message);
    }
}
