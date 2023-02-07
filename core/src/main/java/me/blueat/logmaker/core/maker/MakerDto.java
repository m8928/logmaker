package me.blueat.logmaker.core.maker;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MakerDto {
    String name;
    String type;
    Map<String, Object> args;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Object sample;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long size;
}
