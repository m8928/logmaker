package me.blueat.logmaker.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.common.value.qual.MinLen;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MakerDto {
    @NotEmpty
    @Schema(example = "sample_maker")
    String name;
    @Schema(example = "IP")
    String type;
    @Schema(example = "{}")
    Map<String, Object> args = new HashMap<>();

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Object sample;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long size;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Integer ref;
}
