package me.blueat.logmaker.core.loggen;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.support.Result;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

import me.blueat.logmaker.core.maker.MakerService;

@Service
@RequiredArgsConstructor
public class LogService {
    private ConcurrentHashMap<String, LogThread> logThreadMap;

    private final MakerService makerService;
    private final SenderService senderService;

    @PostConstruct
    protected void init() {
        logThreadMap = new ConcurrentHashMap<>();
    }

    public Result createLog(LogDto logDto) {
        if (!logThreadMap.containsKey(logDto.getName())) {
            try {
                LogThread logThread =
                        new LogThread(makerService, senderService, logDto);
                logThread.start();
                logThreadMap.put(logDto.getName(), logThread);
                return Result.createResultSet(Result.Type.SUCCESS, "maker create success");
            }
            catch (IllegalStateException ise) {
                return Result.createResultSet(Result.Type.ERROR, ise.getMessage());
            } catch (JsonProcessingException e) {
                return Result.createResultSet(Result.Type.ERROR, e.getMessage());
            }
        }
        else {
            return Result.createResultSet(Result.Type.ERROR, "log name is duplicated");
        }
    }

    public LogThread[] getLog() {
        return logThreadMap.values().toArray(new LogThread[]{});
    }

    public LogThread getLog(String name) {
        if (logThreadMap.containsKey(name)) {
            return logThreadMap.get(name);
        }
        return null;
    }

    public boolean removeLog(String name) {
        if (logThreadMap.containsKey(name)) {
            logThreadMap.get(name).interrupt();
            logThreadMap.remove(name);
            return true;
        }
        else {
            return false;
        }
    }
}
