package fr.uge.tcp.element;

import java.util.Objects;

public record MessagePvFile(int opcode, String servername_src, String servername_dst, String username_src, String username_dst, String filename, int nbblock, int blocksize, byte block) {
    public MessagePvFile{
        Objects.requireNonNull(servername_src);
        Objects.requireNonNull(servername_dst);
        Objects.requireNonNull(username_src);
        Objects.requireNonNull(username_dst);
        Objects.requireNonNull(filename);
        if(opcode != 6){
            throw new IllegalArgumentException("the packet must be a private message packet");
        }
        if(nbblock < 0 || blocksize < 0 || block < 0){
            throw new IllegalStateException("the size of a block or the block have a problem");
        }
    }
    public MessagePvFile(String servername_src,String servername_dst,String username_src,String username_dst,String filename,int nbblock,int blocksize,byte block){
        this(6,servername_src,servername_dst,username_src,username_dst,filename,nbblock,blocksize,block);
    }
}
