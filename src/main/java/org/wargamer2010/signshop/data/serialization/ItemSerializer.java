package org.wargamer2010.signshop.data.serialization;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.wargamer2010.signshop.SignShop;
import org.wargamer2010.signshop.incompatibility.IncompatibilityChecker;
import org.wargamer2010.signshop.incompatibility.IncompatibilityType;
import org.wargamer2010.signshop.util.itemUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;

/**
 * Modern item serialization system for SignShop v5.1.0+
 *
 * <p><b>Architecture:</b></p>
 * <pre>
 * Serialization:   ItemStack → Bukkit API → YAML → Base64 → String
 * Deserialization: String → Base64 → YAML → Bukkit API → ItemStack
 * </pre>
 *
 * <p><b>Why this approach:</b></p>
 * <ul>
 *   <li><b>Bukkit API:</b> Version-stable, handles all item types correctly</li>
 *   <li><b>YAML:</b> Native Bukkit format, preserves PDC, handles complex objects</li>
 *   <li><b>Base64:</b> Single-line storage compatible with sellers.yml format</li>
 * </ul>
 *
 * <p><b>Format Prefixes:</b></p>
 * <ul>
 *   <li><code>YAML:</code> - Modern format (v5.1.0+)</li>
 *   <li><code>LEGACY:</code> - Explicit legacy format marker</li>
 *   <li><i>No prefix</i> - Assumed legacy format</li>
 * </ul>
 *
 * <p><b>Fixes:</b> Issues #170, #168, #161, #165, #83</p>
 *
 * @author SignShop Development Team
 * @version 5.1.0
 * @since 5.1.0
 */
public class ItemSerializer {

    private static final String MODERN_PREFIX = "YAML:";
    private static final String LEGACY_PREFIX = "LEGACY:";

    // ========================================
    // Public API
    // ========================================

    /**
     * Serializes an ItemStack to a storage-ready string.
     *
     * <p><b>Enhanced Process (v5.2.0+):</b></p>
     * <ol>
     *   <li>Check for incompatibilities (IncompatibilityChecker)</li>
     *   <li>If incompatible → Use LEGACY format proactively</li>
     *   <li>If compatible → ItemStack → Bukkit serialize() → YAML → Base64 → "YAML:..." string</li>
     * </ol>
     *
     * <p><b>Why check before serialization?</b></p>
     * <p>Some items serialize to YAML successfully but fail to deserialize later,
     * causing NPE when the shop is used. By checking first, we use LEGACY format
     * proactively for known problematic items.</p>
     *
     * @param item The ItemStack to serialize (null returns null)
     * @return Storage-ready string with YAML: or LEGACY: prefix, or null
     */
    public static String serialize(ItemStack item) {
        if (item == null) {
            return null;
        }

        // ==========================================
        // Phase 3A: Proactive Incompatibility Check
        // ==========================================
        // Check for known incompatibilities BEFORE attempting YAML serialization
        IncompatibilityType incompatibility = IncompatibilityChecker.checkItem(item);

        if (incompatibility != null) {
            // Item has known incompatibility - use LEGACY format proactively
            debugLog("Item has incompatibility (" + incompatibility + "), using LEGACY format for " +
                    item.getType() + " (amount: " + item.getAmount() + ")");

            try {
                String base64 = BukkitSerialization.itemStackArrayToBase64(new ItemStack[]{item});
                return LEGACY_PREFIX + base64;

            } catch (Exception e) {
                SignShop.log(
                        "Failed to serialize incompatible item to LEGACY format: " + e.getMessage(),
                        Level.SEVERE
                );
                return null;
            }
        }

        // ==========================================
        // Normal YAML Serialization (Compatible Items)
        // ==========================================
        try {
            // Step 1: Use Bukkit's serialize() to get a Map
            Map<String, Object> itemData = item.serialize();

            // Step 2: Convert Map to YAML string
            String yaml = mapToYaml(itemData);

            // Step 3: Encode YAML as Base64 for single-line storage
            String base64 = encodeBase64(yaml);

            // Step 4: Add prefix
            String result = MODERN_PREFIX + base64;

            debugLog("Serialized " + item.getType() + " (amount: " + item.getAmount() + ") to YAML format");

            return result;

        } catch (Exception e) {
            // Fallback to legacy format on any error
            return handleSerializationError(item, e);
        }
    }

