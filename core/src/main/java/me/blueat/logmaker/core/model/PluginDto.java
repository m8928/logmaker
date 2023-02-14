package me.blueat.logmaker.core.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
@Builder
public class PluginDto {
    String name;
    String version;
    String provider;
    String filename;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Integer ref;
}
