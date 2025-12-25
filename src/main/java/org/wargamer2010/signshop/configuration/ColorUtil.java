
package org.wargamer2010.signshop.configuration;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.wargamer2010.signshop.SignShop;
import org.wargamer2010.signshop.util.signshopUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for leather armor color name lookups.
 *
 * <p>Loads color names from colors.yml based on the configured language,
 * with automatic fallback to en_US. Supports fuzzy matching to find the
 * closest color name for custom RGB values.</p>
 */
public class ColorUtil {
    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final Map<Integer, String> colorLookup = new HashMap<>();

    private ColorUtil() {

    }

    /**
     * Initialize color lookup from colors.yml.
     * Should be called after SignShopConfig is initialized.
     */
    public static void init() {
        colorLookup.clear();

        FileConfiguration config = new YamlConfiguration();
        config = configUtil.loadYMLFromJar(config, "colors.yml");
        if (config == null) {
            loadHardcodedDefaults();
            return;
        }

        // Get the configured language
        String language = getPrimaryLanguage();

        // Load stock colors for the configured language (decimal RGB keys)
        boolean loadedLanguage = loadStockColors(config, language);

        // If configured language wasn't found, load en_US
        if (!loadedLanguage && !language.equals(DEFAULT_LANGUAGE)) {
            loadStockColors(config, DEFAULT_LANGUAGE);
        }

        // Load extended colors for fuzzy matching (hex RGB keys)
        loadExtendedColors(config);

        // If no colors were loaded at all, use hardcoded defaults
        if (colorLookup.isEmpty()) {
            loadHardcodedDefaults();
        }
    }

    /**
     * Load stock Minecraft colors from the stock-colors section.
     *
     * @param config The configuration file
     * @param language The language to load
     * @return true if colors were loaded for this language
     */
    private static boolean loadStockColors(FileConfiguration config, String language) {
        ConfigurationSection section = config.getConfigurationSection("stock-colors." + language);
        if (section == null) {
            return false;
        }

        for (String key : section.getKeys(false)) {
            try {
                int rgb = Integer.parseInt(key);
                String colorName = section.getString(key);
                if (colorName != null && !colorName.isEmpty()) {
                    colorLookup.put(rgb, colorName);
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid keys
            }
        }
        return !section.getKeys(false).isEmpty();
    }

    /**
     * Load extended colors for fuzzy matching (hex RGB keys).
     */
    private static void loadExtendedColors(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("extended-colors");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int rgb = Integer.parseInt(key, 16);
                // Don't overwrite stock colors
                if (!colorLookup.containsKey(rgb)) {
                    String colorName = section.getString(key);
                    if (colorName != null && !colorName.isEmpty()) {
                        colorLookup.put(rgb, colorName);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid hex keys
            }
        }
    }

    /**
     * Gets the primary language from SignShop config.
     */
    private static String getPrimaryLanguage() {
        try {
            SignShopConfig config = SignShop.getInstance().getSignShopConfig();
            if (config != null) {
                String languages = config.getLanguages();
                if (languages != null && !languages.isEmpty()) {
                    String primary = languages.split(",")[0].trim();
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
     * Load hardcoded defaults as fallback if colors.yml is missing or empty.
     */
    private static void loadHardcodedDefaults() {
        // Stock Minecraft leather armor colors
        colorLookup.put(8339378, "purple");
        colorLookup.put(11685080, "magenta");
        colorLookup.put(8073150, "purple");
        colorLookup.put(6724056, "light blue");
        colorLookup.put(5013401, "cyan");
        colorLookup.put(5000268, "gray");
        colorLookup.put(10066329, "light gray");
        colorLookup.put(15892389, "pink");
        colorLookup.put(14188339, "orange");
        colorLookup.put(8375321, "lime");
        colorLookup.put(11743532, "red");
        colorLookup.put(2437522, "blue");
        colorLookup.put(15066419, "yellow");
        colorLookup.put(10040115, "red");
        colorLookup.put(1644825, "black");
        colorLookup.put(6704179, "brown");
        colorLookup.put(6717235, "green");
        colorLookup.put(16777215, "white");
        colorLookup.put(3361970, "blue");
        colorLookup.put(1973019, "black");
        colorLookup.put(14188952, "pink");
        colorLookup.put(14602026, "yellow");
        colorLookup.put(10511680, "brown");
    }

    /**
     * Get a human-readable color name for the given Bukkit Color.
     * Uses fuzzy matching to find the closest color if no exact match exists.
     *
     * @param color The Bukkit color to look up
     * @return The color name, capitalized
     */
    public static String getColorAsString(Color color) {
        int rgb = color.asRGB();
        if (colorLookup.containsKey(rgb)) {
            return signshopUtil.capFirstLetter(colorLookup.get(rgb));
        } else {
            // Fuzzy matching - find closest color
            double diff = -1;
            String last = "";
            for (int val : colorLookup.keySet()) {
                double currentdiff = getDifferenceBetweenColors(rgb, val);
                if (diff == -1 || currentdiff < diff) {
                    diff = currentdiff;
                    last = colorLookup.get(val);
                }
            }
            return signshopUtil.capFirstLetter(last);
        }
    }

    /**
     * Calculate the difference between two RGB colors.
     * Used for fuzzy color matching.
     */
    public static double getDifferenceBetweenColors(int colorone, int colortwo) {
        java.awt.Color a = new java.awt.Color(colorone);
        java.awt.Color b = new java.awt.Color(colortwo);
        int comboa = (a.getRed() + a.getGreen() + a.getBlue());
        int combob = (b.getRed() + b.getGreen() + b.getBlue());
        return Math.abs(comboa - combob);
    }
}
