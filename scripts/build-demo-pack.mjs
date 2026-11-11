#!/usr/bin/env node
/**
 * 데모 팩(seed/demo-pack/)을 만든다 — 설치 마법사 「데모 데이터 포함」이 설치 직후 넣는 표본 데이터. 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」,
 * 만드는 차례는 docs/seed-demo.md 「데모 팩」.
 *
 *   node scripts/build-demo-pack.mjs install   # ① 빈 빌드 DB 에 「구조만」(ROBOYAM)으로 설치하고 기준점(이때까지의 행 = 팩에 넣지 않음)을 적는다
 *   (seed-demo.mjs 만 빌드 서버·빌드 DB 로 돌린다 — seed-local.mjs 의 시험 계정은 팩에 넣지 않는다. 빌드 관리자 서버는 배치를 켜고(사업자 핫딜 게시 배치) local 공구 시더를 끈다
 *    --roboyam.groupbuy.local-seed=false)
 *   node scripts/build-demo-pack.mjs export    # ② 기준점 뒤에 생기거나 바뀐 행 + 올린 파일 → seed/demo-pack/
 *
 * ⚠ 사용자의 로컬 DB(roboyam)에 쓰지 않는다 — BUILD_DB_NAME 이 roboyam 이면 멈춘다. 빌드 DB·빌드 서버(다른 포트, NODE_ID 1001, 빈 저장소 폴더)를 따로 띄운다.
 *
 * 팩 형식: manifest.json(형식·구성·만든 시각·마이그레이션 번호·운영자 ID·파일 목록·표별 행 수) + data.json.gz(TablePack — 표마다 칸 형식·열쇠·행) + files/<저장 키>.
 *   - 표는 FK 차례(부모 먼저)로, 행은 기본 키 차례로. bigint·numeric 칸은 글자로(18자리 ID 가 JSON 숫자로 끝자리를 잃지 않게).
 *   - 방식: 기본 UPSERT. FULL_TABLES(마이그레이션이 설치 시각으로 ID 를 매기는 시드 표 — 로보티콘·포인트 상점 상품)는 표 전체를 REPLACE —
 *     설치마다 시드 ID 가 달라 팩의 행(로보티콘 받기·댓글 :emo-ID:)이 가리키는 ID 를 맞추려고 팩의 시드로 갈아 끼운다.
 *   - 빼는 것: 설치 때 정한 값(installation·site_settings·mail/social 설정), 운영자 계정·역할(만든 DB 의 운영자 ID 는 설치한 곳의 루트로 바뀐다), 감사·세션·배치·분석 기록,
 *     구조 표(구조 스냅숏이 넣는다 — 바뀌었으면 경고).
 *   - 개인정보 칸(DataCipher 암호문)은 그대로 담고 빌드 서버의 키(local 개발 키)를 목록에 적는다 — 설치 서버가 풀어 그곳의 키로 다시 잠근다.
 *   - 데모 회원이 아닌 회원(시험 계정)·local 공구 시더 상품이 있으면 멈춘다.
 *   - 회원 비밀번호는 아무도 모르는 값의 해시로 바꾼다(데모 회원으로 로그인할 수 없다 — 원고의 시험 비밀번호가 실제 사이트에 남지 않게). --keep-passwords 면 그대로.
 *
 * 환경 변수: BUILD_API(기본 http://localhost:8911) · BUILD_DB_CONTAINER(기본 roboyam-local-postgres) · BUILD_DB_NAME(기본 roboyam_demo_build) · BUILD_DB_USER(기본 roboyam)
 *           · BUILD_STORAGE_PATH(빌드 서버의 ROBOYAM_STORAGE_PATH — export 에 필요) · BUILD_INSTALL_TOKEN(기본 roboyam-demo-token, local 프로필 기본값)
 */
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const API = (process.env.BUILD_API ?? 'http://localhost:8911').replace(/\/+$/, '');
const DB_CONTAINER = process.env.BUILD_DB_CONTAINER ?? 'roboyam-local-postgres';
const DB_NAME = process.env.BUILD_DB_NAME ?? 'roboyam_demo_build';
const DB_USER = process.env.BUILD_DB_USER ?? 'roboyam';
const STORAGE = process.env.BUILD_STORAGE_PATH ?? '';
const TOKEN = process.env.BUILD_INSTALL_TOKEN ?? 'roboyam-demo-token';
const KEEP_PASSWORDS = process.argv.includes('--keep-passwords');
const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed', 'demo-pack');
const PRESET = 'ROBOYAM';
/** 빌드 서버가 개인정보 칸을 잠근 키 — 기본은 local 개발 키(저장소에 공개된 값, roboyam-shared-local.yml). 팩 목록(dataKey)에 적어 설치 서버가 풀고 다시 잠근다 */
const DATA_KEY = process.env.BUILD_DATA_KEY
    ?? readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'server', 'shared', 'src', 'main', 'resources', 'roboyam-shared-local.yml'), 'utf8')
        .match(/key:\s*\$\{ROBOYAM_DATA_KEY:([^}]+)\}/)?.[1];

