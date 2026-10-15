package com.ultimatedownloader;

import com.ultimatedownloader.service.AppPaths;

/**
 * Punto de entrada. No extiende Application a propósito: si lo hiciera, la máquina virtual
 * fallaría con «JavaFX runtime components are missing» al arrancar desde el classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        System.setProperty("ud.logDir", AppPaths.carpetaLogs().toString());
        App.main(args);
    }
}
