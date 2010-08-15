all: debug

debug:
	ant debug

release:
	ant release

clean:
	ant clean

install: install-emulator

install-emulator: debug
	adb -e uninstall org.codepunks.keyflinger; adb -e install bin/KeyFlinger-debug.apk
install-release-emulator: release
	adb -e uninstall org.codepunks.keyflinger; adb -e install bin/KeyFlinger-release.apk

install-device: debug
	adb -d uninstall org.codepunks.keyflinger; adb -d install bin/KeyFlinger-debug.apk
install-release-device: release
	adb -d uninstall org.codepunks.keyflinger; adb -d install bin/KeyFlinger-release.apk
