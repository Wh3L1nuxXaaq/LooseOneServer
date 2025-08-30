package invoke.looseone.db;

import invoke.looseone.util.Config;
import invoke.looseone.util.JsonUtil;
import invoke.looseone.util.MathUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static invoke.looseone.util.JsonUtil.sendJson;
import static invoke.looseone.util.MathUtil.isValidCredential;
import static invoke.looseone.util.MathUtil.xorDecode;
import static invoke.looseone.util.ThreadUtil.getClientIP;

public class DBController {

    public static JSONArray usersArray = new JSONArray();
    public static Map<String, JSONObject> userMap = new HashMap<>();

    static Map<String, Long> lastLoginTime = new HashMap<>();
    static Map<String, Long> lastRegisterTime = new HashMap<>();

    public static String ADMIN_KEY = Config.adminka;
    public static Set<String> activeAdminSessions = new HashSet<>();

    public static void handleHwidVerification(ChannelHandlerContext ctx, FullHttpRequest request) {
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
                    String hwid = user.optString("hwid", null);
                    response.put("hwid", hwid);
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

    public static void handleRustEx(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String json = new String(Files.readAllBytes(Paths.get("Files/RustExData.json")));
            sendJson(ctx, json);
        } catch (IOException e) {
            sendJson(ctx, "{\"error\": \"failed to read json\"}");
        }
    }

    public static void saveUsers() throws IOException {
        String json = usersArray.toString(4);
        Files.write(Paths.get("Files/Users.json"), json.getBytes());
    }

