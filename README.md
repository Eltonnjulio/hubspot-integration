# Meetime Case Técnico: Integração com HubSpot

Esta é uma API REST desenvolvida em Java com Spring Boot para integrar com a API do HubSpot, conforme especificado no case técnico da Meetime.

A aplicação implementa os seguintes fluxos:

1.  **Autenticação OAuth 2.0:** Geração da URL de autorização e processamento do callback para obter tokens de acesso/refresh (Authorization Code Flow).
2.  **Criação de Contatos:** Endpoint para criar contatos no CRM do HubSpot via API, respeitando rate limits.
3.  **Recebimento de Webhooks:** Endpoint para receber e processar notificações de webhook do HubSpot para o evento `contact.creation`, validando a assinatura da requisição.

## Tecnologias Utilizadas

*   **Java 17**
*   **Spring Boot 3.2.x**
    *   Spring Web (MVC)
    *   Spring WebFlux (para WebClient)
    *   Spring Security
    *   Spring Validation
    *   Spring Actuator
*   **Maven** (Gerenciador de Dependências)
*   **Project Lombok** (Redução de boilerplate)
*   **Resilience4j** (Implementação de Rate Limiter)
*   **Apache Commons Codec** (Para HmacSHA256 na validação do webhook)
*   **WebClient** (Cliente HTTP reativo para chamadas à API HubSpot)

## Pré-requisitos

