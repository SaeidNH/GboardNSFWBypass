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
                        listOf("client", "safe", "gs_ri", "ei") // ✅ Added v15.5 params
                    ).appendQueryParameter("safe", "off")
                     .appendQueryParameter("client", "chrome")

                    cleanedUri.scheme("https").authority((param.args[0] as Uri).authority)
                    uriField?.set(param.thisObject, cleanedUri.build())
                }

                for (field in hookClass.declaredFields) {
                    if (field.type == String::class.java &&
                        field.get(param.thisObject).toString().contains("Mozilla/5.0 (Linux; "))
                    ) {
                        if (!field.isAccessible) field.isAccessible = true
                        field.set(
                            param.thisObject,
                            "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 7 Build/MOB30X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"
                        )
                        break
                    }
                }
            } catch (_: Throwable) {
                val unhook = hookedMethods.find { it.hookedMethod == param.method }
                unhook?.unhook()
                if (unhook != null) {
                    hookedMethods.remove(unhook)
                }
            }
        }
    }

    companion object {
        private val hookedMethods: MutableList<XposedBridge.Unhook> = mutableListOf()
        private var mainClassLoader: ClassLoader? = null
        private var tenorServerFounded = false

        fun setClassLoader(classLoader: ClassLoader?) {
            mainClassLoader = classLoader
        }

        private fun findingTenorServer(cls: Class<*>) {
            for (field in cls.declaredFields) {
                val value = try {
                    field[null]
                } catch (_: Throwable) {
                    continue
                } ?: continue

                if (!value.toString().contains("tenor_server_url_search_v2")) continue

                val preField = findEmptyField(cls)
                if (preField == null) {
                    XposedBridge.log("Failed to start due to preField is null")
                    break
                }

                field.isAccessible = true
                try {
                    field[null] = preField
                    tenorServerFounded = true
                    XposedBridge.log("NSFW Gboard Started... at $cls")
                    break
                } catch (e: Throwable) {
                    XposedBridge.log("Unable to find tenor server url! due to ${e.message}")
                }
            }
        }

        private fun findEmptyField(cls: Class<*>): Any? {
            for (field in cls.declaredFields) {
                val value = try {
                    field[null]
                } catch (_: Throwable) {
                    continue
                }

                if (value != null && value.toString().contains("enable_tenor_category_v2_for_language_tags")) {
                    return value
                }
            }
            return null
        }
    }
}
