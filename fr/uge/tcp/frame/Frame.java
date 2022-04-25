package fr.uge.tcp.frame;

public sealed interface Frame permits IPvAdress, InitFusion, MessagePub, MessagePv, MessagePvFile {

}
