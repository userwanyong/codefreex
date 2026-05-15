-- =============================================
-- CodeFreeX 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS codefreex DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE codefreex;

-- =============================================
-- 1. 用户关联表
-- =============================================
CREATE TABLE IF NOT EXISTS user_info
(
    id                BIGINT                             COMMENT 'id' PRIMARY KEY,
    user_id           BIGINT                             NOT NULL COMMENT '用户id（关联认证服务用户）',
    inviter_id        BIGINT                             NULL COMMENT '邀请人用户id',
    nickname          VARCHAR(128)                        NULL COMMENT '用户昵称（冗余自认证服务）',
    avatar            VARCHAR(512)                        NULL COMMENT '用户头像URL（冗余自认证服务）',
    total_credits     INT          DEFAULT 0             NOT NULL COMMENT '累计获得额度',
    remaining_credits INT          DEFAULT 0             NOT NULL COMMENT '剩余额度',
    status            VARCHAR(32)  DEFAULT 'active'      NOT NULL COMMENT '用户状态（active-正常/disabled-已禁用）',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_userId (user_id),
    INDEX idx_inviterId (inviter_id),
    INDEX idx_status (status)
) COMMENT '用户关联表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 2. 邀请表
-- =============================================
CREATE TABLE IF NOT EXISTS invite
(
    id            BIGINT                             COMMENT 'id' PRIMARY KEY,
    invite_code   VARCHAR(64)                        NOT NULL COMMENT '邀请码',
    user_id       BIGINT                             NOT NULL COMMENT '生成者用户id',
    batch         VARCHAR(128)                       NULL COMMENT '批次号（方便批量管理）',
    status        VARCHAR(32)  DEFAULT 'unused'      NOT NULL COMMENT '状态（unused/partial/used/expired/disabled）',
    expire_time   DATETIME                           NULL COMMENT '过期时间（为空表示永不过期）',
    max_use_count INT      DEFAULT 1                 NOT NULL COMMENT '最大使用次数',
    used_count    INT      DEFAULT 0                 NOT NULL COMMENT '已使用次数',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete     TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_inviteCode (invite_code),
    INDEX idx_userId (user_id),
    INDEX idx_batch (batch),
    INDEX idx_status (status)
) COMMENT '邀请表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 3. 邀请-用户关联表
-- =============================================
CREATE TABLE IF NOT EXISTS invite_user
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    invite_id   BIGINT                             NOT NULL COMMENT '邀请码id',
    inviter_id  BIGINT                             NOT NULL COMMENT '邀请人用户id',
    invitee_id  BIGINT                             NOT NULL COMMENT '受邀用户id',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '使用时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete   TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_inviteId (invite_id),
    INDEX idx_inviterId (inviter_id),
    INDEX idx_inviteeId (invitee_id)
) COMMENT '邀请-用户关联表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 4. 兑换表
-- =============================================
CREATE TABLE IF NOT EXISTS redeem
(
    id            BIGINT                             COMMENT 'id' PRIMARY KEY,
    redeem_code   VARCHAR(64)                        NOT NULL COMMENT '兑换码',
    user_id       BIGINT                             NOT NULL COMMENT '生成者用户id（管理员）',
    batch         VARCHAR(128)                       NULL COMMENT '批次号',
    quota         INT                                NOT NULL COMMENT '兑换码点数',
    status        VARCHAR(32)  DEFAULT 'unused'      NOT NULL COMMENT '状态（unused/partial/used/expired/disabled）',
    expire_time   DATETIME                           NULL COMMENT '过期时间（为空表示永不过期）',
    max_use_count INT      DEFAULT 1                 NOT NULL COMMENT '最大使用次数',
    used_count    INT      DEFAULT 0                 NOT NULL COMMENT '已使用次数',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete     TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_redeemCode (redeem_code),
    INDEX idx_userId (user_id),
    INDEX idx_batch (batch),
    INDEX idx_status (status)
) COMMENT '兑换表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 5. 兑换-用户关联表
-- =============================================
CREATE TABLE IF NOT EXISTS redeem_user
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    redeem_id   BIGINT                             NOT NULL COMMENT '兑换码id',
    creator_id  BIGINT                             NOT NULL COMMENT '生成者用户id（管理员）',
    user_id     BIGINT                             NOT NULL COMMENT '使用用户id',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '使用时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete   TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_redeemId (redeem_id),
    INDEX idx_creatorId (creator_id),
    INDEX idx_userId (user_id),
    UNIQUE KEY uk_redeem_user (redeem_id, user_id)
) COMMENT '兑换-用户关联表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 6. 应用表
-- =============================================
CREATE TABLE IF NOT EXISTS app
(
    id            BIGINT                             COMMENT 'id' PRIMARY KEY,
    app_name      VARCHAR(256)                       NULL COMMENT '应用名称',
    description   VARCHAR(1024)                      NULL COMMENT '应用描述',
    cover         VARCHAR(512)                       NULL COMMENT '应用封面',
    init_prompt   TEXT                               NULL COMMENT '应用初始化的 prompt',
    code_gen_type VARCHAR(64)                        NULL COMMENT '代码生成类型（枚举）',
    status        VARCHAR(32)  DEFAULT 'draft'       NOT NULL COMMENT '应用状态（draft/generating/generated/deployed/disabled）',
    deploy_key    VARCHAR(64)                        NULL COMMENT '部署标识',
    deployed_time DATETIME                           NULL COMMENT '部署时间',
    is_public     TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否公开（0-未公开 1-公开）',
    is_featured   TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否精选（0-否 1-是）',
    priority      INT       DEFAULT 0                 NOT NULL COMMENT '优先级（越高越优先展示）',
    view_count    INT       DEFAULT 0                 NOT NULL COMMENT '浏览次数',
    like_count    INT       DEFAULT 0                 NOT NULL COMMENT '点赞数',
    user_id       BIGINT                             NOT NULL COMMENT '创建用户id',
    edit_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete     TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_deployKey (deploy_key),
    INDEX idx_appName (app_name),
    INDEX idx_userId (user_id),
    INDEX idx_status (status),
    INDEX idx_isFeatured_priority (is_featured, priority DESC)
) COMMENT '应用' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 7. 对话历史表
-- =============================================
CREATE TABLE IF NOT EXISTS chat_history
(
    id           BIGINT                             COMMENT 'id' PRIMARY KEY,
    message      MEDIUMTEXT                         NOT NULL COMMENT '消息',
    parent_id    BIGINT                             NULL COMMENT '父消息id（上下文关联）',
    message_type VARCHAR(32)                        NOT NULL COMMENT '消息类型（user/ai）',
    app_id       BIGINT                             NOT NULL COMMENT '应用id',
    user_id      BIGINT                             NOT NULL COMMENT '创建用户id',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间' ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_appId (app_id),
    INDEX idx_createTime (create_time),
    INDEX idx_appId_createTime (app_id, create_time)
) COMMENT '对话历史' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 8. 用户用量统计表
-- =============================================
CREATE TABLE IF NOT EXISTS user_usage
(
    id            BIGINT                             COMMENT 'id' PRIMARY KEY,
    user_id       BIGINT                             NOT NULL COMMENT '用户id',
    app_id        BIGINT                             NOT NULL COMMENT '应用id',
    model_id      VARCHAR(128)                       NULL COMMENT '使用的模型标识',
    input_tokens  INT       DEFAULT 0                 NOT NULL COMMENT '输入token数',
    output_tokens INT       DEFAULT 0                 NOT NULL COMMENT '输出token数',
    total_tokens  INT       DEFAULT 0                 NOT NULL COMMENT '总token数',
    latency       INT       DEFAULT 0                 NOT NULL COMMENT '响应延迟（毫秒）',
    status        VARCHAR(32)                        NOT NULL COMMENT '调用状态（success/fail）',
    error_info    TEXT                               NULL COMMENT '失败时的错误信息',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_userId (user_id),
    INDEX idx_appId (app_id),
    INDEX idx_modelId (model_id),
    INDEX idx_userId_createTime (user_id, create_time)
) COMMENT '用户用量统计' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 9. 标签表
-- =============================================
CREATE TABLE IF NOT EXISTS `tag`
(
    id          BIGINT                              COMMENT 'id' PRIMARY KEY,
    name        VARCHAR(64)                         NOT NULL COMMENT '标签名称',
    sort_order  INT         DEFAULT 0               NOT NULL COMMENT '排序（越小越靠前）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete   TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_name (name, is_delete)
) COMMENT '预设标签' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 10. 应用-标签关联表
-- =============================================
CREATE TABLE IF NOT EXISTS app_tag
(
    id          BIGINT                             COMMENT 'id' PRIMARY KEY,
    app_id      BIGINT                             NOT NULL COMMENT '应用id',
    tag_id      BIGINT                             NOT NULL COMMENT '标签id',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    is_delete   TINYINT   DEFAULT 0                NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_app_tag (app_id, tag_id, is_delete),
    INDEX idx_tag_id (tag_id)
) COMMENT '应用-标签关联' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 11. 额度流水表
-- =============================================
CREATE TABLE IF NOT EXISTS credit_transaction
(
    id            BIGINT                              COMMENT 'id' PRIMARY KEY,
    user_id       BIGINT                              NOT NULL COMMENT '用户id',
    type          VARCHAR(32)                         NOT NULL COMMENT '流水类型（recharge-充值/consume-消费/admin_adjust-管理员调整/gift-系统赠送）',
    amount        INT                                 NOT NULL COMMENT '变动数量（正数增加，负数减少）',
    balance_after INT                                  NOT NULL COMMENT '变动后余额',
    source_type   VARCHAR(64)                         NULL COMMENT '来源类型（redeem-兑换码/ai_chat-AI对话/admin-管理员操作/register_gift-注册赠送）',
    source_id     BIGINT                              NULL COMMENT '来源关联id（兑换码id/应用id等）',
    description   VARCHAR(512)                        NULL COMMENT '描述',
    operator_id   BIGINT                              NULL COMMENT '操作者id（管理员调整时记录）',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    INDEX idx_userId (user_id),
    INDEX idx_type (type),
    INDEX idx_sourceType (source_type),
    INDEX idx_userId_createTime (user_id, create_time)
) COMMENT '额度流水表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 12. 应用点赞记录表
-- =============================================
CREATE TABLE IF NOT EXISTS app_like
(
    id          BIGINT                              COMMENT 'id' PRIMARY KEY,
    app_id      BIGINT                              NOT NULL COMMENT '应用id',
    user_id     BIGINT                              NOT NULL COMMENT '用户id',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_app_user (app_id, user_id),
    INDEX idx_userId (user_id)
) COMMENT '应用点赞记录' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 13. 精选申请表
-- =============================================
CREATE TABLE IF NOT EXISTS featured_application
(
    id            BIGINT                              COMMENT 'id' PRIMARY KEY,
    app_id        BIGINT                              NOT NULL COMMENT '应用id',
    user_id       BIGINT                              NOT NULL COMMENT '申请人用户id',
    status        VARCHAR(32)  DEFAULT 'pending'      NOT NULL COMMENT '状态（pending-待审核/approved-已通过/rejected-已拒绝）',
    reason        VARCHAR(512)                        NULL COMMENT '申请理由',
    admin_remark  VARCHAR(512)                        NULL COMMENT '管理员备注',
    reviewer_id   BIGINT                              NULL COMMENT '审核人id',
    review_time   DATETIME                            NULL COMMENT '审核时间',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete     TINYINT   DEFAULT 0                  NOT NULL COMMENT '是否删除',
    INDEX idx_appId (app_id),
    INDEX idx_userId (user_id),
    INDEX idx_status (status)
) COMMENT '精选申请表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 14. 通知表
-- =============================================
CREATE TABLE IF NOT EXISTS notification
(
    id            BIGINT                              COMMENT 'id' PRIMARY KEY,
    user_id       BIGINT                              NOT NULL COMMENT '接收通知的用户id',
    title         VARCHAR(128)                        NOT NULL COMMENT '通知标题',
    content       VARCHAR(512)                        NULL COMMENT '通知内容',
    type          VARCHAR(32)                         NOT NULL COMMENT '通知类型（featured_review-精选审核结果）',
    is_read       TINYINT   DEFAULT 0                 NOT NULL COMMENT '是否已读（0-未读 1-已读）',
    related_id    BIGINT                              NULL COMMENT '关联id（精选申请id等）',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete     TINYINT   DEFAULT 0                  NOT NULL COMMENT '是否删除',
    INDEX idx_userId (user_id),
    INDEX idx_userId_isRead (user_id, is_read),
    INDEX idx_type (type)
) COMMENT '通知表' COLLATE = utf8mb4_unicode_ci;
