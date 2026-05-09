-- H2-compatible DDL for CodeFreeX tests

CREATE TABLE IF NOT EXISTS app
(
    id            BIGINT                             PRIMARY KEY,
    app_name      VARCHAR(256)                       NULL,
    description   VARCHAR(1024)                      NULL,
    cover         VARCHAR(512)                       NULL,
    init_prompt   TEXT                               NULL,
    code_gen_type VARCHAR(64)                        NULL,
    status        VARCHAR(32)  DEFAULT 'draft'       NOT NULL,
    deploy_key    VARCHAR(64)                        NULL,
    deployed_time TIMESTAMP                          NULL,
    is_public     TINYINT    DEFAULT 0               NOT NULL,
    is_featured   TINYINT    DEFAULT 0               NOT NULL,
    priority      INT        DEFAULT 0               NOT NULL,
    view_count    INT        DEFAULT 0               NOT NULL,
    like_count    INT        DEFAULT 0               NOT NULL,
    tags          TEXT                               NULL,
    user_id       BIGINT                             NOT NULL,
    edit_time     TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_time   TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time   TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_delete     TINYINT    DEFAULT 0               NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_history
(
    id           BIGINT                             PRIMARY KEY,
    message      TEXT                               NOT NULL,
    parent_id    BIGINT                             NULL,
    message_type VARCHAR(32)                        NOT NULL,
    app_id       BIGINT                             NOT NULL,
    user_id      BIGINT                             NOT NULL,
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_delete    TINYINT    DEFAULT 0               NOT NULL
);
