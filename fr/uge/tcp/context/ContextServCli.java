package fr.uge.tcp.context;

import fr.uge.tcp.client.ClientChat;
import fr.uge.tcp.frame.MessagePub;
import fr.uge.tcp.frame.MessagePv;
import fr.uge.tcp.frame.MessagePvFile;
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

public class ContextServCli extends Context{
    public ContextServCli(SelectionKey key, ServerChaton server){
        super(key,server);
    }
    private void processPrivateMessage() {
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("private message reader error (Server side)");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var msgPvR = reader.get();
                    if (msgPvR == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    server.msgPv((MessagePv) msgPvR);
                    reader.reset();
                    break;
            }
        }
    }

    private void processPrivateFileMessage() {
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var msgPvFile = reader.get();
                    if (msgPvFile == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    server.msgPvFile((MessagePvFile) msgPvFile);
                    reader.reset();
                    break;
            }
        }
    }
    private void processIn() {
        System.out.println("processIn : " + bufferOut.position());
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var r = reader.get();
                    if (r == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    server.broadcast((MessagePub) r);
                    reader.reset();
                    break;
            }
        }
    }
    public void queueMessage(MessagePub msg) {
        var opcode = msg.opcode();
        var username = UTF8.encode(msg.username());
        var servername = UTF8.encode(msg.servername());
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
        System.out.println("queue : " + bufferOut.position());
        processOut();
        updateInterestOps();
    }

    public void queuePvMessage(MessagePv msg) {
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

    public void queuePvMessageFile(MessagePvFile msg) {
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
    public void queuePvMessageError() {
        var opcode = 7;
        var text = UTF8.encode("the user doesn't exist");
        var buffer = ByteBuffer.allocate(text.remaining() + Integer.BYTES * 2);
        buffer.putInt(opcode)
                .putInt(text.remaining())
                .put(text)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }


    public void doRead() throws IOException {
        System.out.println("doRead");
        if (sc.read(bufferIn) == -1) {
            logger.info("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }
        opcode = -1;
        processOpCode();
        System.out.println(opcode);
        switch (opcode){
            case 4 -> {processIn(); System.out.println("test " + bufferOut.position());}
            case 5 -> processPrivateMessage();
            case 6 -> processPrivateFileMessage();
        }
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

    public void doWrite() throws IOException {
        System.out.println("doWrite");
        write();
    }
}
