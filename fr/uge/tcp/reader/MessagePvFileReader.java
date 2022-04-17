package fr.uge.tcp.reader;


import fr.uge.tcp.element.MessagePvFile;

import java.nio.ByteBuffer;

public class MessagePvFileReader implements Reader<MessagePvFile> {

    private enum State { DONE,WAITING_SERVERNAME_SRC, WAITING_USERNAME_SRC,WAITING_SERVERNAME_DST,WAITING_USERNAME_DST,WAITING_FILENAME,
        WAITING_NBBLOCK,WAITING_BLOCKSIZE,WAITING_BLOCK, ERROR }

    private State state = State.WAITING_SERVERNAME_SRC;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private String servername_src;
    private String username_src;
    private String servername_dst;
    private String username_dst;
    private String filename;
    private int nbblock;
    private int blocksize;
    private byte block;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var status = ProcessStatus.ERROR;
        if (state == State.WAITING_SERVERNAME_SRC) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                servername_src = stringReader.get();
                if(servername_src == null || servername_src.length() > 100){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_USERNAME_SRC;
            }
        }
        if (state == State.WAITING_USERNAME_SRC) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                username_src = stringReader.get();
                if(username_src == null || username_src.length() > 30){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_SERVERNAME_DST;
            }
        }
        if (state == State.WAITING_SERVERNAME_DST) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                servername_dst = stringReader.get();
                if(servername_dst == null || servername_dst.length() > 100){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_USERNAME_DST;
            }
        }
        if (state == State.WAITING_USERNAME_DST) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                username_dst = stringReader.get();
                if(username_dst == null || username_dst.length() > 30){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_FILENAME;
            }
        }
        if (state == State.WAITING_FILENAME) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                filename = stringReader.get();
                if(filename == null || filename.length() > 30){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_NBBLOCK;
            }
        }
        if (state == State.WAITING_NBBLOCK) {
            status = intReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                nbblock = intReader.get();
                if(nbblock < 0){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                intReader.reset();
                state = State.WAITING_BLOCKSIZE;
            }
        }

        if (state == State.WAITING_BLOCKSIZE) {
            status = intReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                blocksize = intReader.get();
                if(blocksize < 0){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                intReader.reset();
                state = State.WAITING_BLOCK;
            }
        }

        if (state == State.WAITING_BLOCK) {
            block = buffer.get();
            if (block < 0) {
                state = State.ERROR;
                return ProcessStatus.ERROR;
            }
            state = State.DONE;
        }

        return status;
    }

    @Override
    public MessagePvFile get() {
        if (state != State.DONE) {
            return null;
        }
        return new MessagePvFile(servername_src,servername_dst,username_src,username_dst,filename,nbblock,blocksize,block);
    }

    @Override
    public void reset() {
        state = State.WAITING_SERVERNAME_SRC;
        stringReader.reset();
        intReader.reset();
        username_src = null;
        servername_src = null;
        servername_dst = null;
        username_dst = null;
        nbblock = -1;
        blocksize = -1;
        block = -1;
    }
}