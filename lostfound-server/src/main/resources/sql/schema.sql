-- =====================================================================
-- 失物招领平台 数据库建表脚本（MySQL 8.0+）
-- 参考：Lostfound_backend/lostfound.sql（旧系统 11 张表）与 16 个 hbm.xml 实体
-- 重设计：补全 message/feedback/foundsite_manager/claim 缺失表，演示数据已脱敏
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `lostfound`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `lostfound`;

-- ---------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `contact`     VARCHAR(100) DEFAULT NULL COMMENT '联系方式（微信号/手机号/邮箱等，发布时自动带入）',
    `openid`      VARCHAR(64)  DEFAULT NULL COMMENT '微信小程序openid',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 / ADMIN-管理员',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 / 0-禁用',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE = InnoDB COMMENT ='用户表';

-- 已有库升级：为 user 表补充微信登录字段（新库可忽略）
-- ALTER TABLE `user` ADD COLUMN `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序openid' AFTER `email`;
-- ALTER TABLE `user` ADD UNIQUE KEY `uk_openid` (`openid`);
-- 已有库升级：补充通用联系方式（幂等脚本见 migration-profile-contact.sql，新库可忽略）
-- ALTER TABLE `user` ADD COLUMN `contact` VARCHAR(100) DEFAULT NULL COMMENT '联系方式（微信号/手机号/邮箱等，发布时自动带入）' AFTER `email`;

-- ---------------------------------------------------------------------
-- 拾物表（捡到物品发布的招领信息，对应旧表 found）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `found` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT        NOT NULL COMMENT '发布人ID',
    `site_id`       BIGINT        DEFAULT NULL COMMENT '所属招领站ID',
    `title`         VARCHAR(100)  NOT NULL COMMENT '物品名称',
    `category`      VARCHAR(30)   NOT NULL COMMENT '类别：证件证照/卡券票据/手机通讯/数码电子/服饰配饰/钥匙锁具/钱包卡包/箱包行李/书刊文件/生活用品/宠物活体/其他',
    `description`   VARCHAR(2000) DEFAULT NULL COMMENT '物品描述',
    `images`        JSON          DEFAULT NULL COMMENT '图片URL数组',
    `province`      VARCHAR(30)   DEFAULT NULL COMMENT '省',
    `city`          VARCHAR(30)   DEFAULT NULL COMMENT '市',
    `district`      VARCHAR(30)   DEFAULT NULL COMMENT '区',
    `address`       VARCHAR(200)  DEFAULT NULL COMMENT '详细地点',
    `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `found_time`    DATETIME      DEFAULT NULL COMMENT '拾获时间',
    `contact`       VARCHAR(100)  DEFAULT NULL COMMENT '联系方式',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING-待认领 / CLAIMED-已认领 / FINISHED-已完成 / OFFLINE-已下架',
    `review_status` VARCHAR(20)   NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING / APPROVED / REJECTED（全局发布审核）',
    `source_type`      VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT '来源：PERSONAL-个人登记 / SITE-站点发布 / TRANSFER-转交入库',
    `transfer_status`  VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '站点转交：NONE/PENDING/APPROVED/REJECTED/CANCELLED',
    `submit_user_id`   BIGINT      DEFAULT NULL COMMENT '源头登记人ID',
    `site_approved_by` BIGINT      DEFAULT NULL COMMENT '站点审批人ID',
    `site_approved_at` DATETIME    DEFAULT NULL COMMENT '站点审批时间',
    `view_count`    INT           NOT NULL DEFAULT 0 COMMENT '浏览量',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_city` (`city`),
    KEY `idx_user` (`user_id`),
    KEY `idx_site` (`site_id`),
    KEY `idx_site_transfer` (`site_id`, `transfer_status`),
    KEY `idx_create_time` (`create_time`),
    FULLTEXT KEY `ft_title_desc` (`title`, `description`) WITH PARSER ngram
) ENGINE = InnoDB COMMENT ='拾物表';

-- ---------------------------------------------------------------------
-- 失物表（丢失物品发布的寻物启事，对应旧表 lost）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lost` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT        NOT NULL COMMENT '发布人ID',
    `title`         VARCHAR(100)  NOT NULL COMMENT '物品名称',
    `category`      VARCHAR(30)   NOT NULL COMMENT '类别：证件证照/卡券票据/手机通讯/数码电子/服饰配饰/钥匙锁具/钱包卡包/箱包行李/书刊文件/生活用品/宠物活体/其他',
    `description`   VARCHAR(2000) DEFAULT NULL COMMENT '物品描述',
    `images`        JSON          DEFAULT NULL COMMENT '图片URL数组',
    `province`      VARCHAR(30)   DEFAULT NULL COMMENT '省',
    `city`          VARCHAR(30)   DEFAULT NULL COMMENT '市',
    `district`      VARCHAR(30)   DEFAULT NULL COMMENT '区',
    `address`       VARCHAR(200)  DEFAULT NULL COMMENT '丢失地点',
    `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `lost_time`     DATETIME      DEFAULT NULL COMMENT '丢失时间',
    `contact`       VARCHAR(100)  DEFAULT NULL COMMENT '联系方式',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING-寻找中 / CLAIMED-待确认 / FINISHED-已找回 / OFFLINE-已下架',
    `review_status` VARCHAR(20)   NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING / APPROVED / REJECTED',
    `view_count`    INT           NOT NULL DEFAULT 0 COMMENT '浏览量',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_city` (`city`),
    KEY `idx_user` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    FULLTEXT KEY `ft_title_desc` (`title`, `description`) WITH PARSER ngram
) ENGINE = InnoDB COMMENT ='失物表';

