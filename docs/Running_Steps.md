# HyperShare — Universal Running & Device Deployment Guide

This guide provides universal, cross-platform instructions (Windows, macOS, Linux) to connect physical Android devices, build, deploy, and test HyperShare.

---

## Prerequisites

1. **Android Device**: Android 8.0+ (API 26+) with Developer Options enabled.
2. **Java Development Kit (JDK)**: JDK 17 or JDK 21 (required by Android Gradle Plugin 8.x).
   - If Android Studio is installed, you already have Java bundled in the `jbr` directory!
3. **Android SDK Platform Tools (`adb`)**: Included with Android Studio or standalone Command Line Tools.

---

## Step 1: Enable Developer Options & USB Debugging on Phone

1. On your phone: Open **Settings** -> **About Phone**.
2. Scroll to **Build Number** and tap it **7 times** until you see *"You are now a developer!"*.
3. Go back to **Settings** -> **System** (or **Additional Settings**) -> **Developer Options**.
4. Turn on **USB Debugging**.
5. *(Optional for Xiaomi/Redmi/POCO)*: Turn on **"Install via USB"** and **"USB debugging (Security settings)"**.

---

## Step 2: Connect Phone to Computer

Choose **Method A** (USB Cable — recommended) or **Method B** (Wireless Wi-Fi):

### Method A: USB Cable (Easiest)
1. Plug your phone into your computer using a USB data cable.
2. Unlock phone screen. On the prompt *"Allow USB debugging from this computer?"*, check **Always allow** and tap **Allow**.

### Method B: Wireless Debugging via Wi-Fi (Android 11+)
1. Ensure both your computer and phone are connected to the **same Wi-Fi network**.
2. On phone: Go to **Developer Options** -> Enable **Wireless Debugging** -> Tap **Wireless Debugging**.
3. Tap **Pair device with pairing code** (displays IP address, Port, and 6-digit Code).
4. Run in your terminal:
   ```bash
   adb pair <PHONE_IP>:<PAIRING_PORT> <6_DIGIT_CODE>
   adb connect <PHONE_IP>:<CONNECT_PORT>
   ```

---

## Step 3: Verify Device Connection

Run the standard `adb devices` command:

```powershell
adb devices
```

*(If `adb` is not yet in your PATH, run with the full path)*:

* **Windows PowerShell**:
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
  ```
* **Windows Command Prompt (`cmd.exe`)**:
  ```cmd
  "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" devices
  ```
* **macOS**:
  ```bash
  ~/Library/Android/sdk/platform-tools/adb devices
  ```
* **Linux**:
  ```bash
  ~/Android/Sdk/platform-tools/adb devices
  ```

You should see your device listed as `device` (not `unauthorized` or `offline`):
```text
List of devices attached
mzv4pfkvlnf6bqx4    device
```

---

## Step 4: Build & Install HyperShare

### 1. Set JAVA_HOME (If Not Already Configured)

If `java -version` returns an error or you get `JAVA_HOME is not set`:

#### Windows (PowerShell):
```powershell
# If Android Studio is installed, use its bundled JBR:
if (Test-Path "C:\Program Files\Android\Android Studio\jbr") {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
# Or point to your installed JDK 17/21:
# $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

#### macOS / Linux (Bash/Zsh):
```bash
# If Android Studio is installed on macOS:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# On Linux (or standard package manager install):
# export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
```

---

### 2. Compile & Install Debug APK

#### On Windows:
```powershell
.\gradlew.bat installDebug
```

#### On macOS / Linux:
```bash
chmod +x ./gradlew
./gradlew installDebug
```

#### Multi-Device Targeting (If multiple devices are plugged in):
If you have multiple phones connected, specify the device ID:
```bash
# 1. Build the APK without auto-installing:
.\gradlew.bat assembleDebug   # (Windows)
./gradlew assembleDebug       # (macOS/Linux)

# 2. Install to a specific device:
adb -s <DEVICE_ID> install -r app/build/outputs/apk/debug/app-debug.apk
```

> [!TIP]
> **Troubleshooting `INSTALL_FAILED_USER_RESTRICTED` (Xiaomi / Redmi / POCO devices):**
> 1. Unlock your phone screen before running the install command.
> 2. Keep the screen on and tap **ALLOW** / **INSTALL** on the system security prompt.
> 3. Ensure **Settings** -> **Developer Options** -> **"Install via USB"** is enabled.

---

## Step 5: Launch & Test on Device

### 1. Launch HyperShare Automatically

Via standard ADB:
```bash
adb shell am start -n com.hypershare/.MainActivity
```

Or target a specific device:
```bash
adb -s <DEVICE_ID> shell am start -n com.hypershare/.MainActivity
```

*(Or simply tap the **HyperShare** app icon on your phone's home screen!)*

---

### 2. Testing Features on Devices

Once HyperShare is open on your device(s):

1. **Nearby Share / Quick Share File Transfer (Week 9)**:
   - Tap **"Quick Share & File Transfer"** on the home screen or tap **📎 Attachment** inside any chatroom.
   - Pick any file (video, document, image, audio, APK).
   - Select a discovered peer chip on the radar to send immediately.
   - Observe real-time transfer speed (`⚡ MB/s`), average speed (`📊 Avg`), and dynamic ETA.
   - On completion, verify the summary card and tap **"Open File"** to view it with your phone's default viewer.
2. **Local Mode Messaging (Mode 1)**:
   - Connect 2 phones to the same Wi-Fi router.
   - Verify both devices show up in the Peer List within 3–5 seconds.
   - Send messages and observe instant 3-state tick delivery (`✓` Sent, `✓✓` Delivered, `✓✓` Read).
3. **Emergency Wi-Fi Direct Mesh (Mode 2)**:
   - Switch to **Emergency Mode** (Red Card).
   - Observe automatic P2P group formation and zero-touch Wi-Fi Direct connection without router/internet.
4. **Crypto & Protocol Playground**:
   - Open **Settings** -> **Developer Options** -> **Security Playground** to test on-device Curve25519 ECDH key generation, AES-256-GCM encryption/decryption, and packet serialization.

---

## Handy Inspection & Debugging Commands

* **View live logcat filtered for HyperShare**:
  ```bash
  adb logcat -s "LanSocketManager" "TransferManager" "WifiDirectController" "DiscoveryService"
  ```
* **Take a screenshot and pull to computer**:
  ```bash
  adb exec-out screencap -p > screenshot.png
  ```
* **Inspect app storage & downloaded files**:
  ```bash
  adb shell ls -la /sdcard/Download/HyperShare/
  ```