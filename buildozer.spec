[app]

# (str) Title of your application
title = MP4转MP3

# (str) Package name
package.name = mp4tomp3

# (str) Package domain (needed for android/ios packaging)
package.domain = com.mp4tomp3.app

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (let empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas,ttf

# (list) Application version
version = 1.0

# (list) Application requirements
requirements = kivy,plyer

# (str) Supported orientation (one of landscape, sensorLandscape, portrait or all)
orientation = portrait

# (list) Permissions
android.permissions = READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, INTERNET

# (int) Target Android API
android.api = 34

# (int) Minimum API required (Android 5.0)
android.minapi = 21

# (int) NDK API
android.ndk = 25

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86_64, x86
android.arch = arm64-v8a

# (bool) Use full-screen mode
fullscreen = 0

# (str) Presplash of the application
presplash.filename = %(source.dir)s/presplash.png

# (str) Icon of the application
icon.filename = %(source.dir)s/icon.png

# (list) Pattern to whitelist for the whole project
source.include_patterns = assets/*,ffmpeg/**/*

# (bool) Accept Android SDK license automatically (needed for CI)
android.accept_sdk_license = True

# (bool) If True, will skip building aar files
android.skip_update = False

# (str) Android logcat filters to use
android.logcat_filters = *:S python:D

# (bool) Whether to automatically add the app source dir to the android build
android.add_src = True

# (bool) Allow app to be installed on external SD card
android.allow_backup = True

# (str) Android entry point
android.entrypoint = org.kivy.android.PythonActivity

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug)
log_level = 2

# (int) Display warning if buildozer is run as root (0 = False, 1 = True)
warn_on_root = 1

# (str) Path to build artifact storage
build_dir = .buildozer

# (str) Path to build output (i.e. .apk)
bin_dir = ./bin
