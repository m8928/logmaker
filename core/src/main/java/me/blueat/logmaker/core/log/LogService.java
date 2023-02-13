package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.Result;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.blueat.logmaker.core.maker.MakerService;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

@Service
@RequiredArgsConstructor
@Slf4j
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
                return Result.createResultSet(Result.Type.SUCCESS, "log create success");
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

    public Result updateLog(LogDto logDto) {
        if (!logThreadMap.containsKey(logDto.getName())) {
            return Result.createResultSet(Result.Type.ERROR, "log not found");
        }
        else {
            boolean result = logThreadMap.get(logDto.getName()).updateLogDto(logDto);

            if (result) {
                return Result.createResultSet(Result.Type.SUCCESS, "log update success");
            }
            else {
                return Result.createResultSet(Result.Type.ERROR, "log update fail");
            }
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

    public Result deleteLog(String name) {
        if (logThreadMap.containsKey(name)) {
            logThreadMap.get(name).interrupt();
            logThreadMap.get(name).getExpressions().forEach(e -> makerService.getMaker(e).ifPresent(o -> o.getValue().decreaseRef()));
            logThreadMap.remove(name);
            return Result.createResultSet(Result.Type.SUCCESS);
        }
        else {
            return Result.createResultSet(Result.Type.ERROR);
        }
    }

    public String previewLog(String format) {
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

            return writer.toString();
        }
        catch (Exception e) {
            log.error("log template parsing error. {}", format);
            return "ERROR!";
        }
    }

    private Map<String, Object> getTemplateData(Set<String> expressions) {
        Map<String, Object> result = new HashMap<>();

        for (String key : expressions) {
            makerService.getMaker().stream().filter(m -> m.getName().equals(key)).findAny()
                    .ifPresent(p -> makerService.getMaker(key).ifPresent(v -> result.put(key, v.getValue().getData())));
        }

        return result;
    }
}
