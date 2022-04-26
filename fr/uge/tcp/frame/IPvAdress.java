package fr.uge.tcp.frame;

import java.nio.ByteBuffer;

public sealed interface IPvAdress extends Frame permits IPv4Adress,IPv6Adress {
    int port();
    String toString();
    ByteBuffer queueIpv();
}
