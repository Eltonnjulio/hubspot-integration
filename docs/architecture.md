# Decisões de arquitetura

## Escopo

Integração de uma conta HubSpot por instalação. A autenticação Basic protege o acesso administrativo e a criação de contactos; exige TLS em produção. OAuth liga essa instalação à conta HubSpot. Webhooks autenticam-se por assinatura v3 e timestamp.

## Persistência

JDBC mantém credenciais e deduplicação em tabelas pequenas com migrações Flyway. H2 em ficheiro simplifica o desenvolvimento; PostgreSQL permite backups e operação num servidor dedicado. SQL e concorrência são testados nos dois motores. Não há sincronização entre uma cache de tokens e a base: a base é a fonte de verdade. JDBC executa fora do event loop do WebFlux.

Tokens usam AES-256-GCM, nonce aleatório de 96 bits e dados autenticados que identificam o formato. A chave é externa à base; o formato tem versão e falha se a autenticação do conteúdo não passar. Backups precisam da chave correspondente. Rotação de chave requer migração explícita ou revogação dos tokens.

## Entrega de webhooks

Uma restrição única resolve entregas concorrentes. Reserva e execução do handler pertencem à mesma transação: só há confirmação HTTP após commit. Exceções fazem rollback e permitem reenvio. Todo o lote é validado antes de iniciar o processamento e cada evento suportado tem a sua transação; se um evento posterior falhar, o reenvio ignora os anteriores já concluídos.

A identidade inclui conta, aplicação, subscrição, evento, tipo, objeto e instante original. O número da tentativa não altera a identidade. A retenção é de sete dias. Não existe garantia de deduplicação depois dessa janela.

O handler atual regista a receção. Uma operação externa não participa da transação JDBC: precisa de uma chave de idempotência no destino ou de uma outbox e consumidor próprio. Não se promete processamento exatamente uma vez.

## Concorrência e falhas

O refresh partilha a chamada em curso dentro de uma instância, preserva refresh tokens rodados e permite nova tentativa após falha. Tokens expirados não apagam o refresh token. O rate limiter é não bloqueante, aplicado por subscrição e local ao processo. Criação de contactos não tem retry automático, pois um timeout pode acontecer após a criação no destino. Erros 429 preservam Retry-After quando válido.

## Limites operacionais

Uma instância e uma conta por instalação. Antes de escalar horizontalmente, introduzir sessões partilhadas, coordenação de refresh e rate limit distribuído. Antes de suportar múltiplas contas, associar tokens, permissões e subscrições a uma identidade de conta e validar a pertença dos eventos.

Alterações de dependências e de plataforma continuam a exigir revisão e execução da CI; os testes não equivalem a uma auditoria de segurança. Os artefactos gerados em target não pertencem ao código-fonte.
