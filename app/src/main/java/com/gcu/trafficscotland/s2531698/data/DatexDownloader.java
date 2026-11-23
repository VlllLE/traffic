package com.gcu.trafficscotland.s2531698.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Downloads one DATEX publication into a String.
 * The Authorization header is the coursework form: Base64 of clientId:clientKey,
 * with no "Basic " prefix, matching the supplied skeleton.
 */
public class DatexDownloader {
    public static final String UNPLANNED_URL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml";
    public static final String ROADWORKS_URL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/CurrentRoadworks/Content.xml";

    public String download(String url, String clientId, String clientKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/xml, text/xml, */*");
        String token = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientKey).getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", token);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            String body = read(connection.getInputStream());
            int declaration = body.indexOf("<?");
            if (declaration > 0) {
                body = body.substring(declaration);
            }
            String head = body.length() > 200 ? body.substring(0, 200).toLowerCase() : body.toLowerCase();
            if (head.contains("<html") || head.contains("<!doctype html")) {
                throw new IOException("DATEX returned a login page. Check the client id and key.");
            }
            if (!body.contains("<")) {
                throw new IOException("DATEX response was empty");
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
