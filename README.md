# Telemetry App (Android 12+)

This project is a Kotlin + Jetpack Compose Android Studio app that connects to a Bluetooth Classic SPP (RFCOMM) device, parses line-based telemetry, and visualizes it in three modes (Scope, Panel, Combo).

## Requirements
- **Minimum SDK:** 31 (Android 12)
- **Target SDK:** 34

## Permissions (Android 12+)
The app requests the following runtime permissions:
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.BLUETOOTH_SCAN`

No location permission is used.

## Pairing a device (HC-05 / ESP32 SPP)
1. Open Android **Settings > Connected devices > Pair new device**.
2. Put your HC-05/ESP32 into pairing mode.
3. Select the device and finish pairing (PIN is usually `1234` or `0000`).

## Using the app
1. Launch the app and grant Bluetooth permissions when prompted.
2. In **Connection**, tap **Select paired device** and choose the bonded device.
3. Tap **Connect** to open the SPP connection.
4. Use **Scope**, **Panel**, or **Combo** tabs to view telemetry.

### Demo Mode
Enable **Demo Mode** in Settings to simulate telemetry at ~20 Hz without hardware. This uses the same parser and UI pipeline.

### Alerts
- Set the **Strength threshold** slider (0–100).
- Toggle **Alerts** on to enable haptic + tone alerts.
- Alerts are debounced to once per second unless the signal drops below and rises again.

### Logging & Export
- Tap **Start Recording** to create a CSV in app-specific storage.
- Tap **Export CSV** to share the log using a system share sheet.

## Telemetry format
Each line is parsed if it begins with `S;` and accepts missing fields:
```
S;ts=123456;raw=1876;filt=1900;env=642;strength=76;depth=1.40;cls=gold_like;conf=71
```

## Notes
- Bluetooth Classic SPP UUID used: `00001101-0000-1000-8000-00805F9B34FB`
- The app lists **paired devices** and connects to the selected one.
