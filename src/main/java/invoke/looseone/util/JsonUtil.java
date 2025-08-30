package invoke.looseone.util;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class JsonUtil {
    public static void sendJson(ChannelHandlerContext ctx, String json) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                OK,
                Unpooled.copiedBuffer(json, java.nio.charset.StandardCharsets.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static String unpackJson(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            System.out.println("[ERROR] unpackJson: air data");
            return "{}";
        }
        String cleanedEncoded = encoded;
        if (cleanedEncoded.startsWith("x") && cleanedEncoded.endsWith("x")) {
            cleanedEncoded = cleanedEncoded.substring(1, cleanedEncoded.length() - 1);
        } else {
            System.out.println("[WARN] unpackJson: x symbol fail find");
        }

        try {
            byte[] obfuscated = Base64.getDecoder().decode(cleanedEncoded);
            for (int i = 0; i < obfuscated.length; i++) {
                obfuscated[i] ^= Config.XOR_KEYS_OUTER[i % Config.XOR_KEYS_OUTER.length];
            }
            String wrapperJson = new String(obfuscated, StandardCharsets.UTF_8);

            JSONObject wrapper = new JSONObject(wrapperJson);
            String encryptedData = wrapper.optString("_data", "");
            if (encryptedData.isEmpty()) {
                System.out.println("[ERROR] unpackJson: _data unpack fail");
                return "{}";
            }

            byte[] dataBytes = Base64.getDecoder().decode(encryptedData);
            for (int i = 0; i < dataBytes.length; i++) {
                dataBytes[i] ^= Config.XOR_KEYS_INNER[i % Config.XOR_KEYS_INNER.length];
            }
            String dataJson = new String(dataBytes, StandardCharsets.UTF_8);
            JSONObject transformed = new JSONObject(dataJson);
            JSONObject result = new JSONObject();
            if (transformed.has("s")) {
                JSONObject subs = transformed.getJSONObject("s");
                JSONObject newSubs = new JSONObject();
                if (subs.has("r")) newSubs.put("RustEx", subs.getString("r"));
                if (subs.has("P")) newSubs.put("Premium", subs.getString("P"));
                if (subs.has("Re")) newSubs.put("Remake", subs.getString("Re"));
                result.put("subscriptions", newSubs);
            }
            for (Object keyObj : transformed.keySet()) {
                String key = keyObj.toString();
                if (!key.equals("s")) {
                    result.put(key.equals("h") ? "hwid" : key, transformed.get(key));
                }
            }
            return result.toString();
        } catch (Exception e) {
            System.out.println("[ERROR] unpackJson: Error: " + e.getMessage());
            return "{}";
        }
    }

    public static String packJson(String json) {
        if (json == null || json.isEmpty()) {
            System.out.println("[ERROR] packJson: air JSON");
            return "";
        }
        JSONObject original = new JSONObject(json);
        JSONObject transformed = new JSONObject();
        if (original.has("subscriptions")) {
            JSONObject subs = original.getJSONObject("subscriptions");
            JSONObject newSubs = new JSONObject();
            if (subs.has("RustEx")) newSubs.put("r", subs.getString("RustEx"));
            if (subs.has("Premium")) newSubs.put("P", subs.getString("Premium"));
            if (subs.has("Remake")) newSubs.put("Re", subs.getString("Remake"));
            transformed.put("s", newSubs);
        }
        for (Object keyObj : original.keySet()) {
            String key = keyObj.toString();
            if (!key.equals("subscriptions")) {
                transformed.put(key.equals("hwid") ? "h" : key, original.get(key));
            }
        }
        byte[] dataBytes = transformed.toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < dataBytes.length; i++) {
            dataBytes[i] ^= Config.XOR_KEYS_INNER[i % Config.XOR_KEYS_INNER.length];
        }
        String encryptedData = Base64.getEncoder().encodeToString(dataBytes);
        JSONObject wrapper = new JSONObject();
        wrapper.put("payload", UUID.randomUUID().toString());
        wrapper.put("noise1", MathUtil.generateRandomString(16));
        wrapper.put("dump1", Base64.getEncoder().encodeToString(("noise:" + Math.random()).getBytes()));
        wrapper.put("_data", encryptedData);
        wrapper.put("token", UUID.randomUUID().toString().replace("-", ""));
        wrapper.put("noise2", MathUtil.generateRandomString(20));
        wrapper.put("rotantebeebal", MathUtil.generateRandomString(12));
        wrapper.put("dump2", Base64.getEncoder().encodeToString(("random:" + Math.random()).getBytes()));

        String wrapperJson = wrapper.toString();
        byte[] bytes = wrapperJson.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= Config.XOR_KEYS_OUTER[i % Config.XOR_KEYS_OUTER.length];
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String finalPacket = "x" + encoded + "x";
        return finalPacket;
    }
}
