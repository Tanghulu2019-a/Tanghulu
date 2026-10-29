import java.util.List;

public class DebugTablesFixture {
    @Deprecated
    public static int work(List<String> values) {
        int result = values.stream().mapToInt(String::length).sum();
        try { Integer.parseInt("not a number"); }
        catch (NumberFormatException expected) { result += 7; }
        return result;
    }
    public static void main(String[] args) throws Exception {
        int value = work(List.of("birch", "oak"));
        if (value != 15) throw new AssertionError("Computation changed");
        var method = DebugTablesFixture.class.getMethod("work", List.class);
        if (!method.getParameters()[0].getName().equals("values")) throw new AssertionError("Parameter names lost");
        if (!method.isAnnotationPresent(Deprecated.class)) throw new AssertionError("Annotation lost");
        StackTraceElement trace = new Exception().getStackTrace()[0];
        if (!"DebugTablesFixture.java".equals(trace.getFileName()) || trace.getLineNumber() < 1)
            throw new AssertionError("Source file or line numbers lost");
        System.out.println("CLASS_DEBUG_CHECKS PASS value=" + value + " parameters=true annotations=true lines=true");
    }
}
