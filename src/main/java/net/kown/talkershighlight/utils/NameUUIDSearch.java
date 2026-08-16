package net.kown.talkershighlight.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class NameUUIDSearch {

    public static String id(UUID uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            connection.setConnectTimeout(3000); // give up after 3s connecting
            connection.setReadTimeout(3000);    // give up after 3s waiting on response

            if (connection.getResponseCode() != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

            if(json.get("name")!=null){
                return json.get("name").getAsString();
            }
                return uuid.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}