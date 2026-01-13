package br.ufu.facom.ereno.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Utility for substituting variables in JSON configurations.
 * Replaces placeholders like ${variableName} with actual values.
 */
public class VariableSubstitutor {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * Substitute variables in a JSON object recursively.
     * 
     * @param json The JSON object to process
     * @param variables Map of variable names to values
     * @return A new JSON object with variables substituted
     */
    public static JsonObject substitute(JsonObject json, Map<String, String> variables) {
        JsonObject result = new JsonObject();
        
        for (String key : json.keySet()) {
            JsonElement value = json.get(key);
            result.add(key, substituteElement(value, variables));
        }
        
        return result;
    }
    
    /**
     * Substitute variables in any JSON element.
     */
    private static JsonElement substituteElement(JsonElement element, Map<String, String> variables) {
        if (element.isJsonObject()) {
            return substitute(element.getAsJsonObject(), variables);
        } else if (element.isJsonArray()) {
            return substituteArray(element.getAsJsonArray(), variables);
        } else if (element.isJsonPrimitive()) {
            return substitutePrimitive(element.getAsJsonPrimitive(), variables);
        }
        return element;
    }
    
    /**
     * Substitute variables in a JSON array.
     */
    private static JsonArray substituteArray(JsonArray array, Map<String, String> variables) {
        JsonArray result = new JsonArray();
        
        for (JsonElement element : array) {
            result.add(substituteElement(element, variables));
        }
        
        return result;
    }
    
    /**
     * Substitute variables in a primitive value.
     */
    private static JsonPrimitive substitutePrimitive(JsonPrimitive primitive, Map<String, String> variables) {
        if (primitive.isString()) {
            String value = primitive.getAsString();
            String substituted = substituteString(value, variables);
            return new JsonPrimitive(substituted);
        }
        return primitive;
    }
    
    /**
     * Substitute variables in a string.
     * 
     * @param input The string with ${variable} placeholders
     * @param variables Map of variable names to values
     * @return String with all variables replaced
     */
    public static String substituteString(String input, Map<String, String> variables) {
        if (input == null || variables == null) {
            return input;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Create a variable map from attack pair information.
     */
    public static Map<String, String> createAttackPairVariables(
            String attack1, 
            String attack2, 
            String pattern,
            int iteration) {
        Map<String, String> variables = new HashMap<>();
        variables.put("attack1", attack1);
        variables.put("attack2", attack2);
        variables.put("patternName", pattern);
        variables.put("iteration", String.valueOf(iteration));
        return variables;
    }
    
    /**
     * Extract variable names from a string.
     */
    public static java.util.Set<String> extractVariableNames(String input) {
        java.util.Set<String> variables = new java.util.HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        
        return variables;
    }
    
    /**
     * Resolve field references in a configuration.
     * For example, "${singleAttacks}" references the singleAttacks field in the config.
     * 
     * @param fieldReference The field reference (e.g., "${singleAttacks}")
     * @param config The full configuration JSON object
     * @return The resolved value as a List or null if not found
     */
    public static java.util.List<Object> resolveFieldReference(String fieldReference, JsonObject config) {
        Matcher matcher = VARIABLE_PATTERN.matcher(fieldReference);
        if (!matcher.matches()) {
            return null;
        }
        
        String fieldName = matcher.group(1);
        if (!config.has(fieldName)) {
            return null;
        }
        
        JsonElement element = config.get(fieldName);
        if (!element.isJsonArray()) {
            return null;
        }
        
        JsonArray array = element.getAsJsonArray();
        java.util.List<Object> result = new java.util.ArrayList<>();
        
        for (JsonElement item : array) {
            if (item.isJsonPrimitive()) {
                result.add(item.getAsString());
            } else if (item.isJsonArray()) {
                java.util.List<String> subList = new java.util.ArrayList<>();
                for (JsonElement subItem : item.getAsJsonArray()) {
                    if (subItem.isJsonPrimitive()) {
                        subList.add(subItem.getAsString());
                    }
                }
                result.add(subList);
            }
        }
        
        return result;
    }
}
