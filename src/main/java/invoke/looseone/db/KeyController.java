package invoke.looseone.db;

import invoke.looseone.util.Config;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static invoke.looseone.db.DBController.*;
import static invoke.looseone.util.JsonUtil.sendJson;
import static invoke.looseone.util.MathUtil.isValidCredential;
import static invoke.looseone.util.ThreadUtil.getClientIP;

public class KeyController {
    private static final Map<String, String> keyFiles = new HashMap<>();
    static {
        keyFiles.put("lifetime", "Files/lifetime.txt");
        keyFiles.put("week", "Files/week.txt");
        keyFiles.put("month", "Files/month.txt");
    }

    private static boolean isSubscriptionActive(String expiryDate) {
        try {
            // "dd.MM.yyyy"
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
            Date expiry = sdf.parse(expiryDate);
            Date now = new Date();
            return expiry.after(now);
        } catch (Exception e) {
            Config.log("Ошибка при проверке даты подписки: " + e);
            return false;
        }
    }

    public static void handleDownloadWithToken(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String uri = request.uri();
            String token = uri.substring(uri.lastIndexOf("/") + 1);

            TokenInfo tokenInfo = downloadTokens.get(token);
            if (tokenInfo == null || System.currentTimeMillis() - tokenInfo.createdAt > 5 * 60 * 1000) {
                sendJson(ctx, "{\"success\": false, \"error\": \"Недействительный или истекший токен\"}");
                return;
            }

            boolean hasRustExSubscription = false;
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("login").equalsIgnoreCase(tokenInfo.login)) {
                    JSONObject subscriptions = user.optJSONObject("subscriptions");
                    if (subscriptions != null && subscriptions.has("RustEx")) {
                        String expiryDate = subscriptions.optString("RustEx");
                        if (isSubscriptionActive(expiryDate)) {
                            hasRustExSubscription = true;
                        }
                    }
                    break;
                }
            }

            if (!hasRustExSubscription) {
                sendJson(ctx, "{\"success\": false, \"error\": \"Подписка RustEx истекла или недействительна\"}");
                return;
            }

            // Отправляем файл
            File file = new File("Files/LooseOneLauncher.exe");
            if (!file.exists()) {
                sendJson(ctx, "{\"success\": false, \"error\": \"Файл лаунчера не найден\"}");
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(bytes)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"LooseOneLauncher.exe\"");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            downloadTokens.remove(token);
        } catch (Exception e) {
            Config.log("Ошибка в handleDownloadWithToken: " + e);
            e.printStackTrace();
            sendJson(ctx, "{\"success\": false, \"error\": \"Внутренняя ошибка сервера\"}");
        }
    }

    static Map<String, TokenInfo> downloadTokens = new HashMap<>();

    static class TokenInfo {
        String login;
        long createdAt;

        TokenInfo(String login, long createdAt) {
            this.login = login;
            this.createdAt = createdAt;
        }
    }

    public static void handleDownloadLauncher(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);
            String login = json.optString("login");
            String password = json.optString("password");

            JSONObject response = new JSONObject();
            boolean hasRustExSubscription = false;
            String userLogin = null;
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("login").equalsIgnoreCase(login) && user.getString("password").equals(password)) {
                    userLogin = user.getString("login");
                    JSONObject subscriptions = user.optJSONObject("subscriptions");
                    if (subscriptions != null && subscriptions.has("RustEx")) {
                        String expiryDate = subscriptions.optString("RustEx");
                        if (isSubscriptionActive(expiryDate)) {
                            hasRustExSubscription = true;
                        }
                    }
                    break;
                }
            }

            if (hasRustExSubscription) {
                String token = UUID.randomUUID().toString().replace("-", "");
                downloadTokens.put(token, new TokenInfo(userLogin, System.currentTimeMillis()));
                downloadTokens.entrySet().removeIf(entry ->
                        System.currentTimeMillis() - entry.getValue().createdAt > 5 * 60 * 1000
                );

                response.put("success", true);
                response.put("token", token);
            } else {
                response.put("success", false);
                response.put("error", "Требуется активная подписка RustEx для скачивания лаунчера.");
            }

            sendJson(ctx, response.toString());
        } catch (Exception e) {
            Config.log("Ошибка в handleDownloadLauncher: " + e);
            e.printStackTrace();
            sendJson(ctx, "{\"success\": false, \"error\": \"Внутренняя ошибка сервера\"}");
        }
    }

    static Map<String, Long> lastActivationTime = new HashMap<>();
    static long ACTIVATION_COOLDOWN_MS = 30_000; // 30 секунд

    public static void handleKeyActivation(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String ip = getClientIP(ctx);
            long now = System.currentTimeMillis();
            if (lastActivationTime.containsKey(ip) && now - lastActivationTime.get(ip) < ACTIVATION_COOLDOWN_MS) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Слишком частые запросы. Подождите 30 секунд.")
                        .toString());
                return;
            }

            String body = request.content().toString(StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(body);
            String fullKey = json.getString("key");
            String login = json.getString("login");
            String hwid = json.getString("hwid");

            if (!isValidCredential(login)) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Логин должен содержать только латинские буквы и цифры (3-12 символов).")
                        .toString());
                return;
            }

            String[] parts = fullKey.split("_", 3);
            if (parts.length != 3) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Неверный формат ключа")
                        .toString());
                return;
            }

            String subscriptionName = parts[0]; // "Remake"
            String subType = parts[1];          // "week"

            if (!keyFiles.containsKey(subType)) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Неверный тип подписки")
                        .toString());
                return;
            }

            String hashedKey = hashKey(fullKey);
            boolean keyFound = findAndRemoveKeyInType(hashedKey, subType);
            if (!keyFound) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Ключ не найден или уже использован")
                        .toString());
                return;
            }

            JSONObject user = userMap.get(login);
            if (user == null) {
                sendJson(ctx, new JSONObject()
                        .put("success", false)
                        .put("error", "Пользователь не найден")
                        .toString());
                return;
            }

            JSONObject subscriptions = user.optJSONObject("subscriptions");
            if (subscriptions == null) {
                subscriptions = new JSONObject();
                user.put("subscriptions", subscriptions);
            }

            String expiryDate = calculateExpiry(subType);
            subscriptions.put(subscriptionName, expiryDate);

            user.put("hwid", hwid);

            saveUsers();

            lastActivationTime.put(ip, now);

            Config.log("Activated key: " + keyFound + " date:" + expiryDate + " hwid:" + hwid + " login: " + login);

            sendJson(ctx, new JSONObject()
                    .put("success", true)
                    .put("subscription", subscriptionName)
                    .put("expiry", expiryDate)
                    .toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ctx, new JSONObject()
                    .put("success", false)
                    .put("error", "Ошибка сервера")
                    .toString());
        }
    }

    private static boolean findAndRemoveKeyInType(String hashedKey, String subType) {
        String filePath = keyFiles.get(subType);
        if (filePath == null) return false;

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return false;

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            boolean found = lines.removeIf(line -> line.trim().equalsIgnoreCase(hashedKey));
            if (found) {
                Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return found;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String calculateExpiry(String subType) {
        LocalDate now = LocalDate.now();
        switch (subType) {
            case "week":
                return now.plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "month":
                return now.plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "lifetime":
                return "2030-01-01";
            default:
                return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }

    private static String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found");
        }
    }
}
