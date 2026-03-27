package me.blueat.logmaker.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SenderDto {
    @NotEmpty(message = "Name field value is required")
    @Schema(example = "syslog")
    String name;
    @Schema(example = "Syslog")
    String type;
    //String ip, int port, int facility, int severity, String messageFormat, List<String> hosts, String hostPrefix
    @Schema(example = "{\"ip\":\"127.0.0.1\", \"port\":9898, \"facility\":1, \"severity\":6, \"messageFormat\":\"RFC_3164\", \"hosts\":[\"127.0.0.1\"], \"hostPrefix\":\"\"}")
    @Builder.Default
    Map<String, Object> args = new HashMap<>();

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Integer ref;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long count;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long bytes;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long bytesPerSec;

    @Builder.Default
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    long regTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();
}
