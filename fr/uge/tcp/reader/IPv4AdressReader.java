package fr.uge.tcp.reader;


import fr.uge.tcp.frame.IPv4Adress;

import java.nio.ByteBuffer;

public class IPv4AdressReader implements Reader<IPv4Adress> {


    private enum State { DONE, WAITING_PORT,ERROR }


    private State state = State.WAITING_PORT;
    private final IntReader intReader = new IntReader();
    private byte a;
    private byte b;
    private byte c;
    private byte d;
    private int port;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        a = buffer.get();
        b = buffer.get();
        c = buffer.get();
        d = buffer.get();
        if(a < 0 || b < 0 || c < 0 || d < 0){
            state = State.ERROR;
            return ProcessStatus.ERROR;
        }
        var status = ProcessStatus.ERROR;
        if (state == State.WAITING_PORT) {
            status = intReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                port = intReader.get();
                if(port < 0){
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                intReader.reset();
                state = State.DONE;
            }
        }

        return status;
    }

    @Override
    public IPv4Adress get() {
        if (state != State.DONE) {
            return null;
        }
        return new IPv4Adress(a,b,c,d,port);
    }

    @Override
    public void reset() {
        intReader.reset();
        a = -1;
        b = -1;
        c = -1;
        d = -1;
        port = -1;
    }
}
