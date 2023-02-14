package me.blueat.logmaker.core.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.plugin.PluginService;
import me.blueat.logmaker.core.sender.SenderService;
import org.springframework.stereotype.Service;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final MakerService makerService;
    private final LogService logService;
    private final SenderService senderService;
    private final PluginService pluginService;

    public DashboardDto getDashboard() {
        long eps = Arrays.stream(logService.getLog()).map(l -> l.getLogDto().getEps()).reduce(0l, Long::sum);
        long actualEps = Arrays.stream(logService.getLog()).map(l -> l.getLogDto().getCurrentEps()).reduce(0l, Long::sum);

        return DashboardDto.builder()
                .maker(makerService.getMaker().size())
                .log(logService.getLog().length)
                .sender(senderService.getSender().size())
                .plugin(pluginService.getPlugin().size())
                .eps(eps)
                .actualEps(actualEps)
                .cpu(getProcessCpuLoad())
                .memory((Runtime.getRuntime().totalMemory()
                        - Runtime.getRuntime().freeMemory())/1024/1024)
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
