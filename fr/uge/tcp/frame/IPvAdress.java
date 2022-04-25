package fr.uge.tcp.frame;

public sealed interface IPvAdress extends Frame permits IPv4Adress,IPv6Adress {
    int port();
    String toString();
}
