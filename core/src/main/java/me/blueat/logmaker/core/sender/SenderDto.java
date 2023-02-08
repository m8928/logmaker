package me.blueat.logmaker.core.sender;

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
public class SenderDto {
    @Schema(example = "syslog")
    String name;
    @Schema(example = "Syslog")
    String type;
    //String ip, int port, int facility, int severity, String messageFormat, List<String> hosts, String hostPrefix
    @Schema(example = "{\"ip\":\"127.0.0.1\", \"port\":9898, \"facility\":1, \"severity\":6, \"messageFormat\":\"RFC_3164\", \"hosts\":[\"127.0.0.1\"], \"hostPrefix\":\"\"}")
    Map<String, Object> args;
}
