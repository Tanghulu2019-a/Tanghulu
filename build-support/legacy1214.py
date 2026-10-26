"""Explicit API translations for the Minecraft 1.21.4 / Meteor 1.21.4 artifact.

These retain behavior; the Minecraft version is never changed at runtime.
"""
import re


def transform(path, s):
    # Module.enable() was introduced after Meteor 1.21.4. Existing guarded calls
    # can use toggle(); the unguarded travel call must remain idempotent.
    s = s.replace('if(!isActive())enable();', 'if(!isActive())toggle();')
    s = s.replace('if(!module.isActive())module.enable();', 'if(!module.isActive())module.toggle();')
    if path.endswith('/AutoLogPlus.java'):
        s = s.replace('            enable();', '            if (!isActive()) toggle();')
    s = s.replace('SharedConstants.getGameVersion().name()', 'SharedConstants.getGameVersion().getName()')
    s = s.replace('packet.syncId()', 'packet.getSyncId()').replace('packet.cursorStack()', 'packet.getCursorStack()')
    s = s.replace('h.serverInventory(packet.getSyncId(),packet.contents())', 'h.serverInventory(packet.getSyncId(),packet.getContents())')
    s = re.sub(r'\.getString\(("[^"]*"),\s*""\)', r'.getString(\1)', s)
    s = re.sub(r'\.getLong\(("[^"]*"),\s*0\)', r'.getLong(\1)', s)
    s = s.replace('.getEntityPos()', '.getPos()').replace('.getCameraPos()', '.getPos()')
    s = s.replace('.getGameProfile().name()', '.getGameProfile().getName()')
    s = s.replace('.asString().get()', '.asString()').replace('.asString().orElse("")', '.asString()')
    s = s.replace('valueTag.getString(key).get()', 'valueTag.getString(key)')
    s = s.replace('mapTag.getList(key).orElse(new NbtList())', 'mapTag.getList(key, net.minecraft.nbt.NbtElement.STRING_TYPE)')
    if path.endswith('/VillagerProfessionListSetting.java'):
        s = s.replace('tag.getList("value").get()', 'tag.getList("value", net.minecraft.nbt.NbtElement.STRING_TYPE)')
    if path.endswith('/BlockPosListSetting.java'):
        s = s.replace('tag.getList("value").get()', 'tag.getList("value", net.minecraft.nbt.NbtElement.INT_ARRAY_TYPE)')
    if path.endswith('/PlayerSkeletonGeometry.java'):
        s = s.replace('.applyTransform(matrices)', '.rotate(matrices)')
    if path.endswith('/ClearXaeroWaypoints.java'):
        s = s.replace('meteor.removeAll(points);', 'points.forEach(meteor::remove);')
    if path.endswith('/Via.java'):
        s = s.replace('mc.player.getInventory().getSelectedSlot()', 'mc.player.getInventory().selectedSlot')
        s = s.replace('tag.getCompoundOrEmpty(key)', 'tag.getCompound(key)')
        s = s.replace('tag.getListOrEmpty(key)', '(tag.get(key) instanceof NbtList list ? list : new NbtList())')
        s = s.replace('data.profession()', 'data.getProfession()')
        s = s.replace('inventoryS2CPacket.syncId()', 'inventoryS2CPacket.getSyncId()')
        s = s.replace('inventoryS2CPacket.contents()', 'inventoryS2CPacket.getContents()')
        s = s.replace('villager.getVillagerData().profession().value()', 'villager.getVillagerData().getProfession()')
        s = s.replace('event.key()', 'event.key')
    if path.endswith('/BetterStashFinder.java'):
        s = s.replace('new MeteorToast.Builder(title).icon(Items.CHEST).text("发现仓库！").build()', 'new MeteorToast(Items.CHEST, title, "发现仓库！")')

    if path.endswith('/PlayerSkeleton.java'):
        s = s.replace('import net.minecraft.entity.player.PlayerSkinType;', 'import net.minecraft.client.util.SkinTextures;')
        s = s.replace('PlayerSkinType.SLIM', 'SkinTextures.Model.SLIM')
        s = s.replace('PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);',
            'PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) mc.getEntityRenderDispatcher().getRenderer(player);')
    if path.endswith('/VillagerProfessionListSettingScreen.java'):
        s = s.replace('meteordevelopment.meteorclient.gui.screens.settings.base.CollectionListSettingScreen',
            'meteordevelopment.meteorclient.gui.screens.settings.RegistryListSettingScreen')
        s = s.replace('CollectionListSettingScreen<VillagerProfession>', 'RegistryListSettingScreen<VillagerProfession>')
        s = s.replace('Registries.VILLAGER_PROFESSION.get(VillagerProfession.NONE)', 'VillagerProfession.NONE')
        s = s.replace('protected String[] getValueNames(VillagerProfession profession)', 'protected String getValueName(VillagerProfession profession)')
        s = s.replace('return new String[]{profession.id().getString(), profession.id().toString()};', 'return profession.id();')
    if path.endswith('/Weather.java'):
        s = s.replace('import net.minecraft.world.attribute.EnvironmentAttributes;', '')
        s = s.replace('!mc.world.getEnvironmentAttributes()\n            .getAttributeValue(EnvironmentAttributes.BED_RULE_GAMEPLAY).explodes()', 'mc.world.getDimension().bedWorks()')
    if path.endswith('/ElytraFlyPlusPlus.java'):
        s = s.replace('import net.minecraft.screen.sync.ItemStackHash;', '')
        start = s.index('        Int2ObjectMap<ItemStackHash> changedHashes')
        end = s.index('\n        mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(', start)
        s = s[:start] + s[end:]
        s = s.replace('            changedHashes,\n            ItemStackHash.EMPTY', '            ItemStack.EMPTY,\n            changedSlots')

    if path.endswith('/BlacklistScreen.java'):
        s = s.replace('var restore=edits.add(theme.confirmedButton("恢复选中 "+selected.size()+" 条","确认恢复选中记录")).widget();\n            restore.action=() -> restore(List.copyOf(selected));',
            'confirmedButton(edits,"恢复选中 "+selected.size()+" 条","确认恢复选中记录",() -> restore(List.copyOf(selected)));')
        s = s.replace('var clear=edits.add(theme.confirmedButton("清空所有记录","确认清空当前世界全部记录")).widget();\n        clear.tooltip="清空当前世界和种子的黑名单及所有战利品版本的开箱隐藏记录。";\n        clear.action=() -> {',
            'var clear=confirmedButton(edits,"清空所有记录","确认清空当前世界全部记录",() -> {')
        s = s.replace('module.clearBlacklist(); selected.clear(); page=0; refresh();\n        };',
            'module.clearBlacklist(); selected.clear(); page=0; refresh();\n        });\n        clear.tooltip="清空当前世界和种子的黑名单及所有战利品版本的开箱隐藏记录。";')
        s = s.replace('    private boolean sameContext()', '''    private WButton confirmedButton(WContainer parent,String text,String confirm,Runnable action) {
        WButton button=parent.add(theme.button(text)).widget();
        boolean[] armed={false};
        button.action=() -> {
            if(!armed[0]) { armed[0]=true; button.set(confirm); }
            else { armed[0]=false; button.set(text); action.run(); }
        };
        return button;
    }
    private boolean sameContext()''')

    if path.endswith('/WorldMarkerRenderer.java'):
        s = s.replace('    private final MinecraftClient mc', '''    private final meteordevelopment.meteorclient.renderer.Renderer3D legacyDepth = new meteordevelopment.meteorclient.renderer.Renderer3D();
    private final MinecraftClient mc''')
        s = s.replace('var renderer = throughWalls ? event.renderer : event.depthRenderer;', '''var renderer = throughWalls ? event.renderer : legacyDepth;
        if(!throughWalls) {
            legacyDepth.lines.depthTest=true; legacyDepth.triangles.depthTest=true;
            legacyDepth.begin();
        }''')
        s = s.replace('BEAM_SIDE, BEAM_EDGE, ShapeMode.Both, 0);\n        }', '''BEAM_SIDE, BEAM_EDGE, ShapeMode.Both, 0);
        }
        if(!throughWalls) { legacyDepth.end(); legacyDepth.render(event.matrices); }''')

    if path.endswith('/XaeroAppleRenderer.java'):
        s = s.replace('import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;', '')
        s = s.replace('client.getItemModelManager().clearAndUpdate(', 'client.getItemModelManager().update(')
        s = s.replace('ItemDisplayContext.GUI', 'ModelTransformationMode.GUI')
        start = s.index('        var previousLights=RenderSystem.getShaderLights();')
        end = s.index('\n    private static boolean reported;', start)
        s = s[:start] + '''        try {
            DiffuseLighting.disableGuiDepthLighting();
            state.render(matrices,buffers,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
            buffers.draw();
        } finally { DiffuseLighting.enableGuiDepthLighting(); }
    }
''' + s[end:]
        start = s.index('    /** Render the resolved vanilla model')
        s = s[:start] + '}\n'

    if path.endswith('/ScratchTerrain.java'):
        s = s.replace('import net.minecraft.command.permission.*;', 'import com.mojang.authlib.GameProfile;')
        s = s.replace('import net.minecraft.world.chunk.ChunkLoadProgress;', '')
        s = s.replace('import net.minecraft.world.rule.GameRules;', 'import net.minecraft.world.GameRules;')
        start = s.index('api,new ChunkLoadProgress() {')
        end = s.index('        this.directory=directory;', start)
        s = s[:start] + 'api,radius -> progress());\n' + s[end:]
        s = s.replace('PermissionPredicate.ALL', '4')
        s = s.replace('saveHandler,getManagementListener()', 'saveHandler,0')
        s = s.replace('createWorlds();', 'createWorlds(progress());')
        s = s.replace('public LeveledPermissionPredicate getOpPermissionLevel() { return LeveledPermissionPredicate.OWNERS; }', 'public int getOpPermissionLevel() { return 4; }')
        s = s.replace('public PermissionPredicate getFunctionPermissions() { return 4; }', 'public int getFunctionPermissionLevel() { return 4; }')
        s = s.replace('isHost(PlayerConfigEntry profile)', 'isHost(GameProfile profile)')
        s = s.replace('    @Override public void exit()', '''    private static WorldGenerationProgressListener progress() {
        return new WorldGenerationProgressListener() {
            public void start(net.minecraft.util.math.ChunkPos pos) {}
            public void setChunkStatus(net.minecraft.util.math.ChunkPos pos,ChunkStatus status) {}
            public void start() {} public void stop() {}
        };
    }
    @Override public boolean areCommandBlocksEnabled() { return false; }
    @Override public void exit()''')
    return s
