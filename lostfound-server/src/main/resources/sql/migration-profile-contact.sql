-- =====================================================================
-- 个人资料通用联系方式：已有库幂等加列（线上 MySQL 5.6.51 兼容）
-- 新装库直接执行 schema.sql 即可，无需本脚本。
-- 线上由临时运维脚本 profile-contact-migrate.js 按 information_schema 幂等执行；
-- 本文件是人工等价 SQL，执行前先跑下面的检查语句。
-- =====================================================================

-- 1) 检查列是否已存在（返回 0 行才需要执行第 2 步）
SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'lostfound'
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME = 'contact';

-- 2) 加列：不使用 ADD COLUMN IF NOT EXISTS（MySQL 5.6 不支持），也不使用
--    CHECK / 生成列 / 函数索引 / JSON 等新特性，避免与线上版本不兼容。
ALTER TABLE `user`
    ADD COLUMN `contact` VARCHAR(100) DEFAULT NULL
    COMMENT '联系方式（微信号/手机号/邮箱等，发布时自动带入）'
    AFTER `email`;

-- 3) 校验：应返回 1 行，且历史行 contact 全为 NULL（不回填任何默认值）
SELECT COUNT(*) AS total, COUNT(`contact`) AS with_contact FROM `user`;
