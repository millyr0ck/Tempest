package rip.tempest.interfaces.menu.alt.microsoft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class MicrosoftLogin {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";

    private static final String CODE_PAGE = "https://login.live.com/oauth20_remoteconnect.srf?otc=";

    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "login.live.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "api.minecraftservices.com"));

    private MicrosoftLogin() {
    }

    public static final class DeviceCode {
        public final String userCode, signInPage;
        private final String deviceCode;
        private final long expiresAt, interval;

        private DeviceCode(String userCode, String signInPage, String deviceCode, long expiresAt, long interval) {
            this.userCode = userCode;
            this.signInPage = signInPage;
            this.deviceCode = deviceCode;
            this.expiresAt = expiresAt;
            this.interval = interval;
        }
    }

    public static final class Account {
        public final String name, uuid, accessToken, refreshToken;

        private Account(String name, String uuid, String accessToken, String refreshToken) {
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static final class LoginException extends Exception {
        public LoginException(String message) {
            super(message);
        }
    }

    public static DeviceCode requestCode() throws LoginException {
        Response response = post("https://login.live.com/oauth20_connect.srf",
                form("client_id", CLIENT_ID, "scope", SCOPE, "response_type", "device_code"));
        String userCode = string(response.json, "user_code"), deviceCode = string(response.json, "device_code");
        if (!response.ok() || userCode == null || deviceCode == null) {
            throw new LoginException("Microsoft didn't give a code (" + response.describe() + ")");
        }
        String page = CODE_PAGE + form("", userCode).substring(1);
        long expiresIn = number(response.json, "expires_in", 900), interval = number(response.json, "interval", 5);
        return new DeviceCode(userCode, page, deviceCode,
                System.currentTimeMillis() + expiresIn * 1000L, Math.max(1, interval) * 1000L);
    }

    public static Account waitForSignIn(DeviceCode code, BooleanSupplier cancelled) throws LoginException {
        long interval = code.interval;
        while (System.currentTimeMillis() < code.expiresAt) {
            long wakeAt = System.currentTimeMillis() + interval;
            while (System.currentTimeMillis() < wakeAt) {
                if (cancelled.getAsBoolean()) throw new LoginException("Sign-in cancelled");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LoginException("Sign-in cancelled");
                }
            }

            Response response = post(TOKEN_URL, form("client_id", CLIENT_ID,
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code", "device_code", code.deviceCode));
            if (response.ok()) return minecraft(response.json);

            String error = string(response.json, "error");
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) {
                interval += 5000;
                continue;
            }
            if ("authorization_declined".equals(error)) throw new LoginException("The sign-in was declined");
            if ("expired_token".equals(error)) break;
            throw new LoginException("Microsoft sign-in failed (" + response.describe() + ")");
        }
        throw new LoginException("The code ran out, press microsoft to get a new one");
    }

    public static Account refresh(String refreshToken) throws LoginException {
        Response response = post(TOKEN_URL, form("client_id", CLIENT_ID, "scope", SCOPE,
                "grant_type", "refresh_token", "refresh_token", refreshToken));
        if (!response.ok()) {
            if ("invalid_grant".equals(string(response.json, "error"))) {
                throw new LoginException("The saved login has expired, sign in again");
            }
            throw new LoginException("Microsoft refused the saved login (" + response.describe() + ")");
        }
        return minecraft(response.json);
    }

    private static Account minecraft(JsonObject microsoft) throws LoginException {
        String msAccess = string(microsoft, "access_token"), msRefresh = string(microsoft, "refresh_token");
        if (msAccess == null) throw new LoginException("Microsoft didn't send a token");

        JsonObject userProperties = new JsonObject();
        userProperties.addProperty("AuthMethod", "RPS");
        userProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        userProperties.addProperty("RpsTicket", "t=" + msAccess);
        JsonObject userBody = new JsonObject();
        userBody.add("Properties", userProperties);
        userBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        userBody.addProperty("TokenType", "JWT");
        Response user = post("https://user.auth.xboxlive.com/user/authenticate", userBody);
        String userToken = string(user.json, "Token"), userHash = userHash(user.json);
        if (!user.ok() || userToken == null || userHash == null) {
            throw new LoginException("Xbox Live refused the sign-in (" + user.describe() + ")");
        }

        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(userToken);
        xstsProperties.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProperties);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        Response xsts = post("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        String xstsToken = string(xsts.json, "Token");
        if (!xsts.ok() || xstsToken == null) throw new LoginException(xstsError(xsts));

        String identity = "XBL3.0 x=" + userHash + ";" + xstsToken;
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("identityToken", identity);
        Response login = post("https://api.minecraftservices.com/authentication/login_with_xbox", loginBody);
        if (!login.ok() && login.status != 429) {
            JsonObject launcherBody = new JsonObject();
            launcherBody.addProperty("xtoken", identity);
            launcherBody.addProperty("platform", "PC_LAUNCHER");
            login = post("https://api.minecraftservices.com/launcher/login", launcherBody);
        }
        String mcToken = string(login.json, "access_token");
        if (login.status == 429) throw new LoginException("Minecraft is rate limiting logins, wait a minute");
        if (!login.ok() || mcToken == null) {
            throw new LoginException("Minecraft refused the Xbox login (" + login.describe() + ")");
        }

        Response profile = get("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (profile.status == 404) throw new LoginException("This account doesn't own Minecraft: Java Edition");
        String name = string(profile.json, "name"), id = string(profile.json, "id");
        if (!profile.ok() || name == null || id == null) {
            throw new LoginException("Couldn't read the Minecraft profile (" + profile.describe() + ")");
        }
        return new Account(name, id, mcToken, msRefresh);
    }

    private static String userHash(JsonObject json) {
        try {
            return json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                    .get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String xstsError(Response xsts) {
        long code = number(xsts.json, "XErr", 0);
        if (code == 2148916233L) return "This Microsoft account has no Xbox profile yet: sign in at minecraft.net once";
        if (code == 2148916235L) return "Xbox Live isn't available in your country";
        if (code == 2148916236L || code == 2148916237L) return "Xbox needs adult verification for this account";
        if (code == 2148916238L) return "Child account: an adult has to add it to a Microsoft family first";
        return "Xbox Live refused the Minecraft login (" + xsts.describe() + (code != 0 ? ", XErr " + code : "") + ")";
    }

    private static final class Response {
        final int status;
        final JsonObject json;

        Response(int status, JsonObject json) {
            this.status = status;
            this.json = json;
        }

        boolean ok() {
            return status / 100 == 2;
        }

        String describe() {
            String error = string(json, "error");
            return "HTTP " + status + (error != null ? ", " + error : "");
        }
    }

    private static String form(String... pairs) {
        StringBuilder out = new StringBuilder();
        try {
            for (int i = 0; i < pairs.length; i += 2) {
                if (out.length() > 0) out.append('&');
                out.append(URLEncoder.encode(pairs[i], "UTF-8")).append('=').append(URLEncoder.encode(pairs[i + 1], "UTF-8"));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    private static Response post(String url, String formBody) throws LoginException {
        return send(url, formBody, "application/x-www-form-urlencoded", null);
    }

    private static Response post(String url, JsonObject jsonBody) throws LoginException {
        return send(url, jsonBody.toString(), "application/json", null);
    }

    private static Response get(String url, String bearer) throws LoginException {
        return send(url, null, null, bearer);
    }

    private static Response send(String address, String body, String contentType, String bearer) throws LoginException {
        URL url;
        try {
            url = new URL(address);
        } catch (IOException e) {
            throw new LoginException("Bad address");
        }
        if (!"https".equals(url.getProtocol()) || !ALLOWED_HOSTS.contains(url.getHost())) {
            throw new LoginException("Refused to contact " + url.getHost());
        }

        try {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (url.getHost().endsWith("xboxlive.com")) connection.setRequestProperty("x-xbl-contract-version", "1");
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);

            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String text = in == null ? "" : read(in);
            connection.disconnect();
            return new Response(status, parse(text));
        } catch (IOException e) {
            throw new LoginException("Couldn't reach " + url.getHost() + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() > 1 << 20) throw new IOException("Response too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static long number(JsonObject json, String key, long fallback) {
        try {
            JsonElement value = json.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
