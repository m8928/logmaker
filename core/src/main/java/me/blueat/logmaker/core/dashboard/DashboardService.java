package me.blueat.logmaker.core.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.plugin.PluginService;
import me.blueat.logmaker.core.scenario.ScenarioService;
import me.blueat.logmaker.core.sender.SenderService;
import org.springframework.stereotype.Service;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final MakerService makerService;
    private final LogService logService;
    private final SenderService senderService;
    private final PluginService pluginService;
    private final ScenarioService scenarioService;

    public DashboardDto getDashboard() {
        List<LogDto> logs = logService.getLog();
        long eps = logs.stream().filter(l -> !"bytes".equals(l.getEpsUnit())).map(LogDto::getEps).reduce(0L, Long::sum);
        long actualEps = logs.stream().map(LogDto::getCurrentEps).reduce(0L, Long::sum);
        long bps = logs.stream().filter(l -> "bytes".equals(l.getEpsUnit())).map(LogDto::getEps).reduce(0L, Long::sum);
        long actualBps = logs.stream().map(LogDto::getBytesPerSec).reduce(0L, Long::sum);

        return DashboardDto.builder()
                .maker(makerService.getMaker().size())
                .log(logs.size())
                .sender(senderService.getSender().size())
                .plugin(pluginService.getPlugin().size())
                .eps(eps)
                .actualEps(actualEps)
                .bps(bps)
                .actualBps(actualBps)
                .cpu(getProcessCpuLoad())
                .memory((Runtime.getRuntime().totalMemory()
                        - Runtime.getRuntime().freeMemory())/1024/1024)
                .maxMemory(Runtime.getRuntime().maxMemory() / (1024 * 1024))
                .thread(Thread.activeCount())
                .scenario(scenarioService.getScenarios().size())
                .build();
    }

    public double getProcessCpuLoad() {
        try {
            MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
            ObjectName name    = ObjectName.getInstance("java.lang:type=OperatingSystem");
            AttributeList list = mbs.getAttributes(name, new String[]{ "ProcessCpuLoad" });

            if (list.isEmpty())     return Double.NaN;

            Attribute att = (Attribute)list.get(0);
            Double value  = (Double)att.getValue();

            if (value == -1.0)      return Double.NaN;
            return ((int)(value * 1000) / 10.0);
        }
        catch (InstanceNotFoundException | ReflectionException | MalformedObjectNameException ioe) {
            return -1;
        }
    }
}
