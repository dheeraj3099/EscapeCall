# EscapeCall

EscapeCall is a local-first Android safety prototype that presents a believable incoming phone call backed by an Agora Conversational AI agent. The caller is configured as Rohan, the user's older brother. During a live call, a locally stored safety phrase can trigger a location-aware SMS to a registered emergency contact without changing the visible call experience.

> EscapeCall is a prototype for personal safety workflows. It is not a replacement for emergency services or a verified emergency communications system.


<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/89cb7f4a-13f4-4d8d-9a28-2d7fde1a539a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/6a8a9137-83a8-473e-a9bb-9da800ffe56f" />
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/4fccc375-02bb-4158-b920-3f0aa514d507" />





## What It Does

- Shows a full-screen incoming-call experience with accept and decline actions.
- Plays the device's configured ringtone while the incoming-call screen is visible.
- Uses Agora RTC for live audio and Agora RTM/Signaling for transcript and agent events.
- Starts a published Agora Conversational AI agent configured in Agora Studio.
- Stores persona, codeword, emergency contact, demo mode, and call history on the device with DataStore.
- Detects the user's configured safety phrase from user transcript events.
- Fetches the current location and sends an SMS through `SmsManager`.
- Falls back to a local notification when SMS permission, a SIM, or the device policy prevents SMS delivery.
- Keeps transcript text in memory during the call and does not display it on the call screen.

## Architecture

```text
Android app (Kotlin + Compose + MVVM)
  ├─ DataStore: persona, codeword, contact, history
  ├─ Agora RTC: microphone and agent audio
  ├─ Agora RTM toolkit: transcript and agent-state events
  ├─ FusedLocationProviderClient: current location
  └─ SmsManager / notification fallback: emergency alert
             │ HTTPS
             ▼
      FastAPI backend
        ├─ RTC/RTM token generation
        ├─ starts/stops published Agora agent
        └─ receives alert result logs
             │ HTTPS + Basic Auth
             ▼
      Agora Conversational AI Engine
        └─ published pipeline: ASR, LLM, TTS, prompt, greeting
```

The Agora App Certificate, Customer Secret, and provider credentials stay on the backend. No Agora secret is compiled into the Android application.

## Project Structure

```text
app/                         Android Kotlin/Compose application
app/src/main/.../rtc/        RTC, RTM, and agent session lifecycle
app/src/main/.../emergency/  Location and SMS alert flow
app/src/main/.../domain/     Codeword matching
server/app/                  FastAPI backend
server/.env.example          Backend configuration template
```

## Prerequisites

- Android Studio with an Android SDK installed.
- A physical Android device is recommended for microphone, GPS, and SMS testing.
- JDK compatible with the Android Studio/Gradle installation.
- Python 3.9 or newer for the backend.
- An Agora project with RTC and Conversational AI access.
- A published agent in Agora Studio.
- A device and development computer on the same Wi-Fi network for local physical-device testing.

Official references:

