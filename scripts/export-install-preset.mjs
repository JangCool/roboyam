#!/usr/bin/env node
/**
 * 로컬 DB 의 「아이로빗 구조」(운영 설정)를 설치 프리셋 IROBIT 으로 내보낸다 — 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」.
 *
 *   node scripts/export-install-preset.mjs            # 로컬 DB(irobit) → install-presets/irobit.structure.json + irobit.json 의 boards
 *   node scripts/export-install-preset.mjs --check    # 쓰지 않고 무엇이 담기는지만 출력
 *
 * 구조가 바뀌면(게시판·메뉴·가젯·홈 배치·게시판 설정을 관리자 화면에서 고친 뒤) 다시 돌린다. 데이터(회원·글·댓글·소모임·공구·광고·하이라이트 항목·포인트)는 담지 않는다.
 *
 * 담는 것(표 = 넣는 차례, FK 차례): 아래 TABLES. 표마다 쓰는 방식이 있다 — 설치 서버(PresetStructureApplier)가 같은 뜻으로 넣는다.
 *   REPLACE  설치 DB 의 그 표를 비우고 이 행들로 채운다(마이그레이션 시드 행 포함 — 메뉴 노드 등)
 *   UPSERT   열쇠(key)가 같으면 이 값으로 고치고 없으면 넣는다(한 줄 설정 표·등급·메뉴 그룹)
 *   UPDATE   이미 있는 행의 몇 칸만 고친다(사이트 설정 — 이름·주소·슬로건은 설치 입력이 정한다)
 * 운영자를 가리키는 칸(FK → operators, 또는 이름이 *_by 인 bigint)은 "$ROOT" 로 적는다 — 설치 때 루트 운영자 ID 가 된다.
 * 데이터를 가리키는 칸(FK → files·posts·users 등 TABLES 밖의 표)은 내보내지 않는다(설치 DB 의 기본값) — 아래 DATA_REF 에 걸리면 출력에 적는다.
 * 사이트 팝업(site_popups)은 담지 않는다 — 로컬의 팝업은 시험 항목이고 그림 팝업은 올린 파일에 매인다(팝업 배치 popup_layout 만 담는다).
 *
 * 환경 변수(선택): EXPORT_DB_CONTAINER(기본 irobit-local-postgres) · EXPORT_DB_NAME(기본 irobit) · EXPORT_DB_USER(기본 irobit)
 * 의존성 없음(docker exec … psql). 로컬 DB 는 읽기만 한다.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DB_CONTAINER = process.env.EXPORT_DB_CONTAINER ?? 'irobit-local-postgres';
const DB_NAME = process.env.EXPORT_DB_NAME ?? 'irobit';
const DB_USER = process.env.EXPORT_DB_USER ?? 'irobit';
const CHECK = process.argv.includes('--check');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const PRESET_DIR = join(ROOT, 'server', 'api', 'src', 'main', 'resources', 'install-presets');
const PRESET_FILE = join(PRESET_DIR, 'irobit.json');
const STRUCTURE_NAME = 'irobit.structure.json';
const ROOT_MARK = '$ROOT';

/**
 * 구조 표. 차례가 넣는 차례다(앞 표를 뒤 표가 FK 로 가리킨다). 서버의 허용 목록(StructureSnapshot.TABLES)과 같아야 한다 — 표를 더하면 둘 다 고친다.
 * columns: UPDATE 의 고칠 칸(열쇠 빼고). exclude: 내보내지 않을 칸. orderBy: 행 차례(diff 가 안정되게).
 */
