package com.fieldview.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppConfig {
    public final List<Camera> cameras = new ArrayList<>();
    public int visibleCount = 4;
    public boolean useMainStream = true;
    public boolean softwareFloatingH264;
    public boolean textureGrid;
    public String site = BuildConfig.TV_INTERFACE ? "wrenhaven" : "home", display = BuildConfig.TV_INTERFACE ? "tv" : "phone", broker = "", brokerUser = "", brokerPassword = "";
    public String clientId = "fieldview-" + UUID.randomUUID().toString();

    /** A prefilled draft only: the catalog is unchanged until the user saves it. */
    public Camera copyCameraDraft(int index) {
        Camera source = cameras.get(index);
        Camera draft = new Camera();
        draft.host = source.host; draft.port = source.port;
        draft.username = source.username; draft.password = source.password;
        draft.zone = source.zone; draft.subPath = source.subPath; draft.mainPath = source.mainPath;
        draft.enabled = source.enabled;
        String baseId = source.id.isEmpty() ? "camera" : source.id;
        for (int number = 1; ; number++) {
            String suffix = number == 1 ? "-copy" : "-copy-" + number;
            String candidate = baseId.substring(0, Math.min(baseId.length(), 60 - suffix.length())) + suffix;
            boolean used = false;
            for (Camera camera : cameras) if (camera.id.equals(candidate)) { used = true; break; }
            if (!used) { draft.id = candidate; break; }
        }
        String baseName = source.name.isEmpty() ? "Camera" : source.name;
        for (int number = 1; ; number++) {
            String candidate = baseName + (number == 1 ? " (copy)" : " (copy " + number + ")");
            boolean used = false;
            for (Camera camera : cameras) if (camera.name.equals(candidate)) { used = true; break; }
            if (!used) { draft.name = candidate; break; }
        }
        return draft;
    }

    public boolean moveCamera(int from, int to) {
        if (from < 0 || from >= cameras.size() || to < 0 || to >= cameras.size() || from == to) return false;
        Camera camera = cameras.remove(from); cameras.add(to, camera);
        return true;
    }

    public List<Camera> enabledCameras() {
        List<Camera> enabled = new ArrayList<>();
        for (Camera camera : cameras) if (camera.enabled) enabled.add(camera);
        return enabled;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray list = new JSONArray();
        for (Camera camera : cameras) list.put(camera.toJson());
        return object.put("cameras", list).put("visibleCount", visibleCount).put("useMainStream", useMainStream)
                .put("softwareFloatingH264", softwareFloatingH264)
                .put("textureGrid", textureGrid)
                .put("site", site).put("display", display).put("broker", broker).put("brokerUser", brokerUser)
                .put("brokerPassword", brokerPassword).put("clientId", clientId);
    }

    public static AppConfig fromJson(JSONObject object) throws JSONException {
        AppConfig config = new AppConfig();
        config.textureGrid = object.optBoolean("textureGrid", false);
        config.visibleCount = Math.max(1, Math.min(9, object.optInt("visibleCount", 4)));
        config.useMainStream = object.optBoolean("useMainStream", true);
        config.softwareFloatingH264 = object.optBoolean("softwareFloatingH264", false);
        config.site = object.optString("site", config.site);
        config.display = object.optString("display", config.display);
        config.broker = object.optString("broker", "");
        config.brokerUser = object.optString("brokerUser", "");
        config.brokerPassword = object.optString("brokerPassword", "");
        config.clientId = object.optString("clientId", config.clientId);
        JSONArray list = object.optJSONArray("cameras");
        if (list != null) for (int i = 0; i < list.length(); i++) config.cameras.add(Camera.fromJson(list.getJSONObject(i)));
        return config;
    }

    public static final class Camera {
        public boolean enabled = true;
        public String id = "", name = "", zone = "", host = "", username = "", password = "";
        public int port = 554;
        public String subPath = "/Preview_01_sub", mainPath = "/Preview_01_main";

        public String streamUri(boolean main) {
            String path = main && !mainPath.isEmpty() ? mainPath : subPath;
            if (!path.startsWith("/")) path = "/" + path;
            String authority = host + ":" + port;
            if (!username.isEmpty()) authority = Uri.encode(username) + ":" + Uri.encode(password) + "@" + authority;
            return Uri.parse("rtsp://" + host + ":" + port + path).buildUpon().encodedAuthority(authority).build().toString();
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("enabled", enabled).put("zone", zone).put("host", host)
                    .put("username", username).put("password", password).put("port", port).put("subPath", subPath).put("mainPath", mainPath);
        }
        static Camera fromJson(JSONObject object) throws JSONException {
            Camera camera = new Camera();
            camera.id = object.getString("id"); camera.name = object.getString("name");
            camera.enabled = object.optBoolean("enabled", true);
            camera.zone = object.optString("zone", ""); camera.host = object.getString("host");
            camera.username = object.optString("username", ""); camera.password = object.optString("password", "");
            camera.port = object.optInt("port", 554); camera.subPath = object.getString("subPath");
            camera.mainPath = object.optString("mainPath", "");
            return camera;
        }
    }
}
