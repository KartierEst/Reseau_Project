package fr.uge.tcp.context;

import fr.uge.tcp.client.ClientChat;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public class ContextCoCli extends Context{
    public ContextCoCli(SelectionKey key, ClientChat client) {
        super(key,client);
    }

    private void processConnected() {
        System.out.println("processClient");
        reader = allReader.reader(opcode);
        while (bufferIn.hasRemaining()) {
            switch (reader.process(bufferIn)) {
                case ERROR:
                    logger.info("Error processing server reader");
                    silentlyClose();
                case REFILL:
                    return;
                case DONE:
                    var getServerReader = (String) reader.get();
                    if (getServerReader == null) {
                        logger.info("Get value at null");
                        return;
                    }
                    client.connect(getServerReader);
                    reader.reset();
                    break;
            }
        }
    }

    @Override
    public void doRead() throws IOException {
        if (sc.read(bufferIn) == -1) {
            closed = true;
        }
        opcode = -1;
        processOpCode();
        System.out.println(opcode);
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

    @Override
    public void doWrite(String login) throws IOException {
        if(opcode == -1){
            bufferOut.clear();
            bufferOut.putInt(1).putInt(login.getBytes(UTF8).length).put(UTF8.encode(login)).flip();
            sc.write(bufferOut);
            bufferOut.clear();
            updateInterestOps();
            return;
        }
        System.out.println(opcode);
        write();
    }

    @Override
    public void doConnect() throws IOException {
        if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
