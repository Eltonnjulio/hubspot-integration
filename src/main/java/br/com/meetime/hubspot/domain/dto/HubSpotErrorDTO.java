package br.com.meetime.hubspot.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HubSpotErrorDTO {

    private String message;
    private String category;
    private String subCategory;
    private String context;
    private String link;
    private String status;
    private String correlationId;
}
