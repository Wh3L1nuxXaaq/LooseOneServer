package invoke.looseone.db;

import invoke.looseone.util.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.CharsetUtil;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import static invoke.looseone.db.DBController.usersArray;
import static invoke.looseone.util.JsonUtil.sendJson;
import static invoke.looseone.util.MathUtil.xorDecode;

public class UIDController {
    public static String getUID() {
        try {
            return new String(Files.readAllBytes(Paths.get(Config.uidFile)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void updateUID() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(Config.uidFile)));
            int current = Integer.parseInt(content.trim());
            int updated = current + 1;
            Files.write(Paths.get(Config.uidFile), String.valueOf(updated).getBytes());

        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    public static void handleUID(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String encryptedBody = request.content().toString(CharsetUtil.UTF_8);
            byte[] decoded = Base64.getDecoder().decode(encryptedBody);
            byte[] decrypted = xorDecode(decoded, (byte) 0x5A);
            String jsonStr = new String(decrypted, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonStr);
            String login = json.optString("login");
            JSONObject response = new JSONObject();
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.optString("login").equalsIgnoreCase(login)) {
                    String hwid = user.optString("uid", null);
                    response.put("uid", hwid);
                    sendJson(ctx, response.toString());
                    return;
                }
            }
            response.put("error", "not found");
            sendJson(ctx, response.toString());
        } catch (Exception e) {
            Config.log("Ошибка в verifyHwid: " + e);
            e.printStackTrace();
            sendJson(ctx, "{\"error\": \"internal\"}");
        }
    }

    public static void handleUIDLogin(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String json = request.content().toString(StandardCharsets.UTF_8);
            JSONObject requestData = new JSONObject(json);

            String login = requestData.optString("login");
            String password = requestData.optString("password");

            String uid = "unknown";
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("login").equals(login) && user.getString("password").equals(password)) {
                    uid = user.optString("uid", "unknown");
                    break;
                }
            }

            JSONObject response = new JSONObject();
            response.put("uid", uid);
            sendJson(ctx, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
