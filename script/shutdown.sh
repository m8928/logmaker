PID=`ps -ef | grep "logmaker-core-3.0.0-exec.jar" | grep -v grep | awk '{print $2}'`

kill -15 $PID