const TABLES = [
    { table: 'grades', mode: 'UPSERT', orderBy: 'level' },
    { table: 'boards', mode: 'REPLACE', orderBy: 'sort_order, id' },
    { table: 'board_prefixes', mode: 'REPLACE', orderBy: 'board_id, sort_order, id' },
    { table: 'group_categories', mode: 'REPLACE', orderBy: 'sort_order, id' },
    { table: 'group_category_boards', mode: 'REPLACE', orderBy: 'category_id, assigned_order' },
    { table: 'menu_sets', mode: 'UPSERT', orderBy: 'set_key' },
    { table: 'menu_nodes', mode: 'REPLACE', orderBy: 'set_key, parent_id nulls first, sort_order, id' },
    { table: 'gadget_pages', mode: 'REPLACE', orderBy: 'id' },
    { table: 'gadget_page_layouts', mode: 'REPLACE', orderBy: 'page_id, theme' },
    { table: 'home_gadgets', mode: 'REPLACE', orderBy: 'theme, page_id nulls first, id' },
    { table: 'home_layouts', mode: 'REPLACE', orderBy: 'theme' },
    { table: 'board_layouts', mode: 'REPLACE', orderBy: 'theme' },
    { table: 'site_theme_menus', mode: 'REPLACE', orderBy: 'theme' },
    {
        table: 'site_settings', mode: 'UPDATE', orderBy: 'id',
        columns: ['theme', 'display_language', 'display_zone_id', 'display_time_format', 'display_date_format', 'terms_links_visible',
            'notice_bar_enabled', 'notice_bar_source', 'notice_bar_board_count', 'notice_bar_custom_text', 'notice_bar_custom_link', 'popup_layout'],
    },
    { table: 'board_defaults', mode: 'UPSERT', orderBy: 'id' },
    { table: 'board_report_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'board_security_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'board_notification_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'board_privacy_settings', mode: 'UPSERT', orderBy: 'id', exclude: ['last_run_at'] },
    { table: 'popular_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'point_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'point_shop_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'deal_settings', mode: 'UPSERT', orderBy: 'id' },
    // 구글 애드센스 칸(shared V176)은 사이트마다 다른 게시자 계정이라 내보내지 않는다 — 새 설치는 기본값(끔)
    { table: 'ad_placement_settings', mode: 'UPSERT', orderBy: 'id', exclude: ['adsense_mode', 'adsense_client', 'adsense_adtest', 'adsense_wide_banner',
        'adsense_strip_banner', 'adsense_square_banner', 'adsense_small_banner', 'adsense_wing_banner', 'adsense_post_inline', 'adsense_board_list_slot',
        'adsense_board_list_layout_key'] },
    { table: 'group_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'grade_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'member_event_settings', mode: 'UPSERT', orderBy: 'code' },
    { table: 'nickname_image_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'emoticon_settings', mode: 'UPSERT', orderBy: 'id' },
    { table: 'group_buy_settings', mode: 'UPSERT', orderBy: 'id' },
];

/** 설치 화면 목록(boards)에 싣지 않는 게시판 — 운영 게시판(OpsBoardSeeder 가 모든 구성에 만든다) */
const OPS_BOARDS = new Set(['bugs', 'requests']);
/** 공통 말머리의 영문 이름(BoardFields.COMMON_PREFIX_NAMES_EN) */
const COMMON_PREFIX_EN = { QUESTION: 'Question', REVIEW: 'Review', CHAT: 'Chat', NOTICE: 'Notice' };

function sql(script) {
    try {
        return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', '-v', 'ON_ERROR_STOP=1', '-U', DB_USER, '-d', DB_NAME, '-f', '-'],
            { input: script, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 256 * 1024 * 1024 }).trim();
    } catch (e) {
        throw new Error(`SQL 실패(${DB_CONTAINER}/${DB_NAME}): ${String(e.stderr ?? e.message).trim()}`);
    }
}
const json = (script) => JSON.parse(sql(script) || 'null');
const ident = (name) => `"${name.replace(/"/g, '""')}"`;
const lit = (s) => `'${String(s).replace(/'/g, "''")}'`;

function tableInfo(table) {
    const info = json(`
        SELECT json_build_object(
          'columns', (SELECT json_agg(json_build_object('name', a.attname, 'type', format_type(a.atttypid, a.atttypmod), 'generated', a.attgenerated <> '') ORDER BY a.attnum)
                      FROM pg_attribute a WHERE a.attrelid = ${lit(table)}::regclass AND a.attnum > 0 AND NOT a.attisdropped),
          'key', (SELECT json_agg(a.attname ORDER BY array_position(i.indkey::int2[], a.attnum))
                  FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                  WHERE i.indrelid = ${lit(table)}::regclass AND i.indisprimary),
          'fks', (SELECT json_agg(json_build_object('column', a.attname, 'target', c.confrelid::regclass::text))
                  FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
                  WHERE c.conrelid = ${lit(table)}::regclass AND c.contype = 'f'))`);
    if (!info.columns) {
        throw new Error(`표가 없다: ${table}`);
    }
    return { columns: info.columns, key: info.key ?? [], fks: info.fks ?? [] };
}

const structureTables = new Set(TABLES.map((t) => t.table));
const notes = [];
const out = [];

for (const spec of TABLES) {
    const info = tableInfo(spec.table);
    if (info.key.length === 0) {
        throw new Error(`기본 키가 없는 표는 담을 수 없다: ${spec.table}`);
    }
    const fkTarget = new Map(info.fks.map((fk) => [fk.column, fk.target]));
    const exclude = new Set(spec.exclude ?? []);
    const selects = [];
    const types = {};
    for (const col of info.columns) {
        if (col.generated || exclude.has(col.name)) continue;
        if (spec.mode === 'UPDATE' && !info.key.includes(col.name) && !spec.columns.includes(col.name)) continue;
        const target = fkTarget.get(col.name);
        const operatorRef = target === 'operators' || (!target && col.type === 'bigint' && col.name.endsWith('_by'));
        if (operatorRef) {
            selects.push(`CASE WHEN ${ident(col.name)} IS NULL THEN NULL ELSE ${lit(ROOT_MARK)} END AS ${ident(col.name)}`);
        } else if (target && !structureTables.has(target)) {
            notes.push(`${spec.table}.${col.name} → ${target}: 데이터를 가리키는 칸이라 뺐다`);
            continue;
        } else if (col.type === 'bigint' || col.type.startsWith('numeric')) {
            // 큰 정수·소수는 글자로 — JSON 숫자로 두면 JS(그리고 일부 JSON 도구)가 18자리 ID 의 끝자리를 잃는다. 설치 쪽(json_populate_recordset)이 칸 형식으로 다시 읽는다
            selects.push(`${ident(col.name)}::text AS ${ident(col.name)}`);
        } else {
            selects.push(ident(col.name));
        }
        types[col.name] = col.type;
    }
    const rows = json(`SELECT coalesce(json_agg(t), '[]'::json) FROM (SELECT ${selects.join(', ')} FROM ${ident(spec.table)} ORDER BY ${spec.orderBy}) t`);
    out.push({ table: spec.table, mode: spec.mode, key: info.key, columns: types, rows });
}

// 가젯 설정·배치 안에 글·파일 같은 데이터 ID 가 있으면 알린다(지금은 없다 — 가젯은 게시판 주소·종류로 가리킨다)
const ids = new Set();
for (const t of out) {
    if (['boards', 'board_prefixes', 'menu_nodes', 'gadget_pages', 'home_gadgets', 'group_categories'].includes(t.table)) {
        for (const row of t.rows) ids.add(String(row.id));
    }
}
for (const t of out.filter((x) => ['home_gadgets', 'home_layouts', 'gadget_page_layouts', 'gadget_pages', 'menu_nodes', 'board_layouts'].includes(x.table))) {
    for (const row of t.rows) {
        for (const [col, value] of Object.entries(row)) {
            if (value === null || typeof value !== 'object') continue;
            for (const m of JSON.stringify(value).matchAll(/\b\d{15,19}\b/g)) {
                if (!ids.has(m[0])) notes.push(`${t.table}.${col}: 구조 밖의 ID ${m[0]} 를 가리킨다 — 확인 필요`);
            }
        }
    }
}

const sharedVersion = sql(`SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1`);
const snapshot = {
    format: 1,
    preset: 'IROBIT',
    exportedAt: new Date().toISOString(),
    source: { sharedVersion },
    tables: out,
};

// ── 설치 화면 목록(irobit.json 의 boards) — 일반·핫딜 게시판(켜진 것)과 소모임 게시판, 켜진 말머리 ──
const boards = out.find((t) => t.table === 'boards').rows;
const prefixes = out.find((t) => t.table === 'board_prefixes').rows;
const categories = new Map(out.find((t) => t.table === 'group_categories').rows.map((c) => [String(c.id), c.name]));
const categoryOf = new Map(out.find((t) => t.table === 'group_category_boards').rows.map((a) => [String(a.board_id), categories.get(String(a.category_id))]));
const preset = JSON.parse(readFileSync(PRESET_FILE, 'utf8'));
const knownEn = new Map();
for (const b of preset.boards ?? []) for (const p of b.prefixes ?? []) knownEn.set(p.name, p.nameEn);
const fallbackEn = [];
const typed = boards
    .filter((b) => b.active && !OPS_BOARDS.has(b.slug))
    .map((b) => {
        const own = prefixes.filter((p) => String(p.board_id) === String(b.id) && p.active);
        return {
            slug: b.slug,
            name: b.name,
            nameEn: b.name_en ?? (fallbackEn.push(`게시판 ${b.slug}`), b.name),
            kind: b.kind,
            sortOrder: b.sort_order * 10,
            description: b.description || null,
            layout: b.layout,
            viewToggle: b.view_toggle,
            investmentNotice: b.investment_notice,
            staffOnlyWrite: b.write_min === 'STAFF',
            ...(b.debate_enabled ? { debateEnabled: true } : {}),
            color: b.color,
            ...(b.kind === 'GROUP' ? { groupCategoryName: categoryOf.get(String(b.id)) } : {}),
            prefixes: own.map((p, i) => {
                const name = p.name ?? p.common_key;
                const nameEn = p.common_key ? COMMON_PREFIX_EN[p.common_key] : knownEn.get(name) ?? (fallbackEn.push(`말머리 ${b.slug}/${name}`), name);
                return { name, nameEn, common: Boolean(p.common_key), staffOnly: p.staff_only, sortOrder: (i + 1) * 10 };
            }),
        };
    });
const nextPreset = { ...preset, structure: STRUCTURE_NAME, demo: true, boards: typed };
delete nextPreset.menu; // 메뉴는 구조(menu_nodes)가 정한다 — 구조를 넣지 못했을 때는 기본 메뉴(V43) + 게시판 자동 추가

console.log(`구조 ${DB_NAME} (shared V${sharedVersion}) → ${STRUCTURE_NAME}`);
for (const t of out) console.log(`  ${t.mode.padEnd(7)} ${t.table.padEnd(28)} ${t.rows.length}행`);
console.log(`설치 화면 목록: 게시판 ${typed.length}개`);
for (const n of notes) console.log(`  · ${n}`);
if (fallbackEn.length) console.log(`  · 영문 이름이 없어 한국어 이름을 그대로 쓴 것 ${fallbackEn.length}개(${fallbackEn.slice(0, 6).join(', ')}${fallbackEn.length > 6 ? ' …' : ''})`);

if (!CHECK) {
    // 한 행 한 줄 — 다시 내보냈을 때 diff 가 행 단위로 보이게
    const lines = ['{', `  "format": ${snapshot.format},`, `  "preset": ${JSON.stringify(snapshot.preset)},`, `  "exportedAt": ${JSON.stringify(snapshot.exportedAt)},`,
        `  "source": ${JSON.stringify(snapshot.source)},`, '  "tables": ['];
    out.forEach((t, ti) => {
        lines.push('    {');
        lines.push(`      "table": ${JSON.stringify(t.table)}, "mode": ${JSON.stringify(t.mode)}, "key": ${JSON.stringify(t.key)},`);
        lines.push(`      "columns": ${JSON.stringify(t.columns)},`);
        lines.push('      "rows": [');
        t.rows.forEach((r, ri) => lines.push(`        ${JSON.stringify(r)}${ri < t.rows.length - 1 ? ',' : ''}`));
        lines.push('      ]');
        lines.push(`    }${ti < out.length - 1 ? ',' : ''}`);
    });
    lines.push('  ]', '}', '');
    writeFileSync(join(PRESET_DIR, STRUCTURE_NAME), lines.join('\n'));
    writeFileSync(PRESET_FILE, `${JSON.stringify(nextPreset, null, 2)}\n`);
    console.log('썼다.');
}
