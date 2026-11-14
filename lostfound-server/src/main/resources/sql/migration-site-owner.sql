-- =====================================================================
-- 招领站主管理员体系迁移（人工等价 SQL）
-- 与 dbtool/site-owner-migrate.js 的 apply 口径一致，MySQL 5.6 兼容：
-- 不使用 CHECK 约束、生成列、函数索引或 JSON 新类型。
-- 执行前必须先创建 foundsite / foundsite_manager 全表快照。
-- =====================================================================

-- 1. 主管理员标识：放在 foundsite 单列，天然保证「每站有且仅一位主管理员」
ALTER TABLE `foundsite`
    ADD COLUMN `owner_user_id` BIGINT NULL COMMENT '主管理员用户ID' AFTER `endorsement_updated_by`;

-- 2. 管理员邀请码
CREATE TABLE IF NOT EXISTS `foundsite_manager_invite` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `site_id`         BIGINT      NOT NULL COMMENT '招领站ID',
    `code`            VARCHAR(32) NOT NULL COMMENT '邀请码',
    `inviter_user_id` BIGINT      NOT NULL COMMENT '邀请人（主管理员）用户ID',
    `status`          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/USED/REVOKED/EXPIRED',
    `expire_time`     DATETIME    NOT NULL COMMENT '过期时间',
    `used_by_user_id` BIGINT      DEFAULT NULL COMMENT '使用人用户ID',
    `used_time`       DATETIME    DEFAULT NULL COMMENT '使用时间',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_site_status` (`site_id`, `status`)
) ENGINE = InnoDB COMMENT ='招领站管理员邀请码';

-- 3. 存量回填：每个已有管理员的站点取最早一条 foundsite_manager 作为主管理员
UPDATE `foundsite` s
JOIN (
    SELECT `site_id`, MIN(`id`) AS `min_id`
    FROM `foundsite_manager`
    GROUP BY `site_id`
) x ON x.`site_id` = s.`id`
JOIN `foundsite_manager` m ON m.`id` = x.`min_id`
SET s.`owner_user_id` = m.`user_id`
WHERE s.`owner_user_id` IS NULL;

-- 4. 校验（应全部返回 0）
-- SELECT COUNT(*) FROM `foundsite` s
--   WHERE s.`owner_user_id` IS NOT NULL
--     AND NOT EXISTS (SELECT 1 FROM `foundsite_manager` m
--                     WHERE m.`site_id` = s.`id` AND m.`user_id` = s.`owner_user_id`);
-- SELECT COUNT(*) FROM `foundsite` s
--   WHERE s.`owner_user_id` IS NULL
--     AND EXISTS (SELECT 1 FROM `foundsite_manager` m WHERE m.`site_id` = s.`id`);
