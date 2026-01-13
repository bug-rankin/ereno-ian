package br.ufu.facom.ereno.config;

import com.google.gson.JsonObject;

/**
 * Configuration holder for attack-specific parameters loaded from config/attacks/*.json
 * This class provides typed access to attack configuration parameters.
 */
public class AttackConfig {
    private final JsonObject json;
    private final String attackType;

    public AttackConfig(JsonObject json) {
        this.json = json;
        this.attackType = json.has("attackType") ? json.get("attackType").getAsString() : "unknown";
    }

    public String getAttackType() {
        return attackType;
    }

    public JsonObject getRaw() {
        return json;
    }

    // Helper methods for common parameter access patterns
    
    public double getDouble(String key, double defaultValue) {
        return json.has(key) ? json.get(key).getAsDouble() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return json.has(key) ? json.get(key).getAsInt() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return json.has(key) ? json.get(key).getAsBoolean() : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        return json.has(key) ? json.get(key).getAsString() : defaultValue;
    }

    // Nested object helpers
    
    public JsonObject getObject(String key) {
        return json.has(key) && json.get(key).isJsonObject() ? 
            json.getAsJsonObject(key) : new JsonObject();
    }

    public boolean hasObject(String key) {
        return json.has(key) && json.get(key).isJsonObject();
    }

    // Range helpers (common pattern: {min: X, max: Y})
    
    public double getRangeMin(String key, double defaultValue) {
        if (json.has(key) && json.get(key).isJsonObject()) {
            JsonObject obj = json.getAsJsonObject(key);
            if (obj.has("min")) {
                return obj.get("min").getAsDouble();
            }
        }
        return defaultValue;
    }

    public double getRangeMax(String key, double defaultValue) {
        if (json.has(key) && json.get(key).isJsonObject()) {
            JsonObject obj = json.getAsJsonObject(key);
            if (obj.has("max")) {
                return obj.get("max").getAsDouble();
            }
        }
        return defaultValue;
    }

    public int getRangeMinInt(String key, int defaultValue) {
        return (int) getRangeMin(key, defaultValue);
    }

    public int getRangeMaxInt(String key, int defaultValue) {
        return (int) getRangeMax(key, defaultValue);
    }

    // Specific attack parameter helpers
    
    /**
     * Get delay in milliseconds from config (handles both seconds and milliseconds)
     */
    public double getDelayMinMs(double defaultMs) {
        double val = getRangeMin("delayMs", defaultMs);
        // If value is very small (< 10), assume it's in seconds, convert to ms
        return val < 10 ? val * 1000 : val;
    }

    public double getDelayMaxMs(double defaultMs) {
        double val = getRangeMax("delayMs", defaultMs);
        return val < 10 ? val * 1000 : val;
    }

    /**
     * Get probability value (handles both 0-1 and 0-100 ranges)
     */
    public double getProbability(String key, double defaultValue) {
        double val = getDouble(key, defaultValue);
        // Normalize to 0-1 range
        return val > 1.0 ? val / 100.0 : val;
    }

    /**
     * Get nested probability (e.g., burst.prob)
     */
    public double getNestedProb(String parent, String child, double defaultValue) {
        if (hasObject(parent)) {
            JsonObject obj = getObject(parent);
            if (obj.has(child)) {
                double val = obj.get(child).getAsDouble();
                return val > 1.0 ? val / 100.0 : val;
            }
        }
        return defaultValue;
    }

    public int getNestedInt(String parent, String child, int defaultValue) {
        if (hasObject(parent)) {
            JsonObject obj = getObject(parent);
            if (obj.has(child)) {
                return obj.get(child).getAsInt();
            }
        }
        return defaultValue;
    }

    public double getNestedDouble(String parent, String child, double defaultValue) {
        if (hasObject(parent)) {
            JsonObject obj = getObject(parent);
            if (obj.has(child)) {
                return obj.get(child).getAsDouble();
            }
        }
        return defaultValue;
    }

    public int getNestedRangeMinInt(String parent, String child, int defaultValue) {
        if (hasObject(parent)) {
            JsonObject parentObj = getObject(parent);
            if (parentObj.has(child) && parentObj.get(child).isJsonObject()) {
                JsonObject rangeObj = parentObj.getAsJsonObject(child);
                if (rangeObj.has("min")) {
                    return rangeObj.get("min").getAsInt();
                }
            }
        }
        return defaultValue;
    }

    public int getNestedRangeMaxInt(String parent, String child, int defaultValue) {
        if (hasObject(parent)) {
            JsonObject parentObj = getObject(parent);
            if (parentObj.has(child) && parentObj.get(child).isJsonObject()) {
                JsonObject rangeObj = parentObj.getAsJsonObject(child);
                if (rangeObj.has("max")) {
                    return rangeObj.get("max").getAsInt();
                }
            }
        }
        return defaultValue;
    }

    public double getNestedRangeMin(String parent, String child, double defaultValue) {
        if (hasObject(parent)) {
            JsonObject parentObj = getObject(parent);
            if (parentObj.has(child) && parentObj.get(child).isJsonObject()) {
                JsonObject rangeObj = parentObj.getAsJsonObject(child);
                if (rangeObj.has("min")) {
                    return rangeObj.get("min").getAsDouble();
                }
            }
        }
        return defaultValue;
    }

    public double getNestedRangeMax(String parent, String child, double defaultValue) {
        if (hasObject(parent)) {
            JsonObject parentObj = getObject(parent);
            if (parentObj.has(child) && parentObj.get(child).isJsonObject()) {
                JsonObject rangeObj = parentObj.getAsJsonObject(child);
                if (rangeObj.has("max")) {
                    return rangeObj.get("max").getAsDouble();
                }
            }
        }
        return defaultValue;
    }

    /**
     * Get an integer array from config
     */
    public int[] getIntArray(String key, int[] defaultValue) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            com.google.gson.JsonArray arr = json.getAsJsonArray(key);
            int[] result = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                result[i] = arr.get(i).getAsInt();
            }
            return result;
        }
        return defaultValue;
    }
}
