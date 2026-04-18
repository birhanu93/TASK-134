#!/usr/bin/env bash
set -euo pipefail

cd /app

APP_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' \
    --non-recursive exec:exec 2>/dev/null || echo "1.0.0")
APP_JAR="target/fleetride-console-${APP_VERSION}.jar"

echo ">>> [1/5] compiling app jar (skipping tests — run_tests.sh is separate)"
mvn -B -DskipTests package

if [ ! -f "$APP_JAR" ]; then
    echo "ERROR: expected $APP_JAR not found" >&2
    ls -1 target/ >&2
    exit 1
fi

STAGE=/tmp/FleetRide
DIST=/app/dist
rm -rf "$STAGE"
mkdir -p "$STAGE/lib" "$DIST"

WIN_JDK=$(ls -d /opt/win-jdk/jdk-17* | head -1)
JFX_JMODS=$(ls -d /opt/jfx-win/javafx-jmods-* | head -1)

echo ">>> [2/5] cross-building Windows runtime via jlink"
echo "    WIN_JDK=$WIN_JDK"
echo "    JFX_JMODS=$JFX_JMODS"
"$JAVA_HOME/bin/jlink" \
    --module-path "$WIN_JDK/jmods:$JFX_JMODS" \
    --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,java.management,jdk.crypto.ec,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
    --no-header-files --no-man-pages --compress=2 \
    --output "$STAGE/runtime"

echo ">>> [3/5] copying app jar + runtime dependencies into lib/"
cp "$APP_JAR" "$STAGE/lib/fleetride-console.jar"
mvn -B dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$STAGE/lib" \
    -q
# JavaFX is already baked into the bundled runtime as jlinked modules; keeping
# the jars on the classpath as well causes "module already defined" splits.
rm -f "$STAGE/lib"/javafx-*.jar

echo ">>> [4/5] generating FleetRide.exe launcher via launch4j"
cat >/tmp/l4j.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>true</dontWrapJar>
  <headerType>gui</headerType>
  <outfile>$STAGE/FleetRide.exe</outfile>
  <jar>lib/fleetride-console.jar</jar>
  <errTitle>FleetRide</errTitle>
  <chdir>.</chdir>
  <priority>normal</priority>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <classPath>
    <mainClass>com.fleetride.ui.FleetRideApp</mainClass>
    <cp>lib/*.jar</cp>
  </classPath>
  <jre>
    <path>runtime</path>
    <bundledJre64Bit>true</bundledJre64Bit>
    <minVersion>17</minVersion>
    <opt>--add-modules=javafx.controls,javafx.fxml</opt>
  </jre>
  <versionInfo>
    <fileVersion>${APP_VERSION}.0</fileVersion>
    <txtFileVersion>${APP_VERSION}</txtFileVersion>
    <fileDescription>FleetRide Dispatch and Billing Console</fileDescription>
    <copyright>Local deployment</copyright>
    <productVersion>${APP_VERSION}.0</productVersion>
    <txtProductVersion>${APP_VERSION}</txtProductVersion>
    <productName>FleetRide</productName>
    <internalName>FleetRide</internalName>
    <originalFilename>FleetRide.exe</originalFilename>
  </versionInfo>
</launch4jConfig>
EOF

(cd /opt/launch4j && java -Djava.awt.headless=true -jar launch4j.jar /tmp/l4j.xml)

if [ ! -f "$STAGE/FleetRide.exe" ]; then
    echo "ERROR: launch4j did not produce FleetRide.exe" >&2
    exit 1
fi

echo ">>> [5/5] zipping portable bundle into $DIST"
ZIP_NAME="FleetRide-${APP_VERSION}-windows-x64.zip"
rm -f "$DIST/$ZIP_NAME"
(cd /tmp && zip -qr "$DIST/$ZIP_NAME" FleetRide)

echo ""
echo "    ┌──────────────────────────────────────────────────────────────┐"
echo "    │ Build complete:                                              │"
echo "    │   dist/$ZIP_NAME"
echo "    │                                                              │"
echo "    │ Transfer the zip to a Windows 10/11 machine, unzip, then     │"
echo "    │ double-click FleetRide.exe.                                  │"
echo "    └──────────────────────────────────────────────────────────────┘"
ls -lh "$DIST/$ZIP_NAME"
