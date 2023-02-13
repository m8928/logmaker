package me.blueat.logmaker.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogDto {
    @NotEmpty
    private String name;
    @NotEmpty
    private String format;
    @Schema(example = "10")
    private long eps;
    private List<String> sender = new ArrayList<>();

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String sample;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long currentEps;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long count;
}
