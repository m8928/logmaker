package me.blueat.logmaker.core.loggen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SyslogDto {
    @Schema(example = "127.0.0.1")
    private String ip;
    @Schema(example = "21")
    private int port;
    @Schema(example = "1")
    private int facility = 1;
    @Schema(example = "6")
    private int severity = 6;
    @Schema(example = "RFC_3164")
    private String messageFormat = "RFC_3164";
}
