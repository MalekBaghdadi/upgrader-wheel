#!/bin/sh
# Build the mod, install it in the headless test server, boot the server, report values.
# Usage: SERVER=<server folder> [PACK=<pack list>] sh tools/test.sh   (see CLAUDE.md for the server setup)
# Java paths default to Malek's Windows PC; elsewhere set GRADLE_JDK (JDK 25, runs Gradle) and JAVA8 (java binary
# that runs the 1.12.2 server). NOBUILD=1 reuses the jar already in build/libs. ANALYZE=1 also kills every mob kind
# and writes config/upgraderwheel-drops.csv (for tools/drops.py; takes a few extra minutes).
S="${SERVER:?set SERVER to the headless Forge server folder}"
P="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${GRADLE_JDK:-/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
JAVA8="${JAVA8:-/c/Program Files/Java/jre1.8.0_351/bin/java.exe}"
WAIT="${WAIT:-900}"
if [ -z "$NOBUILD" ]; then
  cd "$P" && rm -f build/libs/*.jar && sh ./gradlew build -q 2>&1 | grep -v "^WARNING" | tail -40
fi
J=$(ls "$P"/build/libs/upgraderwheel-*.jar | grep -v -- -dev | head -1)
[ -f "$J" ] || { echo "BUILD FAILED"; exit 1; }
rm -f "$S"/mods/upgraderwheel-*.jar && cp "$J" "$S/mods/"
[ -n "$PACK" ] && cp "$PACK" "$S/config/upgraderwheel-pack.txt"
cd "$S" || exit 1
for attempt in 1 2 3 4; do
  rm -f config/upgraderwheel-values.csv console.log logs/latest.log
  [ -n "$ANALYZE" ] && rm -f config/upgraderwheel-drops.csv
  ( (for i in $(seq 1 "$WAIT"); do grep -q -E "Done \(|Crash report|Encountered an unexpected" logs/latest.log 2>/dev/null && break; sleep 1; done; sleep 2; echo stop; sleep 30) \
    | timeout $((WAIT + 60)) "$JAVA8" -Xmx6G -Xms2G -XX:+UseG1GC ${ANALYZE:+-Dupgraderwheel.analyze=true} -jar forge-1.12.2-14.23.5.2860.jar nogui > console.log 2>&1)
  [ -f config/upgraderwheel-values.csv ] && break
  echo "server attempt $attempt failed, retrying"
done
grep -E "Upgrader values ready|Drop analysis|\[Upgrader Wheel\].*(WARN|ERROR)|Crash report|Exception" logs/latest.log | grep -iv "NormalStringPool" | head -20
ls -la config/upgraderwheel-values.csv
