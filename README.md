
# permission_auto_reset

[![StandWithPalestine](https://raw.githubusercontent.com/TheBSD/StandWithPalestine/main/badges/StandWithPalestine.svg)](https://github.com/TheBSD/StandWithPalestine/blob/main/docs/README.md) [![Pub Package](https://img.shields.io/pub/v/permission_auto_reset.svg)](https://pub.dev/packages/permission_auto_reset)

A Flutter plugin to manage Android's permission auto-reset feature, allowing you to check and request disabling of automatic permission revocation for apps that require consistent background operation.

## Features

- Check the current status of permission auto-reset for your app
- Open system settings to disable permission auto-reset
- Support for Android 11 (API 30) and above
- Proper handling of background apps that need persistent permissions

## Getting started

Add **`permission_auto_reset`** to your `pubspec.yaml`:

```yaml
dependencies:
  permission_auto_reset: ^1.0.0
```

Run:

```bash
flutter pub get
```

## Usage

Import the package:

```dart
import 'package:permission_auto_reset/permission_auto_reset.dart';
```

### Check Permission Auto-Reset Status

```dart
try {
  final status = await PermissionAutoReset.checkRestrictionsStatus();
  switch (status) {
    case RestrictionsStatus.enabled:
      print('Auto-reset is enabled - permissions may be revoked if unused');
      break;
    case RestrictionsStatus.disabled:
      print('Auto-reset is disabled for this app');
      break;
    case RestrictionsStatus.featureNotAvailable:
      print('Feature not available on this device');
      break;
    case RestrictionsStatus.error:
      print('Error checking status');
      break;
  }
} catch (e) {
  print('Error: $e');
}
```

### Request to Disable Auto-Reset

```dart
// Open system settings to disable auto-reset
await PermissionAutoReset.openRestrictionsSettings();
```

### Example for Background Apps

If your app performs background operations, you should check and handle the auto-reset status to ensure continuous operation:

```dart
Future<void> checkAndRequestPermissionAutoReset() async {
  final status = await PermissionAutoReset.checkRestrictionsStatus();
  
  if (status == RestrictionsStatus.enabled) {
    final shouldOpen = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Background Operation'),
        content: Text(
          'This app needs to work in the background to perform periodic tasks. '
          'To ensure reliable operation, please disable permission auto-reset.'
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Later'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Open Settings'),
          ),
        ],
      ),
    );
    
    if (shouldOpen) {
      await PermissionAutoReset.openRestrictionsSettings();
    }
  }
}
```

## Additional Information

### When to Use

This plugin is particularly useful for:

- Apps that perform periodic background tasks
- Apps that need to maintain permissions for extended periods
- Apps that handle background file uploads or downloads
- Services that need to run consistently without user interaction

### Android Version Compatibility

- Android 11 (API 30) and above: Full support for checking and managing permission auto-reset
- Android 10 and below: Feature not available, plugin will return `RestrictionsStatus.featureNotAvailable`

## Support

If you find this plugin helpful, consider supporting me:

[![Buy Me A Coffee](https://www.buymeacoffee.com/assets/img/guidelines/download-assets-sm-1.svg)](https://buymeacoffee.com/is10vmust)
