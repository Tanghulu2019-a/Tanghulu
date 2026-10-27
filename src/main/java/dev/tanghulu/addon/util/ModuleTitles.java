package dev.tanghulu.addon.util;

/** Display prefixes only; persisted module names and aliases stay unchanged. */
public final class ModuleTitles {
    private ModuleTitles() {}

    public static String withEnglishInitial(String title, Class<?> moduleClass) {
        if (!moduleClass.getName().startsWith("dev.tanghulu.") || title.isEmpty()) return title;
        int first = title.codePointAt(0);
        if (Character.isDigit(first) || (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z')) {
            return title;
        }

        // The embedded Ancient Apple Hunter uses the generic class name HunterModule.
        String englishName = moduleClass.getName().equals("dev.tanghulu.ancientapple.HunterModule")
            ? "Ancient Apple Hunter" : moduleClass.getSimpleName();
        if (englishName.isEmpty()) return title;
        char initial = Character.toUpperCase(englishName.charAt(0));
        return initial >= 'A' && initial <= 'Z' ? initial + title : title;
    }
}
