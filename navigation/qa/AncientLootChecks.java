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
            check(TargetLoot.selectedForQuickLoot(book,false,false,false,true)==(level==3),"quick loot book level "+level);
            check(!TargetLoot.selectedForQuickLoot(book,true,true,true,false),"unchecked books rejected");
            check(target.swiftSneakBooks==(level==3?1:0),"native serialized book level "+level);
        }
        check(TargetLoot.count(Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack())==1,"apple retained");
        check(TargetLoot.count(Items.BOOK.getDefaultStack())==0,"plain book rejected");
        var quickLootItems=List.of(Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack(),
            Items.EXPERIENCE_BOTTLE.getDefaultStack(),Items.GOLDEN_CARROT.getDefaultStack(),
            EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(swift,3)));
        for(int mask=0;mask<16;mask++) {
            for(int item=0;item<4;item++) {
                check(TargetLoot.selectedForQuickLoot(quickLootItems.get(item),(mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0)
                    ==((mask&(1<<item))!=0),"independent quick loot selection mask "+mask+" item "+item);
            }
        }
        for(var item:List.of(Items.GOLDEN_APPLE,Items.GLASS_BOTTLE,Items.CARROT,Items.BOOK,Items.DIAMOND))
            check(!TargetLoot.selectedForQuickLoot(item.getDefaultStack(),true,true,true,true),"unrelated item "+item);
        check(!TargetLoot.selectedForQuickLoot(net.minecraft.item.ItemStack.EMPTY,true,true,true,true),"empty stack rejected");
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
