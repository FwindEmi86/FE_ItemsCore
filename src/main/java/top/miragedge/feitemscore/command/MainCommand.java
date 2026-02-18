package top.miragedge.feitemscore.command;

import top.miragedge.feitemscore.FE_ItemsCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final FE_ItemsCore plugin; // 插件实例引用

    // 构造方法注入插件实例
    public MainCommand(FE_ItemsCore plugin) {
        this.plugin = plugin;
    }

    // 命令执行逻辑
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 主命令匹配（支持别名）
        if (!command.getName().equalsIgnoreCase("feitemscore") && !command.getName().equalsIgnoreCase("fec")) {
            return false;
        }

        // 无参数时显示基础信息
        if (args.length == 0) {
            sender.sendMessage("§l§eFwindEmi-Core §f版本: §a" + plugin.getDescription().getVersion());
            sender.sendMessage("§f使用 §b/fec reload §f重载配置");
            return true;
        }

        // 子命令分发
        switch (args[0].toLowerCase()) {
            case "info":
                handleInfoCommand(sender, args);
                break;
            case "reload":
                handleReloadCommand(sender, args);
                break;
            default:
                sender.sendMessage("§c未知子命令! 可用: info, reload");
                break;
        }
        return true;
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§aFwindEmiCore 插件信息:");
        sender.sendMessage("§l§eFwindEmi-Core §f版本: §a" + plugin.getDescription().getVersion());
        sender.sendMessage("§7作者: 狐风轩汐");
    }

    private void handleReloadCommand(CommandSender sender, String[] args) {
        // 权限检查
        if (!sender.hasPermission("feitemscore.reload")) {
            sender.sendMessage("§c你没有权限执行此命令!");
            return;
        }

        // 调用重载方法
        plugin.reloadPlugin();
        sender.sendMessage("§a[核心] 配置重载完成！");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("info".startsWith(input)) completions.add("info");
            if ("reload".startsWith(input)) completions.add("reload");
        }
        return completions;
    }
}