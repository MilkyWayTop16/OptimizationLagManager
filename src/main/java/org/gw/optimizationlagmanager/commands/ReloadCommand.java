package org.gw.optimizationlagmanager.commands;

import org.bukkit.command.CommandSender;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.managers.ConfigManager;
import org.gw.optimizationlagmanager.managers.RedstoneManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ReloadCommand implements OptimizationCommand {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final RedstoneManager redstoneManager;

    public ReloadCommand(OptimizationLagManager plugin, ConfigManager configManager, RedstoneManager redstoneManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.redstoneManager = redstoneManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("olm.reload")) {
            configManager.getMessages("no-permission").forEach(sender::sendMessage);
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                redstoneManager.resetRedstoneCooldowns();
                configManager.reloadConfig();
                redstoneManager.setLagDetectionActive(configManager.isLagDetectionEnabled());

                File file = new File(plugin.getDataFolder(), "Опа, попался! Прочитал = гей.txt");
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
                                plugin.getLogger().info("Создан файл 'Опа, попался! Прочитал = гей.txt' при перезагрузке плагина.");
                            }
                        } catch (IOException e) {
                            plugin.getLogger().severe("Ошибка при создании файла 'Опа, попался! Прочитал = гей.txt' при перезагрузке: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                configManager.getMessages("reload.success").forEach(sender::sendMessage);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Конфигурация перезагружена, редстоун-ограничения сброшены");
                }
            } catch (Exception e) {
                configManager.getMessages("reload.error").forEach(sender::sendMessage);
                plugin.getLogger().severe("Ошибка при перезагрузке конфига: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        } else {
            configManager.getMessages("reload.usage").forEach(sender::sendMessage);
        }
    }
}