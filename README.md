# NotiHop

Android app that forwards notifications one-way from a **sender** phone to a **receiver** phone over Classic Bluetooth (RFCOMM/SPP). Both phones install the same APK; the user picks a role at runtime.

## How it works

- The sender captures its own notifications via `NotificationListenerService` and transmits them over Bluetooth.
- The receiver posts them as local notifications.
- Transport: 4-byte big-endian length prefix + UTF-8 JSON.
- Connection model: connect-on-demand. The sender connects when a notification arrives, drains the queue (5 s idle window), then disconnects.

## Features

- **App filter** — per-app toggle controls which apps' notifications are forwarded. New apps are enabled automatically by default (configurable).
- **Phone call forwarding** — optionally forwards incoming calls as notifications, including caller name looked up from contacts. Enabled per-device in Settings.

## Requirements

- Android API 31+ on both devices
- Classic Bluetooth (not BLE) on both devices
- On the sender: grant Notification Access in system Settings → Notification Access

## Permissions required at runtime

| Permission | Needed by |
|---|---|
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | Both roles (API 31+) |
| `POST_NOTIFICATIONS` | Receiver (API 33+) |
| `READ_PHONE_STATE`, `READ_CALL_LOG` | Sender — phone call forwarding (optional feature) |
| `READ_CONTACTS` | Sender — resolve caller name from contacts (optional feature) |
| Battery optimisation exemption | Both roles (recommended for reliability) |
| Notification Access (system setting) | Sender only |
