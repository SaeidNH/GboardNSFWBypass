package com.nsfw.hook;

import android.net.Uri;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.*;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.google.android.inputmethod.latin")) return;

        findAndHookMethod(
            "android.net.Uri$Builder", // Hook Uri.Builder
            lpparam.classLoader,
            "appendQueryParameter",
            String.class,
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    if (key != null && key.toLowerCase().contains("safe")) {
                        param.args[1] = "false"; // change value to false
                    }
                }
            }
        );
    }
}
