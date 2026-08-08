Here is the step-by-step guide to connect your phone, deploy the app, and test the UI directly on your device.

---

### Step 1: Enable Developer Options & USB Debugging on Phone

1. On your Android phone, open **Settings** -> **About Phone**.
2. Scroll down to **Build Number** and tap it **7 times** until you see *"You are now a developer!"*.
3. Go back to **Settings** -> **System** (or **Additional Settings**) -> **Developer Options**.
4. Turn on **USB Debugging**.

---

### Step 2: Connect Phone to PC

Choose **Method A** (USB Cable — recommended) or **Method B** (WiFi):

#### Method A: USB Cable (Easiest)
1. Plug your phone into your PC using a USB data cable.
2. Unlock your phone. You will see a pop-up prompt: *"Allow USB debugging from this computer?"*.
3. Check **Always allow from this computer** and tap **Allow**.

#### Method B: Wireless Debugging via WiFi (Android 11+)
1. Ensure both your PC and phone are on the **same WiFi network**.
2. On phone: Go to **Developer Options** -> Enable **Wireless Debugging** -> Tap **Wireless Debugging**.
3. Tap **Pair device with pairing code** (it will display an IP Address, Port, and 6-digit Pairing Code).
4. Run these two commands in PowerShell:
   ```powershell
   # 1. Pair device (replace with IP, Port, and Code shown on your phone)
   C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe pair 192.168.x.x:PORT 123456

   # 2. Connect device
   C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe connect 192.168.x.x:PORT
   ```

---

### Step 3: Verify Connection & Install App

#### 1. Verify Connected Device
Run this command to check if your phone is recognized:
```powershell
C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```
*(You should see your phone's serial number listed under `List of devices attached`)*

#### 2. Install Debug APK Directly to Phone
Run this command in the terminal to compile and install HyperShare on your phone:
```powershell
$env:JAVA_HOME="C:\Users\ADMIN\.vscode\extensions\redhat.java-1.51.0-win32-x64\jre\21.0.9-win32-x86_64"; .\gradlew.bat installDebug
```

> [!TIP]
> **If you see `INSTALL_FAILED_USER_RESTRICTED` (Xiaomi / Redmi / POCO devices):**
> 1. **Unlock the phone screen** before running the install command.
> 2. Watch your phone screen when installing — tap **ALLOW** / **INSTALL** on the Security Pop-up.
> 3. Go to **Settings** -> **Developer Options** -> Enable **"Install via USB"**.
> 4. Alternatively install via ADB directly:
>    ```powershell
>    C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
>    ```

#### 3. Launch App Automatically
Launch HyperShare on your phone screen:
```powershell
android run --activity="com.hypershare.MainActivity"
```
*(Or simply tap the **HyperShare** app icon on your phone's home screen!)*

---

### Step 4: Testing & Inspecting on Device

Once open on your phone:
1. Tap the **`⚙ Test`** button in the top right corner of the **HyperShare Peers** home screen to open the **Crypto & Protocol Playground**.
2. Tap **"Generate & Compute ECDH"** to test live Curve25519 key derivation on your phone.
3. Type text into **"AES-256-GCM Encryption"** and tap **"Encrypt & Decrypt"** to test payload encryption.
4. Tap **"Serialize & Parse MSG Packet"** to test binary protocol serialization.
5. Tap **"Run TOFU Test Flow"** to test identity key pinning.

#### Handy Inspection Commands (Run from PC terminal while app is open):
* **Capture phone screen into IDE**:
  ```powershell
  android screen capture
  ```
* **Inspect live UI layout tree**:
  ```powershell
  android layout --pretty
  ```
* **Capture screenshots of all devices**:
  ```powershell
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"; android screen capture --device="20805d020409" -o="screenshots/device_20805d020409_$ts.png"; android screen capture --device="mzv4pfkvlnf6bqx4" -o="screenshots/device_mzv4pfkvlnf6bqx4_$ts.png"
  ```