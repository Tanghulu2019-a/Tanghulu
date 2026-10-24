package dev.tanghulu.ancientapple.gui;

import dev.tanghulu.ancientapple.HunterModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import net.minecraft.client.MinecraftClient;
import java.util.*;

/** Keep controls outside the paged table so typing does not lose focus. */
public final class BlacklistScreen extends WindowScreen {
    private static final int PAGE_SIZE=10;
    private enum Records {
        COLLECTED("已搜取黑名单"), OPENED("开箱隐藏记录");
        private final String label;
        Records(String label) { this.label=label; }
        @Override public String toString() { return label; }
    }
    private enum Order {
        DISTANCE("距离从近到远"), POSITION("按坐标排序");
        private final String label;
        Order(String label) { this.label=label; }
        @Override public String toString() { return label; }
    }
    private final HunterModule module;
    private final Set<String> selected=new HashSet<>();
    private Records records=Records.COLLECTED;
    private Order order=Order.DISTANCE;
    private String query="",viewContext="";
    private int page;
    private WTable rows,actions;
    private WLabel summary,counts,notice;

    public BlacklistScreen(GuiTheme theme,HunterModule module) {
        super(theme,"古城附魔金苹果 · 黑名单管理");
        this.module=module;
    }

    @Override public void initWidgets() {
        var content=add(theme.verticalList()).expandX().widget();
        summary=content.add(theme.label("")).widget();
        var options=content.add(theme.horizontalList()).expandX().widget();
        WCheckbox enabled=options.add(theme.checkbox(module.blacklistEnabled.get())).widget();
        options.add(theme.label("启用黑名单过滤"));
        enabled.action=() -> { module.blacklistEnabled.set(enabled.checked); refresh(); };
        button(options,"刷新",this::refresh);
        var filters=content.add(theme.horizontalList()).expandX().widget();
        var type=filters.add(theme.dropdown(Records.values(),records)).widget();
        type.action=() -> { records=type.get(); selected.clear(); page=0; refresh(); };
        var sorting=filters.add(theme.dropdown(Order.values(),order)).widget();
        sorting.action=() -> { order=sorting.get(); page=0; refresh(); };
        var search=content.add(theme.textBox(query,"搜索坐标，例如 100 或 100,-48")).expandX().widget();
        search.tooltip="输入完整或部分 X,Y,Z；空格和中文逗号也可用。";
        search.action=() -> { query=search.get(); page=0; refresh(); };
        counts=content.add(theme.label("")).widget();
        rows=content.add(theme.table()).expandX().widget();
        actions=content.add(theme.table()).expandX().widget();
        notice=content.add(theme.label("已搜取与开箱隐藏分开管理；管理操作涵盖所有战利品版本。")).widget();
        refresh();
    }

    private void refresh() {
        rows.clear(); actions.clear();
        String current=module.managementContext();
        if(!current.equals(viewContext)) { selected.clear(); page=0; viewContext=current; }
        if(current.isEmpty()) {
            summary.set("请进入世界，并在定位模块中填写完整种子。"); counts.set("暂无可管理的记录");
            return;
        }
        summary.set(module.managementSummary());
        List<String> all=records==Records.COLLECTED?module.blacklistPositions():module.openedPositions();
        selected.retainAll(all);
        String needle=query.trim().replaceAll("[\\s，]+",",");
        Comparator<String> byPosition=Comparator.comparingInt((String k) -> coordinate(k,0))
            .thenComparingInt(k -> coordinate(k,1)).thenComparingInt(k -> coordinate(k,2));
        Comparator<String> comparator=order==Order.DISTANCE?Comparator.comparingDouble(module::horizontalDistance).thenComparing(byPosition):byPosition;
        var matches=all.stream().filter(k -> k.contains(needle)).sorted(comparator).toList();
        int pages=Math.max(1,(matches.size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(page,pages-1));
        var visible=matches.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE).toList();
        counts.set(records+" "+all.size()+" 条 · 匹配 "+matches.size()+" 条 · 已选 "+selected.size()+" 条 · 第 "+(page+1)+"/"+pages+" 页");
        if(visible.isEmpty()) {
            rows.add(theme.label(all.isEmpty()?"暂无记录。":"没有匹配的坐标，请调整搜索内容。")); rows.row();
        } else {
            for(String header:List.of("选择","X","Y","Z","水平距离","操作"))rows.add(theme.label(header));
            rows.row();
            for(String pos:visible) {
                WCheckbox checkbox=rows.add(theme.checkbox(selected.contains(pos))).widget();
                checkbox.action=() -> { if(checkbox.checked)selected.add(pos); else selected.remove(pos); refresh(); };
                for(String axis:pos.split(","))rows.add(theme.label(axis));
                rows.add(theme.label(String.format(Locale.ROOT,"%,.0f 格",module.horizontalDistance(pos))));
                var controls=rows.add(theme.horizontalList()).widget();
                button(controls,"复制",() -> { MinecraftClient.getInstance().keyboard.setClipboard(pos); notice.set("已复制坐标："+pos); });
                button(controls,"恢复",() -> restore(List.of(pos)));
                rows.row();
            }
        }
        var paging=actions.add(theme.horizontalList()).expandX().widget(); actions.row();
        if(page>0)button(paging,"上一页",() -> { page--; refresh(); }); else paging.add(theme.label("上一页"));
        if(page+1<pages)button(paging,"下一页",() -> { page++; refresh(); }); else paging.add(theme.label("下一页"));
        if(!visible.isEmpty())button(paging,"选择本页",() -> { selected.addAll(visible); refresh(); });
        if(!selected.isEmpty())button(paging,"取消选择",() -> { selected.clear(); refresh(); });
        var edits=actions.add(theme.horizontalList()).expandX().widget();
        if(!selected.isEmpty()) {
            var restore=edits.add(theme.confirmedButton("恢复选中 "+selected.size()+" 条","确认恢复选中记录")).widget();
            restore.action=() -> restore(List.copyOf(selected));
        }
        var clear=edits.add(theme.confirmedButton("清空所有记录","确认清空当前世界全部记录")).widget();
        clear.tooltip="清空当前世界和种子的黑名单及所有战利品版本的开箱隐藏记录。";
        clear.action=() -> {
            if(!sameContext())return;
            module.clearBlacklist(); selected.clear(); page=0; refresh();
        };
    }
    private boolean sameContext() {
        if(!viewContext.isEmpty() && viewContext.equals(module.managementContext()))return true;
        notice.set("世界或种子已变化，列表已刷新，请重新选择。"); refresh(); return false;
    }
    private void restore(List<String> positions) {
        if(!sameContext())return;
        if(module.restoreRecords(positions,records==Records.OPENED,viewContext)) {
            selected.removeAll(positions); notice.set("已恢复 "+positions.size()+" 条记录；有预测的箱子会重新显示航标。");
        } else notice.set("恢复失败，详情见聊天提示。");
        refresh();
    }
    private static int coordinate(String key,int index) { return Integer.parseInt(key.split(",")[index]); }
    private void button(WContainer parent,String text,Runnable action) {
        WButton button=parent.add(theme.button(text)).widget(); button.action=action;
    }
}
