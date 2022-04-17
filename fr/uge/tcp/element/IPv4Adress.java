package fr.uge.tcp.element;

public record IPv4Adress(byte a,byte b,byte c,byte d,int port) implements IPvAdress {
    public IPv4Adress{
        if(port < 0 || port > 65535){
            throw new IllegalArgumentException("the port is too short or too long");
        }
    }
    @Override
    public String toString(){
        return a+"."+b+"."+c+"."+d;
    }
}
