package dev.tanghulu.ancientapple.prediction;

import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import net.minecraft.component.ComponentMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.stream.Stream;

/** Private RNG selection rules for ancient-city leggings/hoes and random books.
 * Never registers objects or edits tags in the connected game's registries. */
final class LootEnchantments {
    private LootEnchantments() {}
    static RegistryWrapper.WrapperLookup create(JsonObject rules,RegistryWrapper.WrapperLookup server) {
        var local=new SimpleRegistry<Enchantment>(RegistryKeys.ENCHANTMENT,Lifecycle.stable());
        Map<String,TagKey<Enchantment>> exclusions=new LinkedHashMap<>();
        for(var entry:rules.entrySet()) {
            String name=entry.getKey(); var rule=entry.getValue().getAsJsonObject();
            List<RegistryEntry<Item>> items=new ArrayList<>();
            rule.getAsJsonArray("items").forEach(id -> items.add(Registries.ITEM.getOrThrow(RegistryKey.of(RegistryKeys.ITEM,Identifier.of(id.getAsString())))));
            var applicable=RegistryEntryList.of(items);
            var definition=new Enchantment.Definition(applicable,Optional.empty(),rule.get("weight").getAsInt(),
                rule.get("max_level").getAsInt(),cost(rule.getAsJsonObject("min_cost")),cost(rule.getAsJsonObject("max_cost")),0,List.of());
            var excluded=TagKey.of(RegistryKeys.ENCHANTMENT,Identifier.of("ancient_apple_hunter",Identifier.of(name).getPath()));
            exclusions.put(name,excluded);
            var value=new Enchantment(Text.literal(name),definition,RegistryEntryList.of(local,excluded),ComponentMap.EMPTY);
            local.add(RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(name)),value,RegistryEntryInfo.DEFAULT);
        }
        for(var entry:exclusions.entrySet()) {
            List<RegistryEntry<Enchantment>> incompatible=new ArrayList<>();
            rules.getAsJsonObject(entry.getKey()).getAsJsonArray("exclusive_set").forEach(id -> {
                if(rules.has(id.getAsString()))incompatible.add(local.getOrThrow(RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(id.getAsString()))));
            });
            local.setEntries(entry.getValue(),incompatible);
        }
        local.freeze();
        return RegistryWrapper.WrapperLookup.of(Stream.concat(server.stream().filter(r -> !r.getKey().equals(RegistryKeys.ENCHANTMENT)),Stream.of(local)));
    }
    private static Enchantment.Cost cost(JsonObject rule) {
        return new Enchantment.Cost(rule.get("base").getAsInt(),rule.get("per_level_above_first").getAsInt());
    }
}
