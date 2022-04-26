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
import java.util.zip.CheckedOutputStream;

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
                        msgPv((MessagePv) msgPvR);
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
                        msgPvFile((MessagePvFile) msgPvFile);
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
                        System.out.println("error");
                        silentlyClose();
                    case REFILL:
                        System.out.println("refill");
                        return;
                    case DONE:
                        System.out.println("done");
                        var initFus = reader.get();
                        if (initFus == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        System.out.println("ca passe ?");
                        initFusion((InitFusion) initFus);
                        reader.reset();
                        break;
                }
            }
        }

        private void processInitFusionOk() {
            reader = allReader.reader(opcode);
            while (bufferIn.hasRemaining()) {
                switch (reader.process(bufferIn)) {
                    case ERROR:
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var initFusR = reader.get();
                        if (initFusR == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        System.out.println("ca passe ok");
                        fusionOk((InitFusion) initFusR);
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
                    var ipleader = (IPvAdress) codeReader;
                    changeClientServer(ipleader);
                    reader.reset();
                    break;
            }
        }

        private void processMergeLeader(){
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
                    var str = (String) codeReader;
                    serverMom = str;
                    reader.reset();
                    break;
            }
        }


        private void processOpCode() {
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
                        silentlyClose();
                    case REFILL:
                        return;
                    case DONE:
                        var r = reader.get();
                        if (r == null) {
                            logger.info("Get value at null");
                            return;
                        }
                        broadcast((MessagePub) r);
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

        private void queuePvMessageError() {
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

        public void queueInitFusionFwd(String localAdress) {
            var locadr = UTF8.encode(localAdress);
            var buffer = ByteBuffer.allocate(2*Integer.BYTES + locadr.remaining());
            buffer.putInt(11)
                    .putInt(locadr.remaining())
                    .put(locadr);
            queue.offer(buffer);
            processOut();
            updateInterestOps();
        }

        public void queueInitFusionRequest(String localAdress) {
            var locadr = UTF8.encode(localAdress);
            var buffer = ByteBuffer.allocate(2*Integer.BYTES + locadr.remaining());
            buffer.putInt(12)
                    .putInt(locadr.remaining())
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
        }

        public void queueChangeLeader(IPvAdress localAddress) {
            var opcode = 14;
            var buffip = localAddress.queueIpv();
            var buffer = ByteBuffer.allocate(Integer.BYTES + buffip.remaining());
            buffer.putInt(opcode)
                    .put(buffip);
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
                System.out.println("read");
            }
            if(bufferOut.position() != 0){
                System.out.println("write");
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
            System.out.println("doread");
            if (sc.read(bufferIn) == -1) {
                logger.info("Connection closed by " + sc.getRemoteAddress());
                closed = true;
            }
            opcode = -1;
            processOpCode();
            logger.info("doRead " + opcode);
            switch (opcode){
                case 1 -> processConnected();
                case 4 -> processIn();
                case 5 -> processPrivateMessage();
                case 6 -> processPrivateFileMessage();
                case 8 -> processInitFusion();
                case 9 -> processInitFusionOk();
                case 10 -> logger.info("the fusion doesn't match because you have a common user with the server");
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
            logger.info("doWrite " + opcode);
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
            var serveurnames = serverChatons.keySet().stream().toList();
            if(addr.length < 3){
                logger.info("ipv4");
                var parseIpv4 = adresseFusion.split("\\.");
                var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),portFusion);
                queueInitFusionIp(new InitFusion(8, servername, ipv4, serveurnames));
            }
            else {
                logger.info("ipv6");
                var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                        , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), portFusion);
                queueInitFusionIp(new InitFusion(8, servername, ipv6, serveurnames));
            }
        }
    }
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
    private final HashMap<String, Context> clients = new HashMap<>();
    private final HashMap<String,Context> serverChatons = new HashMap<>();
    private String serverMom;
    private String command;
    private final Object lock = new Object();
    private boolean fusion = false;
    private final boolean fusionPossible = true;

    //private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();

    private String adresseFusion = null;

    private String[] addr = null;
    private int portFusion = -1;

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final String servername;
    private final SocketChannel sc;

    public ServerChaton(int port,String servername) throws IOException {
        if(servername.length() > 100){
            throw new IllegalArgumentException("the size of the servername is too long");
        }
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        Objects.requireNonNull(servername);
        this.servername = servername;
        this.serverMom = servername;
        this.sc = SocketChannel.open();
    }

    public String getServername() {
        return servername;
    }

    /*public HashMap<String, ContextServServ> getServerChatons() {
        return serverChatons;
    }

    public String getAdresseFusion() {
        return adresseFusion;
    }

    public String[] getAddr() {
        return addr;
    }

    public int getPortFusion() {
        return portFusion;
    }*/

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
        //keyClient.attach(new ContextCoServ(keyClient,this));
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
    public void broadcast(MessagePub msg) {
        for(var client : clients.values()){
            client.queueMessage(msg);
        }
    }

    public void connect(SelectionKey key, String user){
        var client = clients.get(user);
        if (client != null) {
            logger.info("the user already exist");
            /*var clientCo = (ContextCoServ) key.attachment();
            clientCo.connexionResp(true);
            var clientCo = (Context) key.attachment();*/
            client.connexionResp(true);
        }
        else {
            /*var ctx = new ContextServCli(key,this);
            var clientCo = (ContextCoServ) key.attachment();
            clientCo.connexionResp(false);
            clients.put(user,ctx);*/
            var clientCo = (Context) key.attachment();
            clients.put(user,clientCo);
            clients.get(user).connexionResp(false);
        }
    }

    public void msgPv(MessagePv messagePv) {
        Context queue = clients.get(messagePv.username_dst());
        Context username_src = clients.get(messagePv.username_src());
        if(username_src == null){
            logger.warning("the client source have a problem");
        }
        else {
            if (queue == null) {
                username_src.queuePvMessageError();
                logger.info("the user doesn't exist");
            } else {
                queue.queuePvMessage(messagePv);
            }
        }
    }

    public void msgPvFile(MessagePvFile messagePvFile) {
        var queue = clients.get(messagePvFile.username_dst());
        var username_src = clients.get(messagePvFile.username_src());
        if(username_src == null){
            logger.warning("the client source have a problem");
        }
        else {
            if (queue == null) {
                username_src.queuePvMessageError();
                logger.info("the user doesn't exist");
            } else {
                queue.queuePvMessageFile(messagePvFile);
            }
        }
    }

    public void initFusion(InitFusion initFusion) {
        try {
            var serveurnames = serverChatons.keySet().stream().toList();
            //var queue = serverChatons.get(serverMom);
            //String adr = initFusion.localAddress().toString();
            /*switch (initFusion.localAddress()){
                case IPv4Adress ipv4 : adr += ipv4.toString();
                case IPv6Adress ipv6 : adr += ipv6.toString();
            }*/
            /*var fusion = new InetSocketAddress(Objects.requireNonNull(adr),initFusion.localAddress().port());
            sc.configureBlocking(false);
            System.out.println(adr);
            var key = sc.register(selector,SelectionKey.OP_WRITE);
            sc.connect(fusion);
            key.attach(new Context(key));
            var queue = (Context) key.attachment();
            for(var key : selector.keys()){
                System.out.println(key.channel());
            }*/
            var key = selector.keys().stream().toList().get(selector.keys().size() - 2);
            //var keyServer = sc.register(selector,SelectionKey.OP_WRITE);
            var queue = (Context) key.attachment();
            serverChatons.put(initFusion.servername(),queue);
            if(this.servername.equals(serverMom)){
                //Ici mettre la verification des noms differents dans les serveurs
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
                if(addr.length < 3){
                    logger.info("ipv4");
                    var parseIpv4 = addresse.split("\\.");
                    var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),port);
                    queue.queueInitFusionIp(new InitFusion(9, servername, ipv4, serveurnames));
                }
                else {
                    logger.info("ipv6");
                    var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                            , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), port);
                    queue.queueInitFusionIp(new InitFusion(9, servername, ipv6, serveurnames));
                }
            }
            else{
                //queue = (Context) this..keyServer.attachment();
                queue = serverChatons.get(servername);
                queue.queueInitFusionFwd(queue.sc.getLocalAddress().toString());
            }
        }catch (IOException e){
            logger.info("error with the local address of the server");
        }
    }

    public void fusionOk(InitFusion initFusion) {
        var key = selector.keys().stream().toList().get(selector.keys().size()-1);
        for(var k : selector.keys()){
            System.out.println(k);
        }
        Context ctx = (Context) key.attachment();
        serverChatons.put(initFusion.servername(),ctx);
        if(servername.length() < initFusion.servername().length()){
            /*Mon idee : Transformer la liste de String d'initFusion par une liste de Serveur, ainsi on pourra parcourir chaque serveur facilement
            Et du coup acceder a tous les champs de chaque serveur et ainsi proceder a la fusion en ajoutant les serveurs manquant au 2 mega-serveurs.
             */
            // recuperer la key (possible avec l'adress ?)
            // recuperer le context
            // context queueChangeLeader()
            // écris le packet 14 sur le initFusion serveur
            // ecris le packet 14 sur tout les serveurs en commun de initFusion serveur
            // changer le leader sur tout les serveurs
            // envoyer un packet 15 comme quoi c'est bien fais
            var adress = initFusion.localAddress();
            var context = serverChatons.get(initFusion.servername());
            logger.info("1er cas");
        }
        else if(servername.length() > initFusion.servername().length()){
            for(var context : serverChatons.values()){
                context.queueChangeLeader(initFusion.localAddress());
            }
            logger.info("2eme cas " + serverChatons.size());
        }
        else if (servername.compareTo(initFusion.servername()) > 0) {
            logger.info("3eme cas");
        }
        else if (servername.compareTo(initFusion.servername()) < 0) {
            for(var context : serverChatons.values()){
                context.queueChangeLeader(initFusion.localAddress());
            }
            logger.info("4eme cas");
        }
        fusion = false;
    }

    private void changeClientServer(IPvAdress ipleader) {
        try {
            var tofusion = new InetSocketAddress(ipleader.toString(), ipleader.port());
            sc.configureBlocking(false);
            sc.connect(tofusion);
            var key = selector.keys().stream().toList().get(selector.keys().size()-2);
            var ctx = (Context) key.attachment();
            ctx.queueFusionMerge(servername);
        } catch (IOException e){
            logger.info("sheesh");
        }
        //silentlyClose(sc);
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
                    if (!fusion) {
                        //fusion = true;
                        if (this.servername.equals(serverMom)) {
                            var serveurnames = serverChatons.keySet().stream().toList();
                            //var queue = serverChatons.get(serverMom);
                            adresseFusion = serverSocketChannel.getLocalAddress().toString().substring(1);
                            addr = adresseFusion.split(":");
                            portFusion = Integer.parseInt(addr[addr.length - 1]);

                            tofusion = new InetSocketAddress(cmdFusion[1], Integer.parseInt(cmdFusion[2]));

                            sc.configureBlocking(false);
                            var key = sc.register(selector, SelectionKey.OP_CONNECT);
                            key.attach(new Context(key));
                            sc.connect(tofusion);
                            //key.attach(new Context(key));
                            //var queue = (Context) key.attachment();
                            //queue.updateInterestOps();

                            /*if(addr.length < 3){
                                logger.info("ipv4");
                                var parseIpv4 = addresse.split("\\.");
                                var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),port);
                                queue.queueInitFusionIpv4(new InitFusion(8, servername, ipv4, serveurnames));
                            }
                            else {
                                logger.info("ipv6");
                                //key.interestOps(SelectionKey.OP_WRITE);
                                var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                                        , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), port);
                                queue.queueInitFusionIpv6(new InitFusion(8, servername, ipv6, serveurnames));
                            }*/
                        } else {
                            //var queue = (Context) this.serverMom.keyServer.attachment();
                            var queue = serverChatons.get(serverMom);
                            var adress = serverChatons.get(servername).sc.getLocalAddress();
                            queue.queueInitFusionRequest(adress.toString());
                        }
                    } else {
                        //var queue = (Context) this.serverMom.keyServer.attachment();
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
