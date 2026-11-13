package com.example.combatcooldowns;

import io.papermc.paper.event.entity.EntityLungeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatCooldowns extends JavaPlugin implements Listener {

    private static final List<String> TYPES = List.of("mace", "lunge", "riptide");
    private static final int MAX_SECONDS = 3600;

    private int maceTicks;
    private int lungeTicks;
    private int riptideTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadValues();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadValues() {
        maceTicks = getConfig().getInt("mace-cooldown-seconds", 30) * 20;
        lungeTicks = getConfig().getInt("lunge-cooldown-seconds", 8) * 20;
        riptideTicks = getConfig().getInt("riptide-cooldown-seconds", 3) * 20;
    }

    private static String configKey(String type) {
        return type + "-cooldown-seconds";
    }

    // Mace: cooldown between hits
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (event.getCause() != DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }
        if (player.hasCooldown(Material.MACE)) {
            event.setCancelled(true);
            return;
        }
        if (maceTicks > 0) {
            player.setCooldown(Material.MACE, maceTicks);
        }
    }

    // Spear: cooldown on the lunge enchantment
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Material spear = spearInHands(player);
        if (spear == null) {
            return;
        }
        if (player.hasCooldown(spear)) {
            event.setCancelled(true);
            return;
        }
        if (lungeTicks > 0) {
            player.setCooldown(spear, lungeTicks);
        }
    }

    // Trident: cooldown on riptide
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        Material trident = event.getItem().getType();
        if (player.hasCooldown(trident)) {
            event.setCancelled(true);
            return;
        }
        if (riptideTicks > 0) {
            player.setCooldown(trident, riptideTicks);
        }
    }

    private static Material spearInHands(Player player) {
        Material main = player.getInventory().getItemInMainHand().getType();
        if (main.name().endsWith("_SPEAR")) {
            return main;
        }
        Material off = player.getInventory().getItemInOffHand().getType();
        return off.name().endsWith("_SPEAR") ? off : null;
    }

    // /combatcooldowns [list | set <mace|lunge|riptide> <seconds> | reload]
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("combatcooldowns.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                sender.sendMessage(Component.text("Cooldowns (seconds, 0 = off):", NamedTextColor.GOLD));
                for (String type : TYPES) {
                    sender.sendMessage(Component.text(
                            " " + type + ": " + getConfig().getInt(configKey(type)), NamedTextColor.YELLOW));
                }
            }
            case "reload" -> {
                reloadConfig();
                loadValues();
                sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
            }
            case "set" -> {
                if (args.length != 3) {
                    sender.sendMessage(Component.text(
                            "Usage: /" + label + " set <mace|lunge|riptide> <seconds>", NamedTextColor.RED));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!TYPES.contains(type)) {
                    sender.sendMessage(Component.text(
                            "Type must be mace, lunge or riptide.", NamedTextColor.RED));
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Seconds must be a whole number.", NamedTextColor.RED));
                    return true;
                }
                if (seconds < 0 || seconds > MAX_SECONDS) {
                    sender.sendMessage(Component.text(
                            "Seconds must be between 0 and " + MAX_SECONDS + ".", NamedTextColor.RED));
                    return true;
                }
                getConfig().set(configKey(type), seconds);
                saveConfig();
                loadValues();
                sender.sendMessage(Component.text(
                        "Set " + type + " cooldown to " + seconds + " seconds.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Usage: /" + label + " [list | set <type> <seconds> | reload]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("combatcooldowns.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("list", "set", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (String t : TYPES) {
                if (t.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(t);
                }
            }
        }
        return out;
    }
}
