CREATE TABLE oauth_token (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload VARCHAR(16384)
);

INSERT INTO oauth_token (id, payload) VALUES (1, NULL);

CREATE TABLE processed_webhook (
    event_key VARCHAR(64) PRIMARY KEY,
    expires_at BIGINT NOT NULL
);

CREATE INDEX idx_processed_webhook_expiry ON processed_webhook (expires_at);
