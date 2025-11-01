package org.wargamer2010.signshop.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.FileUtil;
import org.wargamer2010.signshop.SignShop;
import org.wargamer2010.signshop.data.*;
import org.wargamer2010.signshop.data.Storage;
import org.wargamer2010.signshop.data.serialization.BukkitSerialization;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class DataConverter {
    static File sellersFile;
    static File sellersFileBackup;
    static File timingFile;
    static File timingFileBackup;


    public static void init() {
        File dataFolder = SignShop.getInstance().getDataFolder();
        sellersFile = new File(dataFolder, "sellers.yml");
        FileConfiguration sellers = new YamlConfiguration();
        try {
            sellers.load(sellersFile);
            SignShop.log("Checking data version.", Level.INFO);
            if (sellers.getInt("DataVersion") < SignShop.DATA_VERSION) {
                sellersFileBackup = new File(dataFolder, "sellersBackup" + SSTimeUtil.getDateTimeStamp() + ".yml");
                FileUtil.copy(sellersFile, sellersFileBackup);
                convertData(sellers);
                convertTiming();
            }
            else {
                SignShop.log("Your data is current.", Level.INFO);
            }
        } catch (IOException | InvalidConfigurationException ignored) {
        }
    }

    private static void convertTiming() {
        File dataFolder = SignShop.getInstance().getDataFolder();
        timingFile = new File(dataFolder, "timing.yml");
        FileConfiguration timing = new YamlConfiguration();
        try {
            timing.load(timingFile);
            ConfigurationSection expirables = timing.getConfigurationSection("expirables");
            if (expirables != null && !expirables.getKeys(false).isEmpty()) {
                timingFileBackup = new File(dataFolder, "timingBackup" + SSTimeUtil.getDateTimeStamp() + ".yml");
                FileUtil.copy(timingFile, timingFileBackup);
                for (String key : expirables.getKeys(false)) {
                    if (key.contains("sshotel")) {
                        Map<String, Object> propertyMap = expirables.getConfigurationSection(key).getValues(true);
                        timing.createSection("expirables." + key.replace("sshotel", "signshophotel"), propertyMap);
                    }
                }
                timing.save(timingFile);
            }
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Converts legacy shop data to modern format with smart format detection.
     * <p>
     * Handles three scenarios:
     * 1. YAML: prefix - Already modern, skip
     * 2. rO0AB or LEGACY: prefix - DataVersion 3 legacy, upgrade to YAML
     * 3. No prefix - Very old format (pre-DataVersion 3), full conversion
     * <p>
     * Also migrates chest1/chest2 data from legacy Base64 to YAML format.
     */
    private static void convertData(FileConfiguration sellers) {
        try {
            SignShop.log("Converting old data.", Level.INFO);
            ConfigurationSection section = sellers.getConfigurationSection("sellers");
            if (section == null) {
                SignShop.log("There was a problem with the sellers.yml, attempting to fix it. If the problem persists try regenerating the SignShop folder.", Level.WARNING);
                sellers.set("sellers", new ArrayList<>());
            } else {
                Set<String> shops = section.getKeys(false);
                int itemsConverted = 0;
                int itemsSkipped = 0;
                int chestsConverted = 0;

                for (String shop : shops) {
                    StringBuilder itemPath = new StringBuilder().append("sellers.").append(shop).append(".items");
                    StringBuilder miscPath = new StringBuilder().append("sellers.").append(shop).append(".misc");

                    // ==========================================
                    // ITEM CONVERSION - Smart format detection
                    // ==========================================
                    List<String> items = sellers.getStringList(itemPath.toString());

                    if (!items.isEmpty()) {
                        String firstItem = items.getFirst();

                        // SCENARIO 1: Already modern (YAML format)
                        if (firstItem.startsWith("YAML:")) {
                            itemsSkipped++;
                            // Skip - already in modern format

                            // SCENARIO 2: DataVersion 3 legacy format (needs upgrade to YAML)
                        } else if (firstItem.startsWith("rO0AB") || firstItem.startsWith("LEGACY:")) {
                            try {
                                SignShop.log("Upgrading DataVersion 3 items to YAML for shop: " + shop, Level.INFO);

                                // Deserialize from legacy Base64
                                ItemStack[] legacyItems = new ItemStack[items.size()];
                                for (int i = 0; i < items.size(); i++) {
                                    String itemData = items.get(i);
                                    // Remove LEGACY: prefix if present
                                    if (itemData.startsWith("LEGACY:")) {
                                        itemData = itemData.substring("LEGACY:".length());
                                    }

                                    ItemStack[] decoded = BukkitSerialization.itemStackArrayFromBase64(itemData);
                                    if (decoded != null && decoded.length > 0) {
                                        legacyItems[i] = decoded[0];
                                    }
                                }

                                // Re-serialize using modern YAML format
                                sellers.set(itemPath.toString(), itemUtil.convertItemStacksToString(legacyItems));
                                itemsConverted++;

                            } catch (Exception e) {
                                SignShop.log("Failed to upgrade DataVersion 3 items for shop " + shop + ": " + e.getMessage(), Level.WARNING);
                                SignShop.log("Keeping original format", Level.WARNING);
                            }

                            // SCENARIO 3: Very old format (pre-DataVersion 3) - needs full conversion
                        } else {
                            try {
                                SignShop.log("Converting very old format items for shop: " + shop +
                                                ". First item: " +
                                                (firstItem.length() > 50 ? firstItem.substring(0, 50) + "..." : firstItem),
                                        Level.INFO);

                                ItemStack[] itemStacks = convertOldStringsToItemStacks(items);

                                // Check if conversion actually produced items
                                boolean hasValidItems = false;
                                for (ItemStack stack : itemStacks) {
                                    if (stack != null) {
                                        hasValidItems = true;
                                        break;
                                    }
                                }

                                if (hasValidItems) {
                                    sellers.set(itemPath.toString(), itemUtil.convertItemStacksToString(itemStacks));
                                    itemsConverted++;
                                } else {
                                    SignShop.log("No valid items found after conversion for shop " + shop + ", keeping original",
                                            Level.WARNING);
                                }

                            } catch (Exception e) {
                                SignShop.log("Failed to convert unknown format items for shop " + shop + ": " +
                                        e.getMessage(), Level.WARNING);
                                SignShop.log("Keeping original data for safety", Level.WARNING);
                                // Keep original on failure - better than nulling everything
                            }
                        }
                    }

                    // ==========================================
                    // MISC CONVERSION - Strip old data + migrate chests
                    // ==========================================
                    List<String> misc = sellers.getStringList(miscPath.toString());
                    if (!misc.isEmpty()) {
                        List<String> newMisc = new ArrayList<>();

                        for (String miscString : misc) {
                            String[] keyPair = miscString.split(":", 2);
                            String key = keyPair.length >= 1 ? keyPair[0] : "";
                            String data = keyPair.length == 2 ? keyPair[1] : "";

                            // Strip old pipe-separated data if it exists
                            if (data.contains("|")) {
                                String[] dataPair = data.split("\\|", 2);
                                data = dataPair.length == 2 ? dataPair[1] : dataPair[0];
                            }

                            newMisc.add(key + ":" + data);
                        }

                        // Migrate chest serialization (chest1, chest2)
                        List<String> migratedMisc = migrateChestSerialization(newMisc);
                        if (migratedMisc.size() > newMisc.size() || !migratedMisc.equals(newMisc)) {
                            chestsConverted++;
                        }

                        sellers.set(miscPath.toString(), migratedMisc);
                    }
                }

                SignShop.log("Data conversion of " + shops.size() + " shops has finished.", Level.INFO);
                SignShop.log("  - Items upgraded to YAML: " + itemsConverted, Level.INFO);
                SignShop.log("  - Items already modern (skipped): " + itemsSkipped, Level.INFO);
                SignShop.log("  - Shops with chests migrated: " + chestsConverted, Level.INFO);
            }

            sellers.set("DataVersion", SignShop.DATA_VERSION);
            sellers.save(sellersFile);

        } catch (IOException e) {
            SignShop.log("Error converting data!", Level.WARNING);
            e.printStackTrace();
        }
    }

    /**
     * Migrates legacy chest data (chest1:, chest2:) from BukkitObjectOutputStream
     * to modern YAML format. Part of v5.1.0 modernization.
     * <p>
     * Handles multi-line Base64 where a single item's Base64 data wraps across lines,
     * and multiple items are separated by lines starting with ~.
     * <p>
     * Example input:
     *   "chest1:rO0AB...\n      continuesBase64...\n      moreBase64...\n~rO0AB...(item2)"
     * <p>
     * The ~ prefix indicates the START of a new item, not just a line continuation.
     *
     * @param miscList List of misc data strings
     * @return Updated list with modern serialization
     */
    private static List<String> migrateChestSerialization(List<String> miscList) {
        if (miscList == null || miscList.isEmpty()) {
            return miscList;
        }

        List<String> updated = new ArrayList<>();
        int migratedCount = 0;

        for (String miscEntry : miscList) {
            // Parse key:value format
            String[] parts = miscEntry.split(":", 2);
            if (parts.length != 2) {
                updated.add(miscEntry);
                continue;
            }

            String key = parts[0];
            String value = parts[1];

            // Only migrate chest1 and chest2 entries
            if (!key.equals("chest1") && !key.equals("chest2")) {
                updated.add(miscEntry);
                continue;
            }

            // Check if already modern format (no newlines, just ~ separators)
            if (value.startsWith("YAML:") && !value.contains("\n")) {
                updated.add(miscEntry);
                continue;
            }

            // Check if it's the separator character (chest might be empty)
            if (value.equals(Storage.getItemSeperator()) || value.isEmpty()) {
                updated.add(miscEntry);
                continue;
            }

            try {
                List<String> modernItems = new ArrayList<>();

                // Split by lines that START with ~ (item boundaries)
                // Use regex to split on \n~ but keep the ~ with the following content
                String[] itemChunks = miscEntry.split("\n(?=~)");

                for (int i = 0; i < itemChunks.length; i++) {
                    String chunk = itemChunks[i];

                    if (i == 0) {
                        // First chunk: "chest1:rO0AB...\n  continues...\n  more..."
                        String[] firstParts = chunk.split(":", 2);
                        if (firstParts.length != 2) {
                            // Malformed, keep original
                            updated.add(miscEntry);
                            break;
                        }

                        // Remove all newlines and whitespace from Base64 data
                        String base64Data = firstParts[1].replaceAll("\\s+", "");

                        // Skip if already modern
                        if (base64Data.startsWith("YAML:")) {
                            modernItems.add(base64Data);
                            continue;
                        }

                        // Remove LEGACY: prefix if present
                        if (base64Data.startsWith("LEGACY:")) {
                            base64Data = base64Data.substring("LEGACY:".length());
                        }

                        // Deserialize from legacy format
                        ItemStack[] items = BukkitSerialization.itemStackArrayFromBase64(base64Data);

                        // Re-serialize in modern format
                        if (items != null && items.length > 0 && items[0] != null) {
                            String[] modernItem = itemUtil.convertItemStacksToString(new ItemStack[]{items[0]});
                            if (modernItem.length > 0) {
                                modernItems.add(modernItem[0]);
                            }
                        }

                    } else {
                        // Continuation chunks: "~rO0AB...\n  continues...\n  more..."
                        if (chunk.startsWith("~")) {
                            // Remove ~ prefix and all newlines/whitespace from Base64 data
                            String base64Data = chunk.substring(1).replaceAll("\\s+", "");

                            // Skip if already modern
                            if (base64Data.startsWith("YAML:")) {
                                modernItems.add(base64Data);
                                continue;
                            }

                            // Remove LEGACY: prefix if present
                            if (base64Data.startsWith("LEGACY:")) {
                                base64Data = base64Data.substring("LEGACY:".length());
                            }

                            // Deserialize from legacy format
                            ItemStack[] items = BukkitSerialization.itemStackArrayFromBase64(base64Data);

                            // Re-serialize in modern format
                            if (items != null && items.length > 0 && items[0] != null) {
                                String[] modernItem = itemUtil.convertItemStacksToString(new ItemStack[]{items[0]});
                                if (modernItem.length > 0) {
                                    modernItems.add(modernItem[0]);
                                }
                            }
                        }
                    }
                }

                // Join with ~ separator (NO newlines) - this matches what implode() creates
                if (!modernItems.isEmpty()) {
                    String flatFormat = key + ":" + String.join("~", modernItems);
                    updated.add(flatFormat);
                    migratedCount++;
                } else {
                    // If migration produced nothing, keep original
                    updated.add(miscEntry);
                }

            } catch (Exception e) {
                // If migration fails, keep original (better than losing data)
                SignShop.log("Failed to migrate " + key + " data, keeping original: " +
                        e.getMessage(), Level.WARNING);
                e.printStackTrace();
                updated.add(miscEntry);
            }
        }

        if (migratedCount > 0) {
            SignShop.log("Migrated " + migratedCount + " chest entries to modern format", Level.INFO);
        }

        return updated;
    }

    public static ItemStack[] convertOldStringsToItemStacks(List<String> itemStringList) {
        IItemTags itemTags = BookFactory.getItemTags();
        ItemStack[] itemStacks = new ItemStack[itemStringList.size()];
        int invalidItems = 0;

        for (int i = 0; i < itemStringList.size(); i++) {
            try {
                String[] itemProperties = itemStringList.get(i).split(Storage.getItemSeperator());
                if (itemProperties.length < 4) {
                    invalidItems++;
                    continue;
                }

                if (itemProperties.length <= 7) {
                    if (i < (itemStringList.size() - 1) && itemStringList.get(i + 1).split(Storage.getItemSeperator()).length < 4) {
                        // Bug detected, the next item will be the base64 string belonging to the current item
                        // This bug will be fixed at the next save as the ~ will be replaced with a |
                        itemProperties = (itemStringList.get(i) + "|" + itemStringList.get(i + 1)).split(Storage.getItemSeperator());
                    }
                }

                if (itemProperties.length > 7) {
                    String base64prop = itemProperties[7];
                    // The ~ and | are used to differentiate between the old NBTLib and the BukkitSerialization
                    if (base64prop != null && (base64prop.startsWith("~") || base64prop.startsWith("|"))) {
                        String joined = itemUtil.Join(itemProperties, 7).substring(1);

                        ItemStack[] convertedStacks = BukkitSerialization.itemStackArrayFromBase64(joined);
                        if (convertedStacks.length > 0 && convertedStacks[0] != null) {
                            itemStacks[i] = convertedStacks[0];
                        }
                    }
                }

                if (itemStacks[i] == null) {
                    itemStacks[i] = itemTags.getCraftItemstack(
                            Material.getMaterial(itemProperties[1]),
                            Integer.parseInt(itemProperties[0]),
                            Short.parseShort(itemProperties[2])
                    );
                    //noinspection deprecation
                    itemStacks[i].getData().setData(Byte.parseByte(itemProperties[3]));

                    if (itemProperties.length > 4)
                        itemUtil.safelyAddEnchantments(itemStacks[i], signshopUtil.convertStringToEnchantments(itemProperties[4]));
                }

                if (itemProperties.length > 5) {
                    try {
                        itemStacks[i] = SignShopBooks.addBooksProps(itemStacks[i], Integer.parseInt(itemProperties[5]));
                    } catch (NumberFormatException ignored) {

                    }
                }
                if (itemProperties.length > 6) {
                    try {
                        SignShopItemMeta.setMetaForID(itemStacks[i], Integer.parseInt(itemProperties[6]));
                    } catch (NumberFormatException ignored) {

                    }
                }
            } catch (Exception ignored) {

            }
        }

        if (invalidItems > 0) {
            ItemStack[] temp = new ItemStack[itemStringList.size() - invalidItems];
            int counter = 0;
            for (ItemStack i : itemStacks) {
                if (i != null) {
                    temp[counter] = i;
                    counter++;
                }
            }

            itemStacks = temp;
        }


        return itemStacks;
    }

    //Probably won't need this but saving it anyway.
    public static String[] convertItemStacksToOldString(ItemStack[] itemStackArray) {
        List<String> itemStringList = new ArrayList<>();
        if (itemStackArray == null)
            return new String[1];

        ItemStack currentItemStack;
        for (ItemStack itemStack : itemStackArray) {
            if (itemStack != null) {
                currentItemStack = itemStack;
                String ID = "";
                if (itemUtil.isWriteableBook(currentItemStack))
                    ID = SignShopBooks.getBookID(currentItemStack).toString();
                String metaID = SignShopItemMeta.getMetaID(currentItemStack).toString();
                if (metaID.equals("-1"))
                    metaID = "";
                ItemStack[] stacks = new ItemStack[1];
                stacks[0] = currentItemStack;

                itemStringList.add(BukkitSerialization.itemStackArrayToBase64(stacks));

                //noinspection deprecation
                itemStringList.add((currentItemStack.getAmount() + Storage.getItemSeperator()
                        + currentItemStack.getType() + Storage.getItemSeperator()
                        + ((Damageable) currentItemStack.getItemMeta()).getDamage() + Storage.getItemSeperator()
                        + currentItemStack.getData().getData() + Storage.getItemSeperator()
                        + signshopUtil.convertEnchantmentsToString(currentItemStack.getEnchantments()) + Storage.getItemSeperator()
                        + ID + Storage.getItemSeperator()
                        + metaID + Storage.getItemSeperator()
                        + "|" + BukkitSerialization.itemStackArrayToBase64(stacks)));
            }

        }
        String[] items = new String[itemStringList.size()];
        itemStringList.toArray(items);
        return items;
    }
}