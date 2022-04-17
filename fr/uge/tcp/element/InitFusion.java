package fr.uge.tcp.element;

import java.util.List;
import java.util.Objects;

public record InitFusion(int opcode, String servername, IPvAdress localAddress, List<String> servers) {
    public InitFusion{
        Objects.requireNonNull(servername);
        if (localAddress.port() < 0 || localAddress.port() > 65535) {
            throw new IllegalStateException("Invalid port");
        }
    }
    public InitFusion(String servername, IPvAdress localAddress,List<String> servers){
        this(8,servername,localAddress,servers);
    }

}
