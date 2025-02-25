package me.inotwhitecat.catfttrapskins;

import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.schematic.SchematicFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CatFTtrapSkins extends JavaPlugin implements Listener {
    private Map<String, TrapData> trapDataMap; // Для хранения данных о ловушках
    private WorldEditPlugin worldEdit;

    @Override
    public void onEnable() {
        // Проверяем наличие WorldEdit
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit не найден! Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        saveDefaultConfig();  // Сохраняем конфигурацию по умолчанию
        trapDataMap = new HashMap<>();
        loadTrapData();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CatFTtrapSkins включен!");
    }

    private void loadTrapData() {
        FileConfiguration config = getConfig();
        if (config.getConfigurationSection("skins") != null) {
            for (String key : config.getConfigurationSection("skins").getKeys(false)) {
                String name = config.getString("skins." + key + ".name");
                String item = config.getString("skins." + key + ".item");
                int effectDuration = config.getInt("skins." + key + ".effect_duration");
                int horusDelay = config.getInt("skins." + key + ".horus_delay");
                int perlDelay = config.getInt("skins." + key + ".perl_delay");

                Map<PotionEffectType, Integer> effects = new HashMap<>();
                if (config.getConfigurationSection("skins." + key + ".effects") != null) {
                    for (String effectKey : config.getConfigurationSection("skins." + key + ".effects").getKeys(false)) {
                        String effectName = config.getString("skins." + key + ".effects." + effectKey + ".effect");
                        int level = config.getInt("skins." + key + ".effects." + effectKey + ".level");
                        PotionEffectType effectType = PotionEffectType.getByName(effectName.toUpperCase());
                        if (effectType != null) {
                            effects.put(effectType, level);
                        }
                    }
                }

                String sound = config.getString("skins." + key + ".sound");
                String schematic = config.getString("skins." + key + ".schematic");

                trapDataMap.put(key, new TrapData(name, item, schematic, effects, effectDuration, sound, horusDelay, perlDelay));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction().toString().contains("RIGHT_CLICK") && item != null && item.getType() != Material.AIR) {
            String trapName = getTrapName(item);

            if (trapName != null) {
                TrapData trapData = trapDataMap.get(trapName);
                if (trapData != null) {
                    // Проверяем задержки
                    if (item.getType() == Material.ENDER_PEARL) {
                        if (trapData.getPerlDelay() > 0) {
                            event.setCancelled(true);
                            player.sendMessage("Использование Перлов заблокировано на " + trapData.getPerlDelay() + " секунд!");
                            getServer().getScheduler().runTaskLater(this, () -> {
                                player.sendMessage("Вы можете снова использовать Перлы!");
                            }, trapData.getPerlDelay() * 20);
                            return;
                        }
                    }

                    if (item.getType() == Material.CHORUS_FRUIT) {
                        if (trapData.getHorusDelay() > 0) {
                            event.setCancelled(true);
                            player.sendMessage("Использование Хоруса заблокировано на " + trapData.getHorusDelay() + " секунд!");
                            getServer().getScheduler().runTaskLater(this, () -> {
                                player.sendMessage("Вы можете снова использовать Хорусы!");
                            }, trapData.getHorusDelay() * 20);
                            return;
                        }
                    }

                    // Логика активации ловушки
                    File schematicFile = new File(getDataFolder(), trapData.getSchematic());
                    if (schematicFile.exists()) {
                        try {
                            EditSession editSession = worldEdit.getEditSessionFactory().getEditSession(new BukkitWorld(player.getWorld()), -1);
                            SchematicFormat schematicFormat = SchematicFormat.getByFile(schematicFile);
                            schematicFormat.load(schematicFile).paste(editSession, new Vector(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()), true);
                            player.sendMessage("Вы активировали ловушку: " + trapData.getName() + "!");
                            applyEffectsToNearbyPlayers(player, trapData);
                        } catch (Exception e) {
                            player.sendMessage("Ошибка активации ловушки: " + e.getMessage());
                        }
                    } else {
                        player.sendMessage("Схема не найдена для ловушки: " + trapData.getName());
                    }
                }
            }
        }
    }

    // Метод для получения имени ловушки на основе предмета
    private String getTrapName(ItemStack item) {
        for (Map.Entry<String, TrapData> entry : trapDataMap.entrySet()) {
            if (entry.getValue().getItem().equalsIgnoreCase(item.getType().toString())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void applyEffectsToNearbyPlayers(Player player, TrapData trapData) {
        // Здесь можно реализовать логику применения эффектов к ближайшим игрокам
        for (Player nearbyPlayer : Bukkit.getOnlinePlayers()) {
            if (nearbyPlayer.getLocation().distance(player.getLocation()) <= 5) { // Например, радиус 5 блоков
                for (Map.Entry<PotionEffectType, Integer> effect : trapData.getEffects().entrySet()) {
                    nearbyPlayer.addPotionEffect(effect.getKey().createEffect(trapData.getEffectDuration() * 20, effect.getValue()));
                }
            }
        }
    }

    private static class TrapData {
        private final String name;
        private final String item;
        private final String schematic;
        private final Map<PotionEffectType, Integer> effects;
        private final int effectDuration;
        private final String sound;
        private final int horusDelay;  // Задержка для Хоруса
        private final int perlDelay;    // Задержка для Перла

        public TrapData(String name, String item, String schematic, Map<PotionEffectType, Integer> effects,
                        int effectDuration, String sound, int horusDelay, int perlDelay) {
            this.name = name;
            this.item = item;
            this.schematic = schematic;
            this.effects = effects;
            this.effectDuration = effectDuration;
            this.sound = sound;
            this.horusDelay = horusDelay;
            this.perlDelay = perlDelay;
        }

        public String getName() {
            return name;
        }

        public String getItem() {
            return item;
        }

        public String getSchematic() {
            return schematic;
        }

        public Map<PotionEffectType, Integer> getEffects() {
            return effects;
        }

        public int getEffectDuration() {
            return effectDuration;
        }

        public String getSound() {
            return sound;
        }

        public int getHorusDelay() {
            return horusDelay;
        }

        public int getPerlDelay() {
            return perlDelay;
        }
    }
}