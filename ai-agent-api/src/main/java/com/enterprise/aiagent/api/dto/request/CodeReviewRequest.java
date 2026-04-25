package com.enterprise.aiagent.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CodeReviewRequest {

    @NotBlank(message = "Le code source est obligatoire")
    @Size(max = 10000)
    private String code;

    @NotBlank(message = "Le langage est obligatoire")
    private String language;

    @Size(max = 1000)
    private String problemDescription;

    private String analysisType = "CORRECTION"; // CORRECTION | OPTIMISATION | REVIEW | SECURITE
}
