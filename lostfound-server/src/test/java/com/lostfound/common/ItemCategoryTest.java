package com.lostfound.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类别字典防漂移测试：以 config/item-categories.json 为唯一事实来源，
 * 断言四处常量之一（后端 ItemCategory.ALL）与配置一致、旧类别被拒、别名不再兼容。
 * 另外三处由 scripts/sync-category-dict.mjs --check 在 Web / 小程序构建前校验。
 */
class ItemCategoryTest {

    private static JsonNode config() throws Exception {
        Path p = Paths.get("../config/item-categories.json");
        if (!Files.exists(p)) p = Paths.get("config/item-categories.json");
        assertTrue(Files.exists(p), "找不到 config/item-categories.json（Maven 工作目录应为 lostfound-server）");
        return new ObjectMapper().readTree(p.toFile());
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void allMatchesCanonicalDictionary() throws Exception {
        JsonNode c = config();
        List<String> expected = strings(c.get("categories"));
        assertEquals(expected, ItemCategory.ALL, "ItemCategory.ALL 必须与 config/item-categories.json 一致，请运行 node scripts/sync-category-dict.mjs --write");
        assertEquals(expected.size(), ItemCategory.ALL.stream().distinct().count(), "类别不可重复");
        assertTrue(ItemCategory.ALL.contains(c.get("migration").get("fallback").asText()), "fallback 必须在字典中");
    }

    @Test
    void newCategoriesAreAccepted() throws Exception {
        for (String c : strings(config().get("categories"))) {
            assertEquals(c, ItemCategory.requireValid(c));
            assertTrue(ItemCategory.isValid(c));
        }
    }

    @Test
    void legacyCategoriesAreRejectedWithUpgradeHint() throws Exception {
        JsonNode c = config();
        List<String> current = strings(c.get("categories"));
        for (String old : strings(c.get("legacyCategories"))) {
            if (current.contains(old)) continue; // 「其他」在新旧字典中都存在
            BusinessException e = assertThrows(BusinessException.class, () -> ItemCategory.requireValid(old),
                    "旧类别应被拒绝：" + old);
            assertTrue(e.getMessage().contains("更新到最新版本"), "错误文案需提示更新客户端");
            assertEquals(400, e.getCode());
        }
        assertThrows(BusinessException.class, () -> ItemCategory.requireValid("2"));
        assertThrows(BusinessException.class, () -> ItemCategory.requireValid("其它"));
        assertThrows(BusinessException.class, () -> ItemCategory.requireValid(null));
    }

    @Test
    void whitespaceIsRejectedButNormalizedForQuery() {
        assertThrows(BusinessException.class, () -> ItemCategory.requireValid(" 证件证照"));
        assertThrows(BusinessException.class, () -> ItemCategory.requireValid("证件证照 "));
        assertEquals("证件证照", ItemCategory.normalize(" 证件证照 "));
        assertNull(ItemCategory.normalize(null));
    }

    @Test
    void normalizeNoLongerTranslatesLegacyAliases() {
        // 旧类别彻底作废：查询只去空白，不再做别名/数字代码转换，精确匹配自然返回空
        assertEquals("证件", ItemCategory.normalize("证件"));
        assertEquals("其它", ItemCategory.normalize("其它"));
        assertEquals("2", ItemCategory.normalize("2"));
        assertEquals("数码电子", ItemCategory.normalize("数码电子"));
    }

    @Test
    void migrationTargetsAreAllValid() throws Exception {
        JsonNode m = config().get("migration");
        m.get("explicit").fields().forEachRemaining(e ->
                assertTrue(ItemCategory.isValid(e.getValue().asText()), "explicit 目标非法：" + e.getValue().asText()));
        for (JsonNode rule : m.get("keywordRules")) {
            assertTrue(ItemCategory.isValid(rule.get("to").asText()), "keywordRules 目标非法：" + rule.get("to").asText());
            assertTrue(rule.get("contains").size() > 0, "关键词规则不能为空");
        }
        assertTrue(ItemCategory.isValid(m.get("fallback").asText()));
    }
}
