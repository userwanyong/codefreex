-- =============================================
-- CodeFreeX 增量脚本 1：系统配置 / 公告 / 部署计费
-- =============================================
-- 说明：
-- 1. 本脚本为增量变更，请在已执行 init.sql 的库上执行一次。
-- 2. system_config 的初始值（含 yml 中的密钥）由应用启动时自动播种，
--    无需在 SQL 中插入种子数据。
-- 3. app 表新增 deploy_billed_time 列用于部署按周期计费；
--    该列为 MySQL 不支持 IF NOT EXISTS 的 DDL，重复执行会报列已存在，属预期。
-- =============================================

USE codefreex;

-- =============================================
-- 1. 系统配置表（AI 服务商、码点计费等可热更新的运行时配置）
-- =============================================
CREATE TABLE IF NOT EXISTS system_config
(
    id           BIGINT                               COMMENT 'id' PRIMARY KEY,
    config_key   VARCHAR(128)                         NOT NULL COMMENT '配置键（代码内枚举定义）',
    config_value VARCHAR(2048)                        NULL COMMENT '配置值',
    remark       VARCHAR(255)                         NULL COMMENT '备注',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete    TINYINT   DEFAULT 0                  NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_configKey (config_key, is_delete)
) COMMENT '系统配置表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 2. 公告表（内容为 Markdown，管理员维护）
-- =============================================
CREATE TABLE IF NOT EXISTS announcement
(
    id           BIGINT                               COMMENT 'id' PRIMARY KEY,
    title        VARCHAR(128)                         NOT NULL COMMENT '公告标题',
    content      MEDIUMTEXT                           NULL COMMENT '公告内容（Markdown）',
    status       VARCHAR(32)  DEFAULT 'draft'         NOT NULL COMMENT '状态（draft-草稿/published-已发布/offline-已下线）',
    publish_time DATETIME                             NULL COMMENT '发布时间',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete    TINYINT   DEFAULT 0                  NOT NULL COMMENT '是否删除',
    INDEX idx_status_publishTime (status, publish_time)
) COMMENT '公告表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 3. 公告-用户确认表（用户点击“不再弹出”后记录）
-- =============================================
CREATE TABLE IF NOT EXISTS announcement_ack
(
    id              BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    announcement_id BIGINT                             NOT NULL COMMENT '公告id',
    user_id         BIGINT                             NOT NULL COMMENT '用户id',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '确认时间',
    UNIQUE KEY uk_announcement_user (announcement_id, user_id),
    INDEX idx_userId (user_id)
) COMMENT '公告-用户确认表' COLLATE = utf8mb4_unicode_ci;

-- =============================================
-- 4. app 表新增部署计费扣费时间列
-- =============================================
ALTER TABLE app
    ADD COLUMN deploy_billed_time DATETIME NULL COMMENT '部署计费最近一次扣费时间' AFTER deployed_time;
