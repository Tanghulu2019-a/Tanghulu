package dev.tanghulu.addon.util.seeds;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** A fixed batch: late callbacks and duplicate chunk completions cannot advance another search. */
public final class OreSearchProgress {
    public enum State { Idle, Running, Complete, Cancelled, Failed }
    private State state = State.Idle;
    private long token;
    private Set<Long> remaining = new HashSet<>();
    private int total, completed, found;

    public long begin(Collection<Long> chunks) {
        token++;
        remaining = new HashSet<>(chunks);
        total = remaining.size();
        completed = found = 0;
        state = total == 0 ? State.Complete : State.Running;
        return token;
    }

    public boolean complete(long expectedToken, long chunk, int matches) {
        if (expectedToken != token || !running() || !remaining.remove(chunk)) return false;
        completed++;
        found += Math.max(0, matches);
        if (remaining.isEmpty()) state = State.Complete;
        return true;
    }

    public void cancel() {
        token++;
        remaining.clear();
        if (running()) state = State.Cancelled;
    }

    public boolean fail(long expectedToken) {
        if (expectedToken != token || !running()) return false;
        state = State.Failed;
        remaining.clear();
        return true;
    }

    public boolean running() { return state == State.Running; }
    public State state() { return state; }
    public int completed() { return completed; }
    public int total() { return total; }
    public int found() { return found; }
    public int percent() { return total == 0 ? (state == State.Complete ? 100 : 0) : completed * 100 / total; }

    public String status() {
        return switch (state) {
            case Idle -> "等待开始搜索";
            case Running -> "搜索中 " + completed + "/" + total + " 区块（" + percent() + "%），已找到 " + found + " 处矿物";
            case Complete -> "搜索完成 " + completed + "/" + total + " 区块（100%），找到 " + found + " 处矿物";
            case Cancelled -> "搜索已取消（" + completed + "/" + total + " 区块），已找到的矿物保留";
            case Failed -> "搜索失败（" + completed + "/" + total + " 区块），请查看聊天提示";
        };
    }
}
