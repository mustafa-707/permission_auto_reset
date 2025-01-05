package com.example.permission_auto_reset

import android.app.Activity
import android.content.Context
import android.content.Intent
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
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun checkRestrictionsStatus(result: MethodChannel.Result) {
        context?.let { ctx ->
            try {
                val future: ListenableFuture<Int> = PackageManagerCompat.getUnusedAppRestrictionsStatus(ctx)
                future.addListener({
                    try {
                        val status = future.get()
                        handleRestrictionStatus(status, result)
                    } catch (e: Exception) {
                        result.error("FUTURE_ERROR", "Error getting restrictions status: ${e.message}", null)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            } catch (e: Exception) {
                result.error("EXCEPTION", "Error checking restrictions: ${e.message}", null)
            }
        } ?: result.error("NO_CONTEXT", "Application context is null", null)
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
                    val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(ctx, ctx.packageName)
                    act.startActivityForResult(intent, REQUEST_CODE)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SETTINGS_LAUNCH_FAILED", "Could not open settings: ${e.message}", null)
                }
            } ?: result.error("NO_ACTIVITY", "Activity is null", null)
        } ?: result.error("NO_CONTEXT", "Application context is null", null)
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