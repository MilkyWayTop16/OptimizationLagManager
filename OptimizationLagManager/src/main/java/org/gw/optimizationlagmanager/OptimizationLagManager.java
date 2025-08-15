package org.gw.optimizationlagmanager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.optimizationlagmanager.commands.*;
import org.gw.optimizationlagmanager.listeners.CommandSendListener;
import org.gw.optimizationlagmanager.managers.*;
import org.gw.optimizationlagmanager.utils.HexColors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class OptimizationLagManager extends JavaPlugin {
    private ConfigManager configManager;
    private ChunkManager chunkManager;
    private MobSpawnManager mobSpawnManager;
    private RedstoneManager redstoneManager;
    private PhysicsManager physicsManager;
    private EntityManager entityManager;
    private WorldGenManager worldGenManager;
    private ContainerManager containerManager;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        chunkManager = new ChunkManager(this, configManager);
        mobSpawnManager = new MobSpawnManager(this, configManager);
        redstoneManager = new RedstoneManager(this, configManager);
        physicsManager = new PhysicsManager(this, configManager);
        entityManager = new EntityManager(this, configManager);
        worldGenManager = new WorldGenManager(this, configManager);
        containerManager = new ContainerManager(this, configManager);

        ReloadCommand reloadCommand = new ReloadCommand(this, configManager, redstoneManager);
        RedstoneLagCommand redstoneLagCommand = new RedstoneLagCommand(this, configManager, redstoneManager);
        AlertsCommand alertsCommand = new AlertsCommand(this, configManager);
        PhysicsCommand physicsCommand = new PhysicsCommand(this, configManager);

        getCommand("olm").setExecutor(new CommandsHandler(this, configManager, reloadCommand, redstoneLagCommand, alertsCommand, physicsCommand));
        getCommand("olm").setTabCompleter(new CommandsTabCompleter(configManager));

        getServer().getPluginManager().registerEvents(new CommandSendListener(), this);

        createInfoFile();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00█▀█ █▀█ ▀█▀ █ █▀▄▀█ █ ▀█ ▄▀█ ▀█▀ █ █▀█ █▄░█ █░░ ▄▀█ █▀▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00█▄█ █▀▀ ░█░ █ █░▀░█ █ █▄ █▀█ ░█░ █ █▄█ █░▀█ █▄▄ █▀█ █▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#00FF26▶ Плагин успешно загружен!", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fПлагин был написан &#FFFF00@vkusniy_milkyway &fдля &#FFFF00https://t.me/gornasquad", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВерсия плагина: &#FFFF00" + getDescription().getVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВерсия сервера: &#FFFF00" + Bukkit.getMinecraftVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВремя загрузки: &#FFFF00" + loadTime + " мс", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();

        try {
            chunkManager.savePreloadedChunkCache();
            if (configManager.isConsoleLoggingEnabled()) {
                getLogger().info("Кэш предзагруженных чанков сохранён при выключении плагина.");
            }
        } catch (Exception e) {
            getLogger().severe("Ошибка сохранения кэша чанков при выключении: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }

        try {
            redstoneManager.resetRedstoneCooldowns();
        } catch (Exception e) {
            getLogger().severe("Ошибка при сбросе кулдаунов редстоуна: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }

        long unloadTime = System.currentTimeMillis() - startTime;
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00█▀█ █▀█ ▀█▀ █ █▀▄▀█ █ ▀█ ▄▀█ ▀█▀ █ █▀█ █▄░█ █░░ ▄▀█ █▀▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00█▄█ █▀▀ ░█░ █ █░▀░█ █ █▄ █▀█ ░█░ █ █▄█ █░▀█ █▄▄ █▀█ █▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FB8808▶ Плагин успешно выгружен!", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВерсия плагина: &#FFFF00" + getDescription().getVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВерсия сервера: &#FFFF00" + Bukkit.getMinecraftVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&#FFFF00◆ &fВремя выгрузки: &#FFFF00" + unloadTime + " мс", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
    }

    private void createInfoFile() {
        File file = new File(getDataFolder(), "Опа, попался! Прочитал = гей.txt");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(
                            "\n" +
                                    "         █▀█ █▀█ ▀█▀ █ █▀▄▀█ █ ▀█ ▄▀█ ▀█▀ █ █▀█ █▄░█ █░░ ▄▀█ █▀▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█\n" +
                                    "         █▄█ █▀▀ ░█░ █ █░▀░█ █ █▄ █▀█ ░█░ █ █▄█ █░▀█ █▄▄ █▀█ █▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄\n" +
                                    "\n" +
                                    "                          ◆ Плагин был написан автором @vkusniy_milkyway специально для\n" +
                                    "                      сервера GornaWorld.fun и для Телеграмм-Канала: https://t.me/gornasquad\n" +
                                    "                                    (Чтобы пользоваться им могли все)\n" +
                                    "\n" +
                                    "\n" +
                                    "\n" +
                                    "              ◆ Так, ты наверное спросишь зачем данный txt-файл есть в этом плагине, а я скажу тебе: \n" +
                                    "             это для того, чтобы все поняли, чей это плагин и для кого он был создан, так как я знаю, \n" +
                                    "          что многие будут сливать данный плагин по всем разным чатам, тг каналам, друзьям и так далее. \n" +
                                    "             И кстати, ты этот файл можешь не удалять, так как он всё равно снова создаться, хехехех :)\n" +
                                    "\n" +
                                    "   ◆ Ну а вообще, если ты нашёл баг или ошибку в плагине, то напиши мне просто в тг -  https://t.me/vkusniy_milkyway\n" +
                                    "\n" +
                                    "         ◆ И также, данный плагин был полностью написан для моего, наверно уже открытого или ещё не открытого, \n" +
                                    "            сервера GornaWorld.fun и для моих подписчиков в моём телеграм-канале - https://t.me/gornasquad\n"
                    );
                    if (configManager.isConsoleLoggingEnabled()) {
                        getLogger().info("Создан файл 'Опа, попался! Прочитал = гей.txt' в папке плагина.");
                    }
                } catch (IOException e) {
                    getLogger().severe("Ошибка при создании файла 'Опа, попался! Прочитал = гей.txt': " + e.getMessage());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}