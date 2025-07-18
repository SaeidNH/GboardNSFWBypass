package com.gboard.nsfw.xposed

import android.net.Uri
import com.gboard.nsfw.utils.UriUtils
import de.robv.android.xposed.*
import java.util.concurrent.Callable

class NSFW : Callable<Void?> {

    override fun call(): Void? {
        val potentialMatches = mutableListOf<String>()
        for (c in 'a'..'z') {
            for (c2 in 'a'..'z') {
                for (c3 in 'a'..'z') {
                    val name = "$c$c2$c3"
                    try {
                        val cls = XposedHelpers.findClass(name, mainClassLoader)
                        for (field in cls.declaredFields) {
                            if (field.toString().contains("tenor") || field.toString().contains("search_v2")) {
                                potentialMatches.add(name)
                                break
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        potentialMatches.forEach {
            preHookProcess(it)
            hookMethod(it)
        }
        return null
    }

    private fun preHookProcess(className: String) {
        try {
            findingTenorServer(XposedHelpers.findClass(className, mainClassLoader))
        } catch (_: Throwable) {}
    }

    private fun hookMethod(className: String) {
        try {
            val findClass = XposedHelpers.findClass(className, mainClassLoader)
            val methods = XposedHelpers.findMethodsByExactParameters(findClass, Void.TYPE, Uri::class.java)
            for (method in methods) {
                val unhook = XposedBridge.hookMethod(method, HookMethodClass(findClass))
                hookedMethods.add(unhook)
            }
        } catch (_: Throwable) {}
    }

    class HookMethodClass(private val hookClass: Class<*>) : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                val uriField = XposedHelpers.findFirstFieldByExactType(hookClass, Uri::class.java)
                val originalUri = uriField?.get(param.thisObject).toString()
                if (originalUri.contains("https://www.google.com/search")) {
                    val cleanedUri = UriUtils.removeUriParameters(
                        Uri.parse(originalUri),
                        listOf("client", "safe", "gs_ri", "ei") // 🔧 Modified: Added v15.5 parameters
                    ).appendQueryParameter("safe", "off")
                     .appendQueryParameter("client", "chrome")

                    cleanedUri.scheme("https").authority((param.args[0] as Uri).authority)
                    uriField?.set(param.thisObject, cleanedUri.build())
                }

                for (field in hookClass.declaredFields) {
                    if (field.type == String::class.java && field.get(param.thisObject).toString()
                            .contains("Mozilla/5.0 (Linux; ")) && !field.isAccessible
                    ) {
                        field.isAccessible = true
                        field.set(
                            param.thisObject,
