package br.com.meetime.hubspot;

import br.com.meetime.hubspot.config.HubSpotConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(HubSpotConfig.class)
public class HubSpotIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubSpotIntegrationApplication.class, args);
    }

}