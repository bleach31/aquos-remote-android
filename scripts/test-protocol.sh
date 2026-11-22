#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p /tmp/aquos-protocol-test
javac -encoding UTF-8 -d /tmp/aquos-protocol-test \
  app/src/main/java/jp/local/aquosremote/AquosProtocol.java \
  app/src/main/java/jp/local/aquosremote/AquosClient.java \
  app/src/main/java/jp/local/aquosremote/KidCommandPolicy.java \
  tests/ProtocolSmokeTest.java
java -cp /tmp/aquos-protocol-test ProtocolSmokeTest
