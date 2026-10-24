package dev.tanghulu.ancientapple.prediction;

import com.google.gson.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.*;
import net.minecraft.potion.Potions;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Executes the vanilla 1.21.11 ancient-city table against the vanilla enchantment helpers.
 * Unknown functions fail closed: silently skipping an RNG call corrupts later apple rolls. */
public final class AncientLoot {
    private final RegistryWrapper.WrapperLookup registries;
    private final JsonObject table;
    private final List<String> enchantmentOptions;
    public AncientLoot(RegistryWrapper.WrapperLookup registries) {
        this(registries,LootVersion.MC_1_21_11);
    }
    public AncientLoot(RegistryWrapper.WrapperLookup registries,LootVersion version) {
        JsonObject profile=profile(version);
        this.registries=LootEnchantments.create(profile.getAsJsonObject("selection"),registries);
        table=profile.getAsJsonObject("table");
        enchantmentOptions=new ArrayList<>();
        profile.getAsJsonArray("enchantments").forEach(e -> enchantmentOptions.add(e.getAsString()));
    }
    public static JsonObject table(LootVersion version) {
        return profile(version).getAsJsonObject("table");
    }
    /** Signatures must encode enchantments through their prediction registry owner. */
    public RegistryWrapper.WrapperLookup registries() { return registries; }
    private static JsonObject profile(LootVersion version) {
        String path="/loot-profiles/"+version+".json";
        try (InputStream stream=AncientLoot.class.getResourceAsStream(path)) {
            if (stream==null) throw new IllegalStateException("无法读取服务器战利品规则："+version);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public List<ItemStack> generate(long seed) {
        if (seed==0) throw new IllegalArgumentException("LootTableSeed 为 0，不能确定性预测");
        Random random=Random.create(seed);
        List<ItemStack> result=new ArrayList<>();
        for (JsonElement value:table.getAsJsonArray("pools")) {
            JsonObject pool=value.getAsJsonObject();
            if (pool.has("conditions")) throw new IllegalStateException("不支持修改后的战利品条件");
            JsonArray entries=pool.getAsJsonArray("entries");
            int total=0;
            for (JsonElement e:entries) total+=weight(e.getAsJsonObject());
            int rolls=integer(pool.get("rolls"),random);
            for (int i=0;i<rolls;i++) {
                int choice=random.nextInt(total);
                for (JsonElement e:entries) {
                    JsonObject entry=e.getAsJsonObject(); choice-=weight(entry);
                    if (choice>=0) continue;
                    if (entry.get("type").getAsString().equals("minecraft:empty")) break;
                    if (!entry.get("type").getAsString().equals("minecraft:item")) throw new IllegalStateException("不支持的战利品项目");
                    ItemStack stack=new ItemStack(Registries.ITEM.get(Identifier.of(entry.get("name").getAsString())));
                    if (entry.has("functions")) for (JsonElement f:entry.getAsJsonArray("functions")) stack=apply(stack,f.getAsJsonObject(),random);
                    while (!stack.isEmpty()) {
                        int count=Math.min(stack.getCount(),stack.getMaxCount());
                        result.add(stack.copyWithCount(count)); stack.decrement(count);
                    }
                    break;
                }
            }
        }
        // A vanilla chest has 27 slots. Reproduce the supplyInventory shuffle before truncation.
        List<Integer> slots=new ArrayList<>(); for(int i=0;i<27;i++) slots.add(i);
        shuffle(slots,random);
        List<ItemStack> split=new ArrayList<>();
        result.removeIf(s -> { if(s.getCount()>1) { split.add(s); return true; } return s.isEmpty(); });
        while (27-result.size()-split.size()>0 && !split.isEmpty()) {
            ItemStack stack=split.remove(random.nextInt(split.size()));
            ItemStack other=stack.split(MathHelper.nextInt(random,1,stack.getCount()/2));
            if(stack.getCount()>1 && random.nextBoolean()) split.add(stack); else result.add(stack);
            if(other.getCount()>1 && random.nextBoolean()) split.add(other); else result.add(other);
        }
        result.addAll(split); shuffle(result,random);
        return result.size()>27 ? new ArrayList<>(result.subList(0,27)) : result;
    }
    private ItemStack apply(ItemStack stack,JsonObject f,Random random) {
        switch (f.get("function").getAsString()) {
            case "minecraft:set_count" -> stack.setCount(integer(f.get("count"),random)+(f.has("add")&&f.get("add").getAsBoolean()?stack.getCount():0));
            case "minecraft:set_damage" -> stack.setDamage(MathHelper.floor((1-MathHelper.clamp(number(f.get("damage"),random),0,1))*stack.getMaxDamage()));
            case "minecraft:set_potion" -> {
                if(!f.get("id").getAsString().equals("minecraft:strong_regeneration")) throw new IllegalStateException("意外的药水类型");
                stack.set(DataComponentTypes.POTION_CONTENTS,new PotionContentsComponent(Potions.STRONG_REGENERATION));
            }
            case "minecraft:enchant_with_levels" -> {
                int level=integer(f.get("levels"),random);
                stack=EnchantmentHelper.enchant(random,stack,level,enchantments(f.get("options").getAsString()).stream());
            }
            case "minecraft:enchant_randomly" -> {
                List<RegistryEntry<Enchantment>> options=enchantments(f.get("options").getAsString());
                RegistryEntry<Enchantment> enchantment=options.get(random.nextInt(options.size()));
                int level=MathHelper.nextInt(random,enchantment.value().getMinLevel(),enchantment.value().getMaxLevel());
                if(stack.isOf(Items.BOOK)) stack=EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(enchantment,level));
                else stack.addEnchantment(enchantment,level);
            }
            default -> throw new IllegalStateException("未适配的战利品函数: "+f.get("function"));
        }
        return stack;
    }
    private List<RegistryEntry<Enchantment>> enchantments(String name) {
        var lookup=registries.getOrThrow(RegistryKeys.ENCHANTMENT);
        // A translated server connection exposes client-version tags. Use the selected
        // backend's ordered candidates, notably excluding lunge before 1.21.11.
        if(name.equals("#minecraft:on_random_loot"))return enchantmentOptions.stream()
            .map(id -> lookup.getOrThrow(RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(id))))
            .map(e -> (RegistryEntry<Enchantment>)e).toList();
        if(name.startsWith("#")) throw new IllegalStateException("未适配的附魔标签："+name);
        return List.of(lookup.getOrThrow(RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(name))));
    }
    private static int weight(JsonObject e) { return e.has("weight")?e.get("weight").getAsInt():1; }
    private static int integer(JsonElement e,Random r) {
        if(e.isJsonPrimitive()) return MathHelper.floor(e.getAsFloat());
        JsonObject o=e.getAsJsonObject();
        return MathHelper.nextInt(r,MathHelper.floor(o.get("min").getAsFloat()),MathHelper.floor(o.get("max").getAsFloat()));
    }
    private static float number(JsonElement e,Random r) {
        if(e.isJsonPrimitive()) return e.getAsFloat();
        JsonObject o=e.getAsJsonObject(); return MathHelper.nextFloat(r,o.get("min").getAsFloat(),o.get("max").getAsFloat());
    }
    private static <T> void shuffle(List<T> list,Random r) {
        for(int i=list.size();i>1;i--) Collections.swap(list,i-1,r.nextInt(i));
    }
}
