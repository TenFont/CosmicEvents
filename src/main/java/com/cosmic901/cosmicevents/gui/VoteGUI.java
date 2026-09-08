package com.cosmic901.cosmicevents.gui;

import com.cosmic901.cosmicevents.CosmicEvents;
import com.cosmic901.cosmicevents.service.EventManager;
import com.cosmic901.cosmicevents.util.Items;
import com.cosmic901.cosmicevents.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VoteGUI {
    private final CosmicEvents plugin;
    private final EventManager eventManager;
    private final Player player;
    private final int page;
    private final Map<Integer, String> slotToGameMap = new HashMap<>();
    private final Map<Integer, Integer> slotToPageAction = new HashMap<>();
    private final Set<Integer> closeSlots = new HashSet<>();

    public VoteGUI(CosmicEvents plugin, EventManager eventManager, Player player, int page) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.player = player;
        this.page = Math.max(0, page);
    }

    public void open() {
        if (!eventManager.ensureCanVote(player)) {
            return;
        }
        FileConfiguration guiConfig = plugin.getVoteGuiConfig();
        String titleTemplate = guiConfig.getString("title", "&8Vote for a Minigame &7(Page {page}/{pages})");
        boolean dynamicSize = guiConfig.getBoolean("dynamic_size", true);
        int configuredSize = guiConfig.getInt("size", 54);
        if (configuredSize % 9 != 0 || configuredSize < 9 || configuredSize > 54) {
            configuredSize = 54;
        }

        List<String> games = new ArrayList<>(eventManager.getVoteService().getConfiguredMinigames());
        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        ConfigurationSection decoSection = guiConfig.getConfigurationSection("decorations");

        int size;
        int pageSize;
        int totalPages;
        int safePage;
        int from;
        int to;
        int prevSlot;
        int nextSlot;
        List<Integer> contentSlots;

        if (dynamicSize) {


            boolean needsPagination = games.size() > 45;
            int contentCapacity = needsPagination ? 45 : Math.max(games.size(), 1);
            size = inventorySizeFor(contentCapacity + (needsPagination ? 9 : 0));
            size = Math.min(54, Math.max(9, size));
            if (needsPagination) {
                size = 54;
            }

            contentSlots = new ArrayList<>();
            int usable = needsPagination ? size - 9 : size;
            for (int slot = 0; slot < usable && slot < 54; slot++) {
                contentSlots.add(slot);
            }
            pageSize = Math.max(1, contentSlots.size());
            totalPages = Math.max(1, (int) Math.ceil(games.size() / (double) pageSize));
            safePage = Math.min(page, totalPages - 1);
            from = safePage * pageSize;
            to = Math.min(games.size(), from + pageSize);


            prevSlot = size - 9;
            nextSlot = size - 1;
        } else {
            size = configuredSize;
            prevSlot = guiConfig.getInt("pagination.previous_slot", 45);
            nextSlot = guiConfig.getInt("pagination.next_slot", 53);
            Set<Integer> reserved = new HashSet<>();
            reserved.add(prevSlot);
            reserved.add(nextSlot);

            if (decoSection != null) {
                for (String key : decoSection.getKeys(false)) {
                    reserved.add(decoSection.getInt(key + ".slot", -1));
                }
            }

            contentSlots = new ArrayList<>();
            for (int slot = 0; slot < size; slot++) {
                if (!reserved.contains(slot)) {
                    contentSlots.add(slot);
                }
            }
            if (contentSlots.isEmpty()) {
                for (int slot = 0; slot < Math.min(size, 45); slot++) {
                    contentSlots.add(slot);
                }
            }

            pageSize = Math.max(1, contentSlots.size());
            totalPages = Math.max(1, (int) Math.ceil(games.size() / (double) pageSize));
            safePage = Math.min(page, totalPages - 1);
            from = safePage * pageSize;
            to = Math.min(games.size(), from + pageSize);
        }


        if (dynamicSize && totalPages == 1) {
            int itemsOnPage = Math.max(1, to - from);
            size = inventorySizeFor(itemsOnPage);
        }

        String title = titleTemplate
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));

        VoteGUIHolder holder = new VoteGUIHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, size, Messages.parse(title));
        holder.setInventory(inventory);

        int contentIndex = 0;
        for (int i = from; i < to; i++) {
            if (contentIndex >= contentSlots.size()) {
                break;
            }
            String minigameId = games.get(i);
            int slot = contentSlots.get(contentIndex++);
            if (slot >= size) {
                continue;
            }
            ItemStack item = buildGameItem(guiConfig, itemsSection, minigameId);
            inventory.setItem(slot, item);
            slotToGameMap.put(slot, minigameId);
        }

        if (decoSection != null) {
            for (String key : decoSection.getKeys(false)) {
                if (key.equalsIgnoreCase("arrow_left") || key.equalsIgnoreCase("arrow_right")) {
                    continue;
                }
                int slot = decoSection.getInt(key + ".slot", -1);
                if (slot < 0 || slot >= size) {
                    continue;
                }
                String matName = decoSection.getString(key + ".material", "ARROW");
                String name = decoSection.getString(key + ".name", "");
                List<String> lore = decoSection.getStringList(key + ".lore");
                inventory.setItem(slot, Items.create(plugin, matName, Material.BOOK, 1, name, lore));

                String action = decoSection.getString(key + ".action", "");
                if (action != null && action.equalsIgnoreCase("close")) {
                    closeSlots.add(slot);
                }
            }
        }

        if (safePage > 0 && prevSlot >= 0 && prevSlot < size) {
            String mat = guiConfig.getString("pagination.previous_material", "ARROW");
            String name = guiConfig.getString("pagination.previous_name", "&e« &7Previous Page");
            inventory.setItem(prevSlot, Items.create(plugin, mat, Material.ARROW, 1, name, List.of()));
            slotToPageAction.put(prevSlot, safePage - 1);
        }
        if (safePage < totalPages - 1 && nextSlot >= 0 && nextSlot < size) {
            String mat = guiConfig.getString("pagination.next_material", "ARROW");
            String name = guiConfig.getString("pagination.next_name", "&7Next Page &e»");
            inventory.setItem(nextSlot, Items.create(plugin, mat, Material.ARROW, 1, name, List.of()));
            slotToPageAction.put(nextSlot, safePage + 1);
        }

        applyFiller(guiConfig, inventory, size);

        player.openInventory(inventory);
    }

    private void applyFiller(FileConfiguration guiConfig, Inventory inventory, int size) {
        ConfigurationSection filler = guiConfig.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        String matName = filler.getString("material", "BLACK_STAINED_GLASS_PANE");
        String name = filler.getString("name", " ");
        List<String> lore = filler.getStringList("lore");
        ItemStack pane = Items.create(plugin, matName, Material.BLACK_STAINED_GLASS_PANE, 1, name, lore);
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, pane.clone());
            }
        }
    }

    private static int inventorySizeFor(int itemCount) {
        int rows = Math.max(1, (itemCount + 8) / 9);
        return Math.min(54, rows * 9);
    }

    private ItemStack buildGameItem(FileConfiguration guiConfig, ConfigurationSection itemsSection, String minigameId) {
        String matName = "PAPER";
        String name = "&f" + minigameId;
        List<String> lore = List.of(
                "",
                " &8» &7Vote to play this minigame.",
                " &8» &fCurrent Votes: &b{votes}",
                "",
                " &aClick to vote!"
        );

        if (itemsSection != null) {
            ConfigurationSection section = itemsSection.getConfigurationSection(minigameId);
            if (section == null) {
                for (String key : itemsSection.getKeys(false)) {
                    if (key.equalsIgnoreCase(minigameId)) {
                        section = itemsSection.getConfigurationSection(key);
                        break;
                    }
                }
            }
            if (section != null) {
                matName = section.getString("material", matName);
                name = section.getString("name", name);
                List<String> configuredLore = section.getStringList("lore");
                if (!configuredLore.isEmpty()) {
                    lore = configuredLore;
                }
            }
        }

        int voteCount = eventManager.getVoteCount(minigameId);
        List<String> formattedLore = new ArrayList<>();
        for (String line : lore) {
            formattedLore.add(line.replace("{votes}", String.valueOf(voteCount)));
        }
        return Items.create(plugin, matName, Material.PAPER, 1, name, formattedLore);
    }

    public void handleClick(int slot) {
        if (closeSlots.contains(slot)) {
            player.closeInventory();
            return;
        }
        Integer targetPage = slotToPageAction.get(slot);
        if (targetPage != null) {
            new VoteGUI(plugin, eventManager, player, targetPage).open();
            return;
        }
        String minigameId = slotToGameMap.get(slot);
        if (minigameId != null) {
            player.closeInventory();
            eventManager.castVote(player, minigameId);
        }
    }

    public static class VoteGUIHolder implements InventoryHolder {
        private final VoteGUI gui;
        private Inventory inventory;

        public VoteGUIHolder(VoteGUI gui) {
            this.gui = gui;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public VoteGUI getGUI() {
            return gui;
        }
    }
}

