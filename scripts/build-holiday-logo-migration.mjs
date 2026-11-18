/**
 * 기념일 로고 마이그레이션을 그림 파일에서 만들어 낸다 — 기본 제공 묶음 56개(28일 × 캐릭터·글자 두 벌)와 기념일 규칙 28개.
 * docs/features/site-logo-library-contract.md 「기념일 로고」·「서버 구현」.
 *
 * 쓰기(저장소 루트에서):
 *   node scripts/build-holiday-logo-migration.mjs                 → V249__site_logo_holidays.sql 을 다시 쓴다
 *   node scripts/build-holiday-logo-migration.mjs V260__이름.sql   → 다른 파일 이름으로(그림을 고쳐 새 마이그레이션을 낼 때 — --update 와 함께)
 *   … --update                                                    → 표·규칙은 건드리지 않고 묶음의 SVG 칸만 고치는 UPDATE 문으로 뽑는다
 *
 * ⚠ 이미 적용된 마이그레이션 파일은 고치지 않는다 — 그림을 고쳤으면 새 번호 + --update 로 뽑는다. V251 를 다시 쓰는 것은 어디에도 적용되기 전까지만.
 *
 * 재료: design/brand/holiday-logos/<날>/irobit-{logo,wordmark}-<날>-{ko,en}-{light,dark}.svg (만드는 법 robotikon/logo/build-all.mjs)
 *   logo = 귀여운 버전(캐릭터 로고 글꼴) → 벌 CHARACTER, wordmark = 일반 버전(글자 로고 글꼴) → 벌 WORDMARK
 * 날의 차례(달력 순)·이름·걸리는 날은 아래 DAYS 가 원천이다(이름은 로고 속 짧은 표기(mascot.mjs THEME_NAMES)가 아니라 관리 화면에 보일 이름).
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = join(ROOT, 'design/brand/holiday-logos');
const MIGRATIONS = join(ROOT, 'server/shared/src/main/resources/db/migration/shared');

/** [날 키, 묶음·규칙 이름, 매년 그날(MM-DD) 또는 { holiday: 쉬는 날 표의 이름 }] — 달력 순 */
const DAYS = [
    ['newyear', '새해 첫날', '01-01'],
    ['seollal', '설날', { holiday: '설날' }],
    ['samil', '삼일절', '03-01'],
    ['jeju43', '4·3 희생자 추념일', '04-03'],
    ['sikmok', '식목일', '04-05'],
    ['imjeong', '대한민국 임시정부 수립 기념일', '04-11'],
    ['sailgu', '4·19 혁명 기념일', '04-19'],
    ['gwahak', '과학의 날', '04-21'],
    ['jeongbo', '정보통신의 날', '04-22'],
    ['eorini', '어린이날', '05-05'],
    ['eobeoi', '어버이날', '05-08'],
    ['seuseung', '스승의 날', '05-15'],
    ['oilpal', '5·18 민주화운동 기념일', '05-18'],
    ['buddha', '부처님오신날', { holiday: '부처님오신날' }],
    ['hyeonchung', '현충일', '06-06'],
    ['yusip', '6·10 민주항쟁 기념일', '06-10'],
    ['yugio', '6·25 전쟁일', '06-25'],
    ['jeheon', '제헌절', '07-17'],
    ['girim', "일본군 '위안부' 피해자 기림의 날", '08-14'],
    ['gwangbok', '광복절', '08-15'],
    ['gwangbokgun', '광복군 창설일', '09-17'],
    ['chuseok', '추석', { holiday: '추석' }],
    ['gukgun', '국군의 날', '10-01'],
    ['gaecheon', '개천절', '10-03'],
    ['hangeul', '한글날', '10-09'],
    ['haksaeng', '학생독립운동 기념일', '11-03'],
    ['sunguk', '순국선열의 날', '11-17'],
    ['xmas', '성탄절', '12-25'],
];

/** 벌 — 묶음 id 대역(날 차례 1~28 을 더한다) · 파일 이름 조각 · 이름 꼬리 */
const STYLES = [
    { key: 'CHARACTER', base: 100, file: 'logo', suffix: ' (캐릭터)' },
    { key: 'WORDMARK', base: 200, file: 'wordmark', suffix: ' (글자)' },
];
const CELLS = [['ko_light', 'ko-light'], ['ko_dark', 'ko-dark'], ['en_light', 'en-light'], ['en_dark', 'en-dark']];
/** 규칙 id 대역(앱이 매기는 Snowflake 와 겹치지 않는 작은 수) */
const RULE_BASE = 100;
const SVG_MAX = 100_000; // 관리자 SVG 올리기 한도(SvgInspector.MAX_CHARS)와 같다
const TAG = '$logo$';

const args = process.argv.slice(2);
const update = args.includes('--update');
const out = args.find((a) => !a.startsWith('--')) ?? 'V249__site_logo_holidays.sql';

const text = (value) => `'${value.replace(/'/g, "''")}'`;
function svg(day, style, cell) {
    const path = join(SOURCE, day, `irobit-${style.file}-${day}-${cell}.svg`);
    const body = readFileSync(path, 'utf8').replace(/\r/g, '').trim();
    if (body.includes(TAG)) throw new Error(`달러 인용 꼬리표가 본문에 있다: ${path}`);
    if (!body.startsWith('<svg') || !body.endsWith('</svg>')) throw new Error(`<svg>…</svg> 한 덩어리가 아니다: ${path}`);
    if (!/\sxmlns="http:\/\/www\.w3\.org\/2000\/svg"/.test(body)) throw new Error(`xmlns 가 없다: ${path}`);
    if (body.length > SVG_MAX) throw new Error(`SVG 가 ${SVG_MAX}자를 넘는다: ${path}`);
    return TAG + body + TAG;
}

