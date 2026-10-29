package com.hacer13.update;

/** Public verification material only. Rotation requires a normally signed APK update. */
public final class Trust {
    private Trust() {}
    public static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAjueARTaFPhSGpFx6hUC57YFR9f7/8TegzBySwaafEvU=";
    public static final String BASE = "https://raw.githubusercontent.com/jjjjjjjjjjjjjjae-hub/hacer-13/main/updates/lac/";
    public static final String MANIFEST = BASE + "release-v2.json";
    public static final String FALLBACK = "{\"title\":\"HACER 13\",\"features\":["
        + "{\"id\":\"god\",\"title\":\"God Mode\",\"description\":\"DEMO — тек интерфейс\",\"kind\":\"demo\"},"
        + "{\"id\":\"jump\",\"title\":\"High Jump\",\"description\":\"DEMO — тек интерфейс\",\"kind\":\"demo\"},"
        + "{\"id\":\"speed\",\"title\":\"Speed\",\"description\":\"DEMO — тек интерфейс\",\"kind\":\"demo\"}]}";
}
