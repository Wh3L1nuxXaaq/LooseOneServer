package invoke.looseone.secure;

import invoke.looseone.util.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static invoke.looseone.db.DBController.*;
import static invoke.looseone.util.JsonUtil.sendJson;

public class AdminPanel {

    static Map<String, Long> bannedIps = new HashMap<>();
    static long BAN_DURATION_MS = 5 * 60 * 1000; // 5 минут

    public static void handleAdminGetUsers(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);

            if (!validateAdminSession(json.optString("session"))) {
                if (Config.debug) {
                    Config.log("govnoed idet nahui (hAdmGetUsers) ");
                }
                return;
            }

            JSONObject response = new JSONObject();
            response.put("users", usersArray);
            response.put("count", usersArray.length());

            sendJson(ctx, response.toString());
        } catch (Exception e) {
            if (Config.debug) {
                Config.log("getUsers fail with admin panel " + e.toString());
            }
        }
    }

    public static void handleAdminUpdateUser(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);

            if (!validateAdminSession(json.optString("session"))) {
                if (Config.debug) {
                    Config.log("kakoita gavnaed pitaetsha voiti");
                }
                sendJson(ctx, "idi nahui dalbaeb");
                return;
            }

            String login = json.getString("login");
            JSONObject updates = json.getJSONObject("updates");
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("login").equals(login)) {
                    Iterator<String> keys = updates.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        user.put(key, updates.get(key));
                    }
                    saveUsers();
                    sendJson(ctx, String.valueOf(new JSONObject().put("success", true)));
                    return;
                }
            }

            if (Config.debug) {
                Config.log("wtf? ne zabral u bomja sabku (adm)");
            }
        } catch (Exception e) {
            if (Config.debug) {
                Config.log("str 115 DBController: " + e.toString());
            }
        }
    }

    public static boolean validateAdminSession(String session) {
        return activeAdminSessions.contains(session);
    }

    public static void handleAdminAuth(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);
            String key = json.optString("key");

            JSONObject response = new JSONObject();

            String ip = ctx.channel().remoteAddress().toString().replaceAll("^/|:.*$", "");
            if (bannedIps.containsKey(ip)) {
                long unblockTime = bannedIps.get(ip);
                if (System.currentTimeMillis() < unblockTime) {
                    response.put("success", false);
                    response.put("error", "Слишком много попыток");
                    sendJson(ctx, response.toString());
                    return;
                } else {
                    bannedIps.remove(ip);
                }
            }

            if (ADMIN_KEY.equals(key)) {
                String sessionId = UUID.randomUUID().toString();
                activeAdminSessions.add(sessionId);
                response.put("success", true).put("session", sessionId);
            } else {
                bannedIps.put(ip, System.currentTimeMillis() + BAN_DURATION_MS);
                response.put("success", false).put("error", "Неверный ключ");
            }

            sendJson(ctx, response.toString());
        } catch (Exception e) {
            if (Config.debug) {
                Config.log("govnoed idet nahui (hAdmAuth) " + e);
            }
        }
    }
}
