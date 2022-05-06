package fr.uge.tcp.context;

import fr.uge.tcp.frame.IPv4Adress;
import fr.uge.tcp.frame.IPv6Adress;
import fr.uge.tcp.frame.IPvAdress;
import fr.uge.tcp.frame.InitFusion;
import fr.uge.tcp.server.ServerChaton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;

public class ContextServServ extends Context{
    public ContextServServ(SelectionKey key, ServerChaton chaton) {
        super(key,chaton);
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
                    server.initFusion((InitFusion) initFus);
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
                    //server.fusionOk((InitFusion) initFusR);
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
        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    public void queueErreurInitFusion() {
        var buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(10);
    }

    public void queueChangeLeader(IPvAdress localAddress) {
        var opcode = 14;
            /*switch (localAddress){
                case IPv4Adress ipv4: {
                    var buffer = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES * 4);
                    buffer.putInt(opcode)
                            .put(ipv4.a())
                            .put(ipv4.b())
                            .put(ipv4.c())
                            .put(ipv4.d());
                }
                case IPv6Adress ipv6: {
                    var buffer = ByteBuffer.allocate(Integer.BYTES + Short.BYTES * 8);
                    buffer.putInt(opcode)
                            .putShort(ipv6.a())
                            .putShort(ipv6.b())
                            .putShort(ipv6.c())
                            .putShort(ipv6.d())
                            .putShort(ipv6.e())
                            .putShort(ipv6.f())
                            .putShort(ipv6.g())
                            .putShort(ipv6.h());
                }
            }*/
    }
    public void queueChangeLeaderIpv6(IPvAdress localAdress) {
        var opcode = 14;
    }

    public void doWrite(String login) throws IOException {
        write();
    }



}