*   **Java Development Kit (JDK) 17 ou superior:** [Instruções de Instalação](https://adoptium.net/)
*   **Apache Maven 3.6 ou superior:** [Instruções de Instalação](https://maven.apache.org/install.html)
*   **Conta de Desenvolvedor HubSpot:** [Criar conta](https://developers.hubspot.com/signup)
*   **Aplicativo HubSpot Criado:**
    *   Vá para sua conta de desenvolvedor HubSpot > Apps.
    *   Crie um novo aplicativo privado ou público. Para OAuth, um público é mais comum.
    *   **Auth Settings:**
        *   Anote o **Client ID** e o **Client Secret**.
        *   Configure os **Scopes** necessários. No mínimo, para este projeto: `crm.objects.contacts.write`, `crm.objects.contacts.read`, `oauth`.
        *   Adicione o **Redirect URI**: `http://localhost:8080/oauth/callback` (ou o endereço onde sua aplicação estará rodando + `/oauth/callback`).
    *   **Webhooks:**
        *   Vá para a seção de Webhooks do seu aplicativo.
        *   Crie uma nova **Subscription**.
        *   **Target URL:** `http://<SEU_IP_OU_DOMINIO_PUBLICO>:8080/webhooks/contacts` (Use uma ferramenta como [ngrok](https://ngrok.com/) para expor seu `localhost` publicamente durante o desenvolvimento).
        *   **Event Type:** Selecione `Contact Creation`.
        *   Anote o **Client Secret** do seu aplicativo (usado para validar a assinatura v3 do webhook).

## Configuração

1.  **Clone o repositório:**
    ```bash
    git clone <URL_DO_SEU_REPOSITORIO>
    cd hubspot-integration
    ```

2.  **Configure as credenciais do HubSpot:**
    *   Renomeie (ou copie) o arquivo `src/main/resources/application.yml.example` para `src/main/resources/application.yml`.
    *   Abra o arquivo `src/main/resources/application.yml` e preencha os seguintes valores com as informações do seu aplicativo HubSpot:
        *   `hubspot.oauth.client-id`
        *   `hubspot.oauth.client-secret`
        *   `hubspot.oauth.redirect-uri` (Deve ser EXATAMENTE igual ao configurado no HubSpot)
        *   `hubspot.oauth.scopes` (Verifique se os escopos correspondem aos configurados no HubSpot)
        *   `hubspot.webhook.client-secret` (Normalmente o mesmo `client-secret` do OAuth para assinatura v3)

    **IMPORTANTE:** Nunca comite o arquivo `application.yml` com suas credenciais reais em um repositório público. Use variáveis de ambiente ou um gerenciador de segredos em produção.

## Execução

1.  **Compile e execute a aplicação usando Maven:**
    ```bash
    mvn spring-boot:run
    ```
    A aplicação estará disponível em `http://localhost:8080`.

2.  **(Opcional) Execute usando Ngrok (para Webhooks):**
    *   Se você precisa testar o recebimento de webhooks do HubSpot na sua máquina local, use o `ngrok` para criar um túnel público:
        ```bash
        ngrok http 8080
        ```
    *   O ngrok fornecerá uma URL pública (ex: `https://abcdef12345.ngrok.io`).
    *   Use essa URL pública + `/webhooks/contacts` (ex: `https://abcdef12345.ngrok.io/webhooks/contacts`) como a **Target URL** na configuração do webhook no HubSpot.

## Endpoints da API

1.  **Iniciar Fluxo OAuth:**
    *   **Método:** `GET`
    *   **Path:** `/oauth/authorize`
    *   **Descrição:** Redireciona o navegador do usuário para a página de autorização do HubSpot. Acesse esta URL no seu navegador para iniciar o processo.

2.  **Callback OAuth:**
    *   **Método:** `GET`
    *   **Path:** `/oauth/callback`
    *   **Descrição:** Endpoint chamado pelo HubSpot após o usuário autorizar o aplicativo. Ele recebe o código de autorização e o troca pelos tokens de acesso/refresh. O usuário é redirecionado para cá automaticamente pelo HubSpot. Retorna uma mensagem de sucesso ou erro.

3.  **Criar Contato:**
    *   **Método:** `POST`
    *   **Path:** `/contacts`
    *   **Headers:** `Content-Type: application/json`
    *   **Corpo da Requisição (Exemplo):**
        ```json
        {
          "properties": {
            "email": "novo.contato.teste@example.com",
            "firstname": "Novo",
            "lastname": "Contato",
            "phone": "11999998888",
            "company": "Empresa Teste"
          }
        }
        ```
    *   **Resposta de Sucesso (201 Created):**
        ```json
        {
          "id": "123456789", // ID do contato no HubSpot
          "properties": {
            // ... propriedades retornadas pelo HubSpot ...
          },
          "createdAt": "...",
          "updatedAt": "...",
          "archived": false
        }
        ```
    *   **Descrição:** Cria um novo contato no HubSpot. Requer que o fluxo OAuth tenha sido completado com sucesso anteriormente (o token de acesso é armazenado em memória).

4.  **Receber Webhook de Criação de Contato:**
    *   **Método:** `POST`
    *   **Path:** `/webhooks/contacts`
    *   **Headers (Esperados do HubSpot):**
        *   `X-HubSpot-Signature-V3: <assinatura>`
        *   `Content-Type: application/json`
    *   **Corpo da Requisição (Exemplo vindo do HubSpot):**
        ```json
        [
          {
            "eventId": 1,
            "subscriptionId": 12345,
            "portalId": 98765,
            "appId": 112233,
            "occurredAt": 1678886400000,
            "subscriptionType": "contact.creation",
            "attemptNumber": 0,
            "objectId": 555111, // ID do contato criado
            "changeSource": "CRM",
            "changeFlag": "CREATED"
          }
        ]
        ```
    *   **Resposta de Sucesso:** `200 OK` (Corpo vazio)
    *   **Resposta de Erro (Assinatura inválida):** `401 Unauthorized`
    *   **Descrição:** Endpoint que recebe notificações do HubSpot quando um contato é criado. Valida a assinatura `X-HubSpot-Signature-V3` antes de processar o evento. A lógica de processamento atual apenas loga o evento recebido.

## Decisões Técnicas e Melhorias Futuras

*   **Framework:** Spring Boot foi escolhido por sua robustez, ecossistema maduro e facilidade para criar APIs REST e gerenciar dependências.
*   **Cliente HTTP:** WebClient (Spring WebFlux) foi usado para chamadas assíncronas e não bloqueantes à API do HubSpot, melhorando a performance e resiliência da aplicação.
*   **Armazenamento de Token:** Atualmente, os tokens OAuth são armazenados **em memória** (`TokenStorageService`). Isso é **inadequado para produção** pois os tokens se perdem ao reiniciar a aplicação e não suporta múltiplos usuários/instâncias.
    *   **Melhoria:** Implementar persistência segura dos tokens em um banco de dados (SQL ou NoSQL) ou um cofre de segredos (como HashiCorp Vault, AWS Secrets Manager, etc.).
*   **Refresh Token:** A lógica para usar o *refresh token* para obter novos *access tokens* quando o atual expirar está implementada (`HubSpotOAuthService.refreshToken` e `getValidAccessToken`), tornando a integração mais duradoura sem exigir reautenticação constante do usuário.
*   **Rate Limiting:** Resilience4j foi utilizado para implementar um Rate Limiter (`@RateLimiter(name = "hubspotApi")`) no `ContactService`, garantindo que a aplicação respeite os limites de taxa da API HubSpot e evitando erros `429 Too Many Requests`. A configuração está em `application.yml`.
*   **Validação de Webhook:** A validação da assinatura v3 (`X-HubSpot-Signature-V3`) foi implementada usando HmacSHA256 conforme a documentação do HubSpot, garantindo que apenas requisições legítimas sejam processadas.
*   **Segurança:** Spring Security foi adicionado para configuração básica, principalmente para desabilitar CSRF no endpoint do webhook. Uma configuração mais granular pode ser necessária em produção. Credenciais **NÃO** devem ser commitadas.
*   **Tratamento de Erros:** Um `GlobalExceptionHandler` foi implementado para capturar exceções (erros da API HubSpot, validação, rate limit, autenticação de webhook) e retornar respostas de erro padronizadas em formato JSON.
*   **Testes:** O projeto inclui as dependências de teste, mas os testes unitários e de integração **não foram implementados** como parte deste código inicial.
    *   **Melhoria:** Adicionar testes unitários (Mockito) para serviços e controladores, e testes de integração (Spring Boot Test, Testcontainers) para validar os fluxos completos, incluindo a interação com a API (usando mocks ou um ambiente de teste).
*   **Processamento de Webhook:** A lógica atual no `WebhookService` apenas loga o evento.
    *   **Melhoria:** Implementar a lógica de negócio real necessária ao receber um evento de criação de contato. Considerar o uso de filas de mensagens (RabbitMQ, Kafka, SQS) para processamento assíncrono e desacoplado dos webhooks, aumentando a resiliência.
*   **Escalabilidade:** A solução atual com armazenamento em memória não escala horizontalmente. A persistência de tokens e, potencialmente, o uso de sessões distribuídas ou estratégias stateless seriam necessários para múltiplas instâncias.

```markdown

```

Com isso, temos a estrutura completa do projeto, todas as classes Java necessárias para os 4 endpoints, configurações, DTOs, tratamento de erros, validação de webhook e um `README.md` detalhado. Lembre-se de configurar corretamente o `application.yml` com suas credenciais e ajustar a URL do webhook no HubSpot (usando `ngrok` ou similar para testes locais).