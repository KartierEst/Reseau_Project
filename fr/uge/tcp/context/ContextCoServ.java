package fr.uge.tcp.context;


import fr.uge.tcp.frame.IPv4Adress;
import fr.uge.tcp.frame.IPv6Adress;
import fr.uge.tcp.frame.InitFusion;
import fr.uge.tcp.server.ServerChaton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;

public class ContextCoServ extends Context {
    public ContextCoServ(SelectionKey key,ServerChaton chaton) {
        super(key,chaton);
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
                server.connect(key,user);
                reader.reset();
                break;
        }
    }

    @Override
    public void connexionResp(boolean close) {
        if(close){
            //bufferOut.putInt(3).putInt(server.getServername().getBytes(StandardCharsets.UTF_8).length).put(UTF8.encode(server.getServername()));
            return;
        }
        //bufferOut.putInt(2).putInt(server.getServername().getBytes(StandardCharsets.UTF_8).length).put(UTF8.encode(server.getServername()));
    }

    public void queueInitFusionIpv6(InitFusion initFusion) {
        var opcode = initFusion.opcode();
        var svname = UTF8.encode(initFusion.servername());
        var locadr = (IPv6Adress) initFusion.localAddress();
        var nb_members = initFusion.servers().size();
        var buffer = ByteBuffer.allocate(svname.remaining() + Integer.BYTES * 4 + Short.BYTES * 8);
        buffer.putInt(opcode)
                .putInt(svname.remaining())
                .put(svname)
                .putShort(locadr.a())
                .putShort(locadr.b())
                .putShort(locadr.c())
                .putShort(locadr.d())
                .putShort(locadr.e())
                .putShort(locadr.f())
                .putShort(locadr.g())
                .putShort(locadr.h())
                .putInt(locadr.port())
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

    public void queueInitFusionIpv4(InitFusion initFusion) {
        var opcode = initFusion.opcode();
        var svname = UTF8.encode(initFusion.servername());
        var locadr = (IPv4Adress) initFusion.localAddress();
        var nb_members = initFusion.servers().size();
        var buffer = ByteBuffer.allocate(svname.remaining() + Integer.BYTES * 3 + Byte.BYTES * 4);
        buffer.putInt(opcode)
                .putInt(svname.remaining())
                .put(svname)
                .put(locadr.a())
                .put(locadr.b())
                .put(locadr.c())
                .put(locadr.d())
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

    /**
     * Performs the read action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException if the read fails
     */
    public void doRead() throws IOException {
        System.out.println("doRead");
        if (sc.read(bufferIn) == -1) {
            logger.info("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }
        opcode = -1;
        processOpCode();
        if (opcode == 1) {
            processConnected();
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
        if(opcode == 1){
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.clear();
            key.attach(new ContextServCli(key,server));
            updateInterestOps();
            return;
        }
        write();
    }

    /*public void doConnect() throws IOException {
        if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
        }
        key.interestOps(SelectionKey.OP_WRITE);
        var serveurnames = server.getServerChatons().keySet().stream().toList();
        var addr = server.getAddr();
        if(server.getAddr().length < 3){
            logger.info("ipv4");
            var parseIpv4 = server.getAdresseFusion().split("\\.");
            var ipv4 = new IPv4Adress(Byte.parseByte(parseIpv4[0]),Byte.parseByte(parseIpv4[1]),Byte.parseByte(parseIpv4[2]),Byte.parseByte(parseIpv4[3]),server.getPortFusion());
            queueInitFusionIpv4(new InitFusion(8, server.getServername(), ipv4, serveurnames));
        }
        else {
            logger.info("ipv6");
            var ipv6 = new IPv6Adress(Short.parseShort(addr[0].substring(1)), Short.parseShort(addr[1]), Short.parseShort(addr[2]), Short.parseShort(addr[3]), Short.parseShort(addr[4])
                    , Short.parseShort(addr[5]), Short.parseShort(addr[6]), Short.parseShort(addr[7].substring(0, addr[7].length() - 1)), server.getPortFusion());
            queueInitFusionIpv6(new InitFusion(8, server.getServername(), ipv6, serveurnames));
        }
    }*/
}
