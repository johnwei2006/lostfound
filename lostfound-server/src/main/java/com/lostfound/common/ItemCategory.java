package com.lostfound.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物品类别字典（唯一事实来源）。
 *
 * <p>与 Web 端 {@code lostfound-web/src/types/index.ts} 的 CATEGORIES、小程序
 * {@code lostfound-uniapp/utils/api.js} 的 CATEGORIES 保持一致，新增类别必须三处同步。
 *
 * <p>历史库使用 objectType 数字代码，迁移时原样写入 category 导致界面按中文枚举筛选不到数据。
 * 2026-11 已把 found/lost.category 与 legacy_*.objectType 全量归一为下列 8 类；
 * 此处保留旧代码映射，仅用于兼容历史链接的查询参数。
 */
public final class ItemCategory {

    /** 界面枚举顺序固定，勿改为 Set.of（迭代顺序不确定） */
    public static final List<String> ALL =
            List.of("证件", "钥匙", "手机", "电子产品", "钱包", "书籍", "衣物", "其他");

    private static final Set<String> ALL_SET = Set.copyOf(ALL);

    /** 旧 objectType 代码 / 中文别名 → 标准类别 */
    private static final Map<String, String> LEGACY = legacyMap();

    private ItemCategory() {
    }

    private static Map<String, String> legacyMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("1", "手机");
        m.put("2", "证件");
        m.put("3", "证件");
        m.put("4", "钱包");
        m.put("5", "钱包");
        m.put("6", "其他");
        m.put("7", "衣物");
        m.put("8", "钥匙");
        m.put("9", "电子产品");
        m.put("10", "书籍");
        m.put("11", "书籍");
        m.put("12", "其他");
        m.put("13", "其他");
        m.put("14", "其他");
        m.put("15", "其他");
        m.put("16", "其他");
        m.put("17", "其他");
        m.put("18", "其他");
        m.put("其它", "其他");
        m.put("文件书籍", "书籍");
        m.put("数码电子", "电子产品");
        m.put("服装", "衣物");
        m.put("卡类", "证件");
        m.put("现金", "钱包");
        m.put("文具", "书籍");
        return Map.copyOf(m);
    }

    public static boolean isValid(String v) {
        return v != null && ALL_SET.contains(v.trim()) && v.equals(v.trim());
    }

    /** 发布/编辑写入前校验，防止再次写入历史数字代码等非法类别 */
    public static String requireValid(String v) {
        if (!isValid(v)) {
            throw new BusinessException("物品类别不合法，可选：" + String.join("、", ALL));
        }
        return v;
    }

    /** 查询兼容：把历史数字代码/别名转成标准类别；未知值原样返回（精确匹配为空） */
    public static String normalize(String v) {
        if (v == null) return null;
        String raw = v.trim();
        if (ALL_SET.contains(raw)) return raw;
        return LEGACY.getOrDefault(raw, v);
    }
}
