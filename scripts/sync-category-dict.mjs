/**
 * 物品类别字典同步器：以 config/item-categories.json 为唯一事实来源。
 *
 *   node scripts/sync-category-dict.mjs --write   # 按标记区间重写四处常量
 *   node scripts/sync-category-dict.mjs --check   # 校验四处常量、schema.sql 注释与规则合法性（默认）
 *
 * 构建脚本前置调用 --check，防止 README 里记录过的「三处手工同步漂移」再次发生。
 * 标记区间之外的内容一律不动，避免正则误伤状态字典等相邻代码。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const CONFIG = path.join(ROOT, 'config', 'item-categories.json')
const BEGIN = 'CATEGORY_DICT_BEGIN'
const END = 'CATEGORY_DICT_END'

const TARGETS = [
  { file: 'lostfound-server/src/main/java/com/lostfound/common/ItemCategory.java', lang: 'java' },
  { file: 'lostfound-web/src/types/index.ts', lang: 'ts' },
  { file: 'lostfound-uniapp/utils/api.js', lang: 'js' },
  { file: 'lostfound-uniapp-cli/src/utils/api.js', lang: 'js' },
]

const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf8')
const write = (rel, text) => fs.writeFileSync(path.join(ROOT, rel), text, 'utf8')
const quote = (s, lang) => (lang === 'java' ? `"${s}"` : `'${s}'`)

function validate(dict) {
  const errors = []
  const cats = dict.categories
  if (!Number.isInteger(dict.version)) errors.push('version 必须是整数')
  if (!Array.isArray(cats) || cats.length === 0) errors.push('categories 不能为空')
  else {
    if (new Set(cats).size !== cats.length) errors.push('categories 存在重复项')
    if (cats.some((c) => typeof c !== 'string' || !c.trim() || c !== c.trim())) {
      errors.push('categories 含空值或首尾空白')
    }
    if (cats.some((c) => c.length > 30)) errors.push('categories 单个值超过 VARCHAR(30) 长度')
  }
  const m = dict.migration || {}
  if (!cats?.includes(m.fallback)) errors.push('fallback 不在 categories 中')
  if (!['title'].includes(m.matchField)) errors.push('matchField 仅支持 title')
  for (const [from, to] of Object.entries(m.explicit || {})) {
    if (!dict.legacyCategories?.includes(from)) errors.push(`explicit 含未知旧类别 ${from}`)
    if (!cats?.includes(to)) errors.push(`explicit.${from} 目标 ${to} 不在新字典中`)
  }
  for (const [i, r] of (m.keywordRules || []).entries()) {
    if (!Array.isArray(r.from) || !r.from.length) errors.push(`keywordRules[${i}].from 为空`)
    if (!Array.isArray(r.contains) || !r.contains.length) errors.push(`keywordRules[${i}].contains 为空`)
    if (r.to && !cats?.includes(r.to)) errors.push(`keywordRules[${i}].to 非法：${r.to}`)
  }
  if ((m.keywordRules || []).length > 30) errors.push('关键词规则超过 30 条，批量 UPDATE 过多')
  return errors
}

function render(dict, lang) {
  const cats = dict.categories
  if (lang === 'java') {
    const lines = []
    for (let i = 0; i < cats.length; i += 4) {
      lines.push('                    ' + cats.slice(i, i + 4).map((c) => quote(c, lang)).join(', ') + (i + 4 < cats.length ? ',' : ''))
    }
    return [
      '    /** 界面枚举顺序固定，勿改为 Set.of（迭代顺序不确定）；由生成器写入 */',
      '    public static final List<String> ALL =',
      '            List.of(' + lines[0].trim(),
      ...lines.slice(1),
      '            );',
    ].join('\n')
  }
  const suffix = lang === 'ts' ? ' as const' : ''
  return `export const CATEGORIES = [${cats.map((c) => quote(c, lang)).join(', ')}]${suffix}`
}

/** 只返回两条标记行之间的内容，标记行本身保留不动 */
function region(text, rel) {
  const b = text.indexOf(BEGIN)
  const e = text.indexOf(END)
  if (b < 0 || e < 0 || e <= b) throw new Error(`${rel} 缺少 ${BEGIN}/${END} 标记`)
  const start = text.indexOf('\n', b) + 1 || text.length
  const end = text.lastIndexOf('\n', e) + 1
  if (end <= start) throw new Error(`${rel} 标记区间异常（BEGIN 与 END 不能在同一行）`)
  return { start, end, body: text.slice(start, end) }
}

