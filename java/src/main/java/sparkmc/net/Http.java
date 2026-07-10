package sparkmc.net;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class Http {
    private static final Gson GSON = new Gson();
    private static final String UA =
            "sparkmc/0.1.0 (https://github.com/T1REI/rHn9DyUOHrVi1Sc54dhG)";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {}

    public static <T> T getJson(String url, Class<T> type) throws IOException {
        String body = getString(url);
        return GSON.fromJson(body, type);
    }

    public static <T> T getJson(String url, Type type) throws IOException {
        String body = getString(url);
        return GSON.fromJson(body, type);
    }

    public static String getString(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IOException("request failed (" + url + "): status code " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static long download(String url, Path path) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                throw new IOException("download failed (" + url + "): status code " + resp.statusCode());
            }
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(path)) {
                return in.transferTo(out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static Type listOf(Class<?> element) {
        return TypeToken.getParameterized(java.util.List.class, element).getType();
    }

    public static Type mapOf(Class<?> key, Class<?> value) {
        return TypeToken.getParameterized(java.util.Map.class, key, value).getType();
    }
}
