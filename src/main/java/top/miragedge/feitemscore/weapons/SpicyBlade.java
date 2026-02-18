package top.miragedge.feitemscore.weapons;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SpicyBlade implements Listener {

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
    // 新增：攻速提升参数
    private boolean attackSpeedSkillEnabled; // 是否启用攻速技能
    private double attackSpeedIncrease;
    private int attackSpeedDuration;
    private int attackSpeedCooldown;

    // 冷却时间追踪
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    
    // ProtocolLib包监听器
    private PacketAdapter useItemPacketAdapter;

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
        
        // 新增：攻速提升参数
        this.attackSpeedSkillEnabled = config.getBoolean("attack-speed-skill-enabled", false); // 默认不启用
        this.attackSpeedIncrease = config.getDouble("attack-speed-increase-percent", 0.4); // 40%攻速提升
        this.attackSpeedDuration = config.getInt("attack-speed-duration", 10); // 10秒持续时间
        this.attackSpeedCooldown = config.getInt("attack-speed-cooldown", 18); // 18秒冷却时间
    }

    //注册事件
    public void register() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            
            // 注册ProtocolLib包监听器
            registerPacketListener();
            
            registered = true;
            plugin.getLogger().info("[模块] 辛辣之刃 事件监听已注册");
        }
    }
    
    // 注册ProtocolLib包监听器
    private void registerPacketListener() {
        useItemPacketAdapter = new PacketAdapter(plugin, PacketType.Play.Client.USE_ITEM) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                    onUseItemPacket(event);
                }
            }
        };
        
        ProtocolLibrary.getProtocolManager().addPacketListener(useItemPacketAdapter);
    }

    //卸载事件
    public void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            
            // 注销ProtocolLib包监听器
            unregisterPacketListener();
            
            registered = false;
            plugin.getLogger().info("[模块] 辛辣之刃 事件监听已注销");
        }
    }
    
    // 注销ProtocolLib包监听器
    private void unregisterPacketListener() {
        if (useItemPacketAdapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(useItemPacketAdapter);
            useItemPacketAdapter = null;
        }
    }

    // 使用ProtocolLib处理右键技能
    private void onUseItemPacket(PacketEvent event) {
        if (!event.isServerPacket()) {
            Player player = event.getPlayer();
            ItemStack weapon = player.getInventory().getItemInMainHand();

            if (!isHoldingSpicyBlade(weapon)) return;

            // 检查攻速技能是否启用
            if (!attackSpeedSkillEnabled) {
                return; // 如果未启用，则直接返回，不执行技能
            }

            // 检查是否在冷却中
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long lastUse = cooldowns.getOrDefault(playerId, 0L);
            long timeSinceLastUse = currentTime - lastUse;
            long cooldownTimeLeft = (attackSpeedCooldown * 1000) - timeSinceLastUse;

            if (timeSinceLastUse < attackSpeedCooldown * 1000) {
                // 显示冷却时间
                int secondsLeft = (int) Math.ceil(cooldownTimeLeft / 1000.0);
                player.sendActionBar("§c技能冷却中: §e" + secondsLeft + "s");
                return;
            }

            // 应用攻速提升
            applyAttackSpeedBoost(player);

            // 更新冷却时间
            cooldowns.put(playerId, currentTime);

            // 显示ActionBar消息
            player.sendActionBar("§a攻速提升 §e40% §a持续 §e" + attackSpeedDuration + "§a 秒");
        }
    }
    
    // 保留Bukkit事件用于其他右键交互（如点燃目标等）
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isHoldingSpicyBlade(weapon)) return;

        // 检查攻速技能是否启用 - 如果启用了ProtocolLib版本，则跳过Bukkit版本的技能触发
        // 仅保留其他功能（如点燃、失明等）的触发
    }

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
    
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AttributeInstance attackSpeedAttribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttribute != null) {
            // 使用现代API移除所有同名修饰符
            attackSpeedAttribute.getModifiers().stream()
                .filter(modifier -> "spicy_blade_attack_speed".equals(modifier.getKey().getKey()))
                .forEach(modifier -> attackSpeedAttribute.removeModifier(modifier));
        }
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

    private void applyAttackSpeedBoost(Player player) {
        AttributeInstance attackSpeedAttribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttribute != null) {
            // 先移除现有的同名修饰符
            attackSpeedAttribute.getModifiers().stream()
                .filter(modifier -> "spicy_blade_attack_speed".equals(modifier.getKey().getKey()))
                .forEach(modifier -> attackSpeedAttribute.removeModifier(modifier));

            // 创建新的攻速提升修饰符 - 增加原始攻速的一定百分比
            // 例如，如果attackSpeedIncrease是0.4，这将使攻速增加40%
            AttributeModifier modifier = new AttributeModifier(
                    new NamespacedKey(plugin, "spicy_blade_attack_speed"), // 使用NamespacedKey
                    attackSpeedIncrease, // 攻速增加的倍数
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1 // 将原始值乘以(1 + value)
            );
            
            attackSpeedAttribute.addModifier(modifier);

            // 播放开始音效
            player.playSound(player.getLocation(), "entity.arrow.shoot", 0.5F, 1.5F);

            // 持续时间后移除攻速提升
            new BukkitRunnable() {
                @Override
                public void run() {
                    AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
                    if (attr != null) {
                        // 移除特定名称的修饰符
                        attr.getModifiers().stream()
                            .filter(m -> "spicy_blade_attack_speed".equals(m.getKey().getKey()))
                            .forEach(m -> attr.removeModifier(m));
                            
                        // 播放结束音效 (辣椒效果消失)
                        player.playSound(player.getLocation(), "entity.enderman.teleport", 0.4F, 0.7F);
                    }
                }
            }.runTaskLater(plugin, attackSpeedDuration * 20L); // 转换为ticks
        }
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