package fr.uge.tcp.context;

import fr.uge.tcp.client.ClientChat;
import fr.uge.tcp.element.MessagePub;
import fr.uge.tcp.element.MessagePv;
import fr.uge.tcp.element.MessagePvFile;
import fr.uge.tcp.reader.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class ContextCoCli {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    static private int BUFFER_SIZE = 10_000;
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private boolean closed = false;
    private final IntReader opcodeReader = new IntReader();
    private final MessagePubReader messageReader = new MessagePubReader();
    private final StringReader serverReader = new StringReader();
    static private Logger logger = Logger.getLogger(ClientChat.class.getName());

    private int opcode = -1;



    private ContextCoCli(SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
    }

    private void processConnected() {
        while (bufferIn.hasRemaining()) {
            switch (serverReader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing server reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var getServerReader = serverReader.get();
                    if (getServerReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    connect(getServerReader);
                    serverReader.reset();
                    break;
            }
        }
    }


    private void processOpCode() {
        switch (opcodeReader.process(bufferIn)) {
            case ERROR:
                logger.info("Error processing opcode");
                silentlyClose();
            case REFILL:
                return;
            case DONE:
                var opReader = opcodeReader.get();
                if (opReader == null) {
                    logger.info("Get value at null");
                    return;
                }
                opcode = opReader;
                opcodeReader.reset();
                break;
        }
    }


    /**
     * Process the content of bufferIn
     *
     * The convention is that bufferIn is in write-mode before the call to process
     * and after the call
     *
     */
    private void processIn() {
        while (bufferIn.hasRemaining()) {
            switch (messageReader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing message reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var reader = messageReader.get();
                    if (reader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    System.out.println(reader.username() + " : " + reader.message());
                    messageReader.reset();
                    break;
            }
        }
    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param msg
     */
    private void queueMessage(MessagePub msg) {
        var opcode = msg.opcode();
        var servername = UTF8.encode(msg.servername());
        var username = UTF8.encode(msg.username());
        var text = UTF8.encode(msg.message());
        var buffer = ByteBuffer.allocate(servername.remaining() + username.remaining() + text.remaining() + Integer.BYTES * 4);
        buffer.putInt(opcode)
                .putInt(servername.remaining())
                .put(servername)
                .putInt(username.remaining())
                .put(username)
                .putInt(text.remaining())
                .put(text)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    private void queuePvMessage(MessagePv msg) {
        var opcode = msg.opcode();
        var servername_src = UTF8.encode(msg.servername_src());
        var servername_dst = UTF8.encode(msg.servername_dst());
        var username_src = UTF8.encode(msg.username_src());
        var username_dst = UTF8.encode(msg.username_dst());
        var text = UTF8.encode(msg.message());
        var buffer = ByteBuffer.allocate(servername_src.remaining() + servername_dst.remaining() + username_src.remaining() + username_dst.remaining() + text.remaining() + Integer.BYTES * 6);
        buffer.putInt(opcode)
                .putInt(servername_src.remaining())
                .put(servername_src)
                .putInt(username_src.remaining())
                .put(username_src)
                .putInt(servername_dst.remaining())
                .put(servername_dst)
                .putInt(username_dst.remaining())
                .put(username_dst)
                .putInt(text.remaining())
                .put(text)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    private void queuePvMessageFile(MessagePvFile msg) {
        var opcode = msg.opcode();
        var nbblock = msg.nbblock();
        var blocksize = msg.blocksize();
        var block = msg.block();
        var servername_src = UTF8.encode(msg.servername_src());
        var servername_dst = UTF8.encode(msg.servername_dst());
        var username_src = UTF8.encode(msg.username_src());
        var username_dst = UTF8.encode(msg.username_dst());
        var filename = UTF8.encode(msg.filename());

        var buffer = ByteBuffer.allocate(servername_src.remaining() + servername_dst.remaining() + username_src.remaining() + username_dst.remaining() + filename.remaining() + Integer.BYTES * 9);
        buffer.putInt(opcode)
                .putInt(servername_src.remaining())
                .put(servername_src)
                .putInt(username_src.remaining())
                .put(username_src)
                .putInt(servername_dst.remaining())
                .put(servername_dst)
                .putInt(username_dst.remaining())
                .put(username_dst)
                .putInt(filename.remaining())
                .put(filename)
                .putInt(nbblock)
                .putInt(blocksize)
                .put(block)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
     *
     */
    private void processOut() {
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
    }

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     *
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */

    private void updateInterestOps() {
        var interesOps = 0;
        if(!closed && bufferIn.hasRemaining()) {
            interesOps |= SelectionKey.OP_READ;
        }
        if(bufferOut.position() != 0){
            interesOps |= SelectionKey.OP_WRITE;
        }
        if(interesOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interesOps);
    }

    private void silentlyClose() {
        try {
            sc.close();
            logger.info("it's impossible to connect because your login already exist or have a problem with the server");
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Performs the read action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    private void doRead() throws IOException {
        if (sc.read(bufferIn) == -1) {
            closed = true;
        }
        processOpCode();
        switch (opcode){
            case 1 -> {}
            case 2 -> processConnected();
            case 3 -> silentlyClose();
        }
        updateInterestOps();
    }


    /**
     * Performs the write action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     * @param login
     */

    private void doWrite(String login) throws IOException {
        if(opcode == -1){
            bufferOut.clear();
            bufferOut.putInt(1).putInt(login.getBytes(UTF8).length).put(UTF8.encode(login)).flip();
            sc.write(bufferOut);
            bufferOut.clear();
            updateInterestOps();
            return;
        }
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    public void doConnect() throws IOException {
        if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
