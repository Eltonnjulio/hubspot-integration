package br.com.meetime.hubspot.domain.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactResponse {
    private String id;
    private Map<String, String> properties;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean archived;
}