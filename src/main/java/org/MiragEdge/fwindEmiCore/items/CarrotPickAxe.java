package org.MiragEdge.fwindEmiCore.items;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CarrotPickAxe implements Listener {

    private boolean debugMode;
    private final JavaPlugin plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean registered = false;

    // 配置参数
    private String customItemId;
    private int triggerChance;
    private int minDrops;
    private int maxDrops;
    private Set<Material> enabledBlocks;
    private boolean isItemsAdderLoaded = false;

    public CarrotPickAxe(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 配置加载方法
    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("carrot-pickaxe");
        if (config == null) {
            plugin.getLogger().severe("\\u001B[31m[胡萝卜镐] 配置节不存在，模块已禁用！\\u001B[0m");
            this.triggerChance = 0;
            return;
        }

        // 调试模式
        this.debugMode = config.getBoolean("debug", plugin.getConfig().getBoolean("debug", false));
        debugLog(() -> "调试模式: " + (debugMode ? "\\u001B[33m启用\\u001B[0m" : "\\u001B[34m禁用\\u001B[0m"));

        // 物品ID校验
        this.customItemId = config.getString("item-id", "carrot_pickaxe");
        if (!customItemId.matches("^[a-z0-9_]+$")) {
            plugin.getLogger().severe("\\u001B[31m[胡萝卜镐] 物品ID格式错误，使用默认值！\\u001B[0m");
            customItemId = "carrot_pickaxe";
        }

        // 参数范围控制
        this.triggerChance = Math.max(0, Math.min(100, config.getInt("trigger-chance", 25)));
        this.minDrops = Math.max(0, config.getInt("drops.min", 1));
        this.maxDrops = Math.max(minDrops, config.getInt("drops.max", 3));

        // 方块类型加载
        this.enabledBlocks = config.getStringList("enabled-blocks").stream()
                .map(name -> {
                    try {
                        return Material.valueOf(name.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        debugLog(() -> "无效方块类型: " + name);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (enabledBlocks.isEmpty()) {
            plugin.getLogger().warning("[胡萝卜镐] 生效方块列表为空，功能已禁用！");
            triggerChance = 0;
        }

        debugLog(() -> "配置加载完成: " + customItemId);
    }

    //注册事件
    public void register() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
            plugin.getLogger().info("\\u001B[32m[模块] 胡萝卜镐 事件监听已注册\\u001B[0m");
        }
    }

    //卸载事件
    public void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
            plugin.getLogger().info("\\u001B[32m[模块] 胡萝卜镐 事件监听已注销\\u001B[0m");
        }
    }

    // 调试日志工具
    private void debugLog(Supplier<String> message) {
        if (debugMode) {
            plugin.getLogger().info("[调试] " + message.get());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !isItemsAdderLoaded || triggerChance <= 0) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Block block = event.getBlock();

        debugLog(() -> "触发事件 - 方块: " + block.getType());
        if (!isHoldingValidTool(tool)) return;
        if (!isEnabledBlock(block)) return;
        if (!isTriggerSuccessful()) return;

        applyExtraDrops(event, tool);
    }

    private boolean isHoldingValidTool(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            debugLog(() -> "玩家未持有有效工具");
            return false;
        }

        CustomStack customStack = CustomStack.byItemStack(tool);
        if (customStack == null) {
            debugLog(() -> "工具非ItemsAdder物品");
            return false;
        }

        boolean isMatch = customItemId.equals(customStack.getId());
        if (!isMatch) {
            debugLog(() -> "物品ID不匹配，当前: " + customStack.getId());
        }
        return isMatch;
    }

    private boolean isEnabledBlock(Block block) {
        boolean enabled = enabledBlocks.contains(block.getType());
        if (!enabled) {
            debugLog(() -> "非生效方块: " + block.getType());
        }
        return enabled;
    }

    private boolean isTriggerSuccessful() {
        boolean success = random.nextInt(100) < triggerChance;
        debugLog(() -> "概率检测: " + success);
        return success;
    }

    private void applyExtraDrops(BlockBreakEvent event, ItemStack tool) {
        List<ItemStack> originalDrops = new ArrayList<>(event.getBlock().getDrops(tool));
        event.setDropItems(false);

        // 计算掉落数量
        int amount = minDrops;
        if (maxDrops > minDrops) {
            amount += random.nextInt(maxDrops - minDrops + 1);
        }
        originalDrops.add(new ItemStack(Material.CARROT, amount));

        // 生成掉落物
        originalDrops.forEach(item -> {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    item
            );
            debugLog(() -> "生成掉落物: " + item.getType() + "x" + item.getAmount());
        });
    }

    @EventHandler
    public void onItemsAdderLoad(ItemsAdderLoadDataEvent event) {
        isItemsAdderLoaded = true;
        plugin.getLogger().info("\\u001B[32m[模块] 胡萝卜镐 ItemsAdder数据加载完成\\u001B[0m");

        CustomStack item = CustomStack.getInstance(customItemId);
        if (item == null) {
            plugin.getLogger().severe("\\u001B[31m[胡萝卜镐] 自定义物品加载失败: \\u001B[0m" + customItemId);
        } else {
            debugLog(() -> "\\u001B[32m物品校验成功: \\u001B[0m" + item.getId());
        }
    }
}