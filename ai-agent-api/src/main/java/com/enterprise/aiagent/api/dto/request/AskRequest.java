package com.enterprise.aiagent.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AskRequest {
    @NotBlank(message = "La question est obligatoire")
    @Size(max = 5000)
    private String question;

    private String language;

    @Size(max = 2000)
    private String context;

    private String priority; // LOW | NORMAL | HIGH | CRITICAL
}
