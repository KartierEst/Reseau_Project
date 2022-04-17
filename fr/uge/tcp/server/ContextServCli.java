package fr.uge.tcp.server;

import fr.uge.tcp.element.MessagePub;
import fr.uge.tcp.reader.MessagePubReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.logging.Logger;

public class ContextServCli {
    private final SelectionKey key;
    private final SocketChannel sc;
    private final MessagePubReader reader = new MessagePubReader();
    private final static int BUFFER_SIZE = 1024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private boolean closed = false;
    private ServerChaton server;

    public ContextServCli(ServerChaton server, SelectionKey key) {
        Objects.requireNonNull(server);
        this.server = server;
        this.key = key;
        this.sc = (SocketChannel) key.channel();
    }

    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process and
     * after the call
     */
    private void processIn() {
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Process reader error");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var r = reader.get();
                    if (r == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    server.broadcast(r);
                    reader.reset();
                    break;
            }
        }
    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param msg the message to add
     */
    public void queueMessage(MessagePub msg) {
        var username = UTF8.encode(msg.username());
        var servername = UTF8.encode(msg.servername());
        var text = UTF8.encode(msg.message());
        var buffer = ByteBuffer.allocate(username.remaining() + servername.remaining() + text.remaining() + Integer.BYTES * 4);
        buffer.putInt(msg.opcode())
                .putInt(username.remaining())
                .put(username)
                .putInt(servername.remaining())
                .put(servername)
                .putInt(text.remaining())
                .put(text)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
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
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */
    private void updateInterestOps() {
        int interestOps = 0;
        if (!closed && bufferIn.hasRemaining()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (bufferOut.position() != 0) {
            interestOps |= SelectionKey.OP_WRITE;
        }

        if (interestOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interestOps);
    }

    private void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Performs the read action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException if the read fails
     */
    private void doRead() throws IOException {
        if (sc.read(bufferIn) == -1) {
            logger.info("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }
        processIn();
        updateInterestOps();
    }

    /**
     * Performs the write action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException if the write fails
     */

    private void doWrite() throws IOException {
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

}
