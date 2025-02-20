package com.example.permission_auto_reset

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.google.common.util.concurrent.ListenableFuture

/** PermissionAutoResetPlugin */
class PermissionAutoResetPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var context: Context? = null
    private val REQUEST_CODE = 1001

    companion object {
        private const val ERROR = -1
        private const val FEATURE_NOT_AVAILABLE = 0
        private const val DISABLED = 1
        private const val API_30_BACKPORT = 2
        private const val API_30 = 3
        private const val API_31 = 4
        
        // Known intent actions for different manufacturers
        private val PERMISSION_SETTINGS_INTENTS = arrayOf(
            // Google/AOSP standard
            "android.settings.AUTO_REVOKE_PERMISSIONS_SETTINGS",
            // Samsung specific
            "com.samsung.android.settings.permission.PERM_AUTO_REVOKE",
            // Xiaomi specific
            "miui.intent.action.APP_PERM_EDITOR",
            // OPPO specific
            "com.coloros.safecenter.permission.PermissionManager",
            // Generic permission settings
            "android.settings.APPLICATION_SETTINGS",
            "android.settings.MANAGE_APPLICATIONS_SETTINGS",
            "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS"
        )
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "permission_auto_reset")
        context = flutterPluginBinding.applicationContext
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context = null
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "checkRestrictionsStatus" -> {
                checkRestrictionsStatus(result)
            }
            "openRestrictionsSettings" -> {
                openRestrictionsSettings(result)
            }
            "getDeviceManufacturer" -> {
                result.success(Build.MANUFACTURER)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun checkRestrictionsStatus(result: MethodChannel.Result) {
        context?.let { ctx ->
            try {
                // Check Android version first
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // Android 10 (API 29) or lower
                    // Feature not available on Android 10 and below
                    result.success("FEATURE_NOT_AVAILABLE")
                    return
                }
                
                // For Android 11+ (API 30+), use the proper API
                val future: ListenableFuture<Int> = PackageManagerCompat.getUnusedAppRestrictionsStatus(ctx)
                future.addListener({
                    try {
                        val status = future.get()
                        handleRestrictionStatus(status, result)
                    } catch (e: Exception) {
                        // Fallback to manual check if future fails
                        fallbackStatusCheck(ctx, result)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            } catch (e: Exception) {
                // If the PackageManagerCompat method fails, fall back to a manual check
                fallbackStatusCheck(ctx, result)
            }
        } ?: result.error("NO_CONTEXT", "Application context is null", null)
    }

    private fun fallbackStatusCheck(ctx: Context, result: MethodChannel.Result) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 (API 30) or higher
                // Try to check based on manufacturer
                when (Build.MANUFACTURER.lowercase()) {
                    "samsung" -> {
                        // Samsung devices use different settings keys
                        val autoResetSetting = Settings.Secure.getInt(
                            ctx.contentResolver,
                            "app_auto_restriction_enabled", 
                            1 // Default is enabled on Samsung
                        )
                        result.success(if (autoResetSetting > 0) "ENABLED" else "DISABLED")
                        return
                    }
                    "xiaomi", "redmi", "poco" -> {
                        // MIUI often has custom implementations
                        try {
                            ctx.packageManager.getApplicationInfo("com.miui.securitycenter", 0)
                            // If MIUI security center exists, assume the feature exists
                            // but be conservative in reporting status
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                                result.success("ENABLED")
                            } else {
                                result.success("DISABLED")
                            }
                            return
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Not a MIUI device or security center isn't found
                        }
                    }
                }
                
                // Generic check for all manufacturers
                // Check if hibernation is enabled (Android 12+ feature but check it anyway)
                val hibernationSetting = Settings.Secure.getInt(
                    ctx.contentResolver,
                    "unused_app_hibernation_enabled", 
                    1 // Default is enabled
                )
                
                // Check permission auto-reset setting
                val autoResetSetting = Settings.Secure.getInt(
                    ctx.contentResolver,
                    "auto_revoke_permissions_mode", 
                    0 // Default varies by device
                )
                
                if (hibernationSetting == 1 || autoResetSetting > 0) {
                    result.success("ENABLED")
                } else {
                    result.success("DISABLED")
                }
            } else {
                result.success("FEATURE_NOT_AVAILABLE")
            }
        } catch (e: Exception) {
            // If all else fails, return a default based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                result.success("ENABLED") // Default for Android 12+ is enabled
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11
                result.success("DISABLED") // Be conservative for Android 11
            } else {
                result.success("FEATURE_NOT_AVAILABLE")
            }
        }
    }

    private fun handleRestrictionStatus(status: Int, result: MethodChannel.Result) {
        when (status) {
            ERROR -> 
                result.success("ERROR")
            FEATURE_NOT_AVAILABLE -> 
                result.success("FEATURE_NOT_AVAILABLE")
            DISABLED -> 
                result.success("DISABLED")
            API_30_BACKPORT,
            API_30,
            API_31 -> 
                result.success("ENABLED")
            else -> result.success("DISABLED")
        }
    }

    private fun openRestrictionsSettings(result: MethodChannel.Result) {
        context?.let { ctx ->
            activity?.let { act ->
                try {
                    // First, try the official API (most reliable when available)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(ctx, ctx.packageName)
                            act.startActivityForResult(intent, REQUEST_CODE)
                            result.success(true)
                            return
                        } catch (e: Exception) {
                            // Continue to manufacturer-specific methods
                        }
                    }
                    
                    // Try manufacturer-specific approach
                    if (tryOpenManufacturerSpecificSettings(act, ctx)) {
                        result.success(true)
                        return
                    }
                    
                    // Try standard permission settings intents
                    if (tryStandardPermissionIntents(act, ctx)) {
                        result.success(true)
                        return
                    }
                    
                    // Last resort: open app details settings
                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = android.net.Uri.fromParts("package", ctx.packageName, null)
                    detailsIntent.setData(uri)
                    act.startActivityForResult(detailsIntent, REQUEST_CODE)
                    result.success(true)
                    
                } catch (e: Exception) {
                    result.error("SETTINGS_LAUNCH_FAILED", "Could not open settings: ${e.message}", null)
                }
            } ?: result.error("NO_ACTIVITY", "Activity is null", null)
        } ?: result.error("NO_CONTEXT", "Application context is null", null)
    }
    
    private fun tryOpenManufacturerSpecificSettings(activity: Activity, context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        try {
            when (manufacturer) {
                "samsung" -> {
                    // Try Samsung-specific permission settings
                    val samsungIntent = Intent("com.samsung.android.settings.permission.PERM_AUTO_REVOKE")
                    samsungIntent.putExtra("packageName", context.packageName)
                    if (isIntentResolvable(context, samsungIntent)) {
                        activity.startActivityForResult(samsungIntent, REQUEST_CODE)
                        return true
                    }
                }
                "xiaomi", "redmi", "poco" -> {
                    // Try MIUI-specific permission settings
                    val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    miuiIntent.putExtra("extra_pkgname", context.packageName)
                    if (isIntentResolvable(context, miuiIntent)) {
                        activity.startActivityForResult(miuiIntent, REQUEST_CODE)
                        return true
                    }
                }
                "oppo", "realme", "oneplus" -> {
                    // ColorOS/OxygenOS specific settings
                    val colorOsIntent = Intent()
                    colorOsIntent.setClassName("com.coloros.safecenter", 
                                            "com.coloros.safecenter.permission.PermissionManagerActivity")
                    if (isIntentResolvable(context, colorOsIntent)) {
                        activity.startActivityForResult(colorOsIntent, REQUEST_CODE)
                        return true
                    }
                }
                "huawei", "honor" -> {
                    // Try EMUI-specific permission settings
                    val emuiIntent = Intent()
                    emuiIntent.setClassName("com.huawei.systemmanager", 
                                         "com.huawei.permissionmanager.ui.MainActivity")
                    if (isIntentResolvable(context, emuiIntent)) {
                        activity.startActivityForResult(emuiIntent, REQUEST_CODE)
                        return true
                    }
                }
                "vivo", "iqoo" -> {
                    // Try Funtouch OS-specific permission settings
                    val vivoIntent = Intent()
                    vivoIntent.setClassName("com.vivo.permissionmanager", 
                                         "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    vivoIntent.putExtra("packagename", context.packageName)
                    if (isIntentResolvable(context, vivoIntent)) {
                        activity.startActivityForResult(vivoIntent, REQUEST_CODE)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Manufacturer-specific approach failed, continue to next method
        }
        
        return false
    }
    
    private fun tryStandardPermissionIntents(activity: Activity, context: Context): Boolean {
        // Try each known intent action for permission settings
        for (intentAction in PERMISSION_SETTINGS_INTENTS) {
            try {
                val intent = Intent(intentAction)
                if (intentAction == "android.settings.AUTO_REVOKE_PERMISSIONS_SETTINGS" && 
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // For the standard auto-revoke settings, add the package name
                    intent.putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                }
                
                if (isIntentResolvable(context, intent)) {
                    activity.startActivityForResult(intent, REQUEST_CODE)
                    return true
                }
            } catch (e: Exception) {
                // Try next intent
                continue
            }
        }
        
        return false
    }
    
    private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }
}