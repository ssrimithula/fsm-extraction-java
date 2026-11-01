#!/usr/bin/env bash
# Runs the JUnit 5 test-suite without Maven. (With Maven installed:  mvn test)
set -e
cd "$(dirname "$0")"
./build.sh 2>&1 | grep -v "Picked up"
CON=$(ls lib/junit-platform-console-standalone*.jar 2>/dev/null | head -1)
if [ -z "$CON" ]; then
  CON=lib/junit-platform-console-standalone-1.10.2.jar
  if [ -f /usr/share/java/junit-platform-console-standalone.jar ]; then CON=lib/junit-platform-console-standalone-local.jar; cp /usr/share/java/junit-platform-console-standalone.jar $CON
  else echo "downloading JUnit console launcher ..."; curl -fsSL -o $CON https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar; fi
fi
rm -rf build/test-classes; mkdir -p build/test-classes
javac --release 11 -cp "$CON:build/classes" -d build/test-classes test/java/*.java 2>&1 | grep -v "Picked up" || true
java -Dproject.root=$PWD -jar $CON --class-path "build/classes:build/test-classes" --scan-class-path build/test-classes \
     --details=tree --details-theme=ascii --single-color 2>&1 | grep -v "Picked up"
