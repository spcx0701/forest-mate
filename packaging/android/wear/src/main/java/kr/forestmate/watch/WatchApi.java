package kr.forestmate.watch;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

final class WatchApi {
    static final class ClaimResult {
        final String watchToken;
        final String hikeId;
        final String courseId;
        final String courseName;
        final Double courseKm;
        final Integer courseElev;
        final String route;

        ClaimResult(String watchToken, String hikeId, String courseId,
                    String courseName, Double courseKm, Integer courseElev, String route) {
            this.watchToken = watchToken;
            this.hikeId = hikeId;
            this.courseId = courseId;
            this.courseName = courseName;
            this.courseKm = courseKm;
            this.courseElev = courseElev;
            this.route = route;
        }
    }

    static final class UploadResult {
        final double progress;
        final int distressLevel;

        UploadResult(double progress, int distressLevel) {
            this.progress = progress;
            this.distressLevel = distressLevel;
        }
    }

    private WatchApi() {
    }

    static ClaimResult claim(String apiBase, String code) throws Exception {
        JSONObject body = new JSONObject();
        body.put("code", code);
        JSONObject json = request(normalizeBase(apiBase) + "/watch/pair/claim", body, null);
        return new ClaimResult(
                json.getString("watch_token"),
                nullableString(json, "hike_id"),
                nullableString(json, "course_id"),
                nullableString(json, "course_name"),
                nullableDouble(json, "course_km"),
                nullableInt(json, "course_elev"),
                nullableString(json, "route")
        );
    }

    static UploadResult upload(String apiBase, String token, Integer hr, Double lat, Double lon,
                               Integer alt, Integer acc, Integer battery) throws Exception {
        JSONObject body = new JSONObject();
        body.put("alt", alt == null ? 0 : alt);
        if (hr != null) body.put("hr", hr);
        if (lat != null) body.put("lat", lat);
        if (lon != null) body.put("lon", lon);
        if (acc != null) body.put("acc", acc);
        if (battery != null) body.put("battery", battery);
        JSONObject json = request(normalizeBase(apiBase) + "/watch/track", body, token);
        JSONObject distress = json.optJSONObject("distress");
        return new UploadResult(
                json.optDouble("progress", 0.0),
                distress == null ? 0 : distress.optInt("level", 0)
        );
    }

    private static JSONObject request(String url, JSONObject body, String bearerToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        if (bearerToken != null && bearerToken.length() > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"));
        writer.write(body.toString());
        writer.flush();
        writer.close();

        int status = conn.getResponseCode();
        String response = read(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " " + response);
        }
        if (response == null || response.trim().length() == 0) {
            return new JSONObject();
        }
        return new JSONObject(response);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }
        reader.close();
        return out.toString();
    }

    private static String normalizeBase(String apiBase) {
        String base = apiBase == null ? "" : apiBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String nullableString(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optString(key, "") : "";
    }

    private static Double nullableDouble(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optDouble(key) : null;
    }

    private static Integer nullableInt(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optInt(key) : null;
    }
}
