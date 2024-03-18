package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.model.Result;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.xml.bind.DataBindingException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import me.blueat.logmaker.core.maker.MakerService;
import org.springframework.web.multipart.MultipartFile;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogService {
    private final ObjectMapper mapper;
    private ConcurrentHashMap<String, LogThread> logThreadMap;

    private final MakerService makerService;
    private final SenderService senderService;

    @PostConstruct
    protected void init() {
        logThreadMap = new ConcurrentHashMap<>();
    }

    public ResponseEntity<Result> createLog(LogDto logDto) {
        ResponseEntity<Result> result;

        if (!logThreadMap.containsKey(logDto.getName())) {
            try {
                LogThread logThread =
                        new LogThread(makerService, senderService, logDto);
                logThread.start();
                logThreadMap.put(logDto.getName(), logThread);
                result = Result.createResultSet(Result.Type.SUCCESS, "Successful log registration");
            }
            catch (IllegalStateException e) {
                result = Result.createResultSet(Result.Type.ERROR, String.format("Invalid log argument (%s)",logDto.getFormat()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is the sender name already in use", logDto.getName()));
        }

        return result;
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
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully updated log");
        }
        else {
            return Result.createResultSet(Result.Type.ERROR, "Update log failed");
        }
    }

    public List<LogDto> getLog() {
        List<LogDto> result = logThreadMap.values().stream()
                .map(LogThread::getLogDto)
                .collect(Collectors.toList());

        result.sort(Comparator.comparing(LogDto::getRegTime).reversed());
        return result;
    }

    public LogThread getLog(String name) {
        if (logThreadMap.containsKey(name)) {
            return logThreadMap.get(name);
        }
        return null;
    }

    public ResponseEntity<Result> deleteLog(String name) {
        if (logThreadMap.containsKey(name)) {
            logThreadMap.get(name).interrupt();
            logThreadMap.get(name).getMakerName().forEach(e -> makerService.getMaker(e).ifPresent(o -> o.getValue().decreaseRef()));
            logThreadMap.get(name).getSenderName().forEach(e -> senderService.getSender(e).ifPresent(s -> s.getValue().decreaseRef()));
            logThreadMap.remove(name);
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted sender");
        }
        else {
            return Result.createResultSet(Result.Type.ERROR, "Log does not exist");
        }
    }

    public ResponseEntity<Result> previewLog(String format) {
        ResponseEntity<Result> result;

        try {
            VelocityEngine ve;
            Template vTemplate;
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

            ve = new VelocityEngine();
            ve.setProperty("parser.pool.size", 1);
            ve.init();

            RuntimeServices rs = RuntimeSingleton.getRuntimeServices();
            StringReader sr = new StringReader(vFormat);
            SimpleNode sn = null;
            try {
                sn = rs.parse(sr, "preview");
            } catch (ParseException e) {
                log.error("log template parsing error. {}", format);
            }

            vTemplate = new Template();
            vTemplate.setRuntimeServices(rs);
            vTemplate.setData(sn);
            vTemplate.initDocument();

            VelocityContext context = new VelocityContext();
            Map<String, Object> templateData = getTemplateData(expressions);

            templateData.keySet().forEach(key -> {
                if (templateData.containsKey(key)) {
                    context.put(key, templateData.get(key));
                }
            });

            StringWriter writer = new StringWriter();
            vTemplate.merge(context, writer);

            result = Result.createResultSet(Result.Type.SUCCESS, writer.toString(), false);
        }
        catch (Exception e) {
            result = Result.createResultSet(Result.Type.ERROR, String.format("Invalid log template (%s)", format), false);
        }

        return result;
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
}
