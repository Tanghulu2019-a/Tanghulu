package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.ChatUtils;
import meteordevelopment.meteorclient.systems.modules.Category;

public class HeadlessModule extends BaseModule {

    public HeadlessModule(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    public HeadlessModule(String name, String desc) {
        super(BaseModule.CATEGORY, name, desc);
    }

    @Override
    public void onActivate() {
        super.onActivate();
        // 提示模块未完成
        ChatUtils.sendMsg("该功能正在开发中， (｡･ω･｡)  再等等叭~");

        // 关闭模块
        toggle();
    }
}
