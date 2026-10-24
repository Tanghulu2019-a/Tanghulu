package dev.tanghulu.ancientapple;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tanghulu.ancientapple.prediction.LootVersion;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public final class HunterCommand extends Command {
    private final HunterModule module;
    public HunterCommand(HunterModule module) { super("aah","古城附魔金苹果箱定位。","古城箱"); this.module=module; }
    @Override public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(c -> { info("seed <种子> | loot <版本：1.21.1～26.3> | scan | cancel | diagnose | blacklist（管理窗口）| blacklist on/off/list [页码]/clear"); return SINGLE_SUCCESS; });
        builder.then(literal("seed").then(argument("seed",LongArgumentType.longArg()).executes(c -> { module.seed.set(Long.toString(LongArgumentType.getLong(c,"seed"))); if(!module.isActive())module.enable(); return SINGLE_SUCCESS; })));
        var loot=literal("loot");
        for(LootVersion version:LootVersion.values())loot.then(literal(version.toString()).executes(c -> {
            module.lootVersion.set(version); info("服务器战利品模型：%s，请重新 scan。",version); return SINGLE_SUCCESS;
        }));
        builder.then(loot);
        builder.then(literal("scan").executes(c -> { module.scan(); return SINGLE_SUCCESS; }));
        builder.then(literal("cancel").executes(c -> { module.cancelSearch(); return SINGLE_SUCCESS; }));
        builder.then(literal("diagnose").executes(c -> { module.diagnose(); return SINGLE_SUCCESS; }));
        builder.then(literal("blacklist").executes(c -> { module.openBlacklist(); return SINGLE_SUCCESS; })
            .then(literal("list").executes(c -> { module.listBlacklist(1); return SINGLE_SUCCESS; })
                .then(argument("page",IntegerArgumentType.integer(1)).executes(c -> { module.listBlacklist(IntegerArgumentType.getInteger(c,"page")); return SINGLE_SUCCESS; })))
            .then(literal("on").executes(c -> { module.blacklistEnabled.set(true); return SINGLE_SUCCESS; }))
            .then(literal("off").executes(c -> { module.blacklistEnabled.set(false); return SINGLE_SUCCESS; }))
            .then(literal("clear").executes(c -> { module.clearBlacklist(); return SINGLE_SUCCESS; })));
    }
}