/** 표 전체를 갈아 끼우는 시드 표(마이그레이션이 설치 시각 Snowflake 로 ID 를 매긴다 — V86·V142·V157) */
const FULL_TABLES = ['emoticon_images', 'emoticons', 'emoticon_packs', 'emoticon_pack_revisions', 'emoticon_revision_items', 'point_shop_items'];
/** 서로 가리키는 표에서 나중에 채울 칸(차례를 정할 때 이 FK 는 보지 않는다) — 사업자 신원 → 등록증(등록증이 신원을 가리키고 「붙임」 CHECK 가 그 칸을 요구한다) */
const LATE = ['business_profiles.certificate_id'];
/** 담지 않는 표(이름 그대로 또는 앞부분) */
const EXCLUDE = [
    'installation', 'site_settings', 'mail_settings', 'mail_setting_history', 'social_provider_settings', 'social_provider_setting_history', 'portone_settings',
    'public_data_settings', 'address_search_settings', 'site_operator', 'site_operator_history', 'stats_collection_settings', 'stats_collection_setting_history',
    'commerce_sales_settings', 'admin_security_settings', 'operators', 'operator_roles', 'roles', 'role_permissions', 'permissions', 'audit_logs', 'auth_tokens',
    'login_attempts', 'idempotency_requests', 'guest_password_attempts', 'identity_verification_attempts', 'demo_pack_baseline', 'member_view_settings',
    // 하루 판정 표 — 방문자 해시만 담고 날짜 조각(파티션)이 있어 옛 날짜 줄은 새 설치에 들어갈 조각이 없다(site-analytics-contract 3단계 결정 ⑤·⑥)
    'deal_click_daily',
    // 구조 스냅숏이 넣는 표(scripts/export-install-preset.mjs 의 TABLES)
    'grades', 'boards', 'board_prefixes', 'group_categories', 'group_category_boards', 'menu_sets', 'menu_nodes', 'gadget_pages', 'gadget_page_layouts', 'home_gadgets',
    'home_layouts', 'board_layouts', 'site_theme_menus', 'board_defaults', 'board_report_settings', 'board_security_settings', 'board_notification_settings',
    'board_privacy_settings', 'popular_settings', 'point_settings', 'point_shop_settings', 'deal_settings', 'ad_placement_settings', 'group_settings', 'grade_settings',
    'member_event_settings', 'nickname_image_settings', 'emoticon_settings', 'group_buy_settings',
];
const EXCLUDE_PREFIX = ['flyway_schema_history', 'spring_session', 'admin_spring_session', 'batch_', 'worker_heartbeats', 'analytics_', 'operator_mfa', 'operator_trusted',
    'operator_preferences', 'operator_dashboards', 'operator_notification', 'operator_invitation', 'operator_cert', 'admin_', 'notification_send_', 'mail_outbox'];
/** 구조 표 중 데모 로더가 바꿔도 되는 것(바뀌면 경고만) */
const STRUCTURE_TABLES = EXCLUDE.slice(EXCLUDE.indexOf('grades'));

function sql(script) {
    try {
        return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', '-v', 'ON_ERROR_STOP=1', '-U', DB_USER, '-d', DB_NAME, '-f', '-'],
            { input: script, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 1024 * 1024 * 1024 }).trim();
    } catch (e) {
        throw new Error(`SQL 실패(${DB_CONTAINER}/${DB_NAME}): ${String(e.stderr ?? e.message).trim().slice(0, 2000)}`);
    }
}
const json = (script) => JSON.parse(sql(script) || 'null');
const ident = (name) => `"${name.replace(/"/g, '""')}"`;
const lit = (s) => `'${String(s).replace(/'/g, "''")}'`;

if (DB_NAME === 'roboyam') {
    console.error('BUILD_DB_NAME 이 사용자의 로컬 DB(roboyam)다 — 빌드 DB 를 따로 만든다(예: roboyam_demo_build). 아무것도 하지 않았다.');
    process.exit(2);
}
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(API)) {
    console.error(`빌드 서버는 localhost 만: ${API}`);
    process.exit(2);
}

