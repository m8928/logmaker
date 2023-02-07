package me.blueat.logmaker.core.loggen;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogDto {
    private String name;
    private String format;
    @Schema(example = "10")
    private long eps;
    private List<String> devices;
    @Schema(example = "devices=")
    private String ipPrefix;
    private List<SyslogDto> syslog;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String sample;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long currentEps;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long count;
}
