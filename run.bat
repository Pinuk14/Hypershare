@echo off
setlocal enabledelayedexpansion

title HyperShare - Build ^& Deployment Tool

echo ========================================================
echo             HyperShare Android Deployment Tool
echo ========================================================
echo.

:: -----------------------------------------------------------------------------
:: Step 1: Detect and Set JAVA_HOME
:: -----------------------------------------------------------------------------
echo [*] Checking Java environment...

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        goto :JAVA_OK
    )
)

:: Search common installation paths
if exist "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
    goto :JAVA_OK
)

if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    goto :JAVA_OK
)

:: Scan Program Files for any JDK 17 or 21
for /d %%D in ("C:\Program Files\Java\jdk-17*" "C:\Program Files\Java\jdk-21*" "C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Eclipse Adoptium\jdk-17*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :JAVA_OK
    )
)

:: Check if javac is in PATH
where javac >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [+] Java is available in system PATH.
    goto :JAVA_VERIFIED
)

echo [!] ERROR: No suitable Java Development Kit (JDK 17 or 21) found!
echo     Please install OpenJDK 17 via: winget install Microsoft.OpenJDK.17
echo.
pause
exit /b 1

:JAVA_OK
echo [+] Using JAVA_HOME: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

:JAVA_VERIFIED
echo.

:: -----------------------------------------------------------------------------
:: Step 1.5: Detect and Set ANDROID_HOME
:: -----------------------------------------------------------------------------
if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
        set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
    )
)
if defined ANDROID_HOME (
    echo [+] Using ANDROID_HOME: %ANDROID_HOME%
)

:: -----------------------------------------------------------------------------
:: Step 2: Detect ADB (Android Debug Bridge)
:: -----------------------------------------------------------------------------
echo [*] Checking Android Debug Bridge (ADB)...

set "ADB_BIN=adb"
where adb >nul 2>nul
if %ERRORLEVEL% equ 0 goto :ADB_OK

:: Try WinGet Package Path
set "WINGET_ADB=%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
if exist "%WINGET_ADB%" (
    set "ADB_BIN=%WINGET_ADB%"
    goto :ADB_OK
)

:: Try Android SDK Path
set "SDK_ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if exist "%SDK_ADB%" (
    set "ADB_BIN=%SDK_ADB%"
    goto :ADB_OK
)

echo [!] ERROR: ADB not found!
echo     Please install it via: winget install Google.PlatformTools
echo.
pause
exit /b 1

:ADB_OK
echo [+] Using ADB: %ADB_BIN%
echo.

:: -----------------------------------------------------------------------------
:: Step 3: Check Connected Devices
:: -----------------------------------------------------------------------------
echo [*] Scanning for connected Android devices...
"%ADB_BIN%" start-server >nul 2>nul

set "DEVICE_COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB_BIN%" devices') do (
    if not "%%A"=="" (
        if "%%B"=="device" (
            set /a DEVICE_COUNT+=1
            echo     [Device !DEVICE_COUNT!] Serial: %%A [Authorized]
        ) else if "%%B"=="unauthorized" (
            echo     [!] WARNING: Device %%A is UNAUTHORIZED!
            echo         Unlock your phone screen and tap "Allow USB debugging".
        )
    )
)

if %DEVICE_COUNT% equ 0 (
    echo.
    echo [!] WARNING: No authorized devices connected.
    echo     Connect your phone via USB or Wireless Debugging.
    echo.
) else (
    echo [+] Total authorized devices: %DEVICE_COUNT%
    echo.
)

:: -----------------------------------------------------------------------------
:: Step 4: Menu Options
:: -----------------------------------------------------------------------------
:MENU
echo ========================================================
echo Select an action:
echo ========================================================
echo  [1] Compile and Install (Build debug APK and deploy)
echo  [2] Install only (Deploy existing build output)
echo  [3] Compile only (Build APK without installing)
echo  [4] Launch HyperShare on connected device(s)
echo  [5] Take Screenshot from connected device(s)
echo  [6] Exit
echo ========================================================
set /p "CHOICE=Enter option [1-6]: "

if "%CHOICE%"=="1" goto :ACTION_COMPILE_AND_INSTALL
if "%CHOICE%"=="2" goto :ACTION_INSTALL_ONLY
if "%CHOICE%"=="3" goto :ACTION_COMPILE_ONLY
if "%CHOICE%"=="4" goto :ACTION_LAUNCH_ONLY
if "%CHOICE%"=="5" goto :ACTION_TAKE_SCREENSHOT
if "%CHOICE%"=="6" exit /b 0

echo [!] Invalid option. Please choose between 1 and 6.
echo.
goto :MENU

:: -----------------------------------------------------------------------------
:: Actions
:: -----------------------------------------------------------------------------

