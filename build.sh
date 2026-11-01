#!/usr/bin/env bash
# Builds everything: agent jar (Java 11 bytecode, ASM bundled inside), tools, benchmarks.
# Needs: JDK 11+ (javac, jar). ASM is downloaded from Maven Central if lib/asm-*.jar are missing.
set -e
cd "$(dirname "$0")"
mkdir -p lib build/agent build/classes
ASM_VER=9.7
for m in asm asm-commons asm-tree asm-analysis; do
  [ -f lib/$m-$ASM_VER.jar ] && continue
  if [ -f /usr/share/java/$m-$ASM_VER.jar ]; then cp /usr/share/java/$m-$ASM_VER.jar lib/
  else echo "downloading $m-$ASM_VER.jar ..."; curl -fsSL -o lib/$m-$ASM_VER.jar \
        https://repo1.maven.org/maven2/org/ow2/asm/$m/$ASM_VER/$m-$ASM_VER.jar; fi
done
CP=$(ls lib/*.jar | tr '\n' ':')
rm -rf build/agent/*; javac --release 11 -cp "$CP" -d build/agent agent/src/*.java
for j in lib/*.jar; do (cd build/agent && unzip -oq ../../$j -x 'META-INF/*' 'module-info.class' >/dev/null 2>&1 || true); done
printf 'Premain-Class: FSMAgent\nCan-Redefine-Classes: false\nCan-Retransform-Classes: false\n' > build/manifest.txt
jar cfm agent/FSMAgent.jar build/manifest.txt -C build/agent .
rm -rf build/classes/*
javac --release 11 -d build/classes phase1/*.java tools/*.java benchmarks/*.java verification/*.java
echo "build OK -> agent/FSMAgent.jar, build/classes/"
