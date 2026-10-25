package dev.tanghulu.stash;

import dev.tanghulu.ancientapple.core.ChestTarget;
import dev.tanghulu.ancientapple.core.TargetLoot;
import dev.tanghulu.ancientapple.prediction.AncientLoot;
import dev.tanghulu.ancientapple.prediction.LootSignature;
import dev.tanghulu.ancientapple.prediction.LootVersion;
import net.minecraft.enchantment.*;
import net.minecraft.item.Items;
import net.minecraft.registry.*;
import java.util.List;

public final class AncientLootChecks {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        try { run(); }
        catch(Throwable failure) {
            try(var output=new java.io.PrintStream("build/ancient-loot-failure.txt",java.nio.charset.StandardCharsets.UTF_8)) {
                failure.printStackTrace(output);
            }
            throw failure;
        }
    }
    private static void run() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var registries=new AncientLoot(BuiltinRegistries.createWrapperLookup(),LootVersion.MC_1_21_11).registries();
        var swift=registries.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SWIFT_SNEAK);
        for(int level=1;level<=3;level++) {
            var book=EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(swift,level));
            var target=new ChestTarget(0,-48,0,0,0,1,"ancient_city",0,LootSignature.of(List.of(book),registries));
            check(TargetLoot.count(book)==(level==3?1:0),"server book level "+level);
            check(target.swiftSneakBooks==(level==3?1:0),"native serialized book level "+level);
        }
        check(TargetLoot.count(Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack())==1,"apple retained");
        check(TargetLoot.count(Items.BOOK.getDefaultStack())==0,"plain book rejected");
        int books=0,chests=0;
        for(var version:LootVersion.values()) {
            var loot=new AncientLoot(registries,version);
            for(long seed=1;seed<=100;seed++) {
                var stacks=loot.generate(seed);
                int expected=stacks.stream().mapToInt(TargetLoot::swiftSneakBooks).sum();
                var target=new ChestTarget(0,-48,0,0,0,seed,"ancient_city",0,LootSignature.of(stacks,loot.registries()));
                check(target.swiftSneakBooks==expected,"book signature mismatch for "+version+" seed "+seed);
                books+=expected; chests++;
            }
        }
        check(books>0,"no level III samples exercised");
        System.out.println("Ancient loot: native level I/II/III, apples/plain books and "+chests+" chests across all profiles PASS ("+books+" level III books)");
    }
}
