package dev.tanghulu.addon;

import dev.tanghulu.addon.ok.PreLaunchInitializer;

public class NewLogin {

    public static void login() {


        PreLaunchInitializer.authenticated = true;


        PreLaunchInitializer.auth = "null";

        TanghuluMagic2.hit = 0.5;

    }
}
