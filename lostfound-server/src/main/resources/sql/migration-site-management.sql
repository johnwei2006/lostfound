-- =====================================================================
-- 招领站管理功能 数据库迁移（MySQL 5.6 兼容）
--
-- 推荐执行方式（含快照、幂等判断、行数校验与回滚）：
--   LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js survey
--   LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js dry-run
--   LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js apply
--   LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js verify
--   LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js rollback <14位后缀>
-- 脚本位于临时运维目录 C:/Users/John/AppData/Local/Temp/dbtool/，不入库。
--
-- 如需手工执行：MySQL 5.6 不支持 ADD COLUMN IF NOT EXISTS / ADD INDEX IF NOT EXISTS，
-- 必须先查 information_schema.COLUMNS / STATISTICS 逐条判断，否则重复执行会报错。
-- 下方 ALTER 语句按「列尚不存在」的前提书写；CREATE TABLE 已带 IF NOT EXISTS 保护。
-- MySQL 5.6 的 TEXT/BLOB 列不允许声明默认值，故 evidence 不写 DEFAULT。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 拾物转交招领站申请表（保留申请、审批意见、审批人与撤回/驳回历史）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `found_transfer` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `found_id`      BIGINT        NOT NULL COMMENT '拾物ID',
    `site_id`       BIGINT        NOT NULL COMMENT '目标招领站ID',
    `from_user_id`  BIGINT        NOT NULL COMMENT '转交人（原始登记人）',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '转交备注或审批意见',
    `reviewer_id`   BIGINT        DEFAULT NULL COMMENT '审批人ID',
    `review_time`   DATETIME      DEFAULT NULL COMMENT '审批时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_found` (`found_id`),
    KEY `idx_site_status` (`site_id`, `status`),
    KEY `idx_from_user` (`from_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='拾物转交招领站申请表';

-- ---------------------------------------------------------------------
-- 2. 拾物归还登记表（认领审批通过 + 现场登记两条路径统一落表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `found_return` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `found_id`       BIGINT       NOT NULL COMMENT '拾物ID',
    `site_id`        BIGINT       DEFAULT NULL COMMENT '所属招领站ID',
    `claim_id`       BIGINT       DEFAULT NULL COMMENT '关联认领申请ID（认领通过时）',
    `way`            VARCHAR(20)  NOT NULL DEFAULT 'ONSITE' COMMENT 'CLAIM-认领审批 / ONSITE-现场登记',
    `receiver_name`  VARCHAR(50)  DEFAULT NULL COMMENT '领取人',
    `receiver_phone` VARCHAR(30)  DEFAULT NULL COMMENT '领取人联系方式',
    `evidence`       TEXT         COMMENT '归还凭证图片URL（JSON数组）',
    `remark`         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `operator_id`    BIGINT       NOT NULL COMMENT '登记/审批人ID',
    `return_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归还时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_found` (`found_id`),
    KEY `idx_site` (`site_id`),
    KEY `idx_claim` (`claim_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='拾物归还登记表';

-- ---------------------------------------------------------------------
-- 3. found 增列（来源、转交状态、源头与审批信息）
--    不写 AFTER，避免依赖线上列顺序；列顺序不影响 MyBatis-Plus 映射。
-- ---------------------------------------------------------------------
ALTER TABLE `found`
    ADD COLUMN `source_type`      VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT '来源：PERSONAL-个人登记 / SITE-站点发布 / TRANSFER-转交入库',
    ADD COLUMN `transfer_status`  VARCHAR(20) NOT NULL DEFAULT 'NONE'     COMMENT 'NONE/PENDING/APPROVED/REJECTED/CANCELLED',
    ADD COLUMN `submit_user_id`   BIGINT       DEFAULT NULL COMMENT '源头登记人ID',
    ADD COLUMN `site_approved_by` BIGINT       DEFAULT NULL COMMENT '站点审批人ID',
    ADD COLUMN `site_approved_at` DATETIME     DEFAULT NULL COMMENT '站点审批时间';

ALTER TABLE `found` ADD KEY `idx_site_transfer` (`site_id`, `transfer_status`);

-- ---------------------------------------------------------------------
-- 4. 存量数据回填：已有 site_id 的物品视为「站点直接发布且已通过」，
--    避免上线后站点清单突然清空；源头登记人统一回填为发布人。
--    只处理保持默认值的历史行，绝不覆盖新功能产生的 TRANSFER/PENDING 数据。
-- ---------------------------------------------------------------------
UPDATE `found`
   SET `source_type` = 'SITE',
       `transfer_status` = 'APPROVED'
 WHERE `site_id` IS NOT NULL
   AND `source_type` = 'PERSONAL'
   AND `transfer_status` = 'NONE';

UPDATE `found`
   SET `submit_user_id` = `user_id`
 WHERE `submit_user_id` IS NULL;
