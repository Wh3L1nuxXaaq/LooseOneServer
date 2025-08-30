package invoke.looseone.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Config {

    public static String adminka = "wh3loader";

    public static String uidFile = "Files/UIDs.txt";
    public static String dbFile =  "Files/Users.json";

    public static boolean debug = true;
    public static boolean logInFile = true;

    public static byte[] XOR_KEYS_OUTER = { 0x42, 0x6F, 0x11 };
    public static byte[] XOR_KEYS_INNER = { 0x1A, 0x2B, 0x3C };

    public static int port;

    public static void log(String msg) {
        if (debug == true) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            System.out.println("[" + time + "] " + msg);

            if (logInFile) {
                logToFile(msg);
            }
        }
    }

    private static void logToFile(String msg) {
        try (FileWriter writer = new FileWriter("Files/Logs.txt", true)) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            writer.write("[" + time + "] " + msg + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static {
        String portProp = System.getProperty("port");
        String portEnv = System.getenv("PORT");
        if (portProp != null) {
            port = Integer.parseInt(portProp);
        } else if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        } else {
            port = 5000;
        }
    }
}
