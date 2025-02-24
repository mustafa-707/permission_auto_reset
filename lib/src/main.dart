import 'dart:async';
import 'dart:developer';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Enum representing the possible states of permission auto-reset restrictions
enum RestrictionsStatus {
  /// Status could not be determined
  error,

  /// Feature is not available on this device
  featureNotAvailable,

  /// Restrictions are disabled for the app
  disabled,

  /// Restrictions are enabled (app may be hibernated or permissions revoked)
  enabled,
}

class PermissionAutoReset {
  static const MethodChannel _channel = MethodChannel('permission_auto_reset');

  /// Checks the current status of unused app restrictions
  ///
  /// Returns a [RestrictionsStatus] indicating whether:
  /// - error: Status could not be determined
  /// - featureNotAvailable: Feature is not available on this device
  /// - disabled: Restrictions have been disabled by the user
  /// - enabled: App may be hibernated or have permissions revoked if unused
  /// - unknown: Unexpected status value received
  static Future<RestrictionsStatus> checkRestrictionsStatus() async {
    try {
      if (kIsWeb) return RestrictionsStatus.enabled;
      if (!Platform.isAndroid) return RestrictionsStatus.enabled;
      final String status =
          await _channel.invokeMethod('checkRestrictionsStatus');
      switch (status) {
        case 'ERROR':
          return RestrictionsStatus.error;
        case 'FEATURE_NOT_AVAILABLE':
          return RestrictionsStatus.featureNotAvailable;
        case 'DISABLED':
          return RestrictionsStatus.disabled;
        case 'ENABLED':
          return RestrictionsStatus.enabled;
        default:
          return RestrictionsStatus.disabled;
      }
    } on PlatformException catch (e) {
      log('Error checking restrictions status: ${e.message}');
      return RestrictionsStatus.error;
    }
  }

  /// Opens the system settings page where the user can modify app restrictions
  ///
  /// Returns true if the settings page was successfully opened
  static Future<bool> openRestrictionsSettings() async {
    try {
      if (kIsWeb) return false;
      if (!Platform.isAndroid) return false;
      final bool success =
          await _channel.invokeMethod('openRestrictionsSettings');
      return success;
    } on PlatformException catch (e) {
      log('Failed to open settings: ${e.message}');
      return false;
    }
  }
}
