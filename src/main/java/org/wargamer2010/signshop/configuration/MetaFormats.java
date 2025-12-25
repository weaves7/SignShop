package org.wargamer2010.signshop.configuration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.wargamer2010.signshop.SignShop;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for loading and retrieving localizable item meta format strings.
 *
 * <p>Loads format strings from meta.yml based on the configured language,
 * with automatic fallback to en_US if a format is not defined for the
 * current language.</p>
 */
public class MetaFormats {
    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final Map<String, String> formats = new HashMap<>();
    private static final Map<String, String> defaultFormats = new HashMap<>();

    private MetaFormats() {
    }

    /**
     * Initialize format strings from meta.yml.
     * Should be called after SignShopConfig is initialized.
     */
    public static void init() {
        formats.clear();
        defaultFormats.clear();

        FileConfiguration config = new YamlConfiguration();
        config = configUtil.loadYMLFromJar(config, "meta.yml");
        if (config == null) {
            loadHardcodedDefaults();
            return;
        }

        // Load default (en_US) formats first
        ConfigurationSection defaultSection = config.getConfigurationSection("formats." + DEFAULT_LANGUAGE);
        if (defaultSection != null) {
            for (String key : defaultSection.getKeys(false)) {
                defaultFormats.put(key, defaultSection.getString(key, ""));
            }
        } else {
            loadHardcodedDefaults();
        }

        // Get the configured language
        String language = getPrimaryLanguage();

        // If the configured language is not en_US, try to load its formats
        if (!language.equals(DEFAULT_LANGUAGE)) {
            ConfigurationSection langSection = config.getConfigurationSection("formats." + language);
            if (langSection != null) {
                for (String key : langSection.getKeys(false)) {
                    formats.put(key, langSection.getString(key, ""));
                }
            }
        }
    }

    /**
     * Get a format string by key.
     * Returns the localized version if available, otherwise falls back to en_US.
     *
     * @param key The format key (e.g., "damaged-prefix", "player-head-suffix")
     * @return The format string, or empty string if not found
     */
    public static String get(String key) {
        // Try localized format first
        if (formats.containsKey(key)) {
            return formats.get(key);
        }
        // Fall back to default (en_US)
        return defaultFormats.getOrDefault(key, "");
    }

    /**
     * Gets the primary language from SignShop config.
     * Handles the case where multiple languages are configured (comma-separated).
     */
    private static String getPrimaryLanguage() {
        try {
            SignShopConfig config = SignShop.getInstance().getSignShopConfig();
            if (config != null) {
                String languages = config.getLanguages();
                if (languages != null && !languages.isEmpty()) {
                    // Get the first language in the list
                    String primary = languages.split(",")[0].trim();
                    // Handle legacy spelling
                    if (primary.equalsIgnoreCase("english")) {
                        return DEFAULT_LANGUAGE;
                    }
                    return primary;
                }
            }
        } catch (Exception e) {
            // Config not yet initialized, use default
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * Load hardcoded defaults as fallback if meta.yml is missing.
     */
    private static void loadHardcodedDefaults() {
        defaultFormats.put("damaged-prefix", " Damaged ");
        defaultFormats.put("colored-suffix", " Colored ");
        defaultFormats.put("player-head-suffix", "'s Head");
        defaultFormats.put("custom-head", "Custom Player Head");
        defaultFormats.put("empty-container", "Empty");
        defaultFormats.put("firework-duration", "Duration : ");
        defaultFormats.put("firework-with", " with");
        defaultFormats.put("firework-colors", " colors: ");
        defaultFormats.put("firework-fadecolors", " and fadecolors: ");
        defaultFormats.put("firework-twinkle", " +twinkle");
        defaultFormats.put("firework-trail", " +trail");
    }
}
