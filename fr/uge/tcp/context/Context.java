package fr.uge.tcp.context;

import fr.uge.tcp.client.ClientChat;
import fr.uge.tcp.frame.*;
import fr.uge.tcp.reader.AllReader;
import fr.uge.tcp.reader.Reader;
import fr.uge.tcp.server.ServerChaton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public abstract class Context {
    static int BUFFER_SIZE;
    static final Charset UTF8 = StandardCharsets.UTF_8;

    static final Logger logger = Logger.getLogger(Context.class.getName());

    final SelectionKey key;
    final SocketChannel sc;
    final ServerChaton server;
    final ClientChat client;
    int opcode = -1;
    final AllReader allReader = new AllReader();
    Reader reader;

    final ByteBuffer bufferIn;
    final ByteBuffer bufferOut;
    final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    boolean closed = false;

    Context(SelectionKey key, ServerChaton server) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
        client = null;
        BUFFER_SIZE = 1_024;
        bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    }

    Context(SelectionKey key, ClientChat client){
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.client = client;
        server = null;
        BUFFER_SIZE = 10_000;
        bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    }

    void processOpCode() {
        reader = allReader.reader(opcode);
        switch (reader.process(bufferIn)) {
            case ERROR:
                silentlyClose();
            case REFILL:
                return;
            case DONE:
                var codeReader = reader.get();
                if (codeReader == null) {
                    logger.info("Get value at null");
                    return;
                }
                opcode = (int) codeReader;
                reader.reset();
                break;
        }
    }

    /**
     * Try to fill bufferOut from the message queue
     */
    void processOut() {
        System.out.println("processOut1 : " + bufferOut.position());
        while (!queue.isEmpty() && bufferOut.hasRemaining()) {
            var msg = queue.peek();
            if (!msg.hasRemaining()) {
                queue.poll();
                continue;
            }
            if (msg.remaining() <= bufferOut.remaining()) {
                bufferOut.put(msg);
            } else {
                var oldLimit = msg.limit();
                msg.limit(bufferOut.remaining());
                bufferOut.put(msg);
                msg.limit(oldLimit);
            }
        }
        System.out.println("processOut2 : " + bufferOut.position());
    }

    void updateInterestOps() {
        var interesOps = 0;
        System.out.println(bufferOut.position());
        if(!closed && bufferIn.hasRemaining()) {
            System.out.println("read");
            interesOps |= SelectionKey.OP_READ;
        }
        System.out.println(bufferOut.position());
        if(!closed && bufferOut.position() != 0){
            System.out.println("write");
            interesOps |= SelectionKey.OP_WRITE;
        }
        if(interesOps == 0){
            silentlyClose();
            return;
        }
        key.interestOps(interesOps);
        System.out.println("interestops " + bufferOut.position());
    }

    void write() throws IOException {
        System.out.println("doWrite");
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public SocketChannel getSc() {
        return sc;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public void doConnect() throws IOException {}
    public void doWrite(String login) throws IOException {}
    public void doWrite() throws IOException {}
    public void doRead() throws IOException {}
    public void queuePvMessage(MessagePv messagePv) {}

    public void queuePvMessageFile(MessagePvFile messagePvFile) {}
    public void queueMessage(MessagePub messagePub) {}

    public void connexionResp(boolean b) {}

    public void queuePvMessageError() {}

    public void queueErreurInitFusion() {}

    public void queueInitFusionIpv4(InitFusion initFusion) {}

    public void queueInitFusionIpv6(InitFusion initFusion) {}

    public void queueInitFusionFwd(String toString) {}

    public void queueChangeLeader(IPvAdress localAddress) {}

    public void queueInitFusionRequest(String toString) {}

    public void queueInitFusionRequestLoad(boolean fusionPossible) {}
}
