package invoke.looseone.util;

import com.google.gson.*;
import java.util.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class PacketSecurity {
    private static final Random random = new Random();
    private static final List<String> fakeKeys = Arrays.asList(
            "type_", "token0", "authLevel", "timestamp", "dataX", "dataHash",
            "userData", "meta_info", "system_code", "refID", "payloadDump",
            "session", "checksum", "signature", "version", "flags",
            "nonce", "iv", "salt", "tag", "mac", "crypto", "hash"
    );

    private static final byte[][] XOR_KEYS = {
            {0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F, 0x70, (byte)0x81},
            {(byte)0x92, (byte)0xA3, (byte)0xB4, (byte)0xC5, (byte)0xD6, (byte)0xE7, (byte)0xF8, 0x09},
            {0x24, 0x35, 0x46, 0x57, 0x68, 0x79, (byte)0x8A, (byte)0x9B},
            {(byte)0xAC, (byte)0xBD, (byte)0xCE, (byte)0xDF, (byte)0xE0, (byte)0xF1, 0x02, 0x13}
    };

    public static String obfuscate(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return json;

            JsonObject original = el.getAsJsonObject();
            JsonObject obfuscated = new JsonObject();

            addJunkFields(obfuscated, 20, 5);

            for (Map.Entry<String, JsonElement> entry : original.entrySet()) {
                obfuscated.add(entry.getKey(), entry.getValue());
            }

            addJunkFields(obfuscated, 15, 2);
            addChecksumFields(obfuscated);

            String obfuscatedJson = obfuscated.toString();
            return shuffleJsonStructure(obfuscatedJson);
        } catch (Exception e) {
            return json;
        }
    }

    private static void addJunkFields(JsonObject obj, int count, int depth) {
        for (int i = 0; i < count; i++) {
            String key = generateFakeKey();

            switch (random.nextInt(10)) {
                case 0:
                    obj.addProperty(key, generateXorEncodedValue(UUID.randomUUID().toString()));
                    break;
                case 1:
                    obj.addProperty(key, generateXorEncodedValue(String.valueOf(System.currentTimeMillis() + random.nextInt(99999))));
                    break;
                case 2:
                    obj.addProperty(key, generateXorEncodedValue(String.valueOf(random.nextBoolean())));
                    break;
                case 3:
                    obj.addProperty(key, generateXorEncodedValue("0x" + Long.toHexString(random.nextLong())));
                    break;
                case 4:
                    obj.addProperty(key, generateXorEncodedValue(Base64.getEncoder().encodeToString(
                            ("auth_" + random.nextInt(100000)).getBytes())));
                    break;
                case 5:
                    obj.add(key, makeNestedJunk(depth - 1));
                    break;
                case 6:
                    obj.addProperty(key, generateXorEncodedValue(generateRandomHex(16)));
                    break;
                case 7:
                    obj.addProperty(key, generateXorEncodedValue(generateRandomBase64(20)));
                    break;
                case 8:
                    obj.addProperty(key, generateXorEncodedValue(generateRandomUUID()));
                    break;
                default:
                    obj.addProperty(key, generateXorEncodedValue(generateRandomString(15 + random.nextInt(25))));
            }
        }
    }

    private static JsonElement makeNestedJunk(int depth) {
        if (depth <= 0) {
            return new JsonPrimitive(generateXorEncodedValue("secure_packet_" + random.nextInt(100000)));
        }

        if (random.nextBoolean()) {
            JsonArray arr = new JsonArray();
            int items = random.nextInt(6) + 1;
            for (int i = 0; i < items; i++) {
                if (random.nextBoolean()) {
                    arr.add(makeNestedJunk(depth - 1));
                } else {
                    arr.add(new JsonPrimitive(generateXorEncodedValue(generateRandomString(8 + random.nextInt(18)))));
                }
            }
            return arr;
        } else {
            JsonObject nested = new JsonObject();
            addJunkFields(nested, 5 + random.nextInt(6), depth - 1);
            return nested;
        }
    }

    private static String generateFakeKey() {
        String baseKey = fakeKeys.get(random.nextInt(fakeKeys.size()));
        return baseKey + "_" + random.nextInt(10000) + "_" + Long.toHexString(random.nextLong());
    }

    private static boolean isFakeKey(String key) {
        for (String fakeKey : fakeKeys) {
            if (key.startsWith(fakeKey + "_")) {
                return true;
            }
        }
        return false;
    }

    private static String generateXorEncodedValue(String value) {
        byte[] key = XOR_KEYS[random.nextInt(XOR_KEYS.length)];

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= key[i % key.length];
            }
            shuffleBytes(bytes);
        }

        return Base64.getEncoder().encodeToString(bytes) + "|ë̸̻̪̭͖̹͎͖̜̊͑ͨͫ̂͗̈́̀͂̄͛̌́̕͟ñ̶͇͎̦͚̈̑ͨ̋̉ͮ̌̉̂̕͟c̵̷͎̤͇̰̟̺̹̤͕͍ͬ̏ͣ̎͐ͭ́̌͡r̴̨̙͇̞͚̜̣̺̺̥̞͓̺͖̦̩ͯ̈́͒͋̑̆́̓ͤͨ͗̒̀̀y̛̳̙̜͙̳̥͍̞͇͚̻͐̀͗ͨ̃̓͛̒͗̊̑̇̔͒̌͒̀̚͡͝p̡͕̹͇̹̼̻̟̋̒̄͋ͥ̐ͯ͛ͮͩͪ́̕͡t̡̹̣̩̖͕̣̋ͭͧ̔̅̎͌͒ͦ̏̈́͟͠e̟̝̩̥̳̠͙̼̖̪̤̖̝̭͑͗̑͋̔̑̕͡d̵̢͕͙̘̲̻̺͎̖̠̺̤͎͓̜̳ͩͨ̀̓̄̂̓̉ͪ͋̅ͤͅ" + random.nextInt(10);
    }

    private static void shuffleBytes(byte[] bytes) {
        for (int i = bytes.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            byte temp = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = temp;
        }
    }

    private static void addChecksumFields(JsonObject obj) {
        try {
            String data = obj.toString();
            obj.addProperty("checksum_crc32", crc32(data));
            obj.addProperty("checksum_length", data.length());
        } catch (Exception e) {
        }
    }

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String generateRandomHex(int length) {
        String chars = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String generateRandomBase64(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String generateRandomUUID() {
        return UUID.randomUUID().toString();
    }

    private static String shuffleJsonStructure(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonObject shuffled = new JsonObject();
        List<String> keys = new ArrayList<>(obj.keySet());
        Collections.shuffle(keys);

        for (String key : keys) {
            shuffled.add(key, obj.get(key));
        }

        return shuffled.toString();
    }

    private static String crc32(String input) {
        try {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(input.getBytes(StandardCharsets.UTF_8));
            return Long.toHexString(crc.getValue());
        } catch (Exception e) {
            return "";
        }
    }
}
