package fr.uge.tcp.context;

import fr.uge.tcp.client.ClientChat;
import fr.uge.tcp.frame.MessagePub;
import fr.uge.tcp.frame.MessagePv;
import fr.uge.tcp.frame.MessagePvFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class ContextCliServ extends Context{
    public ContextCliServ(SelectionKey key, ClientChat client) {
        super(key,client);
    }

    public void processPrivateMessage() {
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing private reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var getPrivateReader = (MessagePv) reader.get();
                    if (getPrivateReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    System.out.println("\nYou have a message from : " + getPrivateReader.username_src());
                    System.out.println(getPrivateReader.username_src() + " : " + getPrivateReader.message());
                    reader.reset();
                    break;
            }
        }
    }

    public void processPrivateFileMessage() {
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing file reading");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var getFileReader = (MessagePvFile) reader.get();
                    if (getFileReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    System.out.println("You have a message with a file from : " + getFileReader.username_src());
                    System.out.println(getFileReader.username_src() + " : " + getFileReader.filename());
                    reader.reset();
                    break;
            }
        }
    }

    public void processPvMessageError() {
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing private message (Client side)");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var errorPv = (String) reader.get();
                    if (errorPv == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    logger.info(errorPv);
                    reader.reset();
                    break;
            }
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
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing message reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var user = (MessagePub) reader.get();
                    if (user == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    System.out.println(user.username() + " : " + user.message());
                    reader.reset();
                    break;
            }
        }
    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param msg
     */
    public void queueMessage(MessagePub msg) {
        System.out.println("message pub");
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

    @Override
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

    /**
     * Performs the read action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    public void doRead() throws IOException {
        System.out.println("doRead");
        if (sc.read(bufferIn) == -1) {
            closed = true;
        }
        opcode = -1;
        processOpCode();
        System.out.println(opcode);
        switch (opcode){
            case 3 -> silentlyClose();
            case 4 -> processIn();
            case 5 -> processPrivateMessage();
            case 6 -> processPrivateFileMessage();
            case 7 -> processPvMessageError();
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

    public void doWrite(String login) throws IOException {
        System.out.println("doWrite");
        write();
    }

}
