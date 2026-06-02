#!/usr/bin/env sh

JMX_OPTS=""
if [ "${JMX_ENABLED:-false}" = "true" ]; then
  JMX_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=${JMX_PORT:-19998} -Dcom.sun.management.jmxremote.ssl=${JMX_SSL:-false} -Dcom.sun.management.jmxremote.authenticate=${JMX_AUTHENTICATE:-false}"
fi

nohup java \
  -Xms1G \
  -Xmx2G \
  -XX:+UseG1GC \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+G1SummarizeConcMark \
  -XX:InitiatingHeapOccupancyPercent=35 \
  ${JMX_OPTS} \
  -Dplugin.root=./plugins \
  -Ddata.root=./data \
  -jar logmaker-core-3.0.0-exec.jar >/dev/null 2>&1 &
