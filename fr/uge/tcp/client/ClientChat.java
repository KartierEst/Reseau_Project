package fr.uge.tcp.client;


import fr.uge.tcp.frame.*;
import fr.uge.tcp.reader.AllReader;
import fr.uge.tcp.reader.Reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Logger;


public class ClientChat {

 
    private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
        private boolean closed = false;
        private int opcode = -1;
        private final AllReader allReader = new AllReader();
        private Reader reader;



        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        private void processConnected() {
            reader = allReader.reader(opcode);
            while (bufferIn.hasRemaining()) {
                switch (reader.process(bufferIn)) {
                    case ERROR:
                        logger.info("Error processing server reader");
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var getServerReader = reader.get();
                        if (getServerReader == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        connect((String) getServerReader);
                        reader.reset();
                        break;
                }
            }
        }

        private void processPrivateMessage() {
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

        private void processPrivateFileMessage() {
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

        private void processPvMessageError() {
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

        private void processOpCode() {
            reader = allReader.reader(opcode);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing opcode");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var opReader = reader.get();
                    if (opReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    opcode = (int) opReader;
                    reader.reset();
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
                        if (reader == null) {
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
            opcode = -1;
            processOpCode();
            switch (opcode){
                case 1 -> {}
                case 2 -> processConnected();
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

    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChat.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private final SocketChannel sc;
    private final Selector selector;
    private SelectionKey key;
    private final InetSocketAddress serverAddress;
    private final String path;
    private final String login;
    private String servername;
    private final Thread console;
    private Context uniqueContext;
    private final ArrayDeque<ByteBuffer> blockingQueue = new ArrayDeque<>();

    public ClientChat(String login,String path,InetSocketAddress serverAddress) throws IOException {
        Objects.requireNonNull(login);
        Objects.requireNonNull(serverAddress);
        Objects.requireNonNull(path);
        this.serverAddress = serverAddress;
        this.login = login;
        this.path = path;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via a BlockingQueue and wake it up
     *
     * @param msg
     * @throws InterruptedException
     */

    private void sendCommand(String msg) throws InterruptedException {
        synchronized (blockingQueue) {
            blockingQueue.add(StandardCharsets.UTF_8.encode(msg));
            selector.wakeup();
        }
    }

    /**
     * Processes the command from the BlockingQueue 
     */

    private void processCommands() {
        synchronized (blockingQueue) {
            if (blockingQueue.isEmpty()) {
                return;
            }
            var msg = blockingQueue.poll();
            if (msg != null) {
                var string = UTF8.decode(msg).toString();
                if(string.startsWith("@")){
                    try {
                        string = string.replace("@", "");
                        var split = string.split(" ");
                        var dest = split[0].split(":");
                        var login_dst = dest[0];
                        var servername_dst = dest[1];
                        if (!servername_dst.equals(servername)) {
                            logger.info("impossible to send this message to " + login_dst + " because you are not in the same server");
                            return;
                        }
                        uniqueContext.queuePvMessage(new MessagePv(5, servername, servername_dst, login, login_dst, string.substring(2+login_dst.length()+servername_dst.length())));
                        return;
                    } catch(ArrayIndexOutOfBoundsException e){
                        logger.info("your private message without file is malformed -> @user:server message to user");
                        return;
                    }
                }
                if(string.startsWith("/")){
                    try {
                        string = string.replace("/", "");
                        var split = string.split(" ");
                        var dest = split[0].split(":");
                        var login_dst = dest[0];
                        var servername_dst = dest[1];
                        if (!servername_dst.equals(servername)) {
                            logger.info("impossible to send this message to " + login_dst + " because you are not in the same server");
                            return;
                        }
                        if(split.length > 2){
                            logger.info("your private message with file is malformed -> @user:server filename");
                            return;
                        }
                        uniqueContext.queuePvMessageFile(new MessagePvFile(6, servername, servername_dst, login, login_dst,split[1], 5, 5000, (byte) 1));
                        return;
                    } catch (ArrayIndexOutOfBoundsException e){
                        logger.info("your private message with file is malformed -> @user:server filename");
                        return;
                    }
                }
                uniqueContext.queueMessage(new MessagePub(4,servername,login, string));
            }
        }
    }

    public void connect(String server){
        if(uniqueContext.opcode == 3){
            uniqueContext.closed = true;
            return;
        }
        this.servername = server;
        logger.info("Welcome to the serveur : " + server);
        //uniqueContextF = new ContextCliServ(key,this);
        //key.attach(uniqueContextF);
    }



    public void launch() throws IOException {
        sc.configureBlocking(false);
        key = sc.register(selector, SelectionKey.OP_CONNECT);
        //uniqueContextF = new ContextCoCli(key,this);
        //key.attach(uniqueContextF);
        uniqueContext = new Context(key);
        key.attach(uniqueContext);
        sc.connect(serverAddress);

        console.setDaemon(true);
        console.start();

        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite(login);
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ClientChat(args[0],args[1], new InetSocketAddress(args[2], Integer.parseInt(args[3]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChat login path hostname port");
    }
}