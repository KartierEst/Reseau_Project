package fr.uge.tcp.reader;

import fr.uge.tcp.frame.IPv4Adress;
import fr.uge.tcp.frame.IPvAdress;

import java.nio.ByteBuffer;

public class IPvAdressReader implements Reader<IPvAdress> {
    IPv4AdressReader ipv4 = new IPv4AdressReader();
    IPv6AdressReader ipv6 = new IPv6AdressReader();
    int limit = 0;
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        limit = bb.limit();
        return limit < 20 ? ipv4.process(bb) : ipv6.process(bb);
    }

    @Override
    public IPvAdress get() {

        return limit < 20 ? ipv4.get() : ipv6.get();
    }

    @Override
    public void reset() {
        ipv6.reset();
        ipv4.reset();
    }
}
