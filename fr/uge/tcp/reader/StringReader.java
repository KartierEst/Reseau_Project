package fr.uge.tcp.reader;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
    private enum State {
        DONE, WAITING_SIZE, WAITING_STRING, ERROR
    };

    private State state = State.WAITING_SIZE;
    private final ByteBuffer internalBuffer = ByteBuffer.allocate(1024); // write-mode
    private String value;
    private final IntReader intReader = new IntReader();
    private int sizeToRead;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAITING_SIZE) {
            readSize(buffer);
            if (state == State.ERROR) {
                return ProcessStatus.ERROR;
            }
        }

        if (state != State.WAITING_STRING) {
            return ProcessStatus.REFILL;
        }

        buffer.flip();
        try {
            if (buffer.remaining() <= internalBuffer.remaining()) {
                internalBuffer.put(buffer);
            } else {
                var oldLimit = buffer.limit();
                buffer.limit(internalBuffer.remaining());
                internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }
        if (internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = StandardCharsets.UTF_8.decode(internalBuffer).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            return null;
        }
        return value;
    }


    private void readSize(ByteBuffer buffer) {
        var status = intReader.process(buffer);
        if (status == ProcessStatus.DONE) {
            sizeToRead = intReader.get();
            if (sizeToRead > 1024 || sizeToRead < 0) {
                state = State.ERROR;
                return;
            }
            internalBuffer.limit(sizeToRead);
            intReader.reset();
            state = State.WAITING_STRING;
        }
    }

    @Override
    public void reset() {
        state = State.WAITING_SIZE;
        internalBuffer.clear();
        sizeToRead = 0;
    }
}
