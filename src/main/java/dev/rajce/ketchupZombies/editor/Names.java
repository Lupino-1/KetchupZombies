package dev.rajce.ketchupZombies.editor;

import java.util.regex.Pattern;

public final class Names {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,32}$");

    private Names() {
    }

    public static boolean valid(String name) {
        return name != null && PATTERN.matcher(name).matches();
    }
}
