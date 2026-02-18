package top.miragedge.feitemscore;

import top.miragedge.feitemscore.command.MainCommand;
import top.miragedge.feitemscore.tools.CarrotPickAxe;
import top.miragedge.feitemscore.weapons.SpicyBlade;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FE_ItemsCore extends JavaPlugin {

    private CarrotPickAxe carrotPickAxeModule;
    private SpicyBlade spicyBladeModule;

    @Override
    public void onEnable() {
        // 注册命令（关键修改点）
        MainCommand mainCommand = new MainCommand(this);
        getCommand("feitemscore").setExecutor(mainCommand);
        getCommand("feitemscore").setTabCompleter(mainCommand);
        // 首次初始化
        initializePlugin();
        getLogger().info("[核心] 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 关闭插件
        shutdownPlugin();
        getLogger().info("[核心] 插件已关闭！");
    }

    // 首次初始化
    private void initializePlugin() {
        saveDefaultConfig();
        reloadConfig();
        carrotPickAxeModule = new CarrotPickAxe(this);
        carrotPickAxeModule.loadConfig();
        carrotPickAxeModule.register();
        
        spicyBladeModule = new SpicyBlade(this);
        spicyBladeModule.loadConfig();
        spicyBladeModule.register();
        
        // 检查CraftEngine是否已加载，如果没有则安排一个调度器任务检查
        if (Bukkit.getPluginManager().getPlugin("CraftEngine") != null) {
            // CraftEngine已经加载
            carrotPickAxeModule.checkCraftEngineLoaded();
            spicyBladeModule.checkCraftEngineLoaded();
        } else {
            // CraftEngine尚未加载，定期检查
            getLogger().info("[胡萝卜镐] 等待CraftEngine加载...");
            getLogger().info("[辛辣之刃] 等待CraftEngine加载...");
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (!carrotPickAxeModule.isCraftEngineLoaded()) {
                    carrotPickAxeModule.checkCraftEngineLoaded();
                } else {
                    // 已加载，取消任务
                    getLogger().info("[胡萝卜镐] CraftEngine已加载，停止检查任务");
                }
                
                if (!spicyBladeModule.isCraftEngineLoaded()) {
                    spicyBladeModule.checkCraftEngineLoaded();
                } else {
                    getLogger().info("[辛辣之刃] CraftEngine已加载，停止检查任务");
                }
            }, 20L, 40L); // 每2秒检查一次
        }
    }

    // 关闭插件
    private void shutdownPlugin() {
        if (carrotPickAxeModule != null) {
            carrotPickAxeModule.unregister();
            carrotPickAxeModule = null;
        }
        if (spicyBladeModule != null) {
            spicyBladeModule.unregister();
            spicyBladeModule = null;
        }
    }

    // 重载插件
    public void reloadPlugin() {
        shutdownPlugin(); // 清理旧模块
        initializePlugin(); // 重新初始化
        getLogger().info("[核心] 配置重载完成！");
    }
}