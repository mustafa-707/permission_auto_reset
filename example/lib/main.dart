// example/lib/main.dart
import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_auto_reset/permission_auto_reset.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  RestrictionsStatus? _status;
  String? _error;

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    try {
      final status = await PermissionAutoReset.checkRestrictionsStatus();
      setState(() {
        _status = status;
        _error = null;
      });
    } on PlatformException catch (e) {
      log(e.toString());
      setState(() {
        _status = null;
        _error = e.message;
      });
    }
  }

  Future<void> _openSettings() async {
    try {
      await PermissionAutoReset.openRestrictionsSettings();
      // Status might have changed after returning from settings
      await _checkStatus();
    } catch (e) {
      setState(() {
        _error = 'Failed to open settings: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Permission Auto-Reset Example'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_error != null)
                Text(
                  'Error: $_error',
                  style: const TextStyle(color: Colors.red),
                ),
              if (_status != null)
                Text(
                    'Auto-reset status: ${_status.toString().split('.').last}'),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _checkStatus,
                child: const Text('Check Status'),
              ),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: _openSettings,
                child: const Text('Open Settings'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
