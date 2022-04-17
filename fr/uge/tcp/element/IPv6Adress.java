package fr.uge.tcp.element;

public record IPv6Adress(short a,short b,short c,short d,short e,short f,short g,short h,int port) implements IPvAdress {
    public IPv6Adress{
        if(port < 0 || port > 65535){
            throw new IllegalArgumentException("the port is too short or too long");
        }
    }
    @Override
    public String toString(){
        return a+":"+b+":"+c+":"+d+":"+e+":"+f+":"+g+":"+h;
    }
}
