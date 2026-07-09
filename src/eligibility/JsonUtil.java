package eligibility;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal JSON helpers. We avoid pulling in Jackson/Gson so the project
 * builds with nothing but a JDK - no Maven/network access required.
 */
public class JsonUtil {

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "");
    }

    /** Very small flat-object JSON parser: {"key":"value","key2":123} */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> map = new HashMap<>();
        String body = json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);

        int i = 0;
        int n = body.length();
        while (i < n) {
            // skip whitespace/commas
            while (i < n && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ',')) i++;
            if (i >= n) break;
            // parse key (quoted)
            if (body.charAt(i) != '"') break;
            int keyStart = ++i;
            while (i < n && body.charAt(i) != '"') i++;
            String key = body.substring(keyStart, i);
            i++; // skip closing quote
            while (i < n && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ':')) i++;
            String value;
            if (i < n && body.charAt(i) == '"') {
                int valStart = ++i;
                StringBuilder sb = new StringBuilder();
                while (i < n && body.charAt(i) != '"') {
                    if (body.charAt(i) == '\\' && i + 1 < n) {
                        i++;
                    }
                    sb.append(body.charAt(i));
                    i++;
                }
                value = sb.toString();
                i++; // skip closing quote
            } else {
                int valStart = i;
                while (i < n && body.charAt(i) != ',' && body.charAt(i) != '}') i++;
                value = body.substring(valStart, i).trim();
            }
            map.put(key, value);
        }
        return map;
    }
}
