package com.mrsuffix.tpapro.permission;

import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class PermissionService {
    public boolean has(CommandSender sender, Permission permission) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(permission, "permission");
        return sender.hasPermission(permission.node());
    }

    public void require(CommandSender sender, Permission permission) {
        if (!has(sender, permission)) throw new PermissionDeniedException(permission);
    }

    public static final class PermissionDeniedException extends SecurityException {
        private static final long serialVersionUID = 1L;
        private final Permission permission;
        public PermissionDeniedException(Permission permission) {
            super("Missing permission " + permission.node());
            this.permission = permission;
        }
        public Permission permission() { return permission; }
    }
}
