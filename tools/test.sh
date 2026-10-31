#!/bin/sh
# Build the mod, install it in the headless test server, boot the server, report values.
# Usage: SERVER=<server folder> [PACK=<pack list>] sh tools/test.sh   (see CLAUDE.md for the server setup)
S="${SERVER:?set SERVER to the headless Forge server folder}"
P="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
cd "$P" && rm -f build/libs/*.jar && ./gradlew build -q 2>&1 | grep -v "^WARNING" | tail -40
J=$(ls build/libs/upgraderwheel-*.jar | grep -v -- -dev | head -1)
[ -f "$J" ] || { echo "BUILD FAILED"; exit 1; }
rm -f "$S"/mods/upgraderwheel-*.jar && cp "$J" "$S/mods/"
[ -n "$PACK" ] && cp "$PACK" "$S/config/upgraderwheel-pack.txt"
for attempt in 1 2 3 4; do
rm -f "$S/config/upgraderwheel-values.csv" "$S/console.log" "$S/logs/latest.log"
  cd "$S" && ( (for i in $(seq 1 380); do grep -q -E "Done \(|Crash report|Encountered an unexpected" logs/latest.log 2>/dev/null && break; sleep 1; done; sleep 2; echo stop; sleep 30) | timeout 420 "/c/Program Files/Java/jre1.8.0_351/bin/java.exe" -Xmx6G -Xms2G -XX:+UseG1GC -jar forge-1.12.2-14.23.5.2860.jar nogui > console.log 2>&1)
  [ -f "$S/config/upgraderwheel-values.csv" ] && break
  echo "server attempt $attempt failed, retrying"
done
grep -E "Upgrader values ready|\[Upgrader Wheel\].*(WARN|ERROR)|Crash report|Exception" logs/latest.log | grep -iv "NormalStringPool" | head -20
ls -la config/upgraderwheel-values.csv