-- ---------------------------------------------------------------------
-- 招领站表（对应旧表 foundsite）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `foundsite` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100)  NOT NULL COMMENT '站点名称',
    `address`     VARCHAR(200)  NOT NULL COMMENT '站点地址',
    `longitude`   DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`    DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `phone`       VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    `open_time`   VARCHAR(100)  DEFAULT NULL COMMENT '开放时间',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '站点简介',
    `endorsement_org`        VARCHAR(100) DEFAULT NULL COMMENT '自定义背书单位',
    `endorsement_text`       VARCHAR(500) DEFAULT NULL COMMENT '自定义背书推荐语',
    `endorsement_updated_at` DATETIME     DEFAULT NULL COMMENT '背书更新时间',
    `endorsement_updated_by` BIGINT       DEFAULT NULL COMMENT '背书更新人ID',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB COMMENT ='招领站表';

-- ---------------------------------------------------------------------
-- 招领站管理员表（对应旧实体 FoundSiteManager，旧 SQL 缺失，补全）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `foundsite_manager` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `site_id`     BIGINT   NOT NULL COMMENT '招领站ID',
    `user_id`     BIGINT   NOT NULL COMMENT '管理员用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_user` (`site_id`, `user_id`)
) ENGINE = InnoDB COMMENT ='招领站管理员表';

-- ---------------------------------------------------------------------
-- 招领站建站申请表（注册用户申请 -> 平台 ADMIN 审核 -> 自动建站并绑定管理员）
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
) ENGINE = InnoDB COMMENT ='招领站建站申请表';

-- ---------------------------------------------------------------------
-- 拾物转交招领站申请表（申请 -> 站点管理员审批 -> 入库）
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
) ENGINE = InnoDB COMMENT ='拾物转交招领站申请表';

-- ---------------------------------------------------------------------
-- 拾物归还登记表（认领审批通过 / 现场登记两条路径统一落表）
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
) ENGINE = InnoDB COMMENT ='拾物归还登记表';

