package dev.tanghulu.ancientapple.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ValidationGate {
    private ValidationGate() {}
    public static boolean certified() {
        try(var in=ValidationGate.class.getResourceAsStream("/prediction-validation.json")) {
            if(in==null)return false;
            var o=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            return o.get("passed").getAsBoolean() && o.get("seeds").getAsInt()>=10 && o.get("cities").getAsInt()>=20
                && o.get("certaintyPolicy").getAsString().equals("verify-order-sensitive")
                && o.get("minecraft").getAsString().equals("1.21.11")
                && o.get("algorithm").getAsString().equals(dev.tanghulu.ancientapple.storage.WorldStore.ALGORITHM)
                && certified26(o.get("sourceHash").getAsString());
        } catch(Exception e) { return false; }
    }
    private static boolean certified26(String sourceHash) {
        try(var in=ValidationGate.class.getResourceAsStream("/loot26-validation.json")) {
            if(in==null)return false;
            var o=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            return o.get("passed").getAsBoolean() && o.get("minecraft").getAsString().equals("26.1")
                && o.get("checked").getAsInt()>=1024 && o.get("sourceHash").getAsString().equals(sourceHash);
        } catch(Exception e) { return false; }
    }
}
