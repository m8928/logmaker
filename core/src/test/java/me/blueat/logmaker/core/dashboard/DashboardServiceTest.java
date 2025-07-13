package me.blueat.logmaker.core.dashboard;

import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.plugin.PluginService;
import me.blueat.logmaker.core.sender.SenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @Mock
    private MakerService makerService;

    @Mock
    private LogService logService;

    @Mock
    private SenderService senderService;

    @Mock
    private PluginService pluginService;

    @Test
    void getDashboard() {
        // Given
        when(makerService.getMaker()).thenReturn(Collections.emptyList());
        when(logService.getLog()).thenReturn(Collections.singletonList(new LogDto()));
        when(senderService.getSender()).thenReturn(Collections.emptyList());
        when(pluginService.getPlugin()).thenReturn(Collections.emptyList());

        // When
        DashboardDto dashboardDto = dashboardService.getDashboard();

        // Then
        assertEquals(0, dashboardDto.getMaker());
        assertEquals(1, dashboardDto.getLog());
        assertEquals(0, dashboardDto.getSender());
        assertEquals(0, dashboardDto.getPlugin());
    }
}