-- ---------------------------------------------------------------------
-- 拾物评论表（对应旧表 foundcomments）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `found_comment` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`     BIGINT        NOT NULL COMMENT '拾物ID',
    `user_id`     BIGINT        NOT NULL COMMENT '评论人ID',
    `parent_id`   BIGINT        DEFAULT NULL COMMENT '父评论ID（楼中楼）',
    `content`     VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_item` (`item_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='拾物评论表';

-- ---------------------------------------------------------------------
-- 失物评论表（对应旧表 lostcomments）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lost_comment` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`     BIGINT        NOT NULL COMMENT '失物ID',
    `user_id`     BIGINT        NOT NULL COMMENT '评论人ID',
    `parent_id`   BIGINT        DEFAULT NULL COMMENT '父评论ID',
    `content`     VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_item` (`item_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='失物评论表';

-- ---------------------------------------------------------------------
-- 拾物关注/收藏表（对应旧表 myfoundfocus）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `found_focus` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`     BIGINT   NOT NULL COMMENT '拾物ID',
    `user_id`     BIGINT   NOT NULL COMMENT '关注人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_user` (`item_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='拾物关注表';

-- ---------------------------------------------------------------------
-- 失物关注/收藏表（对应旧表 mylostfocus）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lost_focus` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`     BIGINT   NOT NULL COMMENT '失物ID',
    `user_id`     BIGINT   NOT NULL COMMENT '关注人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_user` (`item_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='失物关注表';

-- ---------------------------------------------------------------------
-- 认领申请表（认领流转核心，旧系统由 OrderRequest 实体承载，重设计）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `claim_request` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_type`   VARCHAR(10)   NOT NULL COMMENT '物品类型：FOUND / LOST',
    `item_id`     BIGINT        NOT NULL COMMENT '物品ID',
    `user_id`     BIGINT        NOT NULL COMMENT '申请人ID',
    `description` VARCHAR(1000) NOT NULL COMMENT '认领凭证描述（需描述丢失细节）',
    `contact`     VARCHAR(100)  DEFAULT NULL COMMENT '联系方式',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理 / APPROVED-已通过 / REJECTED-已拒绝',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_item` (`item_type`, `item_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT ='认领申请表';

-- ---------------------------------------------------------------------
-- 智能匹配记录表（对应旧表 match，重设计）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `match_record` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `found_id`    BIGINT        NOT NULL COMMENT '拾物ID',
    `lost_id`     BIGINT        NOT NULL COMMENT '失物ID',
    `score`       DECIMAL(5,2)  NOT NULL COMMENT '匹配得分（0-100）',
    `reason`      VARCHAR(500)  DEFAULT NULL COMMENT '匹配原因说明',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'UNREAD' COMMENT '状态：UNREAD-未读 / ACCEPTED-已采纳 / IGNORED-已忽略',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_found` (`found_id`),
    KEY `idx_lost` (`lost_id`),
    UNIQUE KEY `uk_found_lost` (`found_id`, `lost_id`)
) ENGINE = InnoDB COMMENT ='失物拾物匹配记录表';

-- ---------------------------------------------------------------------
-- 站内消息表（对应旧实体 Message，旧 SQL 缺失，补全）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT        NOT NULL COMMENT '接收用户ID',
    `title`       VARCHAR(100)  NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(1000) DEFAULT NULL COMMENT '消息内容',
    `type`        VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM' COMMENT 'MATCH/CLAIM/SYSTEM/TRANSFER/RETURN/SITE',
    `related_id`  BIGINT        DEFAULT NULL COMMENT '关联ID（匹配记录/申请/物品）',
    `is_read`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 / 1-已读',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE = InnoDB COMMENT ='站内消息表';

-- ---------------------------------------------------------------------
-- 操作日志表（对应旧表 operatelog）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operate_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `action`      VARCHAR(50)  NOT NULL COMMENT '动作：LOGIN/PUBLISH/CLAIM/APPROVE等',
    `detail`      VARCHAR(500) DEFAULT NULL COMMENT '详情',
    `ip`          VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB COMMENT ='操作日志表';
