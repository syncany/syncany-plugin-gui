#!/bin/bash

set -x

# Be safe and clean!
./gradlew clean

#
# The GUI build/upload process is divided in:
#
# (1) the "plugin upload" of 6 JAR files (windows/linux/mac in 32/64 bit) and
#     2 DEB files (32/64 bit):
#
#    - (a) JAR for Linux 32-bit
#    - (b) JAR for Linux 64-bit
#    - (c) JAR for Windows 32-bit
#    - (d) JAR for Windows 64-bit
#    - (e) JAR for Mac OSX 32-bit
#    - (f) JAR for Mac OSX 64-bit
#    - (g) DEB for 32-bit
#    - (h) DEB for 64-bit
#
# (2) and the "application upload" of 2 EXE files (32/64 bit) and 
#     1 APP.ZIP file (64 bit):
#
#    - (i) APP.ZIP for 64-bit
#    - (j) EXE for 32-bit
#    - (k) EXE for 64-bit
#
# They are treated differently during the upload and on the server side, so 
# it is important to keep the order!
# 

## (0) Requirements ############################################################

mkdir -p build/osx-notifier
wget https://www.syncany.org/r/syncany-osx-notifier-latest.app.zip -O build/osx-notifier/osx-notifier.zip


## (1) Plugin ##################################################################

# Build JARs for different Windows and Linux
./gradlew pluginJar -Pos=linux -Parch=x86 # (a)

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginJar -Pos=linux -Parch=x86_64 # (b)

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginJar -Pos=windows -Parch=x86 # (c)

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginJar -Pos=windows -Parch=x86_64 # (d)

# Build JARs for Mac OSX (needs the syncany-osx-notifier)
mkdir -p src/main/resources/org/syncany/gui/helper
cp build/osx-notifier/osx-notifier.zip src/main/resources/org/syncany/gui/helper/osx-notifier.zip

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginJar -Pos=macosx -Parch=x86 # (e) 

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginJar -Pos=macosx -Parch=x86_64 # (f)

rm -rf src/main/resources/org/syncany/gui/helper

# Build Debian DEBs
rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginDebianGuiDeb -Pos=linux -Parch=x86 -PpluginJarDontCopyToUpload # (g)

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew pluginDebianGuiDeb -Pos=linux -Parch=x86_64 -PpluginJarDontCopyToUpload # (h)


## (2) Application #############################################################

# Create a OSX standalone app in upload dir
mkdir -p src/main/resources/org/syncany/gui/helper
cp build/osx-notifier/osx-notifier.zip src/main/resources/org/syncany/gui/helper/osx-notifier.zip

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew createAppZip -Pos=macosx -Parch=x86_64 # (i)

rm -rf src/main/resources/org/syncany/gui/helper

# Build Windows installer
rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew exeWithGui -Pos=windows -Parch=x86 -PpluginJarDontCopyToUpload # (j)

rm build/resources/main/org/syncany/plugins/gui/plugin.properties
./gradlew exeWithGui -Pos=windows -Parch=x86_64 -PpluginJarDontCopyToUpload # (k)


## (3) Upload them #############################################################

core/gradle/upload/upload-plugin.sh
