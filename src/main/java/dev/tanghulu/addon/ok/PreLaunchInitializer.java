package dev.tanghulu.addon.ok;


import dev.tanghulu.addon.NewLogin;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class PreLaunchInitializer implements PreLaunchEntrypoint {
    public static boolean authenticated = false;
    public static String auth = "false";


    @Override
    public void onPreLaunch() {

//        LoginHandler.showLoginWindow();
        NewLogin.login();

    }

}