    /**
     * Deserializes a string back to an ItemStack.
     *
     * <p>Automatically detects format:</p>
     * <ul>
     *   <li>YAML: prefix → Modern format</li>
     *   <li>LEGACY: prefix → Explicit legacy</li>
     *   <li>No prefix → Assumed legacy</li>
     * </ul>
     *
     * @param data The serialized string (null or empty returns null)
     * @return Deserialized ItemStack, or null on failure
     */
    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            // Detect format by prefix
            if (data.startsWith(MODERN_PREFIX)) {
                return deserializeModern(data);
            } else if (data.startsWith(LEGACY_PREFIX)) {
                return deserializeLegacy(data.substring(LEGACY_PREFIX.length()));
            } else {
                // No prefix = old format, assume legacy
                return deserializeLegacy(data);
            }

        } catch (Exception e) {
            return handleDeserializationError(data, e);
        }
    }

    /**
     * Checks if data is in modern YAML format.
     *
     * @param data The serialized string to check
     * @return true if modern format, false otherwise
     */
    public static boolean isModernFormat(String data) {
        return data != null && data.startsWith(MODERN_PREFIX);
    }

    /**
     * Checks if data is in legacy format.
     *
     * @param data The serialized string to check
     * @return true if legacy format, false otherwise
     */
    public static boolean isLegacyFormat(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        return data.startsWith(LEGACY_PREFIX) || !data.startsWith(MODERN_PREFIX);
    }

    // ========================================
    // Modern Format Implementation
    // ========================================

    /**
     * Deserializes modern YAML format.
     * Process: "YAML:base64" → Base64 decode → YAML parse → Bukkit deserialize → ItemStack
     */
    private static ItemStack deserializeModern(String data) {
        // Step 1: Remove prefix and get Base64 string
        String base64 = data.substring(MODERN_PREFIX.length());

        // Step 2: Decode Base64 to YAML string
        String yaml = decodeBase64(base64);

        // Step 3: Parse YAML to Map
        Map<String, Object> itemData;
        try {
            itemData = yamlToMap(yaml);
        } catch (Exception e) {
            // YAML parsing failed - fallback to legacy format
            debugLog("Modern deserialization failed, using legacy format: " + e.getMessage());
            try {
                return deserializeLegacy(base64);
            } catch (Exception legacyError) {
                SignShop.log("Both modern and legacy deserialization failed: " + legacyError.getMessage(), Level.WARNING);
                return null;
            }
        }

        // Step 4: Use Bukkit's deserialize() to create ItemStack
        try {
            ItemStack item = ItemStack.deserialize(itemData);

            // Normalize data version: Round-trip through serialize/deserialize to upgrade
            // items from old Minecraft versions (e.g., v4556 from 1.21.10) to current version
            // (e.g., v4671 in 1.21.11). This ensures items from different versions have matching
            // hashCodes for HashMap lookups in stock checking.
            // Without this, items stored before a Minecraft update won't match player inventory items.
            try {
                item = ItemStack.deserialize(item.serialize());
            } catch (Exception normalizeEx) {
                // If normalization fails, use the original item
                debugLog("Data version normalization failed, using original: " + normalizeEx.getMessage());
            }

            debugLog("Deserialized (modern): " + (item != null ? item.getType() : "null"));
            return item;
        } catch (Exception e) {
            // ItemStack deserialization failed (e.g., corrupt player heads with empty names)
            // This can happen with incompatible items that passed YAML parsing but fail during
            // Bukkit's internal deserialization (NullPointerException in CraftMetaSkull, etc.)
            SignShop.log("ItemStack deserialization failed (incompatible item data): " + e.getMessage(), Level.WARNING);
            debugLog("ItemStack.deserialize() threw exception, returning null");
            return null;
        }
    }

    // ========================================
    // Legacy Format Implementation
    // ========================================

    /**
     * Deserializes legacy BukkitObjectOutputStream format.
     * Applies fixBookLegacy() for WRITTEN_BOOK items only.
     */
    private static ItemStack deserializeLegacy(String base64) throws IOException {
        // Use old BukkitSerialization to read legacy format
        ItemStack[] items = BukkitSerialization.itemStackArrayFromBase64(base64);
        ItemStack item = (items.length > 0) ? items[0] : null;

        // Apply book fix only for legacy books
        if (item != null && item.getType() == Material.WRITTEN_BOOK) {
            itemUtil.fixBookLegacy(item);
        }

        // Normalize data version (same as modern format) to ensure cross-version compatibility
        if (item != null) {
            try {
                item = ItemStack.deserialize(item.serialize());
            } catch (Exception normalizeEx) {
                // If normalization fails, use the original item
                debugLog("Legacy data version normalization failed, using original: " + normalizeEx.getMessage());
            }
        }

        debugLog("Deserialized (legacy): " + (item != null ? item.getType() : "null"));

        return item;
    }

    // ========================================
    // YAML Conversion Helpers
    // ========================================

    /**
     * Converts a Map to YAML string using Bukkit's YamlConfiguration.
     * This ensures proper handling of all Bukkit-specific types.
     */
    private static String mapToYaml(Map<String, Object> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", map);
        return yaml.saveToString();
    }

    /**
     * Converts YAML string back to Map using Bukkit's YamlConfiguration.
     */
    private static Map<String, Object> yamlToMap(String yaml) {
        // Pre-validate: Detect corrupt player heads with empty names (causes NPE in Spigot 1.21)
        // This prevents Bukkit from logging scary stacktraces for known corrupt data
        if (yaml.contains("meta-type: SKULL") && yaml.contains("name: ''")) {
            // Corrupt player head with empty name - will cause NPE in CraftMetaSkull deserialization
            throw new RuntimeException("Corrupt player head detected (empty name) - cannot deserialize");
        }

        YamlConfiguration config = new YamlConfiguration();

        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new RuntimeException("YAML parsing failed: " + e.getMessage(), e);
        }

        // Get the ConfigurationSection and convert to Map
        Object itemSection = config.get("item");

        if (itemSection == null) {
            throw new RuntimeException("No 'item' key found in YAML");
        }

        // ConfigurationSection needs to be converted to Map
        if (itemSection instanceof org.bukkit.configuration.ConfigurationSection) {
            org.bukkit.configuration.ConfigurationSection section =
                    (org.bukkit.configuration.ConfigurationSection) itemSection;
            return section.getValues(false);
        }

        // If it's already a Map (shouldn't happen, but safe fallback)
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) itemSection;

        return map;
    }

    // ========================================
    // Base64 Encoding Helpers
    // ========================================

    /**
     * Encodes a string to Base64 for single-line storage.
     */
    private static String encodeBase64(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Decodes Base64 string back to original string.
     */
    private static String decodeBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Base64 decoding failed: " + e.getMessage(), e);
        }
    }

    // ========================================
    // Error Handling
    // ========================================

    /**
     * Handles serialization errors by falling back to legacy format.
     */
    private static String handleSerializationError(ItemStack item, Exception e) {
        SignShop.log(
                "Modern serialization failed for " + item.getType() +
                        ", falling back to legacy: " + e.getMessage(),
                Level.WARNING
        );

        try {
            return LEGACY_PREFIX + BukkitSerialization.itemStackArrayToBase64(new ItemStack[]{item});
        } catch (Exception fallbackError) {
            SignShop.log(
                    "Legacy serialization also failed: " + fallbackError.getMessage(),
                    Level.SEVERE
            );
            return null;
        }
    }

    /**
     * Handles deserialization errors with helpful logging.
     */
    private static ItemStack handleDeserializationError(String data, Exception e) {
        SignShop.log("Failed to deserialize item: " + e.getMessage(), Level.WARNING);

        if (SignShop.getInstance().getSignShopConfig().debugging()) {
            String preview = (data.length() > 100) ?
                    data.substring(0, 100) + "..." :
                    data;
            SignShop.log("Problematic data: " + preview, Level.WARNING);
        }

        return null;
    }

    // ========================================
    // Debug Logging
    // ========================================

    /**
     * Logs debug messages when debugging is enabled.
     */
    private static void debugLog(String message) {
        SignShop.getInstance().debugClassMessage(message, "ItemSerializer");
    }
}