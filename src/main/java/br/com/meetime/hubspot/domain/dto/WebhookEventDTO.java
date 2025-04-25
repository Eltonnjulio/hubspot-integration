package br.com.meetime.hubspot.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEventDTO {

    private Long eventId;
    private Long subscriptionId;
    private Long portalId;
    private Long appId;
    private Long occurredAt;

    private String subscriptionType;
    private Integer attemptNumber;

    private Long objectId;
    private String objectTypeId;

    private String changeFlag;
    private String changeSource;
    private String sourceId;
}
