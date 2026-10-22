# Meetime Case Técnico: Integração com HubSpot

Esta é uma API REST desenvolvida em Java com Spring Boot para integrar com a API do HubSpot, conforme especificado no case técnico da Meetime.

A aplicação implementa os seguintes fluxos:

1.  **Autenticação OAuth 2.0:** Geração da URL de autorização e processamento do callback para obter tokens de acesso/refresh (Authorization Code Flow).
2.  **Criação de Contatos:** Endpoint para criar contatos no CRM do HubSpot via API, respeitando rate limits.
3.  **Recebimento de Webhooks:** Endpoint para receber e processar notificações de webhook do HubSpot para o evento `contact.creation`, validando a assinatura da requisição.

## Tecnologias Utilizadas

*   **Java 17**
*   **Spring Boot 3.2.x**
    *   Spring WebFlux (servidor e WebClient)
    *   Spring Security
    *   Spring Validation
    *   Spring Actuator
*   **Maven** (Gerenciador de Dependências)
*   **Project Lombok** (Redução de boilerplate)
*   **Resilience4j** (Implementação de Rate Limiter)
*   **WebClient** (Cliente HTTP reativo para chamadas à API HubSpot)

## Pré-requisitos

*   **Java Development Kit (JDK) 17:** [Instruções de Instalação](https://adoptium.net/)
*   **Maven 3.9.9:** fornecido pelo Maven Wrapper; o primeiro uso requer acesso à Internet.
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
    git clone https://github.com/Eltonnjulio/hubspot-integration.git
    cd hubspot-integration
    ```

2. **Configure o ambiente:**

    Copie `.env.example` para `.env` e substitua os valores. O Spring não carrega esse ficheiro automaticamente; exporte as variáveis no terminal antes de iniciar:

    ```bash
    set -a
    source .env
    set +a
    ```

    `HUBSPOT_CLIENT_ID`, `HUBSPOT_CLIENT_SECRET`, `INTEGRATION_USERNAME`, `INTEGRATION_PASSWORD` e `TOKEN_ENCRYPTION_KEY` são obrigatórios. Gere a chave com `openssl rand -base64 32` e coloque o resultado em `TOKEN_ENCRYPTION_KEY`. O redirect URI deve corresponder ao configurado no HubSpot. Use HTTPS em produção. Se o segredo anterior era real, substitua-o no HubSpot: removê-lo do ficheiro não o remove do histórico Git.

## Execução

1.  **Compile e execute a aplicação usando Maven:**
    ```bash
    ./mvnw spring-boot:run
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
    *   **Descrição:** Endpoint chamado pelo HubSpot após o usuário autorizar o aplicativo. Ele recebe o código e o `state`, valida a sessão e troca o código pelos tokens de acesso/refresh. O usuário é redirecionado para cá automaticamente pelo HubSpot. Retorna uma mensagem de sucesso ou erro.

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
          "id": "123456789",
          "properties": {
          },
          "createdAt": "...",
          "updatedAt": "...",
          "archived": false
        }
        ```
    *   **Descrição:** Cria um novo contato no HubSpot. Requer que o fluxo OAuth tenha sido completado com sucesso anteriormente (o token de acesso é armazenado cifrado na base de dados).

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
            "eventId": 4160277826,
            "subscriptionId": 3545374,
            "portalId": 146087035,
            "appId": 11414215,
            "occurredAt": 1745533643583,
            "subscriptionType": "object.creation",
            "attemptNumber": 2,
            "objectId": 269392099525,
            "objectTypeId": "0-1",
            "changeFlag": "CREATED",
            "changeSource": "INTEGRATION",
            "sourceId": "11414215"
          }
        ]
        ```
    *   **Resposta de Sucesso:** `200 OK` (Corpo vazio)
    *   **Resposta de Erro (Assinatura inválida):** `401 Unauthorized`
    *   **Descrição:** Endpoint que recebe notificações do HubSpot quando um contato é criado. Valida a assinatura `X-HubSpot-Signature-V3` antes de processar o evento. A lógica de processamento atual apenas loga o evento recebido.

## Decisões de arquitetura

As decisões de persistência, concorrência, entrega e escalabilidade estão descritas em [docs/architecture.md](docs/architecture.md).

## Segurança e operação

`GET /oauth/authorize`, `/contacts/**`, `/csrf` e os endpoints de gestão, exceto `/actuator/health`, exigem HTTP Basic com as credenciais de integração. O callback OAuth exige a mesma sessão que iniciou a autorização e um `state` de uso único, válido por dez minutos.

Os pedidos que alteram estado exigem CSRF, exceto o webhook autenticado por assinatura. Para criar contactos, obtenha um token e preserve o cookie de sessão:

```bash
curl -u "$INTEGRATION_USERNAME:$INTEGRATION_PASSWORD" -c cookies.txt http://localhost:8080/csrf
curl -u "$INTEGRATION_USERNAME:$INTEGRATION_PASSWORD" -b cookies.txt \
  -H 'X-CSRF-TOKEN: TOKEN_DEVOLVIDO_PELO_ENDPOINT' \
  -H 'Content-Type: application/json' \
  -d '{"properties":{"email":"contact@example.com"}}' http://localhost:8080/contacts
```

