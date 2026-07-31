package com.mrsuffix.tpapro.permission;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class PermissionGroupResolver {
    public enum Benefit { LOWEST, HIGHEST }
    public record Entry(String permission, double value) {
        public Entry {
            Objects.requireNonNull(permission, "permission");
            if (permission.isBlank() || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Invalid permission group entry");
            }
        }
    }

    public double resolve(List<Entry> entries, Predicate<String> permissionCheck, double fallback, Benefit benefit) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(permissionCheck, "permissionCheck");
        Objects.requireNonNull(benefit, "benefit");
        return entries.stream().filter(entry -> permissionCheck.test(entry.permission())).mapToDouble(Entry::value)
                .reduce(benefit == Benefit.LOWEST ? Math::min : Math::max).orElse(fallback);
    }

    public int resolveLimit(List<Entry> entries, Predicate<String> permissionCheck, int fallback) {
        double result = resolve(entries, permissionCheck, fallback, Benefit.HIGHEST);
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(result));
    }
}
