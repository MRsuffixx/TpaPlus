package com.mrsuffix.tpapro.gui;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.TeleportRequest;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.PrivacyMode;
import com.mrsuffix.tpapro.user.UserService;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GuiManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private final ConfigManager configs; private final LocaleManager locales; private final RequestRegistry requests;
    private final RequestCoordinator coordinator; private final UserService users; private final MiniMessage mini = MiniMessage.miniMessage();
    private final SchedulerAdapter scheduler;
    private final Map<UUID, Map<Integer, Object>> actions = new HashMap<>();
    public GuiManager(ConfigManager configs, LocaleManager locales, RequestRegistry requests,
                      RequestCoordinator coordinator, UserService users, SchedulerAdapter scheduler) {
        this.configs = configs; this.locales = locales; this.requests = requests; this.coordinator = coordinator; this.users = users;
        this.scheduler = scheduler;
    }

    public void openRequests(Player player, int page) {
        List<TeleportRequest> entries = new ArrayList<>(requests.incoming(player.getUniqueId()));
        entries.addAll(requests.outgoing(player.getUniqueId()));
        open(player, MenuType.REQUESTS, page, entries, "pending", entry -> requestItem(player, (TeleportRequest) entry));
    }
    public void openTrusted(Player player, int page) {
        open(player, MenuType.TRUSTED, page, new ArrayList<>(users.get(player.getUniqueId()).trusted()), "trusted",
                entry -> playerItem((UUID) entry, "trusted"));
    }
    public void openBlocked(Player player, int page) {
        open(player, MenuType.BLOCKED, page, new ArrayList<>(users.get(player.getUniqueId()).blocked()), "blocked",
                entry -> playerItem((UUID) entry, "blocked"));
    }
    public void openHistory(Player player, int page, List<HistoryEntry> entries) {
        open(player, MenuType.HISTORY, page, new ArrayList<>(entries), "history", this::historyItem);
    }
    public void openSettings(Player player) {
        ConfigurationSection section = configs.menus().getConfigurationSection("settings"); int size = safeSize(section == null ? 27 : section.getInt("size", 27));
        TpaMenuHolder holder = new TpaMenuHolder(player.getUniqueId(), MenuType.SETTINGS, 1);
        Inventory inventory = Bukkit.createInventory(holder, size, title("settings", 1, 1)); holder.inventory(inventory);
        PlayerSettings settings = users.get(player.getUniqueId()).settings();
        Map<Integer, Object> menuActions = new HashMap<>();
        setting(inventory, menuActions, "privacy", "privacy", 10, Material.ENDER_EYE, settings.privacyMode());
        setting(inventory, menuActions, "auto-accept", "auto", 12, Material.COMPARATOR, settings.autoAccept());
        setting(inventory, menuActions, "sounds", "sounds", 14, Material.NOTE_BLOCK, settings.sounds());
        setting(inventory, menuActions, "language", "language", 16, Material.WRITABLE_BOOK, settings.language());
        actions.put(player.getUniqueId(), menuActions); player.openInventory(inventory);
    }

    private void open(Player player, MenuType type, int requestedPage, List<?> entries, String key,
                      java.util.function.Function<Object, ItemStack> itemFactory) {
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); int page = Math.max(1, Math.min(requestedPage, totalPages));
        int size = safeSize(configs.menus().getInt(key + ".size", 54)); TpaMenuHolder holder = new TpaMenuHolder(player.getUniqueId(), type, page);
        Inventory inventory = Bukkit.createInventory(holder, size, title(key, page, totalPages)); holder.inventory(inventory);
        Map<Integer, Object> menuActions = new HashMap<>(); int start = (page - 1) * PAGE_SIZE;
        for (int index = start, slot = 0; index < entries.size() && slot < Math.min(PAGE_SIZE, size); index++, slot++) {
            Object entry = entries.get(index); inventory.setItem(slot, itemFactory.apply(entry)); menuActions.put(slot, entry);
        }
        int previous = navigationSlot("shared.previous.slot", size - 9, size), close = navigationSlot("shared.close.slot", size - 5, size);
        int next = navigationSlot("shared.next.slot", size - 1, size);
        if (page > 1) { inventory.setItem(previous, configured("shared.previous", Map.of(), Material.ARROW, "<yellow>Previous page", List.of())); menuActions.put(previous, "previous"); }
        inventory.setItem(close, configured("shared.close", Map.of(), Material.BARRIER, "<red>Close", List.of())); menuActions.put(close, "close");
        if (page < totalPages) { inventory.setItem(next, configured("shared.next", Map.of(), Material.ARROW, "<yellow>Next page", List.of())); menuActions.put(next, "next"); }
        actions.put(player.getUniqueId(), menuActions); player.openInventory(inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof TpaMenuHolder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer().equals(player.getUniqueId())) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) return;
        Object action = actions.getOrDefault(player.getUniqueId(), Map.of()).get(event.getRawSlot()); if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("previous") || action.equals("next")) { reopen(player, holder.type(), holder.page() + (action.equals("next") ? 1 : -1)); return; }
        switch (holder.type()) {
            case REQUESTS -> {
                TeleportRequest request = (TeleportRequest) action;
                if (!request.targetId().equals(player.getUniqueId())) return;
                if (event.isRightClick()) coordinator.denyById(player.getUniqueId(), request.id()); else coordinator.acceptById(player.getUniqueId(), request.id());
                openRequests(player, holder.page());
            }
            case TRUSTED -> { users.removeTrusted(player.getUniqueId(), (UUID) action); openTrusted(player, holder.page()); }
            case BLOCKED -> { users.removeBlocked(player.getUniqueId(), (UUID) action); openBlocked(player, holder.page()); }
            case SETTINGS -> handleSetting(player, (String) action);
            case HISTORY -> { }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getInventory().getHolder(false) instanceof TpaMenuHolder) event.setCancelled(true); }
    @EventHandler public void onClose(InventoryCloseEvent event) { if (event.getInventory().getHolder(false) instanceof TpaMenuHolder) actions.remove(event.getPlayer().getUniqueId()); }

    private void handleSetting(Player player, String key) {
        PlayerSettings settings = users.get(player.getUniqueId()).settings();
        String selectedLocale = null;
        switch (key) {
            case "privacy" -> settings = settings.withPrivacy(PrivacyMode.values()[(settings.privacyMode().ordinal() + 1) % PrivacyMode.values().length]);
            case "auto" -> settings = settings.withAutoAccept(!settings.autoAccept());
            case "sounds" -> settings = settings.withNotification("sounds", !settings.sounds());
            case "language" -> {
                List<String> available = locales.availableLocales().stream().sorted().toList();
                if (available.isEmpty()) return;
                String current = settings.language() == null ? configs.get().main().language().defaultLocale() : settings.language();
                int currentIndex = available.indexOf(current);
                selectedLocale = available.get(currentIndex < 0 ? 0 : (currentIndex + 1) % available.size());
                settings = settings.withLanguage(selectedLocale);
            }
            default -> { return; }
        }
        String locale = selectedLocale;
        actions.remove(player.getUniqueId());
        users.updateSettings(player.getUniqueId(), settings).thenAccept(saved -> scheduler.run(() -> {
            if (!player.isOnline()) return;
            if (saved && locale != null) locales.setPreference(player.getUniqueId(), locale);
            if (!saved) locales.send(player, player.getUniqueId(), "errors.database-unavailable");
            openSettings(player);
        }));
    }
    private void reopen(Player player, MenuType type, int page) {
        switch (type) { case REQUESTS -> openRequests(player, page); case TRUSTED -> openTrusted(player, page);
            case BLOCKED -> openBlocked(player, page); case SETTINGS -> openSettings(player); case HISTORY -> player.closeInventory(); }
    }
    private ItemStack requestItem(Player viewer, TeleportRequest request) {
        UUID other = request.senderId().equals(viewer.getUniqueId()) ? request.targetId() : request.senderId();
        String name = name(other); String direction = request.targetId().equals(viewer.getUniqueId()) ? "Incoming" : "Outgoing";
        return configured("pending.item", Map.of("player", name, "request_type", direction + " " + request.type()),
                Material.ENDER_PEARL, "<aqua><player>", List.of("<gray><request_type>", "<green>Left-click: accept", "<red>Right-click: deny"));
    }
    private ItemStack playerItem(UUID id, String key) {
        return configured(key + ".item", Map.of("player", name(id)), key.equals("blocked") ? Material.BARRIER : Material.PLAYER_HEAD,
                "<aqua><player>", List.of(key.equals("blocked") ? "<yellow>Click to unblock" : "<red>Click to remove"));
    }
    private ItemStack historyItem(Object value) { HistoryEntry entry = (HistoryEntry) value;
        return configured("history.item", Map.of("request_type", entry.kind(), "world", entry.worldName(),
                "x", Math.round(entry.x()), "y", Math.round(entry.y()), "z", Math.round(entry.z())), Material.COMPASS,
                "<aqua><request_type>", List.of("<gray><world> <x>, <y>, <z>")); }
    private Component title(String key, int page, int total) {
        String raw = configs.menus().getString(key + ".title", "<dark_aqua>TpaPro");
        return mini.deserialize(raw, Placeholder.unparsed("page", String.valueOf(page)), Placeholder.unparsed("total_pages", String.valueOf(total)));
    }
    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(mini.deserialize(name));
        meta.lore(lore.stream().map(mini::deserialize).toList()); item.setItemMeta(meta); return item;
    }
    private void setting(Inventory inventory, Map<Integer, Object> menuActions, String configKey, String action,
                         int fallbackSlot, Material fallbackMaterial, Object value) {
        String path = "settings.items." + configKey; int slot = navigationSlot(path + ".slot", fallbackSlot, inventory.getSize());
        inventory.setItem(slot, configured(path, Map.of("value", String.valueOf(value)), fallbackMaterial,
                "<aqua>" + configKey + ": <white><value>", List.of("<yellow>Click to change")));
        menuActions.put(slot, action);
    }
    private ItemStack configured(String path, Map<String, ?> values, Material fallbackMaterial, String fallbackName,
                                 List<String> fallbackLore) {
        Material found = material(configs.menus().getString(path + ".material", fallbackMaterial.name()));
        String name = configs.menus().getString(path + ".name", fallbackName); List<String> lore = configs.menus().getStringList(path + ".lore");
        if (lore.isEmpty()) lore = fallbackLore;
        TagResolver.Builder resolver = TagResolver.builder();
        values.forEach((key, value) -> resolver.resolver(Placeholder.component(key, Component.text(String.valueOf(value)))));
        TagResolver tags = resolver.build(); ItemStack item = new ItemStack(found); ItemMeta meta = item.getItemMeta();
        meta.displayName(mini.deserialize(name, tags)); meta.lore(lore.stream().map(line -> mini.deserialize(line, tags)).toList());
        item.setItemMeta(meta); return item;
    }
    private int navigationSlot(String path, int fallback, int size) { int configured = configs.menus().getInt(path, fallback); return configured < 0 || configured >= size ? fallback : configured; }
    private static Material material(String value) { Material found = Material.matchMaterial(value); return found == null ? Material.STONE : found; }
    private static int safeSize(int size) { int rounded = Math.max(9, Math.min(54, (size / 9) * 9)); return rounded == 0 ? 9 : rounded; }
    private static String name(UUID id) { OfflinePlayer player = Bukkit.getOfflinePlayer(id); return player.getName() == null ? id.toString().substring(0, 8) : player.getName(); }
}
