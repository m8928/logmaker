package me.blueat.logmaker.core.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.plugin.PluginService;
import me.blueat.logmaker.core.sender.SenderService;
import org.springframework.stereotype.Service;

import javax.management.*;
import java.lang.management.ManagementFactory;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final MakerService makerService;
    private final LogService logService;
    private final SenderService senderService;
    private final PluginService pluginService;

    public DashboardDto getDashboard() {
        long eps = logService.getLog().stream().map(LogDto::getEps).reduce(0L, Long::sum);
        long actualEps = logService.getLog().stream().map(LogDto::getCurrentEps).reduce(0L, Long::sum);

        return DashboardDto.builder()
                .maker(makerService.getMaker().size())
                .log(logService.getLog().size())
                .sender(senderService.getSender().size())
                .plugin(pluginService.getPlugin().size())
                .eps(eps)
                .actualEps(actualEps)
                .cpu(getProcessCpuLoad())
                .memory((Runtime.getRuntime().totalMemory()
                        - Runtime.getRuntime().freeMemory())/1024/1024)
                .maxMemory(Runtime.getRuntime().maxMemory() / (1024 * 1024))
                .thread(Thread.activeCount())
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
