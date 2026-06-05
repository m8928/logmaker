#!/usr/bin/env sh

JAVA_OPTS=${JAVA_OPTS:-}

nohup java \
  -Xms1G \
  -Xmx2G \
  -XX:+UseG1GC \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+G1SummarizeConcMark \
  -XX:InitiatingHeapOccupancyPercent=35 \
  ${JAVA_OPTS} \
  -Dplugin.root=./plugins \
  -Ddata.root=./data \
  -jar logmaker-core-3.0.0-exec.jar >/dev/null 2>&1 &
