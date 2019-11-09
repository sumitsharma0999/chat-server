package storage;

import model.Chat;
import redis.clients.jedis.Jedis;

public class StorageProvider {

    private static Jedis jedis = new Jedis("redis1", 6379);

    public static void save(Chat chat) {
        // Store in redis
        String gid = chat.chatId.toString();
        jedis.lpush(gid, chat.user1, chat.user2);
        jedis.set("game:" + chat.user1, gid);
        jedis.set("game:" + chat.user2, gid);
    }
}
