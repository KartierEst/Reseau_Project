package fr.uge.tcp.reader;


import fr.uge.tcp.frame.MessagePv;

import java.nio.ByteBuffer;

public class MessagePvReader implements Reader<MessagePv> {

    private enum State { DONE,WAITING_SERVERNAME_SRC, WAITING_USERNAME_SRC,WAITING_SERVERNAME_DST,WAITING_USERNAME_DST, WAITING_TEXT, ERROR }

    private State state = State.WAITING_SERVERNAME_SRC;
    private final StringReader stringReader = new StringReader();
    private String servername_src;
    private String username_src;
    private String servername_dst;
    private String username_dst;
    private String message;

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
                state = State.WAITING_TEXT;
            }
        }

        if (state == State.WAITING_TEXT) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                message = stringReader.get();
                if (message == null) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.DONE;
            }
        }

        return status;
    }

    @Override
    public MessagePv get() {
        if (state != State.DONE) {
            return null;
        }
        return new MessagePv(servername_src,servername_dst,username_src,username_dst, message);
    }

    @Override
    public void reset() {
        state = State.WAITING_SERVERNAME_SRC;
        stringReader.reset();
        username_src = null;
        message = null;
        servername_src = null;
        servername_dst = null;
        username_dst = null;
    }
}