const lines = [];
let bytes = 0;
if (update) {
    lines.push('-- 기념일 로고 그림 고침 — scripts/build-holiday-logo-migration.mjs --update 가 design/brand/holiday-logos/ 에서 만들었다(손으로 고치지 않는다).');
    DAYS.forEach(([day], index) => {
        for (const style of STYLES) {
            const sets = CELLS.map(([column, cell]) => `${column}_svg = ${svg(day, style, cell)}`).join(',\n       ');
            lines.push(`UPDATE site_logo_bundles\n   SET ${sets},\n       updated_at = now()\n WHERE id = ${style.base + index + 1};`);
        }
    });
} else {
    lines.push(`-- 기념일 로고 — 기본 제공 묶음 56개(28일 × 두 벌)와 기념일 규칙 28개 (docs/features/site-logo-library-contract.md 「기념일 로고」)
-- ⚠ 이 파일은 scripts/build-holiday-logo-migration.mjs 가 design/brand/holiday-logos/ 의 그림에서 만들었다 — 손으로 고치지 않는다.
--
-- 1) site_logo_bundles.day_key — 그 묶음이 어느 날의 것인지(newyear · seollal · samil …). 평소 로고·올린 묶음은 NULL.
--    벌은 built_in_key 가 말한다: CHARACTER = 캐릭터 로고 계열(귀여운 버전), WORDMARK = 글자 로고 계열(일반 버전). 그래서 built_in_key 하나만 유일하던 것을
--    (built_in_key, day_key) 한 쌍이 유일한 것으로 바꾼다. 기본 제공이라 고치지 못한다(서버가 거절).
-- 2) 묶음 id: 캐릭터 벌 101~128, 글자 벌 201~228(달력 순 — 같은 날의 두 벌은 끝 두 자리가 같다). 「로고에 사이트 이름이 들어 있음」은 켬.
-- 3) 기념일 규칙 28줄(id 101~128, 모두 사용 켬, 달력 순) — 운영자가 이미 만든 줄이 있으면 그 뒤에 붙는다. 줄은 캐릭터 벌 묶음을 가리키고,
--    실제로 보일 벌은 평소 로고의 벌을 따른다(SiteLogoResolver — 평소 로고가 글자 로고 계열이면 같은 날의 글자 벌).
--    양력 날은 「날짜 직접 · 매년 반복」(하루), 음력 날(설날·부처님오신날·추석)은 쉬는 날 표(site_holidays)의 그 이름인 날.
ALTER TABLE site_logo_bundles ADD COLUMN day_key varchar(20);
ALTER TABLE site_logo_bundles DROP CONSTRAINT uq_site_logo_bundles_built_in;
CREATE UNIQUE INDEX ux_site_logo_bundles_built_in ON site_logo_bundles (built_in_key, coalesce(day_key, '')) WHERE built_in_key IS NOT NULL;
ALTER TABLE site_logo_bundles ADD CONSTRAINT ck_site_logo_bundles_day_key CHECK (day_key IS NULL OR built_in_key IS NOT NULL);
`);
    DAYS.forEach(([day, name], index) => {
        for (const style of STYLES) {
            const cells = CELLS.map(([, cell]) => svg(day, style, cell));
            lines.push(`INSERT INTO site_logo_bundles (id, name, built_in_key, day_key, name_in_logo, ko_light_svg, ko_dark_svg, en_light_svg, en_dark_svg)
VALUES (${style.base + index + 1}, ${text(name + style.suffix)}, '${style.key}', '${day}', true,
        ${cells.join(',\n        ')});`);
        }
    });
    const rules = DAYS.map(([, name, when], index) => {
        const id = RULE_BASE + index + 1;
        const bundle = STYLES[0].base + index + 1;
        const tail = typeof when === 'string'
            ? `'DATES', '${when}', '${when}', true, NULL`
            : `'HOLIDAY', NULL, NULL, false, ${text(when.holiday)}`;
        return `       (${id}, ${index + 1}, ${text(name)}, ${tail}, ${bundle})`;
    });
    lines.push(`
-- 기념일 규칙 — 이미 있는 줄(운영자가 만든 것) 뒤에 붙인다
INSERT INTO site_logo_rules (id, sort_order, name, enabled, mode, start_date, end_date, yearly, holiday_name, bundle_id)
SELECT v.id, (SELECT coalesce(max(sort_order), -1) FROM site_logo_rules) + v.n, v.name, true, v.mode, v.start_date, v.end_date, v.yearly, v.holiday_name, v.bundle_id
  FROM (VALUES
${rules.join(',\n')}
       ) AS v (id, n, name, mode, start_date, end_date, yearly, holiday_name, bundle_id);`);
}

const sql = lines.join('\n') + '\n';
bytes = Buffer.byteLength(sql);
writeFileSync(join(MIGRATIONS, out), sql, 'utf8');
console.log(`${out}: ${DAYS.length}일 × ${STYLES.length}벌, ${(bytes / 1024).toFixed(0)}KB`);