function parseDeclared(body, lang, rel) {
  const anchor = lang === 'java' ? body.indexOf('List.of(') : body.indexOf('[')
  if (anchor < 0) throw new Error(`${rel} 标记区间内缺少 List.of/[`)
  const tail = body.slice(anchor)
  const out = []
  const re = /"([^"]+)"|'([^']+)'/g
  let x
  while ((x = re.exec(tail)) !== null) out.push(x[1] || x[2])
  if (!out.length) throw new Error(`${rel} 未解析到类别数组`)
  return out
}

function check(dict) {
  const errors = validate(dict)
  for (const t of TARGETS) {
    try {
      const text = read(t.file)
      const r = region(text, t.file)
      if ((text.match(new RegExp(BEGIN, 'g')) || []).length !== 1 ||
          (text.match(new RegExp(END, 'g')) || []).length !== 1) {
        errors.push(`${t.file} 标记必须各出现一次`)
      }
      const got = parseDeclared(r.body, t.lang, t.file)
      if (got.join('|') !== dict.categories.join('|')) {
        errors.push(`${t.file} 字典不一致\n    期望: ${dict.categories.join(',')}\n    实际: ${got.join(',')}`)
      }
    } catch (e) { errors.push(e.message) }
  }

  // 旧别名兼容必须已移除
  const java = read(TARGETS[0].file)
  if (/\bLEGACY\b|legacyMap\s*\(/.test(java)) errors.push('ItemCategory.java 仍存在 LEGACY/legacyMap 旧别名兼容')
  if (/objectType|数字代码/.test(java)) errors.push('ItemCategory.java 仍含历史数字代码兼容说明')

  // schema.sql 两处 category 注释需包含全部新类别；且不得残留旧 8 类枚举
  const schema = read('lostfound-server/src/main/resources/sql/schema.sql')
  const categoryCols = schema.split('\n').filter((l) => l.trim().startsWith('`category`'))
  if (categoryCols.length < 2) errors.push('schema.sql 未找到 found/lost 两处 category 列定义')
  for (const [i, col] of categoryCols.entries()) {
    const missing = dict.categories.filter((c) => !col.includes(c))
    if (missing.length) errors.push(`schema.sql 第 ${i + 1} 处 category 注释缺少：${missing.join('、')}`)
  }
  if (/类别：证件\/钥匙\/手机\/电子产品\/钱包\/书籍\/衣物\/其他/.test(schema)) {
    errors.push('schema.sql 仍残留旧 8 类列注释')
  }

  // 后台饼图颜色数不得少于类别数
  const dash = read('lostfound-web/src/views/admin/DashboardView.vue')
  const colorLine = dash.split('\n').find((l) => /^\s*color:\s*\[/.test(l)) || ''
  const colorCount = (colorLine.match(/#[0-9a-fA-F]{6}/g) || []).length
  if (colorCount < dict.categories.length) {
    errors.push(`DashboardView.vue 饼图颜色仅 ${colorCount} 色，少于 ${dict.categories.length} 类`)
  }
  return errors
}

const dict = JSON.parse(fs.readFileSync(CONFIG, 'utf8'))
const mode = process.argv.includes('--write') ? 'write' : 'check'

if (mode === 'write') {
  const errors = validate(dict)
  if (errors.length) {
    console.error('[category-dict] 配置校验失败：\n  ' + errors.join('\n  '))
    process.exit(1)
  }
  for (const t of TARGETS) {
    const text = read(t.file)
    const r = region(text, t.file)
    const next = text.slice(0, r.start) + render(dict, t.lang) + '\n' + text.slice(r.end)
    write(t.file, next)
    console.log(`[category-dict] 已生成 ${t.file}`)
  }
} else {
  const errors = check(dict)
  if (errors.length) {
    console.error('[category-dict] 校验失败：\n  ' + errors.join('\n  '))
    process.exit(1)
  }
  console.log(`[category-dict] 校验通过：version=${dict.version}，${dict.categories.length} 类，四处常量与 schema.sql 一致`)
}
