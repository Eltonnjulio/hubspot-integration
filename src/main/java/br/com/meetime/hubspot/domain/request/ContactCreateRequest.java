package br.com.meetime.hubspot.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactCreateRequest {
    @NotNull(message = "Contact properties are required")
    @Valid
    private ContactPropertiesRequest properties;
}