package fr.uge.tcp.reader;


import fr.uge.tcp.element.MessagePub;

import java.nio.ByteBuffer;

public class MessagePubReader implements Reader<MessagePub> {

    private enum State { DONE,WAITING_SERVERNAME, WAITING_USERNAME, WAITING_TEXT, ERROR }

    private State state = State.WAITING_SERVERNAME;
    private final StringReader stringReader = new StringReader();
    private String servername;
    private String username;
    private String message;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var status = ProcessStatus.ERROR;
        if (state == State.WAITING_SERVERNAME) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                servername = stringReader.get();
                if(servername == null || servername.length() > 100){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_USERNAME;
            }
        }
        if (state == State.WAITING_USERNAME) {
            status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                username = stringReader.get();
                if(username == null || username.length() > 30){
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
    public MessagePub get() {
        if (state != State.DONE) {
            return null;
        }
        return new MessagePub(servername,username, message);
    }

    @Override
    public void reset() {
        state = State.WAITING_SERVERNAME;
        stringReader.reset();
        username = null;
        message = null;
        servername = null;
    }
}