package fr.uge.tcp.reader;

import fr.uge.tcp.frame.IPv6Adress;
import fr.uge.tcp.frame.InitFusion;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class InitFusionReader implements Reader<InitFusion> {

    private enum State { DONE,WAITING_SERVERNAME, WAITING_SERVERADRESS,WAITING_NB_MEMBERS, WAITING_SERVERS, ERROR }

    private State state = State.WAITING_SERVERNAME;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private final IPv6AdressReader iPv6AdressReader = new IPv6AdressReader();
    private String servername;
    private IPv6Adress localAdress;
    private int nb_members;
    private ArrayList<String> servers = new ArrayList<>();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var status = ProcessStatus.ERROR;
        if (state == State.WAITING_SERVERNAME) {
            status = stringReader.process(buffer);
            System.out.println(status); //done
            if (status == ProcessStatus.DONE) {
                System.out.println("servername");
                servername = stringReader.get();
                if(servername == null || servername.length() > 100){
                    System.out.println("servername error");
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                stringReader.reset();
                state = State.WAITING_SERVERADRESS;
            }
        }
        if (state == State.WAITING_SERVERADRESS) {
            status = iPv6AdressReader.process(buffer);
            System.out.println(status); // DONE
            if (status == ProcessStatus.DONE) {
                System.out.println("serveradress");
                localAdress = iPv6AdressReader.get();
                if(localAdress.port() > 65535 || localAdress.port() < 0){
                    System.out.println("serveradress erreur");
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                iPv6AdressReader.reset();
                state = State.WAITING_NB_MEMBERS;
            }
        }

        if (state == State.WAITING_NB_MEMBERS) {
            status = intReader.process(buffer); // refill
            System.out.println(status);
            if (status == ProcessStatus.DONE) {
                System.out.println("nb members");
                nb_members = intReader.get();
                if(nb_members < 0){
                    System.out.println("nb members erreur");
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                intReader.reset();
                if(nb_members == 0){
                   state = State.DONE;
                }
                else {
                    state = State.WAITING_SERVERS;
                }
            }
        }
        if(state == State.WAITING_SERVERS){
            String client;
            for(int i = 0; i < nb_members; i++){
                status = stringReader.process(buffer);
                System.out.println(status);
                client = stringReader.get();
                if (client == null) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                if(status == ProcessStatus.DONE){
                    if(client.length() > 30){
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    servers.add(client);
                    stringReader.reset();
                }
            }
            stringReader.reset();
            state = State.DONE;
        }

        return status;
    }

    @Override
    public InitFusion get() {
        if (state != State.DONE) {
            return null;
        }
        return new InitFusion(servername,localAdress, servers);
    }

    @Override
    public void reset() {
        state = State.WAITING_SERVERNAME;
        stringReader.reset();
        iPv6AdressReader.reset();
        intReader.reset();
        servername = null;
        localAdress = null;
        servers = null;
        nb_members = -1;
    }
}
