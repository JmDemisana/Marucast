@echo off
set IP_PORT=192.168.8.199:42287

echo.
echo ==========================================
echo       Marucast Android APK Installer      
echo ==========================================
echo.

set /p INPUT_IP="Enter phone IP:Port [default: %IP_PORT%]: "
if not "%INPUT_IP%"=="" set IP_PORT=%INPUT_IP%

echo.
echo Connecting to Wireless Debugging at %IP_PORT%...
adb connect %IP_PORT%

echo.
echo Installing Marucast APK...
adb install -r app\build\outputs\apk\debug\app-debug.apk

echo.
echo Done!
pause
