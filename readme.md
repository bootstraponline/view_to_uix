
# view_to_uix

Exports Espresso view in uiautomatorviewer format.

# Use

1. Invoke `ViewToUix.dumpView();` from your test.
2. Download png and uix file with `adb pull /sdcard/Android/data/com.example.android.testing.espresso.BasicSample/cache/dump`
3. Open uiautomatorviewer and select file 
<br><img src="readme/open_files.png" width="60%">
4. Inspect the Espresso view hierarchy
<br><img src="readme/inspector.png" width="60%">

# Credits

- [droiddriver](https://android.googlesource.com/platform/external/droiddriver)
- [espresso](https://android.googlesource.com/platform/frameworks/testing/+/android-support-test)
- [uiautomator](https://android.googlesource.com/platform/frameworks/uiautomator/+/android-support-test/src/main/java/android/support/test/uiautomator)
