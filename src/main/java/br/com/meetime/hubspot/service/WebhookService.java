package br.com.meetime.hubspot.service;


public interface WebhookService {

 void processWebhookEvents(String requestBody);

}