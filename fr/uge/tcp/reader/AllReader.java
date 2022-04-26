package fr.uge.tcp.reader;


import fr.uge.tcp.frame.IPvAdress;

public class AllReader {
    public Reader reader(int opcode){
        return switch (opcode) {
            case -1 -> new IntReader();
            case 1, 2, 7 -> new StringReader();
            case 4 -> new MessagePubReader();
            case 5 -> new MessagePvReader();
            case 6 -> new MessagePvFileReader();
            case 8,9 -> new InitFusionReader();
            case 14 -> new IPvAdressReader();
            default -> null;
        };
    }
}
