package com.sayantika.eara;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeSearchHelper {
    private static final String TAG = "YouTubeSearch";
    private static final String DEFAULT_QUERY = "new hindi dubbed full movie hollywood bollywood south all movie";
    private static final String DEFAULT_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String DEFAULT_CLIENT_VER = "2.20240401.00.00";
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    // Curated high quality blockbuster fallback movies (used only when completely offline)
    private static final String[][] CURATED_MOVIES = new String[][] {
        {"tsh7sicm4n0", "New South Action SILENT KILLER 2024 Hindi Dubbed Movie Full 4K | Vishnu Vishal, Reba Monica", "RDC Multiplex Official", "2:07:12", "622K views", "2d ago", "https://i.ytimg.com/vi/tsh7sicm4n0/hqdefault.jpg"},
        {"R7aCOI4DuA0", "A Aa Hindi Dubbed Full Movie New | Nithiin, Samantha, Anupama Parameshwaran | Trivikram", "Aditya Movies", "2:15:30", "124M views", "Super Hit", "https://i.ytimg.com/vi/R7aCOI4DuA0/hqdefault.jpg"},
        {"0NIKDmyV99Y", "BRIGADIER Full Movie Hindi Dubbed | Vijay Deverakonda | Sreeleela | Latest South Indian Action Movie", "Movie Forever", "2:18:45", "32M views", "Blockbuster", "https://i.ytimg.com/vi/0NIKDmyV99Y/hqdefault.jpg"},
        {"2lUcxOl055Q", "DJ - Duvvada Jagannadham (4K Ultra HD) | Allu Arjun, Pooja Hegde Action Hindi Dubbed", "Goldmines Dishoom", "2:32:45", "340M views", "Mega Blockbuster", "https://i.ytimg.com/vi/2lUcxOl055Q/hqdefault.jpg"},
        {"ULEQb_l-N08", "K.G.F Full Movie Hindi Dubbed | Yash, Srinidhi Shetty, Ananth Nag, Ramachandra Raju", "Goldmines", "2:36:10", "410M views", "All Time Blockbuster", "https://i.ytimg.com/vi/ULEQb_l-N08/hqdefault.jpg"},
        {"hiv5X9KZNXI", "Sarrainodu (4K) | Allu Arjun, Rakul Preet Singh, Catherine Tresa Action Blockbuster Full Movie", "Goldmines", "2:38:15", "495M views", "Mega Blockbuster", "https://i.ytimg.com/vi/hiv5X9KZNXI/hqdefault.jpg"},
        {"oqcXT3tkZD0", "Macharla Chunaav Kshetra (M.C.K) New Released Full Hindi Dubbed Movie | Nithiin, Krithi Shetty", "Aditya Movies", "2:22:10", "48M views", "Trending", "https://i.ytimg.com/vi/oqcXT3tkZD0/hqdefault.jpg"},
        {"m2cRK2_oaZ0", "Vaathi (2023) Hindi Dubbed Full Movie | Starring Dhanush, Samyuktha Menon", "RG Entertainment", "2:03:15", "54M views", "Super Hit", "https://i.ytimg.com/vi/m2cRK2_oaZ0/hqdefault.jpg"},
        {"UnlUm6B0djY", "New Release South Action ANTONY 2023 Hindi Dubbed Movie 4K | Joju George, Kalyani Priyadarshan", "Ultra 4K Movies", "2:08:30", "28M views", "Action Hit", "https://i.ytimg.com/vi/UnlUm6B0djY/hqdefault.jpg"},
        {"AeZOz-6TIzY", "New Release South Action KARTHIKEYA 2 Hindi Dubbed Movie 4K | Nikhil Siddhartha, Anupama", "Ultra 4K Movies", "2:12:20", "110M views", "Pan India Hit", "https://i.ytimg.com/vi/AeZOz-6TIzY/hqdefault.jpg"},
        {"ngElkyQ6Rhs", "RRR (Hindi) Full Movie 4K Ultra HD | NTR, Ram Charan, Ajay Devgn, Alia Bhatt", "Pen Movies", "3:01:40", "210M views", "Oscar Winner", "https://i.ytimg.com/vi/ngElkyQ6Rhs/hqdefault.jpg"},
        {"vqu4z34wENw", "LEO (Hindi Dubbed) Full Action Movie | Thalapathy Vijay, Sanjay Dutt, Lokesh Kanagaraj", "Seven Screen Studio", "2:44:20", "78M views", "Super Hit", "https://i.ytimg.com/vi/vqu4z34wENw/hqdefault.jpg"},
        {"Kc5aIIIsmqg", "Vijay Sethupathi AAKHRI CHAAL (Action Blockbuster) Hindi Dubbed Full Movie | Arvind Swami", "Action Movies Digiplex", "2:10:40", "18M views", "Action Thriller", "https://i.ytimg.com/vi/Kc5aIIIsmqg/hqdefault.jpg"},
        {"VDFys9V9poQ", "Extra Ordinary Man Hindi Dubbed Full Movie 2025 | Nithiin, Sreeleela, Rajasekhar", "Aditya Movies", "2:16:50", "38M views", "Comedy Action", "https://i.ytimg.com/vi/VDFys9V9poQ/hqdefault.jpg"},
        {"vkuiI430d_0", "The Super Khiladi 3 (Nenu Sailaja) Hindi Dubbed Full Movie | Ram Pothineni, Keerthy Suresh", "Goldmines", "2:14:25", "280M views", "Super Hit", "https://i.ytimg.com/vi/vkuiI430d_0/hqdefault.jpg"},
        {"Xojf144alBo", "Uppena (Hindi) New Released Hindi Dubbed Full Movie | Panja Vaisshnav Tej, Vijay Sethupathi", "Goldmines", "2:24:18", "95M views", "Blockbuster", "https://i.ytimg.com/vi/Xojf144alBo/hqdefault.jpg"},
        {"1odS6ynbWNo", "World Famous Lover New Released Hindi Dubbed Movie | Vijay Deverakonda, Raashi Khanna", "Goldmines", "2:17:50", "165M views", "Super Hit", "https://i.ytimg.com/vi/1odS6ynbWNo/hqdefault.jpg"}
    };

    /**
     * Searches YouTube for full movies using official Innertube API via POST first.
     * If POST fails, gracefully falls back to desktop web search.
     */
    public static String search(String query) {
        String searchQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : DEFAULT_QUERY;

        // METHOD 1: Direct YouTube Innertube API (Zero redirects, fast JSON response, never blocked)
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/search?key=" + DEFAULT_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            JSONObject body = new JSONObject();
            JSONObject ctx = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "WEB");
            client.put("clientVersion", DEFAULT_CLIENT_VER);
            ctx.put("client", client);
            body.put("context", ctx);
            body.put("query", searchQuery);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                JSONArray videosArray = new JSONArray();
                Set<String> seenIds = new HashSet<>();
                extractVideos(resp, videosArray, seenIds);

                if (videosArray.length() > 0) {
                    String token = extractContinuationToken(resp);

                    JSONObject out = new JSONObject();
                    out.put("success", true);
                    out.put("query", searchQuery);
                    out.put("videos", videosArray);
                    if (token != null) out.put("continuationToken", token);
                    out.put("apiKey", DEFAULT_API_KEY);
                    out.put("clientVersion", DEFAULT_CLIENT_VER);

                    return out.toString();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Innertube search exception: " + e.getMessage());
        }

        // METHOD 2: Desktop HTML Search fallback
        try {
            String encoded = URLEncoder.encode(searchQuery, "UTF-8");
            URL url = new URL("https://www.youtube.com/results?search_query=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,hi;q=0.8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();
                String html = sb.toString();

                JSONObject initialData = parseYtInitialData(html);
                if (initialData != null) {
                    JSONArray videosArray = new JSONArray();
                    Set<String> seenIds = new HashSet<>();
                    extractVideos(initialData, videosArray, seenIds);

                    if (videosArray.length() > 0) {
                        String token = extractContinuationToken(initialData);

                        String apiKey = DEFAULT_API_KEY;
                        Matcher keyMatcher = Pattern.compile("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").matcher(html);
                        if (keyMatcher.find()) {
                            apiKey = keyMatcher.group(1);
                        }

                        String clientVer = DEFAULT_CLIENT_VER;
                        Matcher verMatcher = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").matcher(html);
                        if (verMatcher.find()) {
                            clientVer = verMatcher.group(1);
                        }

                        JSONObject out = new JSONObject();
                        out.put("success", true);
                        out.put("query", searchQuery);
                        out.put("videos", videosArray);
                        if (token != null) out.put("continuationToken", token);
                        out.put("apiKey", apiKey);
                        out.put("clientVersion", clientVer);

                        return out.toString();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "HTML search fallback exception: " + e.getMessage());
        }

        // METHOD 3: Offline fallback only
        return buildCuratedFallback(searchQuery);
    }

    /**
     * Loads subsequent batches for Infinite Scroll using YouTube continuation tokens.
     */
    public static String loadMore(String token, String apiKey, String clientVersion) {
        if (token == null || token.isEmpty()) {
            return "{\"success\":false,\"videos\":[]}";
        }
        try {
            String key = (apiKey != null && !apiKey.isEmpty()) ? apiKey : DEFAULT_API_KEY;
            String ver = (clientVersion != null && !clientVersion.isEmpty()) ? clientVersion : DEFAULT_CLIENT_VER;

            URL url = new URL("https://www.youtube.com/youtubei/v1/search?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            JSONObject body = new JSONObject();
            JSONObject ctx = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "WEB");
            client.put("clientVersion", ver);
            ctx.put("client", client);
            body.put("context", ctx);
            body.put("continuation", token);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                JSONArray videosArray = new JSONArray();
                Set<String> seenIds = new HashSet<>();
                extractVideos(resp, videosArray, seenIds);

                String nextToken = extractContinuationToken(resp);

                JSONObject out = new JSONObject();
                out.put("success", true);
                out.put("videos", videosArray);
                if (nextToken != null) out.put("nextToken", nextToken);
                return out.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Load more exception: " + e.getMessage());
        }

        return "{\"success\":false,\"videos\":[]}";
    }

    private static JSONObject parseYtInitialData(String html) {
        if (html == null) return null;
        int pos = html.indexOf("ytInitialData");
        while (pos != -1) {
            int startBrace = html.indexOf('{', pos);
            if (startBrace != -1 && startBrace - pos < 60) {
                int depth = 0;
                boolean inString = false;
                boolean escape = false;

                for (int i = startBrace; i < html.length(); i++) {
                    char c = html.charAt(i);
                    if (escape) {
                        escape = false;
                        continue;
                    }
                    if (c == '\\') {
                        escape = true;
                        continue;
                    }
                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }
                    if (!inString) {
                        if (c == '{') depth++;
                        else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                String jsonStr = html.substring(startBrace, i + 1);
                                try {
                                    return new JSONObject(jsonStr);
                                } catch (Exception ignored) {
                                }
                                break;
                            }
                        }
                    }
                }
            }
            pos = html.indexOf("ytInitialData", pos + 13);
        }
        return null;
    }

    private static void extractVideos(Object obj, JSONArray out, Set<String> seenIds) {
        if (obj == null) return;
        if (obj instanceof JSONObject) {
            JSONObject jObj = (JSONObject) obj;
            if (jObj.has("videoRenderer")) {
                try {
                    JSONObject vr = jObj.getJSONObject("videoRenderer");
                    String vidId = vr.optString("videoId", "");
                    if (!vidId.isEmpty() && !seenIds.contains(vidId)) {
                        seenIds.add(vidId);

                        String title = "";
                        if (vr.has("title")) {
                            JSONObject tObj = vr.getJSONObject("title");
                            if (tObj.has("runs")) {
                                JSONArray runs = tObj.getJSONArray("runs");
                                if (runs.length() > 0) title = runs.getJSONObject(0).optString("text", "");
                            } else {
                                title = tObj.optString("simpleText", "");
                            }
                        }

                        String channel = "YouTube Channel";
                        if (vr.has("ownerText")) {
                            JSONObject ot = vr.getJSONObject("ownerText");
                            if (ot.has("runs")) {
                                JSONArray runs = ot.getJSONArray("runs");
                                if (runs.length() > 0) channel = runs.getJSONObject(0).optString("text", channel);
                            }
                        } else if (vr.has("shortBylineText")) {
                            JSONObject sbt = vr.getJSONObject("shortBylineText");
                            if (sbt.has("runs")) {
                                JSONArray runs = sbt.getJSONArray("runs");
                                if (runs.length() > 0) channel = runs.getJSONObject(0).optString("text", channel);
                            }
                        }

                        String duration = vr.optJSONObject("lengthText") != null
                                ? vr.optJSONObject("lengthText").optString("simpleText", "Full Movie")
                                : "Full Movie";

                        String views = vr.optJSONObject("viewCountText") != null
                                ? vr.optJSONObject("viewCountText").optString("simpleText", "")
                                : (vr.optJSONObject("shortViewCountText") != null
                                ? vr.optJSONObject("shortViewCountText").optString("simpleText", "")
                                : "");

                        String published = vr.optJSONObject("publishedTimeText") != null
                                ? vr.optJSONObject("publishedTimeText").optString("simpleText", "")
                                : "";

                        String thumb = "https://i.ytimg.com/vi/" + vidId + "/hqdefault.jpg";
                        JSONObject thumbObj = vr.optJSONObject("thumbnail");
                        if (thumbObj != null) {
                            JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
                            if (thumbs != null && thumbs.length() > 0) {
                                thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url", thumb);
                            }
                        }

                        JSONObject item = new JSONObject();
                        item.put("id", vidId);
                        item.put("title", title);
                        item.put("channel", channel);
                        item.put("duration", duration);
                        item.put("views", views);
                        item.put("published", published);
                        item.put("thumbnail", thumb);

                        out.put(item);
                    }
                } catch (Exception ignored) {
                }
                return;
            }

            Iterator<String> keys = jObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                extractVideos(jObj.opt(k), out, seenIds);
            }
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                extractVideos(arr.opt(i), out, seenIds);
            }
        }
    }

    private static String extractContinuationToken(Object obj) {
        if (obj == null) return null;
        if (obj instanceof JSONObject) {
            JSONObject jObj = (JSONObject) obj;
            if (jObj.has("continuationCommand")) {
                JSONObject cc = jObj.optJSONObject("continuationCommand");
                if (cc != null && cc.has("token")) {
                    return cc.optString("token", null);
                }
            }
            Iterator<String> keys = jObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String res = extractContinuationToken(jObj.opt(k));
                if (res != null) return res;
            }
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                String res = extractContinuationToken(arr.opt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    private static String buildCuratedFallback(String query) {
        try {
            String qClean = query.toLowerCase();
            String[] words = qClean.split("\\s+");
            JSONArray arr = new JSONArray();

            for (String[] m : CURATED_MOVIES) {
                String t = (m[1] + " " + m[2]).toLowerCase();
                boolean matches = false;
                for (String w : words) {
                    if (w.length() > 2 && t.contains(w)) {
                        matches = true;
                        break;
                    }
                }
                if (matches || words.length == 0 || query.equals(DEFAULT_QUERY)) {
                    JSONObject item = new JSONObject();
                    item.put("id", m[0]);
                    item.put("title", m[1]);
                    item.put("channel", m[2]);
                    item.put("duration", m[3]);
                    item.put("views", m[4]);
                    item.put("published", m[5]);
                    item.put("thumbnail", m[6]);
                    arr.put(item);
                }
            }

            if (arr.length() == 0) {
                for (String[] m : CURATED_MOVIES) {
                    JSONObject item = new JSONObject();
                    item.put("id", m[0]);
                    item.put("title", m[1]);
                    item.put("channel", m[2]);
                    item.put("duration", m[3]);
                    item.put("views", m[4]);
                    item.put("published", m[5]);
                    item.put("thumbnail", m[6]);
                    arr.put(item);
                }
            }

            JSONObject out = new JSONObject();
            out.put("success", true);
            out.put("query", query);
            out.put("videos", arr);
            return out.toString();
        } catch (Exception e) {
            return "{\"success\":true,\"videos\":[]}";
        }
    }
}
