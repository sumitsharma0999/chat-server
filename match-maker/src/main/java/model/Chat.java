package model;

import java.util.UUID;

public class Chat {
    public String user1;
    public String user2;
    public UUID chatId;

    public Chat(String user1, String user2) {
        this.chatId = UUID.randomUUID();
        this.user1= user1;
        this.user2=user2;
    }
}
