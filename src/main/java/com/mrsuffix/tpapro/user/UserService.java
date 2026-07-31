package com.mrsuffix.tpapro.user;

import com.mrsuffix.tpapro.database.model.PlayerData;
import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UserService {
    private final PlayerDataRepository repository;
    private final Logger logger;
    private final String defaultLocale;
    private final Map<UUID, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<UserProfile>> loads = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, java.util.concurrent.atomic.AtomicLong> generations = new ConcurrentHashMap<>();

    public UserService(PlayerDataRepository repository, Logger logger, String defaultLocale) {
        this.repository = repository; this.logger = logger; this.defaultLocale = defaultLocale;
    }

    public CompletableFuture<UserProfile> load(UUID id) {
        UserProfile existing = profiles.get(id);
        if (existing != null && loaded.contains(id)) return CompletableFuture.completedFuture(existing);
        CompletableFuture<UserProfile> result = loads.computeIfAbsent(id, ignored -> {
            long generation = generations.computeIfAbsent(id, key -> new java.util.concurrent.atomic.AtomicLong()).get();
            return repository.load(id, PlayerSettings.defaults(defaultLocale)).thenApply(data -> {
            UserProfile profile = new UserProfile(data.settings(), data.trusted(), data.blocked(), data.autoAcceptPlayers(), data.backLocation());
                java.util.concurrent.atomic.AtomicLong marker = generations.get(id);
                if (marker != null && marker.get() == generation) { profiles.put(id, profile); loaded.add(id); }
                return profile;
            });
        });
        result.whenComplete((profile, error) -> loads.remove(id, result));
        return result;
    }

    public UserProfile get(UUID id) {
        return profiles.computeIfAbsent(id, ignored -> new UserProfile(PlayerSettings.defaults(defaultLocale), Set.of(), Set.of(), Set.of(), null));
    }

    public Optional<UserProfile> cached(UUID id) { return Optional.ofNullable(profiles.get(id)); }

    public void updateSettings(UUID id, PlayerSettings settings) {
        get(id).settings(settings);
        repository.saveSettings(id, settings).exceptionally(error -> { log("save settings", id, error); return null; });
    }

    public boolean addTrusted(UUID owner, UUID target, int limit) {
        if (owner.equals(target)) return false;
        UserProfile profile = get(owner);
        if (profile.trusted().size() >= limit || !profile.trust(target)) return false;
        repository.addTrusted(owner, target).exceptionally(error -> { profile.untrust(target); log("add trust", owner, error); return false; });
        return true;
    }

    public boolean removeTrusted(UUID owner, UUID target) {
        UserProfile profile = get(owner); if (!profile.untrust(target)) return false;
        repository.removeTrusted(owner, target).exceptionally(error -> { profile.trust(target); log("remove trust", owner, error); return false; });
        return true;
    }

    public boolean addBlocked(UUID owner, UUID target) {
        if (owner.equals(target)) return false;
        UserProfile profile = get(owner); if (!profile.block(target)) return false;
        repository.addBlocked(owner, target).exceptionally(error -> { profile.unblock(target); log("add block", owner, error); return false; });
        return true;
    }

    public boolean removeBlocked(UUID owner, UUID target) {
        UserProfile profile = get(owner); if (!profile.unblock(target)) return false;
        repository.removeBlocked(owner, target).exceptionally(error -> { profile.block(target); log("remove block", owner, error); return false; });
        return true;
    }

    public boolean toggleAutoAccept(UUID owner, UUID target) {
        if (owner.equals(target)) return false;
        UserProfile profile = get(owner);
        if (profile.autoAccepts(target)) {
            profile.removeAutoAccept(target);
            repository.removeAutoAccept(owner, target).exceptionally(error -> { profile.addAutoAccept(target); log("remove auto accept", owner, error); return false; });
            return false;
        }
        profile.addAutoAccept(target);
        repository.addAutoAccept(owner, target).exceptionally(error -> { profile.removeAutoAccept(target); log("add auto accept", owner, error); return false; });
        return true;
    }

    public void saveBack(UUID id, StoredLocation location) {
        get(id).backLocation(location);
        repository.saveBackLocation(id, location).exceptionally(error -> { log("save back location", id, error); return null; });
    }

    public boolean loaded(UUID id) { return loaded.contains(id); }
    public void unload(UUID id) {
        java.util.concurrent.atomic.AtomicLong generation = generations.computeIfAbsent(id, key -> new java.util.concurrent.atomic.AtomicLong());
        generation.incrementAndGet(); profiles.remove(id); loaded.remove(id);
        CompletableFuture<UserProfile> pending = loads.remove(id);
        if (pending == null) generations.remove(id, generation);
        else pending.whenComplete((profile, error) -> { if (!loaded.contains(id)) generations.remove(id, generation); });
    }
    public int cachedCount() { return profiles.size(); }
    private void log(String operation, UUID id, Throwable error) { logger.log(Level.WARNING, "Could not " + operation + " for " + id, error); }
}