O webhook valida a assinatura v3 e rejeita timestamps com mais de cinco minutos ou mais de trinta segundos no futuro. Atrás de proxy, preserve a URI pública usada na assinatura; só aceite cabeçalhos encaminhados de proxies de confiança. O corpo é limitado a 256 KB.

O rate limiter é local à instância, não bloqueia o event loop e permite, por padrão, nove chamadas de criação por segundo. Respostas 429 da HubSpot são repetidas automaticamente até três vezes, tanto na criação de contactos como no OAuth. A espera usa `Retry-After` (segundos ou data HTTP); sem valor válido, aplica backoff de 1, 2 e 4 segundos. O limite por espera é de 60 segundos: uma indicação superior é devolvida ao cliente, sem repetir antes do prazo. Estes limites são configuráveis em `hubspot.retry`. Cada nova tentativa de contacto verifica novamente o token e o rate limiter local. Timeouts, erros de rede, outros códigos HTTP e recusas do limitador local não são repetidos. Ao esgotar as tentativas, preserva-se o 429 e o `Retry-After` num erro sanitizado. Timeouts de conexão e resposta são configuráveis em `http.client`.

Os tokens são persistidos com AES-256-GCM e nonces aleatórios; a chave de 32 bytes vem de `TOKEN_ENCRYPTION_KEY`, nunca da base de dados. Preserve a mesma chave entre reinícios e guarde-a separada dos backups. Perder a chave exige nova autorização. A troca de chave exige uma migração dos dados cifrados ou revogação e nova autorização; alterar apenas a variável impede a leitura dos tokens existentes.

O ambiente local usa H2 em ficheiro (`./data/hubspot`). As migrações Flyway são aplicadas no arranque. Para PostgreSQL, configure `DATABASE_URL=jdbc:postgresql://host:5432/hubspot`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`; em produção, configure TLS e um volume/backup adequados. Os testes de persistência e concorrência são executados também em PostgreSQL na CI.

A deduplicação dos webhooks usa uma chave única persistida, incluindo conta, evento, objeto e data original; `attemptNumber` não faz parte da identidade. A reserva e o handler executam na mesma transação: falhas fazem rollback e permitem nova tentativa. Os registos expiram após sete dias e são removidos periodicamente. O corpo inteiro não é guardado e só é confirmado depois de o handler concluir; não existe fila assíncrona.

A integração suporta uma única conta HubSpot e uma instância da aplicação. Os tokens sobrevivem a reinícios; as sessões OAuth ainda são locais e uma autorização em curso deve ser reiniciada após um restart. Múltiplas réplicas exigem sessões partilhadas, coordenação distribuída do refresh e rate limiter partilhado. Chamadas JDBC são executadas fora do event loop, em `boundedElastic`.

O handler `ContactCreationHandler` regista a receção. Operações externas futuras precisam de idempotência própria ou outbox: a transação local não desfaz efeitos num serviço externo nem garante processamento exatamente uma vez.

Cada resposta inclui `X-Request-ID`, também registado nos logs. O Actuator disponibiliza métricas HTTP, JVM e do cliente HTTP em `/actuator/metrics`, mediante autenticação.

## Validação

```bash
./mvnw --batch-mode verify
```

Execute com JDK 17. A integração contínua executa o mesmo comando. Os testes usam o mock maker por subclasses, sem exigir que o Mockito anexe um agente à JVM.

Para executar os testes de armazenamento contra um PostgreSQL de testes, forneça `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` e `TEST_DATABASE_PASSWORD` e execute `./mvnw -Dtest=DurableStorageTest test`. Cada teste cria o seu próprio schema; use uma base descartável, com permissão para criar schemas. As decisões e limitações estão em `docs/architecture.md`.

## Critérios do desafio e evidências

| Critério | Comportamento | Testes |
| --- | --- | --- |
| OAuth `state` | Vinculado à sessão, expira em dez minutos e é consumido uma vez | `OAuthControllerTest`, `ApiSecurityTest` |
| Webhook v3 | HMAC com método, URI, corpo e timestamp; rejeição de pedidos antigos | `WebhookSignatureVerifierTest`, `ApiSecurityTest` |
| Rate limit | Retry reativo limitado de 429 com `Retry-After`, sem espera bloqueante | `HubSpotRetryTest`, `ContactServiceImplTest`, `HubSpotOAuthServiceImplTest` |
| Expiração do token | Renovação automática antes do pedido, preservando refresh token e partilhando a renovação concorrente | `OAuthHttpContractTest`, `TokenStorageServiceImplTest` |

A renovação acontece quando um pedido precisa de um token: considera a margem de expiração e obtém um novo access token sem intervenção do utilizador. Não é necessário renovar tokens enquanto a integração está inativa. Se o refresh token for revogado, será necessária nova autorização.

Referências: [OAuth na HubSpot](https://developers.hubspot.com/docs/guides/apps/authentication/working-with-oauth), [validação de pedidos](https://developers.hubspot.com/docs/guides/apps/authentication/validating-requests), [gestão de tokens e rate limits](https://developers.hubspot.com/blog/oauth-token-management-hubspot-integrations).
