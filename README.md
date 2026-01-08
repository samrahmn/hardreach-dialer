# Hardreach Auto-Dialer App

Android app that automatically initiates conference calls from your CRM using your phone's SIM card.

## Features

- ✅ Auto-dial from CRM triggers
- ✅ Calls team member first, then contact
- ✅ Auto-conference merge
- ✅ Uses phone SIM (not internet calling)
- ✅ Runs in background
- ✅ Auto-starts on boot

## Installation

### Download APK

1. Go to [GitHub Actions](../../actions)
2. Click the latest successful build
3. Download `hardreach-dialer-apk` artifact
4. Extract ZIP and get the APK file

### Install on Phone

1. Transfer APK to your Android phone
2. Enable "Unknown sources" in Settings → Security
3. Tap APK file to install
4. Grant all requested permissions:
   - Phone calls
   - Phone state
   - Answer calls
   - Notifications

## Setup

1. Open Hardreach Dialer app
2. Enter your CRM server URL: `https://grow.hardreach.com`
3. Enter your API key (get from CRM settings)
4. Enable "Enable Service" toggle
5. Tap "Save Settings"

## How It Works

1. App runs in background and polls CRM every 15 seconds
2. When CRM triggers a call:
   - App calls **you** first
   - You answer
   - App then calls the **contact**
   - App merges both into conference call

## CRM Integration

Add this endpoint to your CRM:

```
POST /api/dialer/pending-calls
Authorization: Bearer {api_key}

Response:
{
  "calls": [
    {
      "id": "123",
      "team_member_number": "+971501234567",
      "contact_number": "+971509876543"
    }
  ]
}
```

## Requirements

- Android 8.0 (API 26) or higher
- Phone with SIM card
- Active CRM account

## Permissions

- `CALL_PHONE` - Make outgoing calls
- `READ_PHONE_STATE` - Monitor call status
- `ANSWER_PHONE_CALLS` - Auto-answer (not used currently)
- `MANAGE_OWN_CALLS` - Manage call state
- `FOREGROUND_SERVICE` - Run background service
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot

## Troubleshooting

**Service stops after phone restart**
- Check that "Auto-start" permission is enabled for the app
- Some manufacturers (Xiaomi, Huawei) require extra battery optimization settings

**Calls don't merge automatically**
- Auto-merge may not work on all devices
- Manually tap "Merge" button when both calls are active

**Permissions denied**
- Go to Settings → Apps → Hardreach Dialer → Permissions
- Enable all permissions manually

## Build from Source

```bash
git clone https://github.com/samrahmn/hardreach-dialer.git
cd hardreach-dialer
./gradlew assembleRelease
```

APK will be in `app/build/outputs/apk/release/`

## License

MIT License - Use freely
