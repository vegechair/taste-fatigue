package com.juejuecat.taste_fatigue;

import com.juejuecat.taste_fatigue.TasteFatigueConfig.Effect;
import com.juejuecat.taste_fatigue.TasteFatigueConfig.Stage;
import com.juejuecat.taste_fatigue.network.TFNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

public class TasteFatigueEvents {

    private int decayTimer = 0;
    private static final int MAX_DISGUST = 200;
    private static final int DISGUST_DECAY_TICKS = 20 * 60 * 10;

    private static Stage getStage(int disgust) {
        for (Stage s : TasteFatigueConfig.CONFIG.stages) {
            if (disgust <= s.maxDisgust) return s;
        }
        return TasteFatigueConfig.CONFIG.stages[TasteFatigueConfig.CONFIG.stages.length - 1];
    }

    @SubscribeEvent
    public void onFoodUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem();
        Item item = stack.getItem();
        if (item.getFoodProperties(stack, player) == null) return;

        ResourceLocation foodId = BuiltInRegistries.ITEM.getKey(item);
        int disgust = getDisgust(player, foodId);

        Stage stage = getStage(disgust);
        int newDuration = (int) (item.getUseDuration(stack, player) * stage.durationMult);

        event.setDuration(newDuration);
        displayDisgustLevel(player, disgust);
        player.getPersistentData().putInt("CurrentFoodDuration", newDuration);

        TFNetwork.sendToPlayer(player, newDuration);

        player.getPersistentData().putInt("TF_ClientDur", newDuration);
    }

    @SubscribeEvent
    public void onFoodUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // 只在客户端执行
        if (player.level().isClientSide) {
            int left = player.getPersistentData().getInt("TF_ClientDur");
            if (left > 0) {
                left--;                                   // 每 tick 减 1
                player.getPersistentData().putInt("TF_ClientDur", left);
                event.setDuration(left);                  // ✅ 让动画继续
            }
        }
    }

    @SubscribeEvent
    public void onFoodUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player) {
            player.getPersistentData().remove("CurrentFoodDuration");
        }
    }

    @SubscribeEvent
    public void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem();
        Item item = stack.getItem();
        if (item.getFoodProperties(stack, player) == null) return;

        ResourceLocation foodId = BuiltInRegistries.ITEM.getKey(item);
        int newDisgust = incrementDisgust(player, foodId);

        applyDisgustEffects(player, newDisgust);
        player.releaseUsingItem();
    }

    /* ================= 显示 ================= */
    private void displayDisgustLevel(Player player, int disgust) {
        Stage stage = getStage(disgust);
        player.displayClientMessage(
                Component.literal(disgust + " - " + stage.emoji)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withItalic(true)),
                true);
    }

    /* ================= 效果 ================= */
    private void applyDisgustEffects(Player player, int disgust) {

        if (player.level().isClientSide) return;

        Stage stage = getStage(disgust);
        for (Effect e : stage.effects) {
            switch (e.type) {
                case "heal" -> player.heal(e.amount);
                case "hurt" -> player.hurt(player.damageSources().magic(), e.amount);
                case "potion" -> {
                    // 1.21.1 写法：先拿到 Holder<MobEffect>
                    var registry = player.level().registryAccess().registryOrThrow(Registries.MOB_EFFECT);
                    var holder   = registry.getHolder(ResourceLocation.parse(e.potion)).orElse(null);
                    if (holder != null) {
                        // 用这个 Holder 创建药水实例
                        player.addEffect(new MobEffectInstance(holder, e.duration, (int) e.amount));
                    }
                }
            }
        }
    }

    private int getDisgust(Player player, ResourceLocation foodId) {
        return loadPlayerDisgust(player).getOrDefault(foodId, 0);
    }

    private int incrementDisgust(Player player, ResourceLocation foodId) {
        Map<ResourceLocation, Integer> map = loadPlayerDisgust(player);
        int cur = map.getOrDefault(foodId, 0);
        int next = Math.min(cur + 1, MAX_DISGUST);
        map.put(foodId, next);
        savePlayerDisgust(player, map);
        return next;
    }

    private Map<ResourceLocation, Integer> loadPlayerDisgust(Player player) {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        CompoundTag root = player.getPersistentData();
        if (root.contains("TasteFatigueDisgust", Tag.TAG_LIST)) {
            ListTag list = root.getList("TasteFatigueDisgust", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag tag = (CompoundTag) t;
                ResourceLocation id = ResourceLocation.tryParse(tag.getString("FoodId"));
                if (id != null) map.put(id, tag.getInt("Value"));
            }
        }
        return map;
    }

    private void savePlayerDisgust(Player player, Map<ResourceLocation, Integer> map) {
        ListTag list = new ListTag();
        map.forEach((id, val) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("FoodId", id.toString());
            tag.putInt("Value", val);
            list.add(tag);
        });
        player.getPersistentData().put("TasteFatigueDisgust", list);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++decayTimer >= DISGUST_DECAY_TICKS) {
            decayTimer = 0;
            event.getServer().getPlayerList().getPlayers()
                    .forEach(this::decayPlayerDisgust);
        }
    }

    private void decayPlayerDisgust(Player player) {
        Map<ResourceLocation, Integer> map = loadPlayerDisgust(player);
        boolean changed = false;
        for (ResourceLocation key : map.keySet()) {
            int v = map.get(key);
            if (v > 0) {
                map.put(key, v - 1);
                changed = true;
            }
        }
        if (changed) savePlayerDisgust(player, map);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getPersistentData()
                    .merge(event.getOriginal().getPersistentData());
        }
    }
}