const command = process.argv[2];
if (command === 'install') await install();
else if (command === 'export') exportPack();
else {
    console.error('쓰는 법: node scripts/build-demo-pack.mjs install | export');
    process.exit(2);
}

// ── ① 설치(구조만) + 기준점 ──
async function install() {
    const jar = new Map();
    const call = async (method, path, body) => {
        const headers = { Accept: 'application/json' };
        if (jar.size) headers.Cookie = [...jar].map(([k, v]) => `${k}=${v}`).join('; ');
        if (method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(jar.get('XSRF-TOKEN') ?? '');
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
        for (const line of res.headers.getSetCookie?.() ?? []) {
            const [pair] = line.split(';');
            const eq = pair.indexOf('=');
            jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
        }
        const text = await res.text();
        return { status: res.status, body: text ? JSON.parse(text) : null };
    };
    const status = await call('GET', '/api/system/installation/status');
    if (status.body?.data?.installed !== false) {
        console.error(`빌드 서버(${API})가 설치 전 상태가 아니다 — 빈 DB 로 다시 띄운다.`);
        process.exit(1);
    }
    const verify = await call('POST', '/api/system/installation/verify-token', { token: TOKEN });
    if (verify.status !== 200) throw new Error(`설치 토큰 실패 ${verify.status} ${JSON.stringify(verify.body?.error)}`);
    const password = `Demo${randomBytes(9).toString('hex')}1`; // 빌드 DB 의 루트 — 쓰지 않는다(데모 로더는 데모 운영자로 들어간다)
    const done = await call('POST', '/api/system/installation/complete', {
        root: { email: 'demo-build-root@example.com', nickname: '빌드루트', password },
        site: { name: 'ROBOYAM', publicUrl: 'http://localhost:3100', tagline: null },
        mail: null, social: null, boardPreset: PRESET, demoData: false,
    });
    if (done.status !== 201) throw new Error(`설치 실패 ${done.status} ${JSON.stringify(done.body?.error)}`);
    sql(`CREATE TABLE IF NOT EXISTS demo_pack_baseline (xid bigint NOT NULL, at timestamptz NOT NULL DEFAULT now());
         DELETE FROM demo_pack_baseline; INSERT INTO demo_pack_baseline (xid) VALUES (txid_current())`);
    const [boards, nodes] = sql(`SELECT (SELECT count(*) FROM boards) || '|' || (SELECT count(*) FROM menu_nodes)`).split('|');
    console.log(`설치함(구조만, ${PRESET}) — 게시판 ${boards} · 메뉴 노드 ${nodes}. 기준점을 적었다. 이제 seed-demo.mjs 를 이 서버·DB 로 돌린다(seed-local.mjs 는 돌리지 않는다 — 시험 계정은 팩에 넣지 않는다).`);
}

// ── ② 내보내기 ──
function exportPack() {
    if (!STORAGE || !existsSync(STORAGE)) {
        console.error('BUILD_STORAGE_PATH(빌드 서버의 저장소 폴더)가 없다.');
        process.exit(2);
    }
    const testAccounts = sql(`SELECT count(*) FROM users WHERE email NOT LIKE 'demo%@example.com'`);
    if (testAccounts !== '0') {
        console.error(`데모 회원(demo…@example.com)이 아닌 회원이 ${testAccounts}명 있다 — 시험 계정(seed-local.mjs)은 팩에 넣지 않는다. 빌드 DB 를 새로 만들어 seed-local 없이 다시 한다.`);
        process.exit(1);
    }
    const seededGroupBuys = sql(`SELECT count(*) FROM group_buy_products WHERE name LIKE '%local 시드%' OR summary LIKE '%local 시드%'`);
    if (seededGroupBuys !== '0') {
        console.error('local 공구 시더의 상품이 있다 — 빌드 관리자 서버를 --roboyam.groupbuy.local-seed=false 로 띄워 다시 한다.');
        process.exit(1);
    }
    const baseline = sql(`SELECT xid FROM demo_pack_baseline`);
    if (!baseline) throw new Error('기준점이 없다 — 먼저 install 을 돌린다');
    const xid32 = BigInt(baseline) % 4294967296n;
    const busy = sql(`SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND state <> 'idle'`);
    if (busy !== '0') console.warn(`⚠ 빌드 DB 에 도는 문장이 ${busy}개 있다 — 데모 로더·배치가 끝난 뒤에 내보내는 것이 맞다.`);

    // 일반 표 + 나눔 표의 부모(조각은 부모로 읽는다), FK 차례
    const tables = json(`
        WITH t AS (SELECT c.oid, c.relname FROM pg_class c
                   WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p') AND NOT c.relispartition)
        SELECT json_agg(json_build_object('name', t.relname,
            'fks', (SELECT coalesce(json_agg(json_build_object('target', p.relname,
                         'columns', (SELECT json_agg(a.attname) FROM pg_attribute a WHERE a.attrelid = t.oid AND a.attnum = ANY(k.conkey)))), '[]')
                    FROM pg_constraint k JOIN pg_class p ON p.oid = k.confrelid
                    WHERE k.conrelid = t.oid AND k.contype = 'f' AND k.confrelid <> t.oid AND NOT k.condeferrable),
            'key', (SELECT json_agg(a.attname ORDER BY array_position(i.indkey::int2[], a.attnum)) FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) WHERE i.indrelid = t.oid AND i.indisprimary),
            'columns', (SELECT json_agg(json_build_object('name', a.attname, 'type', format_type(a.atttypid, a.atttypmod), 'generated', a.attgenerated <> '', 'notnull', a.attnotnull) ORDER BY a.attnum)
                        FROM pg_attribute a WHERE a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped))) FROM t`);
    const byName = new Map(tables.map((t) => [t.name, t]));
    const skipped = (name) => EXCLUDE.includes(name) || EXCLUDE_PREFIX.some((p) => name.startsWith(p));

    let hash = null;
    if (!KEEP_PASSWORDS) {
        hash = sql(`BEGIN; CREATE EXTENSION IF NOT EXISTS pgcrypto; SELECT crypt(${lit(randomBytes(24).toString('hex'))}, gen_salt('bf', 10)); ROLLBACK;`)
            .split('\n').find((l) => l.startsWith('$2'));
        if (!hash) throw new Error('비밀번호 해시를 만들지 못했다');
    }

    const changedStructure = [];
    const picked = [];
    for (const t of tables) {
        const full = FULL_TABLES.includes(t.name);
        const fresh = `(xmin::text::bigint) >= ${xid32}`;
        if (skipped(t.name)) {
            if (STRUCTURE_TABLES.includes(t.name) && sql(`SELECT count(*) FROM ${ident(t.name)} WHERE ${fresh}`) !== '0') changedStructure.push(t.name);
            continue;
        }
        const where = full ? 'true' : fresh;
        if (sql(`SELECT count(*) FROM ${ident(t.name)} WHERE ${where}`) === '0') continue;
        if (!t.key) {
            console.warn(`⚠ 기본 키가 없는 표라 담지 않았다: ${t.name}`);
            continue;
        }
        picked.push({ ...t, full, where });
    }

    // FK 차례(부모 먼저) — 미룰 수 있는 FK(DEFERRABLE, 커밋 때 확인 — 로보티콘 팩 ↔ 판)와 LATE 칸의 FK 는 차례에 넣지 않는다. 남은 고리는 이름 차례로 끊는다
    const lateFk = (table, fk) => fk.columns.length === 1 && LATE.includes(`${table}.${fk.columns[0]}`);
    const order = [];
    const seen = new Set();
    const visit = (name, path = new Set()) => {
        if (seen.has(name) || path.has(name)) return;
        path.add(name);
        for (const fk of byName.get(name)?.fks ?? []) if (!lateFk(name, fk) && picked.some((p) => p.name === fk.target)) visit(fk.target, path);
        seen.add(name);
        order.push(name);
    };
    picked.map((p) => p.name).sort().forEach((n) => visit(n));
    // 뒤 차례 표를 가리키는 FK 칸 = 나중에 채울 칸(late — 설치 서버가 비운 채 넣고 끝에 채운다). 비울 수 없는 칸이면 알린다
    const lateOf = new Map();
    for (const name of order) {
        for (const fk of byName.get(name).fks) {
            const at = order.indexOf(fk.target);
            if (at <= order.indexOf(name)) continue;
            const col = byName.get(name).columns.find((c) => c.name === fk.columns[0]);
            if (fk.columns.length === 1 && col && !col.notnull) lateOf.set(name, [...(lateOf.get(name) ?? []), col.name]);
            else console.warn(`⚠ ${name}(${fk.columns.join(',')}) → ${fk.target}: 뒤 차례 표를 가리키는데 비울 수 없는 칸이라 설치 때 FK 에 걸린다 — LATE 를 고친다`);
        }
    }

    const out = [];
    const counts = {};
    const cipherCols = new Set();
    for (const name of order) {
        const t = picked.find((p) => p.name === name);
        const types = {};
        const selects = [];
        // 암호문 칸(DataCipher)은 그대로 담는다 — 설치 서버가 팩의 키(manifest.dataKey)로 풀어 설치한 곳의 키로 다시 잠근다(DemoDataInstaller · DataKeyColumns)
        for (const col of t.columns) {
            if (col.generated) continue;
            types[col.name] = col.type;
            if (name === 'users' && col.name === 'password_hash' && hash) selects.push(`CASE WHEN password_hash IS NULL THEN NULL ELSE ${lit(hash)} END AS password_hash`);
            else if (col.type === 'bigint' || col.type.startsWith('numeric') || col.type === 'bigint[]') selects.push(`${ident(col.name)}::text${col.type === 'bigint[]' ? '[]' : ''} AS ${ident(col.name)}`);
            else selects.push(ident(col.name));
        }
        const rows = json(`SELECT coalesce(json_agg(r), '[]'::json) FROM (SELECT ${selects.join(', ')} FROM ${ident(name)} WHERE ${t.where}
                           ORDER BY ${t.key.map(ident).join(', ')}) r`);
        for (const row of rows) {
            for (const [col, v] of Object.entries(row)) if (typeof v === 'string' && /^d\d+:[A-Za-z0-9+/=_-]{16,}$/.test(v)) cipherCols.add(`${name}.${col}`);
        }
        out.push({ table: name, mode: t.full ? 'REPLACE' : 'UPSERT', key: t.key, columns: types, rows, ...(lateOf.has(name) ? { late: lateOf.get(name) } : {}) });
        counts[name] = rows.length;
    }

    // 올린 파일 — 빌드 저장소 전체(빈 폴더로 띄운 빌드 서버라 모두 데모 로더가 올린 것)
    const files = [];
    const walk = (dir) => {
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
            const path = join(dir, entry.name);
            if (entry.isDirectory()) walk(path);
            else files.push({ key: relative(STORAGE, path).split(sep).join('/'), size: statSync(path).size, path });
        }
    };
    walk(STORAGE);

    const operatorIds = sql(`SELECT id::text FROM operators ORDER BY id`).split('\n').filter(Boolean);
    const sharedVersion = sql(`SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1`);
    const builtAt = new Date().toISOString();
    const data = { format: 1, preset: PRESET, exportedAt: builtAt, source: { sharedVersion }, tables: out };

    rmSync(OUT, { recursive: true, force: true });
    mkdirSync(join(OUT, 'files'), { recursive: true });
    const gz = gzipSync(Buffer.from(JSON.stringify(data)), { level: 9 });
    writeFileSync(join(OUT, 'data.json.gz'), gz);
    for (const f of files) {
        mkdirSync(dirname(join(OUT, 'files', f.key)), { recursive: true });
        cpSync(f.path, join(OUT, 'files', f.key));
    }
    const manifest = {
        format: 1, preset: PRESET, builtAt, source: { sharedVersion, database: DB_NAME }, operatorIds, dataKey: DATA_KEY, data: 'data.json.gz',
        dataSha256: createHash('sha256').update(gz).digest('hex'),
        counts, files: files.map(({ key, size }) => ({ key, size })),
    };
    writeFileSync(join(OUT, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

    const fileBytes = files.reduce((s, f) => s + f.size, 0);
    console.log(`데모 팩 → ${OUT}`);
    console.log(`  표 ${out.length}개 · 행 ${Object.values(counts).reduce((a, b) => a + b, 0)}개 · data.json.gz ${(gz.length / 1024).toFixed(0)}KB · 파일 ${files.length}개 ${(fileBytes / 1048576).toFixed(1)}MB`);
    for (const [k, v] of Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 25)) console.log(`    ${k.padEnd(34)} ${v}`);
    if (changedStructure.length) console.warn(`⚠ 데모 로더가 구조 표를 바꿨다(팩에 담지 않음 — 구조 스냅숏을 다시 내보내야 하는지 본다): ${changedStructure.join(', ')}`);
    if (cipherCols.size) console.log(`  암호문 칸(설치 때 그곳의 키로 다시 잠근다): ${[...cipherCols].join(', ')}`);
    if (hash) console.log('  회원 비밀번호는 아무도 모르는 값의 해시로 바꿨다(--keep-passwords 면 그대로).');
}
