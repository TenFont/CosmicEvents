package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.util.AttributeKeys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ParticipantTracker {
    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;


    private static final List<TrackedAttribute> NEUTRALIZED_ATTRIBUTES = List.of(
            new TrackedAttribute(Attribute.MOVEMENT_SPEED, 0.1D, false),
            new TrackedAttribute(Attribute.JUMP_STRENGTH, 0.42D, false),
            new TrackedAttribute(Attribute.GRAVITY, 0.08D, false),
            new TrackedAttribute(Attribute.SAFE_FALL_DISTANCE, 3.0D, false),
            new TrackedAttribute(Attribute.STEP_HEIGHT, 0.6D, false),
            new TrackedAttribute(Attribute.SCALE, 1.0D, true)
    );

    private static final List<PotionEffectType> MOVEMENT_EFFECTS = movementEffectTypes();

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> activeParticipants = new HashSet<>();
    private final Set<UUID> aliveParticipants = new HashSet<>();
    private final java.util.List<UUID> eliminatedParticipants = new java.util.ArrayList<>();
    private final Set<UUID> authorizedJoins = new HashSet<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();
    private final Map<UUID, MovementSnapshot> savedMovement = new HashMap<>();

    public Set<UUID> getParticipantsView() {
        return Collections.unmodifiableSet(participants);
    }

    public Set<UUID> getActiveParticipantsView() {
        return Collections.unmodifiableSet(activeParticipants);
    }

    public Set<UUID> getAliveParticipantsView() {
        return Collections.unmodifiableSet(aliveParticipants);
    }

    public java.util.List<UUID> getEliminatedParticipantsView() {
        return Collections.unmodifiableList(eliminatedParticipants);
    }

    public Set<UUID> getAuthorizedJoinsView() {
        return Collections.unmodifiableSet(authorizedJoins);
    }

    public Map<UUID, Location> getSavedLocationsView() {
        return Collections.unmodifiableMap(savedLocations);
    }

    public Set<UUID> participants() {
        return participants;
    }

    public Set<UUID> activeParticipants() {
        return activeParticipants;
    }

    public Set<UUID> aliveParticipants() {
        return aliveParticipants;
    }

    public java.util.List<UUID> eliminatedParticipants() {
        return eliminatedParticipants;
    }

    public Set<UUID> authorizedJoins() {
        return authorizedJoins;
    }

    public Map<UUID, Location> savedLocations() {
        return savedLocations;
    }

    public void clearJoinPhase() {
        participants.clear();
    }

    public void clearGameTracking() {
        activeParticipants.clear();
        aliveParticipants.clear();
        eliminatedParticipants.clear();
    }

    public void authorize(UUID uuid) {
        authorizedJoins.add(uuid);
    }

    public void unauthorize(UUID uuid) {
        authorizedJoins.remove(uuid);
    }

    public boolean isAuthorized(UUID uuid) {
        return authorizedJoins.contains(uuid);
    }

    public boolean isInEvent(UUID uuid) {
        return participants.contains(uuid) || activeParticipants.contains(uuid);
    }

    public void saveJoinSnapshot(Player player) {
        savedLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public Location removeSavedLocation(UUID uuid) {
        return savedLocations.remove(uuid);
    }


    public void saveAndNeutralizeMovement(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        MovementSnapshot snapshot = savedMovement.get(uuid);
        if (snapshot == null) {
            snapshot = captureMovement(player);
            savedMovement.put(uuid, snapshot);
        }
        applyNeutral(player, snapshot.neutralizedKeys(), false);
    }


    public void enforceNeutralMovement(Player player) {
        if (player == null) {
            return;
        }
        MovementSnapshot snapshot = savedMovement.get(player.getUniqueId());
        if (snapshot == null) {
            saveAndNeutralizeMovement(player);
            return;
        }
        applyNeutral(player, snapshot.neutralizedKeys(), true);
    }

    public void restoreMovement(Player player) {
        if (player == null) {
            return;
        }
        MovementSnapshot snapshot = savedMovement.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        applyMovementSnapshot(player, snapshot);
    }

    public void restoreMovementOrDefault(Player player) {
        if (player == null) {
            return;
        }
        if (savedMovement.containsKey(player.getUniqueId())) {
            restoreMovement(player);
            return;
        }
        resetToVanillaMovement(player);
    }

    public boolean hasMovementSnapshot(UUID uuid) {
        return uuid != null && savedMovement.containsKey(uuid);
    }

    public void resetToVanillaMovement(Player player) {
        if (player == null) {
            return;
        }
        savedMovement.remove(player.getUniqueId());
        applyNeutral(player, Set.of(), false);
    }

    public void stripMovementEffects(Player player) {
        if (player == null) {
            return;
        }
        for (PotionEffectType type : MOVEMENT_EFFECTS) {
            try {
                if (player.hasPotionEffect(type)) {
                    player.removePotionEffect(type);
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                if (isMovementEffect(effect.getType())) {
                    player.removePotionEffect(effect.getType());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void discardMovement(UUID uuid) {
        savedMovement.remove(uuid);
    }

    private MovementSnapshot captureMovement(Player player) {
        List<AttributeSnapshot> attributes = new ArrayList<>();
        Set<NamespacedKey> foreignKeys = new HashSet<>();
        for (TrackedAttribute tracked : NEUTRALIZED_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(tracked.attribute());
            if (instance == null) {
                continue;
            }
            List<ModifierSnapshot> modifiers = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                modifiers.add(ModifierSnapshot.from(modifier));
                if (AttributeKeys.isPluginOwned(modifier.getKey())) {
                    foreignKeys.add(modifier.getKey());
                }
            }
            attributes.add(new AttributeSnapshot(tracked.attribute(), instance.getBaseValue(), modifiers));
        }
        return new MovementSnapshot(
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getAllowFlight(),
                player.isFlying(),
                List.copyOf(attributes),
                Set.copyOf(foreignKeys)
        );
    }


    private void applyNeutral(Player player, Set<NamespacedKey> repeatKeys, boolean enforcing) {
        try {
            player.setFlying(false);
            player.setAllowFlight(false);
            if (enforcing) {
                if (player.getWalkSpeed() > DEFAULT_WALK_SPEED) {
                    player.setWalkSpeed(DEFAULT_WALK_SPEED);
                }
                if (player.getFlySpeed() > DEFAULT_FLY_SPEED) {
                    player.setFlySpeed(DEFAULT_FLY_SPEED);
                }
            } else {
                player.setWalkSpeed(DEFAULT_WALK_SPEED);
                player.setFlySpeed(DEFAULT_FLY_SPEED);
            }
        } catch (Throwable ignored) {
        }

        for (TrackedAttribute tracked : NEUTRALIZED_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(tracked.attribute());
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (enforcing && !tracked.strict() && AttributeKeys.keepDuringLiveGame(modifier.getKey())) {
                    continue;
                }
                try {
                    instance.removeModifier(modifier);
                } catch (Throwable ignored) {
                }
            }
            try {
                if (Math.abs(instance.getBaseValue() - tracked.defaultBase()) > 0.0001D) {
                    instance.setBaseValue(tracked.defaultBase());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyMovementSnapshot(Player player, MovementSnapshot snapshot) {
        for (AttributeSnapshot attributeSnapshot : snapshot.attributes()) {
            AttributeInstance instance = player.getAttribute(attributeSnapshot.attribute());
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                try {
                    instance.removeModifier(modifier);
                } catch (Throwable ignored) {
                }
            }
            try {
                instance.setBaseValue(attributeSnapshot.baseValue());
            } catch (Throwable ignored) {
            }
            for (ModifierSnapshot modifier : attributeSnapshot.modifiers()) {
                try {
                    instance.addModifier(modifier.toModifier());
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            player.setWalkSpeed(clampSpeed(snapshot.walkSpeed()));
            player.setFlySpeed(clampSpeed(snapshot.flySpeed()));
            player.setAllowFlight(snapshot.allowFlight());
            if (snapshot.flying() && snapshot.allowFlight()) {
                player.setFlying(true);
            } else {
                player.setFlying(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float clampSpeed(float speed) {
        if (speed < -1.0f) {
            return -1.0f;
        }
        if (speed > 1.0f) {
            return 1.0f;
        }
        return speed;
    }

    private static List<PotionEffectType> movementEffectTypes() {
        List<PotionEffectType> types = new ArrayList<>();
        addEffectType(types, PotionEffectType.SPEED);
        addEffectType(types, PotionEffectType.JUMP_BOOST);
        addEffectType(types, PotionEffectType.SLOW_FALLING);
        addEffectType(types, PotionEffectType.LEVITATION);
        addEffectType(types, PotionEffectType.DOLPHINS_GRACE);
        return List.copyOf(types);
    }

    private static void addEffectType(List<PotionEffectType> types, PotionEffectType type) {
        if (type != null) {
            types.add(type);
        }
    }

    private static boolean isMovementEffect(PotionEffectType type) {
        if (type == null) {
            return false;
        }
        NamespacedKey key;
        try {
            key = type.getKey();
        } catch (Throwable ignored) {
            return false;
        }
        if (key == null) {
            return false;
        }
        String name = key.getKey();
        return name.equals("speed")
                || name.equals("jump_boost")
                || name.equals("jump")
                || name.equals("slow_falling")
                || name.equals("levitation")
                || name.equals("dolphins_grace");
    }

    private record TrackedAttribute(Attribute attribute, double defaultBase, boolean strict) {
    }

    private record MovementSnapshot(
            float walkSpeed,
            float flySpeed,
            boolean allowFlight,
            boolean flying,
            List<AttributeSnapshot> attributes,
            Set<NamespacedKey> neutralizedKeys
    ) {
    }

    private record AttributeSnapshot(
            Attribute attribute,
            double baseValue,
            List<ModifierSnapshot> modifiers
    ) {
    }

    private record ModifierSnapshot(
            NamespacedKey key,
            double amount,
            AttributeModifier.Operation operation,
            EquipmentSlotGroup slotGroup
    ) {
        static ModifierSnapshot from(AttributeModifier modifier) {
            EquipmentSlotGroup slotGroup;
            try {
                slotGroup = modifier.getSlotGroup();
            } catch (Throwable ignored) {
                slotGroup = EquipmentSlotGroup.ANY;
            }
            return new ModifierSnapshot(modifier.getKey(), modifier.getAmount(), modifier.getOperation(), slotGroup);
        }

        AttributeModifier toModifier() {
            return new AttributeModifier(key, amount, operation, slotGroup == null ? EquipmentSlotGroup.ANY : slotGroup);
        }
    }
}

