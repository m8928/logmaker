package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.DataBindingException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.util.VelocityTemplateUtil;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import me.blueat.logmaker.core.maker.MakerService;
import org.springframework.web.multipart.MultipartFile;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

import static me.blueat.logmaker.core.util.FileUtil.loadFromFile;
import static me.blueat.logmaker.core.util.FileUtil.saveToFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
@Order(3)
public class LogService implements DisposableBean {
    private final ObjectMapper mapper;
    private ConcurrentHashMap<String, LogThread> logThreadMap;

    private final LogMakerConfig logMakerConfig;
    private final MakerService makerService;
    private final SenderService senderService;
    private final VelocityEngine previewEngine = VelocityTemplateUtil.createSecureEngine(1);
    private final Object previewTemplateLock = new Object();

    private ExecutorService executorService;

    @PostConstruct
    protected void init() {
        logThreadMap = new ConcurrentHashMap<>();
        executorService = Executors.newCachedThreadPool();
        LogDto[] loadedLogs = loadFromFile(logStoragePath(), LogDto[].class);
        if (loadedLogs != null) {
            Arrays.stream(loadedLogs).forEach(logDto -> createLog(logDto, true));
        }
        log.info("Initializing Log Service");
    }

    @Override
    public void destroy() {
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ExecutorService did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public ResponseEntity<Result> createLog(LogDto logDto) {
        return createLog(logDto, false);
    }

    public ResponseEntity<Result> createLog(LogDto logDto, boolean isImport) {
        if (logThreadMap.containsKey(logDto.getName())) {
            return Result.createResultSet(Result.Type.ERROR, String.format("%s is the log name already in use", logDto.getName()));
        }
        try {
            LogThread logThread = new LogThread(makerService, senderService, logDto);
            if (logThreadMap.putIfAbsent(logDto.getName(), logThread) != null) {
                logThread.interrupt();
                logThread.releaseReferences();
                return Result.createResultSet(Result.Type.ERROR, String.format("%s is the log name already in use", logDto.getName()));
            }
            executorService.submit(logThread);

            if (!isImport) {
                saveToFile(getLog(), logStoragePath());
            }
            return Result.createResultSet(Result.Type.SUCCESS, "Successful log registration");
        }
        catch (IllegalStateException e) {
            return Result.createResultSet(Result.Type.ERROR, String.format("Invalid log argument (%s)", logDto.getFormat()));
        }
    }

    public List<ResponseEntity<Result>> importLog(MultipartFile json) {
        try {
            LogDto[] logs = mapper.readValue(json.getBytes(), LogDto[].class);
            return Arrays.stream(logs).map(this::createLog).collect(Collectors.toList());
        }
        catch (IOException | DataBindingException e) {
            return Lists.newArrayList(Result.createResultSet(Result.Type.ERROR, "Log file import failed"));
        }
    }

    public ResponseEntity<Result> updateLog(LogDto logDto) {
        Optional<LogThread> existsLog = Optional.ofNullable(logThreadMap.get(logDto.getName()));

        if (existsLog.isPresent() && existsLog.get().updateLogDto(logDto)) {
            saveToFile(getLog(), logStoragePath());
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully updated log");
        }
        else {
            return Result.createResultSet(Result.Type.ERROR, "Update log failed");
        }
    }

    public List<LogDto> getLog() {
        List<LogDto> result = logThreadMap.values().stream()
                .map(LogThread::getLogDto)
                .sorted(Comparator.comparing(LogDto::getRegTime).reversed())
                .collect(Collectors.toList());

        return result;
    }

    public LogThread getLog(String name) {
        return logThreadMap.get(name);
    }

    public ResponseEntity<Result> setPaused(String name, boolean paused) {
        LogThread logThread = logThreadMap.get(name);
        if (logThread != null) {
            logThread.getLogDto().setPaused(paused);
            saveToFile(getLog(), logStoragePath());
            return Result.createResultSet(Result.Type.SUCCESS, paused ? "Log stopped" : "Log started");
        }
        return Result.createResultSet(Result.Type.ERROR, "Log does not exist");
    }

    public ResponseEntity<Result> deleteLog(String name) {
        LogThread removed = logThreadMap.remove(name);
        if (removed != null) {
            removed.interrupt();
            removed.releaseReferences();
            saveToFile(getLog(), logStoragePath());
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted log");
        }
        else {
            return Result.createResultSet(Result.Type.ERROR, "Log does not exist");
        }
    }

    public ResponseEntity<Result> previewLog(String format) {
        ResponseEntity<Result> result;

        try {
            String vFormat;
            ST template = new ST(format);
            Set<String> expressions = new HashSet<>();
            TokenStream tokens = template.impl.tokens;

            for (int i = 0; i < tokens.range(); i++) {
                Token token = tokens.get(i);
                if (token.getType() == STLexer.ID) {
                    expressions.add(token.getText());
                }
            }

            for (String string : expressions) {
                template.add(string, "${" + string + "}");
            }

            vFormat = template.render();
            Map<String, Object> templateData = getTemplateData(expressions);

            result = Result.createResultSet(Result.Type.SUCCESS, renderPreview(vFormat, templateData), false);
        }
        catch (Exception e) {
            result = Result.createResultSet(Result.Type.ERROR, String.format("Invalid log template (%s)", format), false);
        }

        return result;
    }

    private String renderPreview(String vFormat, Map<String, Object> templateData) {
        synchronized (previewTemplateLock) {
            Template vTemplate = VelocityTemplateUtil.compile(previewEngine, "preview", vFormat);
            VelocityContext context = new VelocityContext();
            templateData.forEach((key, value) -> {
                if (value != null) {
                    context.put(key, value);
                }
            });

            StringWriter writer = new StringWriter();
            vTemplate.merge(context, writer);
            return writer.toString();
        }
    }

    private Map<String, Object> getTemplateData(Set<String> expressions) {
        Map<String, Object> result = new HashMap<>();

        for (String key : expressions) {
            makerService.getMaker().stream().filter(m -> m.getName().equals(key)).findAny()
                    .flatMap(p -> makerService.getMaker(key))
                    .ifPresent(v -> result.put(key, v.getValue().getData()));
        }

        return result;
    }

    private String logStoragePath() {
        return String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "logs.json");
    }
}
