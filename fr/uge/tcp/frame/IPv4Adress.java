package fr.uge.tcp.frame;

import java.nio.ByteBuffer;

public record IPv4Adress(byte a, byte b, byte c, byte d, int port) implements IPvAdress {
    public IPv4Adress{
        if(port < 0 || port > 65535){
            throw new IllegalArgumentException("the port is too short or too long");
        }
    }
    @Override
    public String toString(){
        return a+"."+b+"."+c+"."+d;
    }

    @Override
    public ByteBuffer queueIpv() {
        var buffer = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES * 4);
        buffer.put(a)
                .put(b)
                .put(c)
                .put(d)
                .putInt(port);
        return buffer.flip();
    }
}
