package invoke.looseone.util;

public class MathUtil {
    public static boolean isValidInput(String input) {
        return input != null &&
                input.length() <= 64 &&
                input.matches("^[\\w\\-]+$");
    }

    public static boolean isValidCredential(String input) {
        return input != null && input.matches("^[a-zA-Z0-9]{3,12}$");
    }

    public static byte[] xorDecode(byte[] data, byte key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return result;
    }

    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