:ACTION_COMPILE_AND_INSTALL
echo.
echo ========================================================
echo [*] Compiling HyperShare with Gradle...
echo ========================================================
call .\gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] ERROR: Compilation failed! Check Gradle build errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [+] Compilation successful!
goto :DO_INSTALL

:ACTION_INSTALL_ONLY
echo.
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo [!] ERROR: No APK found at %APK_PATH%!
    echo     Please choose Option [1] to compile first.
    echo.
    goto :MENU
)
goto :DO_INSTALL

:ACTION_COMPILE_ONLY
echo.
echo ========================================================
echo [*] Compiling HyperShare (Assemble Debug)...
echo ========================================================
call .\gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] ERROR: Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo [+] Build Successful! APK located at:
echo     app\build\outputs\apk\debug\app-debug.apk
echo.
pause
exit /b 0

:DO_INSTALL
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo [!] ERROR: Build artifact not found at %APK_PATH%!
    pause
    exit /b 1
)

if %DEVICE_COUNT% equ 0 (
    echo [!] Cannot install: No authorized devices detected!
    pause
    exit /b 1
)

echo.
echo ========================================================
echo [*] Installing HyperShare to connected device(s)...
echo ========================================================

for /f "skip=1 tokens=1,2" %%A in ('"%ADB_BIN%" devices') do (
    if "%%B"=="device" (
        echo [*] Installing on device: %%A...
        "%ADB_BIN%" -s %%A install -r -d "%APK_PATH%"
        if !ERRORLEVEL! neq 0 (
            echo [!] Standard install failed on %%A - signature mismatch or update conflict.
            echo [*] Attempting clean reinstall - uninstalling old build first...
            "%ADB_BIN%" -s %%A uninstall com.hypershare >nul 2>nul
            "%ADB_BIN%" -s %%A install "%APK_PATH%"
            if !ERRORLEVEL! neq 0 (
                echo [!] Failed to install on %%A!
                echo     Tip for Xiaomi/Redmi/POCO: Unlock screen and enable 'Install via USB'.
            ) else (
                echo [+] Successfully installed on %%A [clean install]!
            )
        ) else (
            echo [+] Successfully installed on %%A!
        )
    )
)

echo.
set /p "LAUNCH_CHOICE=Do you want to launch the app now? (Y/N): "
if /i "%LAUNCH_CHOICE%"=="Y" goto :ACTION_LAUNCH_ONLY
pause
exit /b 0

:ACTION_LAUNCH_ONLY
echo.
echo [*] Launching HyperShare (com.hypershare/.MainActivity)...
for /f "skip=1 tokens=1,2" %%A in ('"%ADB_BIN%" devices') do (
    if "%%B"=="device" (
        "%ADB_BIN%" -s %%A shell am start -n com.hypershare/.MainActivity
    )
)
echo [+] Launch complete!
echo.
pause
goto :MENU

:ACTION_TAKE_SCREENSHOT
echo.
echo ========================================================
echo [*] Capturing Screenshot from connected device(s)...
echo ========================================================

if not exist "screenshots" mkdir "screenshots"

if %DEVICE_COUNT% equ 0 (
    echo [!] ERROR: No authorized devices connected!
    echo     Connect a device and enable USB debugging.
    echo.
    pause
    goto :MENU
)

for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format 'yyyyMMdd_HHmmss'" 2^>nul') do set "TIMESTAMP=%%I"
if not defined TIMESTAMP set "TIMESTAMP=%date:~-4%%date:~4,2%%date:~7,2%_%random%"

set "SHOT_COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB_BIN%" devices') do (
    if "%%B"=="device" (
        set /a SHOT_COUNT+=1
        set "DEVICE_SERIAL=%%A"
        set "REMOTE_FILE=/sdcard/hypershare_shot_!DEVICE_SERIAL!.png"
        set "LOCAL_FILE=screenshots\screenshot_!DEVICE_SERIAL!_!TIMESTAMP!.png"

        echo [*] Capturing from device [!SHOT_COUNT!]: !DEVICE_SERIAL!...
        "%ADB_BIN%" -s !DEVICE_SERIAL! shell screencap -p "!REMOTE_FILE!"
        "%ADB_BIN%" -s !DEVICE_SERIAL! pull "!REMOTE_FILE!" "!LOCAL_FILE!" >nul 2>nul
        "%ADB_BIN%" -s !DEVICE_SERIAL! shell rm "!REMOTE_FILE!" >nul 2>nul

        if exist "!LOCAL_FILE!" (
            echo [+] Screenshot saved to: !LOCAL_FILE!
        ) else (
            echo [!] Failed to capture screenshot on !DEVICE_SERIAL!.
        )
    )
)

echo.
echo [*] All captures saved in: %CD%\screenshots
echo.
set /p "OPEN_CHOICE=Do you want to open the screenshots folder? (Y/N): "
if /i "%OPEN_CHOICE%"=="Y" (
    explorer "screenshots"
)
echo.
pause
goto :MENU
