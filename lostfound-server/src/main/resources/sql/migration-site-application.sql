-- =====================================================================
-- 招领站建站申请与自定义背书：人工等价迁移 SQL（MySQL 5.6 兼容）
--
-- 实际线上执行以临时运维脚本 site-application-migrate.js 为准；本文件供审计与手工核对。
-- 将 <SUFFIX> 替换为 apply 输出的 14 位时间戳。
-- MySQL 5.6 不支持 ADD COLUMN IF NOT EXISTS；DDL 隐式提交，不能放进事务。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 执行前快照与元数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `site_app_bak_<SUFFIX>_foundsite` LIKE `foundsite`;
INSERT INTO `site_app_bak_<SUFFIX>_foundsite` SELECT * FROM `foundsite`;

CREATE TABLE IF NOT EXISTS `site_app_bak_<SUFFIX>_meta` (
    `k` VARCHAR(64) NOT NULL,
    `v` VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (`k`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO `site_app_bak_<SUFFIX>_meta` (`k`, `v`) VALUES
    ('version', '1'),
    ('status', 'running'),
    ('applied_at', NOW()),
    ('added_columns', 'endorsement_org,endorsement_text,endorsement_updated_at,endorsement_updated_by'),
    ('created_tables', 'foundsite_application');

-- ---------------------------------------------------------------------
-- 2. 幂等补充 foundsite 背书字段
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `lf_add_site_application_columns`;

DELIMITER $$
CREATE PROCEDURE `lf_add_site_application_columns`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'foundsite' AND COLUMN_NAME = 'endorsement_org'
    ) THEN
        ALTER TABLE `foundsite`
            ADD COLUMN `endorsement_org` VARCHAR(100) DEFAULT NULL COMMENT '自定义背书单位';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'foundsite' AND COLUMN_NAME = 'endorsement_text'
    ) THEN
        ALTER TABLE `foundsite`
            ADD COLUMN `endorsement_text` VARCHAR(500) DEFAULT NULL COMMENT '自定义背书推荐语';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'foundsite' AND COLUMN_NAME = 'endorsement_updated_at'
    ) THEN
        ALTER TABLE `foundsite`
            ADD COLUMN `endorsement_updated_at` DATETIME DEFAULT NULL COMMENT '背书更新时间';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'foundsite' AND COLUMN_NAME = 'endorsement_updated_by'
    ) THEN
        ALTER TABLE `foundsite`
            ADD COLUMN `endorsement_updated_by` BIGINT DEFAULT NULL COMMENT '背书更新人ID';
    END IF;
END$$
DELIMITER ;

CALL `lf_add_site_application_columns`();
DROP PROCEDURE IF EXISTS `lf_add_site_application_columns`;

-- ---------------------------------------------------------------------
-- 3. 建站申请表（不使用 CHECK / 函数索引 / JSON 新类型）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `foundsite_application` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `applicant_user_id` BIGINT        NOT NULL COMMENT '申请用户ID',
    `name`              VARCHAR(100)  NOT NULL COMMENT '拟建站点名称',
    `address`           VARCHAR(200)  NOT NULL COMMENT '拟建站点地址',
    `longitude`         DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`          DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `phone`             VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    `open_time`         VARCHAR(100)  DEFAULT NULL COMMENT '开放时间',
    `description`       VARCHAR(1000) DEFAULT NULL COMMENT '站点简介',
    `reason`            VARCHAR(500)  NOT NULL COMMENT '申请理由',
    `contact`           VARCHAR(100)  DEFAULT NULL COMMENT '补充联系方式',
    `status`            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `reviewer_user_id`  BIGINT        DEFAULT NULL COMMENT '审核人ID',
    `review_time`       DATETIME      DEFAULT NULL COMMENT '审核时间',
    `review_remark`     VARCHAR(500)  DEFAULT NULL COMMENT '审核意见',
    `created_site_id`   BIGINT        DEFAULT NULL COMMENT '审核通过后创建的站点ID',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_create_time` (`status`, `create_time`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`),
    KEY `idx_applicant_create_time` (`applicant_user_id`, `create_time`),
    KEY `idx_created_site_id` (`created_site_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='招领站建站申请表';

-- ---------------------------------------------------------------------
-- 4. 执行后校验
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM `site_app_bak_<SUFFIX>_foundsite`) AS snapshot_rows,
    (SELECT COUNT(*) FROM `foundsite`) AS current_rows,
    (SELECT COUNT(*) FROM `foundsite_application`) AS application_rows,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'foundsite'
        AND COLUMN_NAME IN ('endorsement_org','endorsement_text','endorsement_updated_at','endorsement_updated_by')) AS endorsement_columns;

UPDATE `site_app_bak_<SUFFIX>_meta` SET `v` = 'completed' WHERE `k` = 'status';

-- ---------------------------------------------------------------------
-- 5. 回滚模板
-- 仅在 foundsite 当前行数与快照一致、foundsite_application 为空、
-- 且四个背书字段全为 NULL 时执行；快照表与元数据表保留供审计。
-- ---------------------------------------------------------------------
-- SELECT COUNT(*) FROM `foundsite_application`;
-- SELECT COUNT(*) FROM `foundsite`
--  WHERE `endorsement_org` IS NOT NULL OR `endorsement_text` IS NOT NULL
--     OR `endorsement_updated_at` IS NOT NULL OR `endorsement_updated_by` IS NOT NULL;
-- DROP TABLE IF EXISTS `foundsite_application`;
-- ALTER TABLE `foundsite` DROP COLUMN `endorsement_updated_by`;
-- ALTER TABLE `foundsite` DROP COLUMN `endorsement_updated_at`;
-- ALTER TABLE `foundsite` DROP COLUMN `endorsement_text`;
-- ALTER TABLE `foundsite` DROP COLUMN `endorsement_org`;
-- UPDATE `site_app_bak_<SUFFIX>_meta` SET `v` = 'rolled_back' WHERE `k` = 'status';
