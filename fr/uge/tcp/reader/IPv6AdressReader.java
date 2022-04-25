package fr.uge.tcp.reader;


import fr.uge.tcp.frame.IPv6Adress;

import java.nio.ByteBuffer;

public class IPv6AdressReader implements Reader<IPv6Adress> {


    private enum State { DONE,WAITING_ALL_SHORT, WAITING_PORT,ERROR }


    private State state = State.WAITING_ALL_SHORT;
    private final IntReader intReader = new IntReader();
    private final ShortReader shortReader = new ShortReader();
    private short a;
    private short b;
    private short c;
    private short d;
    private short e;
    private short f;
    private short g;
    private short h;
    private int port;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var status = ProcessStatus.ERROR;
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                System.out.println("a");
                a = shortReader.get();
                if (a < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                System.out.println("b");
                b = shortReader.get();
                if (b < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                System.out.println("c");
                c = shortReader.get();
                if (c < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                d = shortReader.get();
                if (d < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                e = shortReader.get();
                if (e < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                f = shortReader.get();
                if (f < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                g = shortReader.get();
                if (g < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
            }
        }
        if(state == State.WAITING_ALL_SHORT){
            status = shortReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                h = shortReader.get();
                if (h < 0) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                shortReader.reset();
                state = State.WAITING_PORT;
            }
        }
        if (state == State.WAITING_PORT) {
            status = intReader.process(buffer);
            if(status == ProcessStatus.DONE) {
                port = intReader.get();
                if (port < 0 || port > 65535) {
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
    public IPv6Adress get() {
        if (state != State.DONE) {
            return null;
        }
        return new IPv6Adress(a,b,c,d,e,f,g,h,port);
    }

    @Override
    public void reset() {
        intReader.reset();
        shortReader.reset();
        a = -1;
        b = -1;
        c = -1;
        d = -1;
        e = -1;
        f = -1;
        g = -1;
        h = -1;
        port = -1;
    }
}
