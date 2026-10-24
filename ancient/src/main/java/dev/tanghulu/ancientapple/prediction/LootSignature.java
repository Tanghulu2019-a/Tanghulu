package dev.tanghulu.ancientapple.prediction;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import java.util.*;

public final class LootSignature {
    private LootSignature() {}
    public static Map<String,Integer> of(Collection<ItemStack> stacks, RegistryWrapper.WrapperLookup registries) {
        Map<String,Integer> result=new TreeMap<>();
        var ops=RegistryOps.of(JsonOps.INSTANCE,registries);
        for (ItemStack stack:stacks) {
            if (stack.isEmpty()) continue;
            JsonElement encoded=ItemStack.CODEC.encodeStart(ops,stack.copyWithCount(1)).getOrThrow();
            result.merge(canonical(encoded).toString(),stack.getCount(),Integer::sum);
        }
        return result;
    }
    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject result=new JsonObject();
            new TreeSet<>(element.getAsJsonObject().keySet()).forEach(k -> result.add(k,canonical(element.getAsJsonObject().get(k))));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result=new JsonArray(); element.getAsJsonArray().forEach(e -> result.add(canonical(e))); return result;
        }
        return element;
    }
}
