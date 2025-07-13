package me.blueat.logmaker.core.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDto {
    String name;
    String version;
    String provider;
    String filename;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Integer ref;
}
