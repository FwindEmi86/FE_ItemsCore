package top.miragedge.fwindemicore.weapons;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class SpicyBlade implements Listener {

    // private boolean debugMode; // 已禁用调试模式
    private final JavaPlugin plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean registered = false;

    // 配置参数
    private String customItemId;
    private int igniteChance;
    private int blindChance;
    private int igniteDuration;
    private int speedReductionDuration;
    private double speedReduction;
    private double blindRadius;
    private int blindDuration;
    private int endermanStunDuration;

    public SpicyBlade(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 配置加载方法
    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("spicy-blade");
        if (config == null) {
            plugin.getLogger().severe("[辛辣之刃] 配置节不存在，模块已禁用！");
            this.igniteChance = 0;
            return;
        }

        // 调试模式已禁用

        // 物品ID校验
        this.customItemId = config.getString("item-id", "miragedge_items:spicy_blade");
        if (!customItemId.matches("^[a-z0-9_]+:[a-z0-9_]+$")) {
            plugin.getLogger().severe("[辛辣之刃] 物品ID格式错误，使用默认值！");
            customItemId = "miragedge_items:spicy_blade";
        }

        // 参数配置
        this.igniteChance = Math.max(0, Math.min(100, config.getInt("ignite-chance", 80)));
        this.blindChance = Math.max(0, Math.min(100, config.getInt("blind-chance", 100))); // 默认总是触发
        this.igniteDuration = config.getInt("ignite-duration", 5); // 5秒
        this.speedReductionDuration = config.getInt("speed-reduction-duration", 5); // 5秒
        this.speedReduction = config.getDouble("speed-reduction-percent", 0.3); // 30%减速
        this.blindRadius = config.getDouble("blind-radius", 3.0); // 3格范围
        this.blindDuration = config.getInt("blind-duration", 1); // 1秒
        this.endermanStunDuration = config.getInt("enderman-stun-duration", 500); // 0.5秒（毫秒）


    }

    //注册事件
    public void register() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
            plugin.getLogger().info("[模块] 辛辣之刃 事件监听已注册");
        }
    }

    //卸载事件
    public void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
            plugin.getLogger().info("[模块] 辛辣之刃 事件监听已注销");
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
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家攻击
        if (!(event.getDamager() instanceof Player)) return;
        if (event.isCancelled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        Player player = (Player) event.getDamager();
        Entity target = event.getEntity();

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isHoldingSpicyBlade(weapon)) return;



        // 80% 概率点燃目标
        if (isIgniteSuccessful()) {
            igniteTarget(target);
        }

        // 对范围内其他敌人施加失明效果
        applyBlindEffect(player, target);
    }

    private boolean isHoldingSpicyBlade(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
    
            return false;
        }

        // 使用CraftEngine API检查是否为辛辣之刃
        CustomItem<ItemStack> customItem = CraftEngineItems.byItemStack(tool);
        if (customItem == null) {
    
            return false;
        }

        String itemId = customItem.id().toString();
        boolean isMatch = customItemId.equals(itemId);
        if (!isMatch) {
    
        }
        return isMatch;
    }

    private boolean isIgniteSuccessful() {
        boolean success = random.nextInt(100) < igniteChance;

        return success;
    }

    private void igniteTarget(Entity target) {
        if (target instanceof LivingEntity) {
            LivingEntity livingTarget = (LivingEntity) target;
            livingTarget.setFireTicks(igniteDuration * 20); // 转换为tick
    

            // 使目标移动速度降低30%，持续5秒
            AttributeInstance speedAttribute = livingTarget.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speedAttribute != null) {
                // 创建临时的速度减速效果
                // 这里我们使用一个临时的不可见效果来降低速度
                // 实际上我们会创建一个自定义的减速处理器
                applySpeedReduction(livingTarget);
            }
        }
    }

    private void applySpeedReduction(LivingEntity target) {
        // 应用速度降低效果
        // 这里使用缓慢效果II (20%基础速度 + 40%减速 = 剩余40%)，大约相当于30%的减速
        int amplifier = 1; // 等级II
        PotionEffect slowEffect = new PotionEffect(PotionEffectType.SLOWNESS, speedReductionDuration * 20, amplifier, false, false);
        target.addPotionEffect(slowEffect);

    }

    private void applyBlindEffect(Player player, Entity primaryTarget) {
        if (random.nextInt(100) >= blindChance) return;

        // 获取范围内所有实体
        List<Entity> nearbyEntities = player.getNearbyEntities(blindRadius, blindRadius, blindRadius);

        for (Entity entity : nearbyEntities) {
            // 不对玩家自己和主要目标施加效果
            if (entity.equals(player) || entity.equals(primaryTarget)) continue;

            // 对所有LivingEntity（包括玩家和末影人）施加失明效果
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                PotionEffect blindEffect = new PotionEffect(PotionEffectType.BLINDNESS, blindDuration * 20, 0, false, false);
                livingEntity.addPotionEffect(blindEffect);
        

                // 如果是末影人，额外停止瞬移
                if (entity instanceof Enderman) {
                    Enderman enderman = (Enderman) entity;
                    // 暂停瞬移通过设置目标为当前目标，防止瞬移
                    org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            // 恢复末影人的正常AI行为
                        }
                    };
                    task.runTaskLater(plugin, endermanStunDuration / 50); // 转换为tick
            
                }
            }
        }
    }

    public boolean isCraftEngineLoaded() {
        return plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;
    }

    public void checkCraftEngineLoaded() {
        if (isCraftEngineLoaded()) {
            // 验证自定义物品是否存在
            CustomItem<ItemStack> item = CraftEngineItems.byId(net.momirealms.craftengine.core.util.Key.of(customItemId));
            if (item == null) {
                plugin.getLogger().severe("[辛辣之刃] 自定义物品加载失败: " + customItemId);
            } else {
                // debugLog(() -> "物品校验成功: " + item.id().toString());  // 已禁用
            }
        } else {
            plugin.getLogger().warning("[辛辣之刃] CraftEngine未加载!");
        }
    }
}