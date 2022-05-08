package fr.uge.tcp.server;

//import fr.uge.tcp.context.*;
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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {

    //différent context
    private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private int opcode = -1;
        private final AllReader allReader = new AllReader();
        private Reader reader;

        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
        private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
        private boolean closed = false;

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        private void processConnected() {
            reader = allReader.reader(opcode);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing user reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    String user = (String) reader.get();
                    if (user == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    connect(key,user);
                    reader.reset();
                    break;
            }
        }

        public void connexionResp(boolean close) {
            if(close){
                bufferOut.putInt(3).putInt(servername.getBytes(StandardCharsets.UTF_8).length).put(UTF8.encode(servername));
                return;
            }
            bufferOut.putInt(2).putInt(servername.getBytes(StandardCharsets.UTF_8).length).put(UTF8.encode(servername));
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
                        msgPv((MessagePv) msgPvR,this);
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
                        logger.info("private message reader error (Server side)");
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var msgPvFile = reader.get();
                        if (msgPvFile == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        msgPvFile((MessagePvFile) msgPvFile,this);
                        reader.reset();
                        break;
                }
            }
        }

        private void processInitFusion() {
            reader = allReader.reader(opcode);
            while (bufferIn.hasRemaining()) {
                switch (reader.process(bufferIn)) {
                    case ERROR:
                        logger.info("initiation to a merge is impossible");
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var initFus = reader.get();
                        if (initFus == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        initFusion((InitFusion) initFus);
                        reader.reset();
                        break;
                }
            }
        }

        private void processInitFusionOk() throws IOException {
            reader = allReader.reader(opcode);
            while (bufferIn.hasRemaining()) {
                switch (reader.process(bufferIn)) {
                    case ERROR:
                        logger.info("initiation to a merge is impossible");
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var initFus = reader.get();
                        if (initFus == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        fusionOk((InitFusion) initFus,this);
                        reader.reset();
                        break;
                }
            }
        }

        private void processInitFusionFwd() {
        }

        private void processInitFusionRequest() {
        }

        private void processInitFusionRequestLoad() {
        }

        private void processChangeLeader(){
            IPvAdress ipleader = null;
            String server = null;
            reader = allReader.reader(opcode);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("impossible to change the leader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var codeReader = reader.get();
                    if (codeReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    ipleader = (IPvAdress) codeReader;
                    reader.reset();
                    break;
            }
            reader = allReader.reader(1);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("impossible to change the leader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var str = reader.get();
                    if (str == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    server = (String) str;
                    reader.reset();
                    break;
            }
            changeClientServer(ipleader,server);

        }

        private void processMergeLeader(){
            reader = allReader.reader(opcode);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("the merge doesn't finish");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var codeReader = reader.get();
                    if (codeReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    var str = (String) reader.get();
                    merge(str,this);
                    reader.reset();
                    break;
            }
        }


        private void processOpCode() {
            reader = allReader.reader(opcode);
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("impossible to read the opcode");
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
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            reader = allReader.reader(opcode);
            while (bufferIn.hasRemaining()) {
                switch (reader.process(bufferIn)) {
                    case ERROR:
                        logger.info("impossible to send a public message");
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var r = reader.get();
                        if (r == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        var user = (MessagePub) r;
                        logger.info(user.username() + " : " + user.message());
                        broadcast(user,this);
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

            var buffer = ByteBuffer.allocate(servername_src.remaining() + servername_dst.remaining() +
                    username_src.remaining() + username_dst.remaining() + filename.remaining() + Integer.BYTES * 8 + block.remaining());
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

        private void queuePvMessageError(String s) {
            var opcode = 7;
            var text = UTF8.encode(s);
            var buffer = ByteBuffer.allocate(text.remaining() + Integer.BYTES * 2);
            buffer.putInt(opcode)
                    .putInt(text.remaining())
                    .put(text)
                    .flip();
            queue.offer(buffer);
            processOut();
            updateInterestOps();
        }

        public void queueMessageFusionErr() {
            var buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.putInt(7);
            queue.offer(buffer.flip());
            processOut();
            updateInterestOps();
        }

        public void queueInitFusionIp(InitFusion initFusion) {
            var opcode = initFusion.opcode();
            var svname = UTF8.encode(initFusion.servername());
            ByteBuffer buffipv = initFusion.localAddress().queueIpv();
            var nb_members = initFusion.servers().size();
            var buffer = ByteBuffer.allocate(svname.remaining() + buffipv.remaining() + Integer.BYTES * 3);
            buffer.putInt(opcode)
                    .putInt(svname.remaining())
                    .put(svname)
                    .put(buffipv)
                    .putInt(nb_members);
            for(var client : initFusion.servers()){
                var cli = UTF8.encode(client);
                ByteBuffer tmpBuffer = ByteBuffer.allocate(buffer.capacity() + client.getBytes(StandardCharsets.UTF_8).length + Integer.BYTES);
                tmpBuffer.put(buffer.flip());
                tmpBuffer.putInt(cli.remaining());
                buffer = tmpBuffer;
            }
            queue.offer(buffer.flip());
            processOut();
            updateInterestOps();
        }

        public void queueInitFusionFwd(IPvAdress localAdress) {
            var locadr = localAdress.queueIpv();
            var buffer = ByteBuffer.allocate(Integer.BYTES + locadr.remaining());
            buffer.putInt(11)
                    .put(locadr);
            queue.offer(buffer);
            processOut();
            updateInterestOps();
        }

        public void queueInitFusionRequest(IPvAdress localAdress) {
            var locadr = localAdress.queueIpv();
            var buffer = ByteBuffer.allocate(Integer.BYTES + locadr.remaining());
            buffer.putInt(12)
                    .put(locadr);
            queue.offer(buffer);
            processOut();
            updateInterestOps();
        }

        public void queueInitFusionRequestLoad(boolean fusionPossible) {
            var buffer = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES);
            if(fusionPossible){
                buffer.putInt(13).put((byte) 1);
            }
            else{
                buffer.putInt(13).put((byte) 0);
            }
            queue.offer(buffer.flip());
            processOut();
            updateInterestOps();
        }

        public void queueErreurInitFusion() {
            var buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.putInt(10);
            queue.offer(buffer.flip());
            processOut();
            updateInterestOps();
        }


        public void queueChangeLeader(IPvAdress localAddress,String str) {
            var opcode = 14;
            var buffip = localAddress.queueIpv();
            var buffstr = UTF8.encode(str);
            var buffer = ByteBuffer.allocate(2*Integer.BYTES + buffip.remaining() + buffstr.remaining());
            buffer.putInt(opcode)
                    .put(buffip)
                    .putInt(buffstr.remaining())
                    .put(buffstr);

            queue.offer(buffer.flip());
            processOut();
            updateInterestOps();
        }

        public void queueFusionMerge(String servername) {
            var opcode = 15;
            var buffip = UTF8.encode(servername);
            var buffer = ByteBuffer.allocate(Integer.BYTES*2 + buffip.remaining());
            buffer.putInt(opcode)
                    .putInt(buffip.remaining())
                    .put(buffip);
            queue.offer(buffer.flip());
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
            var interesOps = 0;
            if(!closed && bufferIn.hasRemaining()) {
                interesOps |= SelectionKey.OP_READ;
            }
            if(bufferOut.position() != 0){
                interesOps |= SelectionKey.OP_WRITE;
            }
            if(interesOps == 0){
                silentlyClose();
                return;
            }
            key.interestOps(interesOps);
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
            opcode = -1;
            processOpCode();
            logger.info(String.valueOf(opcode));
            switch (opcode){
                case 1 -> processConnected();
                case 4 -> processIn();
                case 5 -> processPrivateMessage();
                case 6 -> processPrivateFileMessage();
                case 7 -> {}
                case 8 -> processInitFusion();
                case 9 -> processInitFusionOk();
                case 10 -> logger.info("the merge doesn't match because you have a common user with the server");
                case 11 -> processInitFusionFwd();
                case 12 -> processInitFusionRequest();
                case 13 -> processInitFusionRequestLoad();
                case 14 -> processChangeLeader();
                case 15 -> processMergeLeader();
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

        private void doWrite() throws IOException {
            if(opcode == 1){
                bufferOut.flip();
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
            SocketChannel ss = (SocketChannel) key.channel();
            var size = ss.getRemoteAddress().toString().length();
            int port = Integer.parseInt(ss.getLocalAddress().toString().substring(size-4));
            var serveurnames = serverChatons.keySet().stream().filter(e -> !(servername.equals(e))).toList();
            if(addr.length < 3){
                var parseIpv4 = adresseFusion.split("\\.");
                var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),port);
                queueInitFusionIp(new InitFusion(8, servername, ipv4, serveurnames));
            }
            else {
                var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                        , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), port);
                queueInitFusionIp(new InitFusion(8, servername, ipv6, serveurnames));
            }
        }
    }
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
    private final HashMap<String, Context> clients = new HashMap<>();
    private final HashMap<String,Context> serverChatons = new HashMap<>();
    private boolean serverMom;
    private String command;
    private final Object lock = new Object();
    private boolean fusion = false;
    private final boolean fusionPossible = true;

    //private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();

    private String adresseFusion = null;

    private String[] addr = null;
    //private int portFusion = -1;

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final String servername;
    private final SocketChannel sc;

    //private IPvAdress ipv;

    public ServerChaton(int port,String servername) throws IOException {
        if(servername.length() > 100){
            throw new IllegalArgumentException("the size of the servername is too long");
        }
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        Objects.requireNonNull(servername);
        this.servername = servername;
        //this.serverMom = servername;
        this.sc = SocketChannel.open();
        /*var addresse = serverSocketChannel.getLocalAddress().toString().substring(1);
        var addr = addresse.split(":");
        var portFus = Integer.parseInt(addr[addr.length-1]);
        if(addr.length < 3){
            var parseIpv4 = addresse.split("\\.");
            ipv = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),portFus);
        }
        else {
            ipv = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                    , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), portFus);
        }*/
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isConnectable()) {
                ((Context) key.attachment()).doConnect();
            }
            if (key.isValid() && key.isAcceptable()) {
                doAccept();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException");
            silentlyClose(key);
        }
    }

    private void doAccept() throws IOException {
        var client = serverSocketChannel.accept();
        if (client == null) {
            logger.warning("accept() returned null");
            return;
        }
        client.configureBlocking(false);
        var keyClient = client.register(selector, SelectionKey.OP_READ);
        keyClient.attach(new Context(keyClient));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    private void silentlyClose(Channel sc) {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param msg the message to add
     */
    public void broadcast(MessagePub msg,Context context) {
        if(serverChatons.containsValue(context)) {
            if (!serverMom){//!serverMom.equals(servername)) {
                for (var client : clients.values()) {
                    client.queueMessage(msg);
                }
            } else {
                for (var client : clients.values()) {
                    client.queueMessage(msg);
                }
                for (var serveur : serverChatons.values()) {
                    if (!serveur.equals(context)) {
                        serveur.queueMessage(msg);
                    }
                }
            }
        }
        if(clients.containsValue(context)){
            if (!serverMom){//!serverMom.equals(servername)) {
                for (var client : clients.values()) {
                    client.queueMessage(msg);
                }
                var ctx = serverChatons.get("serverMom");
                if(ctx == null){
                    logger.info("the context of the serverMom doesn't exist");
                    return;
                }
                ctx.queueMessage(msg);
            } else {
                for (var client : clients.values()) {
                    client.queueMessage(msg);
                }
                for (var serveur : serverChatons.values()) {
                    serveur.queueMessage(msg);
                }
            }
        }
    }

    public void connect(SelectionKey key, String user){
        var client = clients.get(user);
        if (client != null) {
            logger.info("the user already exist");
            client.connexionResp(true);
        }
        else {
            var clientCo = (Context) key.attachment();
            clients.put(user,clientCo);
            clientCo.connexionResp(false);
        }
    }

    public void msgPv(MessagePv messagePv,Context context) {
        if(messagePv.servername_dst().equals(messagePv.servername_src())){
            var dest_cli = clients.get(messagePv.username_dst());
            if (dest_cli != null) {
                dest_cli.queuePvMessage(messagePv);
            } else {
                context.queuePvMessageError("the user " + messagePv.username_dst() + " does'nt exist");
            }
            return;
        }
        if(clients.containsValue(context)){
            if(!serverMom){//!serverMom.equals(servername)){
                var ctx = serverChatons.get("serverMom");
                if(ctx == null){
                    logger.info("the context of the serverMom doesn't exist");
                    context.queuePvMessageError("you don't have a serverMom ?");
                    return;
                }
                ctx.queuePvMessage(messagePv);
            }
            else {
                var dest_serv = serverChatons.get(messagePv.servername_dst());
                if(dest_serv != null){
                    dest_serv.queuePvMessage(messagePv);
                }
                else {
                    context.queuePvMessageError("the server : " + messagePv.servername_dst() + " don't exist");
                }
            }
        }
        if(serverChatons.containsValue(context)){
            if(!serverMom){//!serverMom.equals(servername)){
                var dest_cli = clients.get(messagePv.username_dst());
                if(dest_cli != null){
                    dest_cli.queuePvMessage(messagePv);
                }
                else{
                    context.queuePvMessageError("no user : " + messagePv.username_dst() + " in this server");
                }
            }
            else {
                /*if(messagePv.servername_dst().equals(serverMom)){
                    var dest_cli = clients.get(messagePv.username_dst());
                    if(dest_cli != null){
                        dest_cli.queuePvMessage(messagePv);
                    }
                    else {
                        context.queuePvMessageError("the user : " + messagePv.username_dst() + " doesn't exist serverMom " + serverMom);
                    }
                    return;
                }*/
                var dest_serv = serverChatons.get(messagePv.servername_dst());
                if(dest_serv != null){
                    dest_serv.queuePvMessage(messagePv);
                }
                else {
                    context.queuePvMessageError("no server with this name : " + messagePv.servername_dst() +", so no user with this name : " + messagePv.username_dst());
                }
            }
        }
    }

    public void msgPvFile(MessagePvFile messagePvFile,Context context) {
        if(messagePvFile.servername_dst().equals(messagePvFile.servername_src())){
            var dest_cli = clients.get(messagePvFile.username_dst());
            if (dest_cli != null) {
                dest_cli.queuePvMessageFile(messagePvFile);
            } else {
                context.queuePvMessageError("the user " + messagePvFile.username_dst() + " does'nt exist");
            }
            return;
        }
        if(clients.containsValue(context)){
            if(!serverMom){//!serverMom.equals(servername)){
                var ctx = serverChatons.get("serverMom");
                if(ctx == null){
                    logger.info("the context of the serverMom doesn't exist");
                    context.queuePvMessageError("you don't have a serverMom ?");
                    return;
                }
                ctx.queuePvMessageFile(messagePvFile);
            }
            else {
                var dest_serv = serverChatons.get(messagePvFile.servername_dst());
                if(dest_serv != null){
                    dest_serv.queuePvMessageFile(messagePvFile);
                }
                else {
                    context.queuePvMessageError("the server : " + messagePvFile.servername_dst() + " don't exist");
                }
            }
        }
        if(serverChatons.containsValue(context)){
            if(!serverMom){//!serverMom.equals(servername)){
                var dest_cli = clients.get(messagePvFile.username_dst());
                if(dest_cli != null){
                    dest_cli.queuePvMessageFile(messagePvFile);
                }
                else{
                    context.queuePvMessageError("no user : " + messagePvFile.username_dst() + " in this server");
                }
            }
            else {
                /*if(messagePvFile.servername_dst().equals(serverMom)){
                    var dest_cli = clients.get(messagePvFile.username_dst());
                    if(dest_cli != null){
                        dest_cli.queuePvMessageFile(messagePvFile);
                    }
                    else {
                        context.queuePvMessageError("the user : " + messagePvFile.username_dst() + " doesn't exist serverMom " + serverMom);
                    }
                    return;
                }*/
                var dest_serv = serverChatons.get(messagePvFile.servername_dst());
                if(dest_serv != null){
                    dest_serv.queuePvMessageFile(messagePvFile);
                }
                else {
                    context.queuePvMessageError("no server with this name : " + messagePvFile.servername_dst() +", so no user with this name : " + messagePvFile.username_dst());
                }
            }
        }
    }

    public void initFusion(InitFusion initFusion) {
        try {
            var serveurnames = serverChatons.keySet().stream().toList();
            SelectionKey key = null;
            for(var ooo : selector.keys().stream().toList()){
                if(ooo.channel() == serverSocketChannel){
                    continue;
                }
                try {
                    var o = (SocketChannel) ooo.channel();
                    var str = o.getRemoteAddress().toString();
                    var size = o.getRemoteAddress().toString().length();
                    var fin = str.substring(size-5);
                    logger.info(fin + " " + initFusion.localAddress().port());
                    if(Integer.parseInt(fin) == initFusion.localAddress().port()){
                        key = ooo;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (key == null) {
                logger.warning("key is null, no server can connect");
                return;
            }
            var queue = (Context) key.attachment();
            serverChatons.put(initFusion.servername(),queue);
            if(this.servername.equals(serverMom)){
                if(!initFusion.servers().isEmpty()) {
                    for (var serveur : initFusion.servers()) {
                        if (serveurnames.contains(serveur)) {
                            queue.queueErreurInitFusion();
                            logger.info("a server with same name exist");
                            return;
                        }
                    }
                }
                var addresse = serverSocketChannel.getLocalAddress().toString().substring(1);
                var addr = addresse.split(":");
                var port = Integer.parseInt(addr[addr.length-1]);
                //queue.queueInitFusionIp(new InitFusion(9,servername,ipv,serveurnames));
                if(addr.length < 3){
                    var parseIpv4 = addresse.split("\\.");
                    var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),port);
                    queue.queueInitFusionIp(new InitFusion(9, servername, ipv4, serveurnames));
                }
                else {
                    var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                            , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), port);
                    queue.queueInitFusionIp(new InitFusion(9, servername, ipv6, serveurnames));
                }
            }
            else{
                //queue = (Context) this..keyServer.attachment();
                queue = serverChatons.get(serverMom);
                queue.queueInitFusionFwd(initFusion.localAddress());
            }
        }catch (IOException e){
            logger.info("error with the local address of the server");
        }
    }

    public void fusionOk(InitFusion initFusion,Context servCtx) throws IOException {
        SelectionKey key = null;
        for(var ooo : selector.keys().stream().toList()){
            if(ooo.channel() == serverSocketChannel){
                continue;
            }
            try {
                var o = (SocketChannel) ooo.channel();
                var str = o.getRemoteAddress().toString();
                var size = o.getRemoteAddress().toString().length();
                var fin = str.substring(size-4);
                if(Integer.parseInt(fin) == initFusion.localAddress().port()){
                    key = ooo;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (key == null) {
            logger.warning("key is null, no server can connect");
            return;
        }
        Context ctx = (Context) key.attachment();
        serverChatons.put(initFusion.servername(),ctx);
        var thisServerCtx = serverChatons.get(initFusion.servername());
        var addresse = serverSocketChannel.getLocalAddress().toString().substring(1);
        var addr = addresse.split(":");
        var port = Integer.parseInt(addr[addr.length-1]);
        IPvAdress ipv;
        if(addr.length < 3){
            var parseIpv4 = addresse.split("\\.");
             ipv = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),port);
        }
        else {
             ipv = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                    , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), port);
        }
        if(servername.length() < initFusion.servername().length()){
            for(var context : serverChatons.values()){
                context.queueChangeLeader(ipv,servername);
            }
        }

        else if(servername.length() > initFusion.servername().length()){
            logger.info(initFusion.localAddress().toString());
            for(var context : serverChatons.values()){
                if(thisServerCtx.equals(context)){
                    //serverMom = initFusion.servername();
                    serverMom = false;
                    context.queueFusionMerge(servername);
                }
                else{
                    context.queueChangeLeader(initFusion.localAddress(),initFusion.servername());
                }
            }
        }
        else if (servername.compareTo(initFusion.servername()) > 0) {
            for(var context : serverChatons.values()){
                context.queueChangeLeader(ipv,servername);
            }
        }
        else if (servername.compareTo(initFusion.servername()) < 0) {
            for(var context : serverChatons.values()){
                if(thisServerCtx.equals(context)){
                    //serverMom = initFusion.servername();
                    serverMom = false;
                    context.queueFusionMerge(servername);
                }
                else{
                    context.queueChangeLeader(initFusion.localAddress(),initFusion.servername());
                }
            }
        }
        fusion = false;
    }

    private void changeClientServer(IPvAdress ipleader,String str) {
        try {
            if(sc.isConnected()) {
                var tofusion = new InetSocketAddress(ipleader.toString(), ipleader.port());
                sc.finishConnect();
                sc.configureBlocking(false);
                sc.connect(tofusion);
            }
            var key = selector.keys().stream().toList().get(selector.keys().size() - 2);
            var ctx = (Context) key.attachment();
            ctx.queueFusionMerge(servername);
        } catch (IOException e){
            logger.info("impossible to connect this server");
        }
    }

    private void merge(String str,Context ctx){
        if(!serverChatons.containsKey(str)){
            serverChatons.put(str,ctx);
        }
        logger.info("the serveur : " + str + " are merge");
    }

    private void sendCommand(String msg) throws InterruptedException {
        synchronized (lock) {
            command = msg;
            selector.wakeup();
        }
    }

    /**
     * Processes the command from the BlockingQueue
     */

    private void processCommands() {
        InetSocketAddress tofusion;
        synchronized (lock) {
            try {
                if (command == null) {
                    return;
                }
                var cmdFusion = command.split(" ");
                if (cmdFusion.length != 3) {
                    logger.warning("Unknown command: " + command + " -> FUSION ipaddress port");
                    return;
                }
                if (!cmdFusion[0].equals("FUSION")) {
                    logger.warning("Unknown command: " + command + " -> FUSION ipaddress port");
                } else {
                    if(Integer.parseInt(cmdFusion[2]) == serverSocketChannel.socket().getLocalPort()){
                        logger.info("it's this server, impossible to fusion");
                        return;
                    }
                    if (!fusion) {
                        fusion = true;
                        if (this.servername.equals(serverMom)) {
                            adresseFusion = serverSocketChannel.getLocalAddress().toString().substring(1);
                            addr = adresseFusion.split(":");

                            tofusion = new InetSocketAddress(cmdFusion[1], Integer.parseInt(cmdFusion[2]));

                            sc.configureBlocking(false);
                            var key = sc.register(selector, SelectionKey.OP_CONNECT);
                            key.attach(new Context(key));
                            sc.connect(tofusion);
                        } else {
                            var queue = serverChatons.get(serverMom);
                            queue.queueInitFusionRequest(new IPv4Adress((byte) 1, (byte) 2, (byte) 3, (byte) 4,45));
                        }
                    } else {
                        var queue = serverChatons.get(serverMom);
                        queue.queueInitFusionRequestLoad(fusionPossible); // verifier si c bien le bon fusionPossible
                    }
                }
            } catch (UnresolvedAddressException e){
                logger.warning("the address doesn't exist");
            } catch (IOException e){
                logger.info("error with the local address of the server");
            }
            finally {
                command = null;
            }
        }
    }

    private Thread console(){
        return new Thread(() -> {
            var scanner = new Scanner(System.in);
            while (!Thread.interrupted()) {
                try {
                    var scan = scanner.nextLine();
                    sendCommand(scan);
                } catch (InterruptedException e) {
                    logger.info("Interrupt the console thread");
                }
            }
        });
    }


    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        var server = new ServerChaton(Integer.parseInt(args[0]),args[1]);
        server.console().setDaemon(true);
        server.console().start();
        server.launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter port");
    }
}
