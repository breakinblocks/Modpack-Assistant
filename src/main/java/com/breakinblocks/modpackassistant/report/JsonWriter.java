package com.breakinblocks.modpackassistant.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.TreeMap;

public final class JsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonWriter() {
    }

    public static String toPrettyString(JsonElement element) {
        return GSON.toJson(sorted(element));
    }

    public static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            Map<String, JsonElement> ordered = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                ordered.put(entry.getKey(), sorted(entry.getValue()));
            }
            JsonObject result = new JsonObject();
            ordered.forEach(result::add);
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(sorted(child));
            }
            return result;
        }
        return element;
    }
}
