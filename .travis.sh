#!/bin/bash

set -x

# Be safe and clean!
./gradlew clean

# Build JARs for different operating systems
./gradlew pluginJar -Pos=linux -Parch=x86
rm build/resources/main/org/syncany/plugins/gui/plugin.properties

./gradlew pluginJar -Pos=linux -Parch=x86_64
rm build/resources/main/org/syncany/plugins/gui/plugin.properties

./gradlew pluginJar -Pos=windows -Parch=x86
rm build/resources/main/org/syncany/plugins/gui/plugin.properties

./gradlew pluginJar -Pos=windows -Parch=x86_64
rm build/resources/main/org/syncany/plugins/gui/plugin.properties

# Build OSX version with included notification helper
mkdir -p src/main/resources/org/syncany/gui/helper
wget https://www.syncany.org/r/syncany-osx-notifier-latest.app.zip -O src/main/resources/org/syncany/gui/helper/osx-notifier.zip

./gradlew pluginJar -Pos=macosx -Parch=x86
rm build/resources/main/org/syncany/plugins/gui/plugin.properties

./gradlew pluginJar -Pos=macosx -Parch=x86_64

# Create a OSX standalone app in upload dir
./gradlew createAppZip -Pos=macosx -Parch=x86_64
rm -r src/main/resources/org/syncany/gui/helper

# Build Debian DEBs
./gradlew pluginDebianGuiDeb -Pos=linux -Parch=x86 -PpluginJarDontCopyToUpload
./gradlew pluginDebianGuiDeb -Pos=linux -Parch=x86_64 -PpluginJarDontCopyToUpload

# Build Windows installer
./gradlew exeWithGui -Pos=windows -Parch=x86 -PpluginJarDontCopyToUpload
./gradlew exeWithGui -Pos=windows -Parch=x86_64 -PpluginJarDontCopyToUpload

# Upload JARs and DEBs
core/gradle/upload/upload-plugin.sh