    public static void handleHWID(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            String json = req.content().toString(StandardCharsets.UTF_8);
            JSONObject requestBody = new JSONObject(json);

            if (!requestBody.has("login")) {
                sendJson(ctx, "{\"error\": \"missing field 'login'\"}");
                return;
            }

            String nickname = requestBody.optString("login");

            JSONObject response = new JSONObject();
            response.put("hwid", getHWIDByNickname(nickname));

            sendJson(ctx, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ctx, "{\"error\": \"invalid request\"}");
        }
    }

    public static void handleRegisterUser(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String ip = getClientIP(ctx);
            long now = System.currentTimeMillis();

            if (lastRegisterTime.containsKey(ip) && now - lastRegisterTime.get(ip) < 600_000) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Регистрация доступна раз в 10 минут.")
                        .toString());
                return;
            }

            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);
            String login = json.getString("login");
            String password = json.getString("password");

            if (!isValidCredential(login) || !isValidCredential(password)) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Логин и пароль должны содержать только латинские буквы и цифры (3-12 символов).")
                        .toString());
                return;
            }

            for (String existingLogin : userMap.keySet()) {
                if (existingLogin.equalsIgnoreCase(login)) {
                    sendJson(ctx, new JSONObject()
                            .put("success", false)
                            .put("error", "Пользователь уже существует")
                            .toString());
                    return;
                }
            }

            JSONObject newUser = new JSONObject();
            newUser.put("login", login);
            newUser.put("password", password);
            newUser.put("hwid", "none");
            String uid = UIDController.getUID();
            newUser.put("uid", uid);
            UIDController.updateUID();
            newUser.put("group", "user");
            newUser.put("subscriptions", new JSONObject());

            usersArray.put(newUser);
            userMap.put(login, newUser);

            saveUsers();
            lastRegisterTime.put(ip, now);

            sendJson(ctx, new JSONObject()
                    .put("success", true)
                    .put("login", login)
                    .toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static String getHWIDByNickname(String nickname) {
        for (int i = 0; i < usersArray.length(); i++) {
            JSONObject user = usersArray.getJSONObject(i);
            if (nickname.equalsIgnoreCase(user.optString("login", ""))) {
                return user.optString("hwid", "unknown");
            }
        }
        return "unknown";
    }


    public static void handleGroup(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String json = request.content().toString(StandardCharsets.UTF_8);
            JSONObject requestData = new JSONObject(json);

            String login = requestData.optString("login");
            String password = requestData.optString("password");

            String group = "unknown";
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userJson = usersArray.getJSONObject(i);
                if (userJson.getString("login").equals(login) && userJson.getString("password").equals(password)) {
                    group = userJson.optString("group", "unknown");
                    break;
                }
            }

            JSONObject response = new JSONObject();
            response.put("group", group);
            sendJson(ctx, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleAuthUser(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String ip = getClientIP(ctx);
            long now = System.currentTimeMillis();

            if (lastLoginTime.containsKey(ip) && now - lastLoginTime.get(ip) < 30_000) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Слишком частый вход. Подождите 30 секунд.")
                        .toString());
                return;
            }

            String body = request.content().toString(CharsetUtil.UTF_8);
            JSONObject json = new JSONObject(body);
            String login = json.getString("login");
            String password = json.getString("password");

            boolean success = false;

            if (!isValidCredential(login) || !isValidCredential(password)) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Нельзя использовать illegal буквы!")
                        .toString());
                return;
            }

            if (userMap.containsKey(login.toString())) {
                JSONObject user = userMap.get(login);
                if (user.getString("password").equals(password)) {
                    success = true;
                }
            }

            JSONObject response = new JSONObject();
            response.put("success", success);
            if (success) {
                response.put("login", login);
                lastLoginTime.put(ip, now);
            }

            sendJson(ctx, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleGetSubscriptions(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);
            String login = json.optString("login");
            String password = json.optString("password");

            JSONObject foundUser = null;
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("login").equals(login) && user.getString("password").equals(password)) {
                    foundUser = user;
                    break;
                }
            }
            JSONObject response = new JSONObject();
            if (foundUser != null) {
                JSONObject subscriptions = foundUser.optJSONObject("subscriptions");
                JSONObject validSubscriptions = new JSONObject();

                if (subscriptions != null) {
                    Iterator<String> keys = subscriptions.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (!subscriptions.isNull(key)) {
                            validSubscriptions.put(key, subscriptions.get(key));
                        }
                    }
                }

                response.put("subscriptions", validSubscriptions);
            } else {
                response.put("error", "Неверный логин или пароль");
            }
            sendJson(ctx, response.toString());
        } catch (Exception e) {
            if (Config.debug) {
                Config.log(e.toString());
            }
            e.printStackTrace();
        }
    }

    public static void loadUsers() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("Files/Users.json")), StandardCharsets.UTF_8);
            usersArray = new JSONArray(content);
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                userMap.put(user.getString("login"), user);
                System.out.println("Loaded user: " + user);
            }
        } catch (IOException e) {
            if (Config.debug) {
                Config.log("Failed to load users: " + e.getMessage());
            }
        }
    }

    public static void handleSubscription(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String encodedBody = request.content().toString(CharsetUtil.UTF_8);
            String jsonBody = JsonUtil.unpackJson(encodedBody);
            JSONObject json = new JSONObject(jsonBody);
            String login = json.optString("login", "");
            String password = json.optString("password", "");
            String hwid = json.optString("hwid", "");
            JSONObject response = new JSONObject();
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                String userLogin = user.optString("login", "");
                String userPassword = user.optString("password", "");
                String userHwid = user.optString("hwid", "");
                if (userLogin.equals(login) && userPassword.equals(password) && userHwid.equals(hwid)) {
                    JSONObject subs = user.optJSONObject("subscriptions");
                    response.put("subscriptions", subs != null ? subs : new JSONObject());
                    response.put("payload", UUID.randomUUID().toString());
                    response.put("dump", Base64.getEncoder().encodeToString(("Random:" + Math.random()).getBytes()));
                    response.put("token", UUID.randomUUID().toString().replace("-", ""));
                    String loaderVersion = "huila1337";
                    try {
                        loaderVersion = Files.readAllLines(Paths.get("Files/LoaderVersion.txt")).toString();
                    } catch (Exception e) {
                    }
                    response.put("dataHash", loaderVersion);
                    String packed = JsonUtil.packJson(response.toString());
                    sendJson(ctx, packed);
                    return;
                }
            }
            response.put("error", "never");
            response.put("payload", UUID.randomUUID().toString());
            response.put("token", UUID.randomUUID().toString().replace("-", ""));
            String loaderVersion = "0.1";
            try {
                loaderVersion = Files.readAllLines(Paths.get("Files/LoaderVersion.txt")).toString();
            } catch (Exception e) {
            }
            response.put("dataHash", loaderVersion);
            String packed = JsonUtil.packJson(response.toString());
            sendJson(ctx, packed);
        } catch (Exception e) {
            System.out.println("[ERROR] Ошибка при обработке запроса: " + e.toString());
            e.printStackTrace();
            sendJson(ctx, "{\"error\": \"Что-то пошло не так\"}");
        }
    }
}