- [Agora Android voice-agent quickstart](https://github.com/AgoraIO-Conversational-AI/recipe-client-android-quickstart)
- [Agora Connect your published agent](https://github.com/AgoraIO/docs-portal/blob/main/content/docs/en/ai/studio/deploy/connect-agent.mdx)
- [Agora Conversational AI quickstart](https://docs.agora.io/en/ai/get-started/quickstart)

## Agora Setup

### 1. Create or select an Agora project

Enable the required RTC and Conversational AI services. Copy the following values from the Agora Console:

- **App ID**
- **App Certificate** with the primary certificate enabled
- **Customer ID**
- **Customer Secret**

The Customer ID and Customer Secret are used only by the backend for Basic Auth when calling the Conversational AI REST API.

### 2. Configure and publish the agent

In Agora Studio:

1. Create or open the Rohan agent.
2. Configure the system prompt, greeting, ASR, LLM, TTS voice, and interruption behavior.
3. Publish the agent.
4. Open the published agent's action menu and select **Embed Agent**.
5. Copy the exact `name` and `pipeline_id` values from the generated request.

For the basic version, provider configuration belongs in Agora Studio. The backend starts the published pipeline and supplies only the per-call channel, RTC UIDs, and agent token.

## Backend Setup

From the project root:

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
```

Edit `server/.env`:

```env
AGORA_APP_ID=your_app_id
AGORA_APP_CERTIFICATE=your_app_certificate

AGORA_API_KEY=your_customer_id
AGORA_API_SECRET=your_customer_secret

AGORA_AGENT_NAME=exact_name_from_embed_agent
AGORA_AGENT_PIPELINE_ID=your_pipeline_id

TOKEN_EXPIRY_SECONDS=3600
SESSION_TTL_SECONDS=7200
ALLOWED_ORIGINS=*
```

Start the backend:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Verify it locally:

```bash
curl http://127.0.0.1:8000/health
```

Expected response:

```json
{"status":"ok","backend_version":"0.1.0","active_sessions":0}
```

The backend endpoints are:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Backend health check |
| `POST` | `/v1/agora/token` | Creates a channel and user RTC/RTM token |
| `POST` | `/v1/agora/agent/start` | Starts the published Agora agent |
| `POST` | `/v1/agora/agent/stop` | Stops the agent session |
| `POST` | `/v1/agora/agent/interrupt` | Compatibility endpoint; published pipeline handles interruption |
| `POST` | `/v1/emergency/alert-emergency-contact` | Logs the client alert result |

## Android Setup

### Emulator

Use the default backend URL:

```text
http://10.0.2.2:8000
```

`10.0.2.2` is the Android emulator alias for the development computer.

### Physical Android device

Find the computer's LAN address. On macOS, for example:

```bash
ipconfig getifaddr en0
```

If the active interface is different, use its address. Put the address in the project root `local.properties`:

```properties
ESCAPE_BACKEND_URL=http://192.168.1.9:8000
```

Use the current address, not the example above. The phone and computer must be on the same network, and the backend must bind to `0.0.0.0`.

The debug build allows local HTTP for development. Use HTTPS for a deployed backend.

### Build and install

From the project root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Alternatively, open the project in Android Studio and run the `app` configuration on the connected device.

## First Run

1. Start the backend.
2. Install and launch the Android app.
3. Grant microphone permission before accepting a call.
4. Open setup and configure:
   - caller name and relationship
   - safety phrase
   - emergency contact name and phone number
   - demo mode, if needed
5. Save the settings on the device.
6. Confirm location, SMS, notification, and full-screen permissions in the permission checklist.
7. Wait on the incoming-call screen. EscapeCall prewarms RTC/RTM and the agent with the microphone unpublished and remote audio muted.
8. Tap Accept. The microphone is published and the agent audio is unmuted immediately.
9. Tap the red disconnect button to end the call.

## Emergency Flow

The current implementation performs the emergency action on the Android device:

```text
Agora user transcript
  → user-only transcript filter
  → exact/normalized/fuzzy CodewordMatcher
  → FusedLocationProviderClient
  → SmsManager
  → notification fallback if SMS is unavailable
  → backend result log
  → local call history
```

The codeword is read from local DataStore settings. It is not necessary for the agent to call a tool for this client-side flow. The current backend endpoint logs the result; it does not send SMS because the backend cannot access the phone's SIM or location hardware.

The app logs safety events with the tag `EscapeCallEmergency`:

```bash
adb logcat -s EscapeCallEmergency
```

Example:

```text
Alert triggered; contact=Mom, phone=****1234
Location resolved: lat=..., lon=...
Alert delivery result=SmsSent, target=****1234
Emergency result accepted by backend.
```

The phone number is masked in logs. `SmsSent` means Android accepted the message for submission; carrier delivery is not guaranteed.

## Permissions

EscapeCall requests these permissions at runtime:

| Permission | Reason |
| --- | --- |
| `RECORD_AUDIO` | Live conversation with the agent |
| `ACCESS_FINE_LOCATION` | Include current location in the alert |
| `SEND_SMS` | Send the emergency SMS from the device |
| `POST_NOTIFICATIONS` | Incoming-call and fallback notifications on Android 13+ |
| `USE_FULL_SCREEN_INTENT` | Full-screen incoming-call surface on Android 14+ |

Some manufacturers restrict full-screen intents, background activity launches, SMS, or microphone behavior. Test on the target device model.

## Testing Checklist

### Backend

```bash
cd server
source .venv/bin/activate
python -m compileall -q app
curl http://127.0.0.1:8000/health
```

### Android unit tests

```bash
./gradlew :app:testDebugUnitTest
```

### Physical device

- Confirm the device reaches `/health` through the configured LAN URL.
- Run the bare RTC smoke test.
- Accept a call and verify the agent speaks.
- Speak over the agent and verify interruption behavior.
- Decline an incoming call and confirm the ringtone stops.
- Revoke microphone permission and confirm Accept explains the requirement.
- Say the safety phrase and verify location/SMS/fallback logs.
- Test with a physical SIM and a real test contact.
- Test Android 14+ full-screen intent settings.
- End the call immediately after the codeword and confirm history records the final alert result.

## Troubleshooting

### Android cannot reach the backend

- Confirm the backend is running with `--host 0.0.0.0`.
- Confirm the phone and computer are on the same Wi-Fi network.
- Replace the old LAN address in `local.properties` if the computer's IP changed.
- Use `10.0.2.2` only for an emulator.
- Confirm macOS firewall rules allow port `8000`.

### Agent start returns `502 Bad Gateway`

The backend reached Agora, but Agora rejected the start request. Check:

- `AGORA_API_KEY` is the Customer ID, not the App ID.
- `AGORA_API_SECRET` is the Customer Secret.
- `AGORA_AGENT_NAME` exactly matches the Embed Agent snippet.
- `AGORA_AGENT_PIPELINE_ID` belongs to the same Agora project.
- The agent is published and its TTS/LLM configuration is valid.

Read the complete backend error printed by Uvicorn after pressing Accept.

### Agent connects but produces no voice

Test the published agent inside Agora Studio first. If it is silent there, fix the agent pipeline or TTS configuration before debugging Android. For isolation, temporarily use an Agora-managed TTS provider and a short greeting.

### SMS is not received

- Use a physical phone with a working SIM.
- Confirm `SEND_SMS` is granted.
- Confirm the phone number includes the correct country code where required.
- Check `adb logcat -s EscapeCallEmergency`.
- Confirm the fallback notification appears when SMS is unavailable.

## Privacy and Security Notes

- Keep `server/.env` out of version control.
- Never place the App Certificate, Customer Secret, or provider keys in Android resources or `local.properties`.
- The Android app stores settings and history locally.
- The backend stores active sessions and received alert payloads in memory only.
- The current alert endpoint is a development logging endpoint and should be authenticated before production use.
- Stop any public tunnel after local testing.

## Current Scope and Next Steps

Implemented:

- Kotlin, Jetpack Compose, and MVVM Android client
- Agora RTC/RTM voice-agent session lifecycle
- Published Agora agent REST integration
- Incoming-call notification and ringtone
- Local DataStore settings and call history
- Codeword detection and device-side SMS/location escalation
- Notification fallback, diagnostics, and demo mode

Not yet implemented:

- Agora agent Custom Tool that directly requests the device-side alert
- Production authentication and deployment for the backend
- Persistent backend database or multi-user account system
- Home-screen widget
- Carrier-level SMS delivery confirmation
