-- ---------------------------------------------------------------------
-- 查询性能优化 · 生产库索引幂等迁移（人工等价 SQL）
--
-- 目标库：lostfound（线上 MySQL 5.6.51）
-- 用法：mysql -uroot -p lostfound < migration-perf-index.sql
-- 幂等：按 information_schema.STATISTICS 逐条判断，重复执行只会 SKIP。
-- 安全：只 ADD INDEX，不删除任何既有索引、不改列类型、不导数据。
-- 兼容：不使用 CHECK / 生成列 / 函数索引 / IF NOT EXISTS 语法（MySQL 5.6 均不支持）。
-- 自动化：线上部署以 dbtool/perf-index-migrate.js 的 survey/dry-run/apply/verify 为准，
--          本文件是与之逐条对应的人工复核版本。
-- ---------------------------------------------------------------------

DELIMITER $$
DROP PROCEDURE IF EXISTS `lf_perf_add_index`$$
CREATE PROCEDURE `lf_perf_add_index`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(512))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @lf_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE lf_stmt FROM @lf_ddl;
        EXECUTE lf_stmt;
        DEALLOCATE PREPARE lf_stmt;
        SELECT CONCAT('ADDED ', p_table, '.', p_index) AS result;
    ELSE
        SELECT CONCAT('SKIP ', p_table, '.', p_index) AS result;
    END IF;
END$$
DELIMITER ;

-- 拾物：deleted 为逻辑删除列（MyBatis-Plus 自动附加），作前导列可让 ORDER BY 走有序索引扫描
CALL lf_perf_add_index('found', 'idx_perf_time', '`deleted`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_hot', '`deleted`, `view_count`');
CALL lf_perf_add_index('found', 'idx_perf_review_time', '`deleted`, `review_status`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_status_review_time', '`status`, `review_status`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_city_time', '`deleted`, `city`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_category_time', '`deleted`, `category`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_user_time', '`deleted`, `user_id`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_site_time', '`deleted`, `site_id`, `create_time`');
CALL lf_perf_add_index('found', 'idx_perf_geo', '`deleted`, `longitude`, `latitude`');

-- 失物：与 found 同构（无 site_id 列）
CALL lf_perf_add_index('lost', 'idx_perf_time', '`deleted`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_hot', '`deleted`, `view_count`');
CALL lf_perf_add_index('lost', 'idx_perf_review_time', '`deleted`, `review_status`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_status_review_time', '`status`, `review_status`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_city_time', '`deleted`, `city`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_category_time', '`deleted`, `category`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_user_time', '`deleted`, `user_id`, `create_time`');
CALL lf_perf_add_index('lost', 'idx_perf_geo', '`deleted`, `longitude`, `latitude`');

-- 转交单：站点概览按站点 + 状态 + 审批时间统计
CALL lf_perf_add_index('found_transfer', 'idx_perf_site_create', '`site_id`, `create_time`');
CALL lf_perf_add_index('found_transfer', 'idx_perf_site_review', '`site_id`, `status`, `review_time`');

-- 认领单：按物品反查待处理数、按状态+更新时间统计今日处理
CALL lf_perf_add_index('claim_request', 'idx_perf_item_status', '`item_type`, `item_id`, `status`');
CALL lf_perf_add_index('claim_request', 'idx_perf_status_update', '`status`, `update_time`');

-- 匹配记录：我的匹配按时间倒序、按 found_id / lost_id 反查
CALL lf_perf_add_index('match_record', 'idx_perf_create_time', '`create_time`');
CALL lf_perf_add_index('match_record', 'idx_perf_found_time', '`found_id`, `create_time`');
CALL lf_perf_add_index('match_record', 'idx_perf_lost_time', '`lost_id`, `create_time`');

-- 消息：按用户 + 时间倒序分页
CALL lf_perf_add_index('message', 'idx_perf_user_time', '`user_id`, `create_time`');

-- 站点管理员：按用户反查我管理的站点（原表仅有 (site_id, user_id) 唯一键）
CALL lf_perf_add_index('foundsite_manager', 'idx_perf_user', '`user_id`');

DROP PROCEDURE IF EXISTS `lf_perf_add_index`;

-- 说明：ngram FULLTEXT 索引保持现状，不在此脚本创建。
-- online MySQL 5.6 是否支持 WITH PARSER ngram 由 perf-index-migrate.js survey 探测，
-- 不支持或缺失时 lostfound.perf.fulltext-enabled 保持 false，搜索走原 LIKE 路径。
