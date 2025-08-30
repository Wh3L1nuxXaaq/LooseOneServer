package invoke.looseone.util;

import io.netty.channel.ChannelHandlerContext;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static invoke.looseone.db.DBController.*;

public class ThreadUtil {
    public static void removeExpiredSubscriptions() {
        LocalDate today = LocalDate.now();
        List<String> usersToRemove = new ArrayList<>();

        for (Map.Entry<String, JSONObject> entry : userMap.entrySet()) {
            JSONObject user = entry.getValue();
            JSONObject subscriptions = user.optJSONObject("subscriptions");

            if (subscriptions == null || subscriptions.length() == 0) {
                usersToRemove.add(entry.getKey());
                continue;
            }

            boolean allExpired = true;
            Iterator<String> keys = subscriptions.keys();
            List<String> expiredKeys = new ArrayList<>();

            while (keys.hasNext()) {
                String subName = keys.next();
                String dateStr = subscriptions.optString(subName, null);

                if (dateStr == null || dateStr.isEmpty()) {
                    expiredKeys.add(subName);
                    continue;
                }

                try {
                    LocalDate endDate = LocalDate.parse(dateStr);
                    if (endDate.isBefore(today)) {
                        expiredKeys.add(subName);
                    } else {
                        allExpired = false;
                    }
                } catch (Exception e) {
                    expiredKeys.add(subName);
                }
            }

            for (String expiredKey : expiredKeys) {
                if (Config.debug) {
                    Config.log("Просроченная сабка у: " + user);
                }
                subscriptions.remove(expiredKey);
            }

            if (subscriptions.length() == 0 || allExpired) {
                usersToRemove.add(entry.getKey());
            }
        }

        for (String login : usersToRemove) {
            userMap.remove(login);
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (login.equals(user.optString("login"))) {
                    usersArray.remove(i);
                    break;
                }
            }
        }

        try {
            saveUsers();
        } catch (IOException e) {
            if (Config.debug) {
                Config.log("Ошибка при сохранении пользователей после очистки подписок: " + e.getMessage());
            }
        }
    }

    public static String getClientIP(ChannelHandlerContext ctx) {
        InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        return socketAddress.getAddress().getHostAddress();
    }
}
