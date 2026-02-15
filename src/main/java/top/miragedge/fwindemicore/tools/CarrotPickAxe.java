package top.miragedge.fwindemicore.tools;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.CustomItem;
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

    // private boolean debugMode; // 已禁用调试模式
    private final JavaPlugin plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean registered = false;

    // 配置参数
    private String customItemId;
    private int triggerChance;
    private int minDrops;
    private int maxDrops;
    private Set<Material> enabledBlocks;
    private boolean isCraftEngineLoaded = false;

    public CarrotPickAxe(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 配置加载方法
    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("carrot-pickaxe");
        if (config == null) {
            plugin.getLogger().severe("[胡萝卜镐] 配置节不存在，模块已禁用！");
            this.triggerChance = 0;
            return;
        }

        // 调试模式已禁用

        // 物品ID校验
        this.customItemId = config.getString("item-id", "miragedge_items:carrot_pickaxe");
        if (!customItemId.matches("^[a-z0-9_]+:[a-z0-9_]+$")) {
            plugin.getLogger().severe("[胡萝卜镐] 物品ID格式错误，使用默认值！");
            customItemId = "miragedge_items:carrot_pickaxe";
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
            
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (enabledBlocks.isEmpty()) {
            plugin.getLogger().warning("[胡萝卜镐] 生效方块列表为空，功能已禁用！");
            triggerChance = 0;
        }


    }

    //注册事件
    public void register() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
            plugin.getLogger().info("[模块] 胡萝卜镐 事件监听已注册");
        }
    }

    //卸载事件
    public void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
            plugin.getLogger().info("[模块] 胡萝卜镐 事件监听已注销");
        }
    }

    // 调试日志工具已禁用
    /*
    private void debugLog(Supplier<String> message) {
        if (debugMode) {
            plugin.getLogger().info("[调试] " + message.get());
        }
    }
    */

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !isCraftEngineLoaded || triggerChance <= 0) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Block block = event.getBlock();


        if (!isHoldingValidTool(tool)) return;
        if (!isEnabledBlock(block)) return;
        if (!isTriggerSuccessful()) return;

        applyExtraDrops(event, tool);
    }

    private boolean isHoldingValidTool(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {

            return false;
        }

        // 使用CraftEngine API检查是否为自定义物品
        CustomItem<ItemStack> customItem = CraftEngineItems.byItemStack(tool);
        if (customItem == null) {

            return false;
        }

        // 获取物品ID的字符串表示
        String itemId = customItem.id().toString();
        boolean isMatch = customItemId.equals(itemId);
        if (!isMatch) {

        }
        return isMatch;
    }

    private boolean isEnabledBlock(Block block) {
        boolean enabled = enabledBlocks.contains(block.getType());
        if (!enabled) {
    
        }
        return enabled;
    }

    private boolean isTriggerSuccessful() {
        boolean success = random.nextInt(100) < triggerChance;

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

        });
    }

    public void checkCraftEngineLoaded() {
        // 检查CraftEngine插件是否已加载完成
        if (plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            isCraftEngineLoaded = true;
            plugin.getLogger().info("[模块] 胡萝卜镐 CraftEngine数据加载完成");

            // 验证自定义物品是否存在
            CustomItem<ItemStack> item = CraftEngineItems.byId(net.momirealms.craftengine.core.util.Key.of(customItemId));
            if (item == null) {
                plugin.getLogger().severe("[胡萝卜镐] 自定义物品加载失败: " + customItemId);
            } else {
                // debugLog(() -> "物品校验成功: " + item.id().toString());  // 已禁用
            }
        }
    }

    public boolean isCraftEngineLoaded() {
        return isCraftEngineLoaded;
    }
}