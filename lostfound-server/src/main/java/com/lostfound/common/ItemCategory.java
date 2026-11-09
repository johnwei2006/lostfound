package com.lostfound.common;

import java.util.List;
import java.util.Set;

/**
 * 物品类别字典。
 *
 * <p>唯一事实来源是 {@code config/item-categories.json}，本类与 Web 端
 * {@code lostfound-web/src/types/index.ts} 的 CATEGORIES、小程序
 * {@code lostfound-uniapp/utils/api.js} 及 {@code lostfound-uniapp-cli/src/utils/api.js}
 * 的 CATEGORIES 由 {@code scripts/sync-category-dict.mjs} 统一生成，禁止手工修改标记区间。
 *
 * <p>2026-11 起旧字典已彻底废止，不再保留历史代码与中文别名的查询兼容；
 * 旧类别筛选返回空结果、旧类别写入被拒绝。
 */
public final class ItemCategory {

    // CATEGORY_DICT_BEGIN
    /** 界面枚举顺序固定，勿改为 Set.of（迭代顺序不确定）；由生成器写入 */
    public static final List<String> ALL =
            List.of("证件证照", "卡券票据", "手机通讯", "数码电子",
                    "服饰配饰", "钥匙锁具", "钱包卡包", "箱包行李",
                    "书刊文件", "生活用品", "宠物活体", "其他"
            );
    // CATEGORY_DICT_END

    private static final Set<String> ALL_SET = Set.copyOf(ALL);

    private ItemCategory() {
    }

    public static boolean isValid(String v) {
        return v != null && ALL_SET.contains(v.trim()) && v.equals(v.trim());
    }

    /** 发布/编辑写入前校验；旧类别一律拒绝并提示更新客户端 */
    public static String requireValid(String v) {
        if (!isValid(v)) {
            throw new BusinessException("物品类别不合法，请更新到最新版本后重新选择类别。可选：" + String.join("、", ALL));
        }
        return v;
    }

    /** 查询归一化：仅去首尾空白，未知值原样返回（精确匹配自然为空），不再做别名转换 */
    public static String normalize(String v) {
        return v == null ? null : v.trim();
    }
}
