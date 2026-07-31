package com.mrsuffix.tpapro.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class TpaMenuHolder implements InventoryHolder {
    private final UUID viewer; private final MenuType type; private final int page; private Inventory inventory;
    public TpaMenuHolder(UUID viewer, MenuType type, int page) { this.viewer = viewer; this.type = type; this.page = page; }
    public UUID viewer() { return viewer; } public MenuType type() { return type; } public int page() { return page; }
    public void inventory(Inventory inventory) { if (this.inventory != null) throw new IllegalStateException("Inventory already assigned"); this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
