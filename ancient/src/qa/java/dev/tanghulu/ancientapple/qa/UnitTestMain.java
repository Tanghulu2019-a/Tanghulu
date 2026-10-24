package dev.tanghulu.ancientapple.qa;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.io.PrintWriter;

public final class UnitTestMain {
    public static void main(String[] args) throws Exception {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectPackage("dev.tanghulu.ancientapple.core")).build();
        var summary=new SummaryGeneratingListener(); var launcher=LauncherFactory.create(); launcher.registerTestExecutionListeners(summary); launcher.execute(request);
        summary.getSummary().printTo(new PrintWriter(System.out));
        summary.getSummary().printFailuresTo(new PrintWriter(System.out));
        var counts=summary.getSummary();
        var report=java.nio.file.Path.of("build/reports/unit-checks.json");
        java.nio.file.Files.createDirectories(report.getParent());
        java.nio.file.Files.writeString(report,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of(
            "passed",counts.getTestsFoundCount()>=6 && counts.getTotalFailureCount()==0,
            "found",counts.getTestsFoundCount(),"succeeded",counts.getTestsSucceededCount(),"failed",counts.getTestsFailedCount())));
        if(summary.getSummary().getTestsFoundCount()<6 || summary.getSummary().getTotalFailureCount()>0)System.exit(1);
    }
}
