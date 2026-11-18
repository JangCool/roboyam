#!/usr/bin/env node
/**
 * 데모 팩(seed/demo-pack/)을 만든다 — 설치 마법사 「데모 데이터 포함」이 설치 직후 넣는 표본 데이터. 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」,
 * 만드는 차례는 docs/seed-demo.md 「데모 팩」.
 *
 * 기본(갱신, 몇 분):
 *   node scripts/build-demo-pack.mjs refresh   # 빈 빌드 DB 에 「데모 포함」으로 설치(서버가 지난 팩을 복원) → seed/demo/patches/ 의 새 패치 → export
 *
 * 처음부터(원고가 많이 바뀔 때만, 한 시간 가까이):
 *   node scripts/build-demo-pack.mjs install   # ① 빈 빌드 DB 에 「구조만」(프리셋 IROBIT)으로 설치하고 기준점(이때까지의 행 = 팩에 넣지 않음)을 적는다
 *   (seed-demo.mjs 만 빌드 서버·빌드 DB 로 돌린다 — seed-local.mjs 의 시험 계정은 팩에 넣지 않는다. 빌드 관리자 서버는 배치를 켜고(사업자 핫딜 게시 배치) local 공구 시더를 끈다
 *    --irobit.groupbuy.local-seed=false)
 *   node scripts/build-demo-pack.mjs export    # ② 기준점 뒤에 생기거나 바뀐 행 + 올린 파일 → seed/demo-pack/
 *
 * ⚠ 사용자의 로컬 DB(irobit)에 쓰지 않는다 — BUILD_DB_NAME 이 irobit 이면 멈춘다. 빌드 DB·빌드 서버(다른 포트, NODE_ID 1001, 빈 저장소 폴더)를 따로 띄운다.
 *
 * 팩 형식: manifest.json(형식·구성·만든 시각·마이그레이션 번호·운영자 ID·파일 목록·표별 행 수) + data.json.gz(TablePack — 표마다 칸 형식·열쇠·행) + files/<저장 키>.
 *   - 표는 FK 차례(부모 먼저)로, 행은 기본 키 차례로. bigint·numeric 칸은 글자로(18자리 ID 가 JSON 숫자로 끝자리를 잃지 않게).
 *   - 방식: 기본 UPSERT. FULL_TABLES(마이그레이션이 설치 시각으로 ID 를 매기는 시드 표 — 로보티콘·포인트 상점 상품·기본 칭호)는 표 전체를 REPLACE —
 *     설치마다 시드 ID 가 달라 팩의 행(로보티콘 받기·댓글 :emo-ID:)이 가리키는 ID 를 맞추려고 팩의 시드로 갈아 끼운다.
 *   - 빼는 것: 설치 때 정한 값(installation·site_settings·mail/social 설정), 운영자 계정·역할 배정(만든 DB 의 운영자 ID 는 설치한 곳의 루트로 바뀐다), 감사·세션·배치·분석 기록,
 *     구조 표(구조 스냅숏이 넣는다 — 바뀌었으면 경고). 역할은 운영자 역할만 담는다(ROW_FILTER — 회원 역할·데모 로더 역할 제외).
 *   - 개인정보 칸(DataCipher 암호문)은 그대로 담고 빌드 서버의 키(local 개발 키)를 목록에 적는다 — 설치 서버가 풀어 그곳의 키로 다시 잠근다.
 *   - 데모 회원이 아닌 회원(시험 계정)·local 공구 시더 상품이 있으면 멈춘다.
 *   - 회원 비밀번호는 아무도 모르는 값의 해시로 바꾼다(데모 회원으로 로그인할 수 없다 — 원고의 시험 비밀번호가 실제 사이트에 남지 않게). --keep-passwords 면 그대로.
 *
 * 환경 변수: BUILD_API(기본 http://localhost:8911) · BUILD_DB_CONTAINER(기본 irobit-local-postgres) · BUILD_DB_NAME(기본 irobit_demo_build) · BUILD_DB_USER(기본 irobit)
 *           · BUILD_STORAGE_PATH(빌드 서버의 IROBIT_STORAGE_PATH — export 에 필요) · BUILD_INSTALL_TOKEN(기본 irobit-demo-token, local 프로필 기본값)
 */
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gunzipSync, gzipSync } from 'node:zlib';

const API = (process.env.BUILD_API ?? 'http://localhost:8911').replace(/\/+$/, '');
const DB_CONTAINER = process.env.BUILD_DB_CONTAINER ?? 'irobit-local-postgres';
const DB_NAME = process.env.BUILD_DB_NAME ?? 'irobit_demo_build';
const DB_USER = process.env.BUILD_DB_USER ?? 'irobit';
const STORAGE = process.env.BUILD_STORAGE_PATH ?? '';
const TOKEN = process.env.BUILD_INSTALL_TOKEN ?? 'irobit-demo-token';
const KEEP_PASSWORDS = process.argv.includes('--keep-passwords');
const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed', 'demo-pack');
const PRESET = 'IROBIT';
/** 갱신 때 지난 팩 위에 차례로 돌릴 패치(이름 규칙·차례: runPatches) */
const PATCH_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed', 'demo', 'patches');
/** 패치 파일 이름: 4자리 번호-이름.sql|.mjs — 번호 차례로 돈다 */
const PATCH_NAME = /^\d{4}-[a-z0-9][a-z0-9-]*\.(sql|mjs)$/;
/** SnowflakeIdGenerator.EPOCH_MS(2026-01-01) — 패치용 ID 기준(demo_id_base)을 만들 때 */
const SNOWFLAKE_EPOCH_MS = 1_767_225_600_000n;
/** 빌드 서버가 개인정보 칸을 잠근 키 — 기본은 local 개발 키(저장소에 공개된 값, irobit-shared-local.yml). 팩 목록(dataKey)에 적어 설치 서버가 풀고 다시 잠근다 */
const DATA_KEY = process.env.BUILD_DATA_KEY
    ?? readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'server', 'shared', 'src', 'main', 'resources', 'irobit-shared-local.yml'), 'utf8')
        .match(/key:\s*\$\{IROBIT_DATA_KEY:([^}]+)\}/)?.[1];

/** 표 전체를 갈아 끼우는 시드 표(마이그레이션이 설치 시각 Snowflake 로 ID 를 매긴다 — V86·V142·V157 · 기본 칭호 7개 V240 — 회원 칭호·대표 칭호가 칭호 ID 를 가리킨다) */
const FULL_TABLES = ['emoticon_images', 'emoticons', 'emoticon_packs', 'emoticon_pack_revisions', 'emoticon_revision_items', 'point_shop_items', 'titles'];
/** 서로 가리키는 표에서 나중에 채울 칸(차례를 정할 때 이 FK 는 보지 않는다) — 사업자 신원 → 등록증(등록증이 신원을 가리키고 「붙임」 CHECK 가 그 칸을 요구한다) */
const LATE = ['business_profiles.certificate_id'];
/** 담지 않는 표(이름 그대로 또는 앞부분) */
const EXCLUDE = [
    // 로고 묶음·보이기 규칙(shared V249) — 기본 제공 묶음은 마이그레이션이 넣고, 평소 로고·규칙은 site_settings 처럼 설치한 곳의 값이다
    'site_logo_bundles', 'site_logo_rules',
    'installation', 'site_settings', 'mail_settings', 'mail_setting_history', 'social_provider_settings', 'social_provider_setting_history', 'portone_settings',
    'public_data_settings', 'address_search_settings', 'site_operator', 'site_operator_history', 'stats_collection_settings', 'stats_collection_setting_history',
    'commerce_sales_settings', 'admin_security_settings', 'operators', 'operator_roles', 'permissions', 'audit_logs', 'auth_tokens',
    'login_attempts', 'idempotency_requests', 'guest_password_attempts', 'identity_verification_attempts', 'demo_pack_baseline', 'member_view_settings',
    // 하루 판정 표 — 방문자 해시만 담고 날짜 조각(파티션)이 있어 옛 날짜 줄은 새 설치에 들어갈 조각이 없다(site-analytics-contract 3단계 결정 ⑤·⑥)
    'deal_click_daily', 'ad_view_marks',
    // 구조 스냅숏이 넣는 표(scripts/export-install-preset.mjs 의 TABLES)
    'grades', 'boards', 'board_prefixes', 'group_categories', 'group_category_boards', 'menu_sets', 'menu_nodes', 'gadget_pages', 'gadget_page_layouts', 'home_gadgets',
    'home_layouts', 'board_layouts', 'site_theme_menus', 'board_defaults', 'board_report_settings', 'board_security_settings', 'board_notification_settings',
    'board_privacy_settings', 'popular_settings', 'point_settings', 'point_shop_settings', 'deal_settings', 'ad_placement_settings', 'group_settings', 'grade_settings',
    'member_event_settings', 'nickname_image_settings', 'emoticon_settings', 'group_buy_settings',
];
const EXCLUDE_PREFIX = ['flyway_schema_history', 'spring_session', 'admin_spring_session', 'batch_', 'worker_heartbeats', 'analytics_', 'operator_mfa', 'operator_trusted',
    'operator_preferences', 'operator_dashboards', 'operator_notification', 'operator_invitation', 'operator_cert', 'admin_', 'notification_send_', 'mail_outbox',
    // AI 기자 뉴스 출처·수집 항목(shared V228) — 출처는 V229 가 설치 시각 ID 로 넣고(팩에 담으면 같은 출처가 겹친다), 항목은 빌드 관리자 서버의 수집 배치가 받은 7일짜리 바깥 기사다
    'ai_news_'];
/**
 * 일부 행만 담는 표 — 운영자 역할(seed/demo/patches/0005-operator-roles.sql)만. 회원 역할(등급·종류·게시판 — 앱·마이그레이션이 만든다)과
 * 데모 로더 전용 역할 ROLE_DEMO_SEED(로더가 도는 동안만 쓰는 데모 운영자 몫)는 담지 않는다. 역할을 운영자에 붙인 줄(operator_roles)도 담지 않는다.
 */
const DEMO_LOADER_ROLE = 'ROLE_DEMO_SEED';
const ROW_FILTER = {
    roles: `axis = 'OPERATOR' AND role_id <> '${DEMO_LOADER_ROLE}'`,
    role_permissions: `role_id IN (SELECT role_id FROM roles WHERE axis = 'OPERATOR' AND role_id <> '${DEMO_LOADER_ROLE}')`,
};
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

if (DB_NAME === 'irobit') {
    console.error('BUILD_DB_NAME 이 사용자의 로컬 DB(irobit)다 — 빌드 DB 를 따로 만든다(예: irobit_demo_build). 아무것도 하지 않았다.');
    process.exit(2);
}
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(API)) {
    console.error(`빌드 서버는 localhost 만: ${API}`);
    process.exit(2);
}

const command = process.argv[2];
if (command === 'install') await install();
else if (command === 'export') exportPack();
else if (command === 'refresh') await refresh();
else {
    console.error('쓰는 법: node scripts/build-demo-pack.mjs refresh | install | export');
    process.exit(2);
}

/** 기준점 표 — 빌드 DB 에만 둔다(팩에 담지 않음). built_at = 내보낼 팩의 「만든 시각」(갱신 때만), patches = 이 팩에 적용한 패치(갱신 때만) */
function ensureBaselineTable() {
    sql(`CREATE TABLE IF NOT EXISTS demo_pack_baseline (xid bigint NOT NULL, at timestamptz NOT NULL DEFAULT now());
         ALTER TABLE demo_pack_baseline ADD COLUMN IF NOT EXISTS built_at timestamptz, ADD COLUMN IF NOT EXISTS patches jsonb`);
}

function requireStorage() {
    if (!STORAGE || !existsSync(STORAGE)) {
        console.error('BUILD_STORAGE_PATH(빌드 서버의 저장소 폴더)가 없다.');
        process.exit(2);
    }
}

/** 설치 마법사와 같은 API 로 빌드 서버를 설치한다(구성 IROBIT). demoData 면 서버가 jar 에 든 데모 팩을 복원한다(DemoDataInstaller) */
async function installSite(demoData) {
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
        site: { name: 'IROBIT', publicUrl: 'http://localhost:3100', tagline: null },
        mail: null, social: null, boardPreset: PRESET, demoData,
    });
    if (done.status !== 201) throw new Error(`설치 실패 ${done.status} ${JSON.stringify(done.body?.error)}`);
    return call;
}

// ── ① 설치(구조만) + 기준점 — 처음부터 만들 때 ──
async function install() {
    await installSite(false);
    ensureBaselineTable();
    sql(`DELETE FROM demo_pack_baseline; INSERT INTO demo_pack_baseline (xid) VALUES (txid_current())`);
    const [boards, nodes] = sql(`SELECT (SELECT count(*) FROM boards) || '|' || (SELECT count(*) FROM menu_nodes)`).split('|');
    console.log(`설치함(구조만, ${PRESET}) — 게시판 ${boards} · 메뉴 노드 ${nodes}. 기준점을 적었다. 이제 seed-demo.mjs 를 이 서버·DB 로 돌린다(seed-local.mjs 는 돌리지 않는다 — 시험 계정은 팩에 넣지 않는다).`);
}

// ── 갱신(기본) — 지난 팩 복원 → 패치 → 내보내기 ──
/**
 * 지난 팩(seed/demo-pack/)을 빌드 DB 에 그대로 되살리고, 바뀐 부분만 패치(seed/demo/patches/)로 고친 뒤 내보낸다 — seed-demo 를 처음부터 돌리지 않는다.
 *  ① 설치 마법사 「데모 포함」과 같은 경로로 설치 — 구조는 jar 의 프리셋, 데모는 jar 의 팩을 서버의 DemoDataInstaller 가 복원(시각·ID 를 「지금 − 1분」으로 옮김)
 *  ② 기준점 = 구조 설치 트랜잭션 바로 뒤(루트 운영자 행의 xmin + 1) — 복원한 데모 행·패치가 고친 행만 내보낸다(처음부터 만들 때의 「구조만 설치 뒤」와 같은 자리)
 *  ③ 팩의 「만든 시각」 = 지난 팩의 만든 시각 + 복원이 옮긴 시간(첫 데모 회원 ID 의 차이로 잰다). 내보낸 시각(지금)을 쓰면 갱신할 때마다
 *     「복원 여유 1분 + 분 내림 + 갱신에 걸린 시간」만큼 데이터가 과거로 밀린다 — 이렇게 하면 몇 번을 갱신해도 데이터와 기준 시각의 간격이 그대로다(ID 도 같은 차이로 옮겨 시각과 맞는다)
 *  ④ 패치 → ⑤ export
 */
async function refresh() {
    requireStorage();
    const started = Date.now();
    const oldManifest = JSON.parse(readFileSync(join(OUT, 'manifest.json'), 'utf8'));
    const oldData = JSON.parse(gunzipSync(readFileSync(join(OUT, oldManifest.data))).toString('utf8'));
    const oldUser = oldData.tables.find((t) => t.table === 'users')?.rows?.find((r) => r.email);
    if (!oldUser) throw new Error('지난 팩에 회원이 없다 — 갱신할 수 없다(처음부터 만든다: install → seed-demo → export)');

    const requestedAt = Date.now();
    const call = await installSite(true);
    process.stdout.write('설치함(데모 포함) — 서버가 데모 팩을 복원하는 중');
    for (const deadline = Date.now() + 15 * 60_000; ;) {
        const demo = (await call('GET', '/api/system/installation/demo')).body?.data?.demo;
        if (demo === 'APPLIED') break;
        if (demo === 'FAILED' || demo === 'NONE') {
            throw new Error(`\n데모 복원 실패(${demo}) — 빌드 api 서버 로그를 본다. 흔한 원인: 팩 뒤 마이그레이션이 팩의 칸을 지움·이름 바꿈(그때는 처음부터 만든다), 빌드 서버에 IROBIT_DATA_KEY 없음`);
        }
        if (Date.now() > deadline) throw new Error('\n데모 복원을 15분 기다렸는데 끝나지 않았다');
        process.stdout.write('.');
        await new Promise((r) => setTimeout(r, 3000));
    }
    const appliedAt = Date.now();
    console.log(` 끝(${((appliedAt - started) / 1000).toFixed(0)}초)`);

    // ② 기준점 — 복원 트랜잭션의 번호(복원한 회원·글·댓글 행의 가장 작은 xmin). 구조 설치(루트 운영자 + 구조 스냅숏 — 스냅숏은 저장점 안이라 하위 트랜잭션 번호가 따로다)와
    //    브랜드 그림은 그 앞이라 담기지 않는다. 데이터 표는 설치가 쓰지 않으므로 이 표들의 첫 행은 복원 트랜잭션이 쓴 것이다
    const [rootXmin, restoreXmin] = sql(`SELECT (SELECT xmin::text FROM operators ORDER BY id LIMIT 1) || '|' ||
        (SELECT min(x) FROM (SELECT min(xmin::text::bigint) x FROM users UNION ALL SELECT min(xmin::text::bigint) FROM posts UNION ALL SELECT min(xmin::text::bigint) FROM comments) m)`).split('|');
    if (!restoreXmin || BigInt(restoreXmin) <= BigInt(rootXmin)) throw new Error(`복원한 행의 트랜잭션(${restoreXmin})이 설치(${rootXmin}) 뒤가 아니다 — 빈 DB 로 다시 한다`);
    const xid = BigInt(restoreXmin) % 4294967296n;

    // ③ 복원이 옮긴 시간 — 첫 데모 회원의 지난 팩 ID → 지금 ID
    const newId = sql(`SELECT id::text FROM users WHERE email = ${lit(oldUser.email)}`);
    if (!newId) throw new Error(`복원한 DB 에 ${oldUser.email} 이 없다 — 빌드 api jar 에 든 팩이 저장소 팩(seed/demo-pack/)과 다르다. jar 를 다시 만든다`);
    const diff = BigInt(newId) - BigInt(oldUser.id);
    const deltaMs = diff >> 22n;
    if (diff < 0n || (diff & ((1n << 22n) - 1n)) !== 0n || deltaMs % 60_000n !== 0n) {
        throw new Error(`회원 ID 차이(${diff})가 복원 규칙(분 단위 시각 차이 << 22)과 맞지 않는다 — jar 의 팩이 저장소 팩과 다르다. jar 를 다시 만든다`);
    }
    // 서버가 저장소 팩과 같은 팩을 복원했다면 옮긴 시간 = 「복원한 때 − 팩 만든 시각 − 1분」의 분 내림(DemoDataInstaller) — 요청~끝 사이의 어느 때로도 맞지 않으면
    // jar 에 든 팩이 다른 것(예: 지난 갱신 전에 만든 jar)이다. 그대로 두면 기준 시각이 그만큼 틀린다
    const expected = (t) => Math.floor(Math.max(0, t - Date.parse(oldManifest.builtAt) - 60_000) / 60_000) * 60_000;
    if (Number(deltaMs) < expected(requestedAt) || Number(deltaMs) > expected(appliedAt)) {
        throw new Error(`복원이 옮긴 시간(${Number(deltaMs) / 60_000}분)이 저장소 팩의 만든 시각(${oldManifest.builtAt})으로 셈한 값(${expected(requestedAt) / 60_000}~${expected(appliedAt) / 60_000}분)과 다르다 — 빌드 api jar 에 든 팩이 저장소 팩(seed/demo-pack/)과 다르다. 팩을 바꾼 뒤 jar 를 다시 만든다(./gradlew :server:api:bootJar)`);
    }
    const builtAt = new Date(Date.parse(oldManifest.builtAt) + Number(deltaMs)).toISOString();
    // 복원한 행 수 = 저장소 팩의 표별 행 수인가(한 번에 센다) — 다르면 jar 에 든 팩이 저장소 팩과 다르다(팩을 바꾼 뒤 jar 를 다시 만들지 않음)
    const restored = json(`SELECT json_object_agg(t, n) FROM (VALUES ${Object.keys(oldManifest.counts)
        .map((t) => `(${lit(t)}, (SELECT count(*) FROM ${ident(t)} WHERE ${FULL_TABLES.includes(t) ? 'true' : `(xmin::text::bigint) >= ${xid}`}${ROW_FILTER[t] ? ` AND ${ROW_FILTER[t]}` : ''}))`).join(', ')}) v(t, n)`);
    const mismatch = Object.entries(oldManifest.counts).filter(([t, n]) => restored[t] !== n);
    if (mismatch.length) {
        throw new Error(`복원한 행 수가 저장소 팩과 다르다 — ${mismatch.slice(0, 8).map(([t, n]) => `${t} ${restored[t]}≠${n}`).join(', ')}. 빌드 api jar 에 든 팩이 저장소 팩(seed/demo-pack/)과 다르다 — 팩을 바꾼 뒤 jar 를 다시 만든다`);
    }
    ensureBaselineTable();
    sql(`DELETE FROM demo_pack_baseline;
         INSERT INTO demo_pack_baseline (xid, built_at, patches) VALUES (${xid}, ${lit(builtAt)}, ${lit(JSON.stringify(oldManifest.patches ?? []))}::jsonb)`);
    console.log(`복원 — 시각 ${(Number(deltaMs) / 3_600_000).toFixed(1)}시간 옮김, 팩의 기준 시각 ${oldManifest.builtAt} → ${builtAt}`);

    // ④ 패치
    runPatches(builtAt);
    // ⑤ 내보내기
    exportPack();
    console.log(`갱신 끝 — ${((Date.now() - started) / 1000).toFixed(0)}초`);
}

// ── 패치(seed/demo/patches/) ──

function listPatches() {
    if (!existsSync(PATCH_DIR)) return [];
    const names = readdirSync(PATCH_DIR).filter((n) => !n.startsWith('.'));
    const bad = names.filter((n) => !PATCH_NAME.test(n));
    if (bad.length) throw new Error(`패치 이름이 규칙(0001-이름.sql | .mjs)과 다르다: ${bad.join(', ')}`);
    return names.sort();
}

/**
 * 아직 이 팩에 적용하지 않은 패치를 번호 차례로 돌린다 — 적용한 이름은 기준점 표에 적고(export 가 manifest.patches 로 옮긴다) 다음 갱신에서는 건너뛴다.
 *  .sql  psql 한 트랜잭션(-1)으로. 변수 :'demo_now' = 팩의 기준 시각(새 행의 시각은 now() 대신 이것으로), :demo_id_base = 그 시각의 Snowflake ID(빌드 서버 번호 1001, 순번 0 — 새 행 ID 는 여기에 1, 2, … 를 더한다)
 *  .mjs  node 로. 환경 변수 BUILD_API · BUILD_DB_CONTAINER · BUILD_DB_NAME · BUILD_DB_USER · DEMO_NOW · DEMO_ID_BASE
 */
function runPatches(demoNow) {
    const applied = new Set(json(`SELECT coalesce(patches, '[]'::jsonb) FROM demo_pack_baseline`) ?? []);
    const pending = listPatches().filter((n) => !applied.has(n));
    if (!pending.length) {
        console.log('패치 — 새로 적용할 것 없음');
        return;
    }
    const idBase = ((BigInt(Date.parse(demoNow)) - SNOWFLAKE_EPOCH_MS) << 22n) | (1001n << 12n);
    for (const name of pending) {
        const path = join(PATCH_DIR, name);
        console.log(`패치 ${name}`);
        if (name.endsWith('.sql')) {
            try {
                execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-1', '-v', 'ON_ERROR_STOP=1', '-v', `demo_now=${demoNow}`, '-v', `demo_id_base=${idBase}`,
                    '-U', DB_USER, '-d', DB_NAME, '-f', '-'], { input: readFileSync(path, 'utf8'), stdio: ['pipe', 'inherit', 'pipe'], encoding: 'utf8' });
            } catch (e) {
                throw new Error(`패치 ${name} 실패(이 패치는 되돌려졌다): ${String(e.stderr ?? e.message).trim().slice(0, 2000)}`);
            }
        } else {
            execFileSync(process.execPath, [path], {
                stdio: 'inherit',
                env: { ...process.env, BUILD_API: API, BUILD_DB_CONTAINER: DB_CONTAINER, BUILD_DB_NAME: DB_NAME, BUILD_DB_USER: DB_USER, DEMO_NOW: demoNow, DEMO_ID_BASE: String(idBase) },
            });
        }
        applied.add(name);
        sql(`UPDATE demo_pack_baseline SET patches = ${lit(JSON.stringify([...applied]))}::jsonb`);
    }
}

// ── ② 내보내기 ──
function exportPack() {
    requireStorage();
    const testAccounts = sql(`SELECT count(*) FROM users WHERE email NOT LIKE 'demo%@example.com'`);
    if (testAccounts !== '0') {
        console.error(`데모 회원(demo…@example.com)이 아닌 회원이 ${testAccounts}명 있다 — 시험 계정(seed-local.mjs)은 팩에 넣지 않는다. 빌드 DB 를 새로 만들어 seed-local 없이 다시 한다.`);
        process.exit(1);
    }
    const seededGroupBuys = sql(`SELECT count(*) FROM group_buy_products WHERE name LIKE '%local 시드%' OR summary LIKE '%local 시드%'`);
    if (seededGroupBuys !== '0') {
        console.error('local 공구 시더의 상품이 있다 — 빌드 관리자 서버를 --irobit.groupbuy.local-seed=false 로 띄워 다시 한다.');
        process.exit(1);
    }
    ensureBaselineTable();
    const baseline = json(`SELECT json_build_object('xid', xid::text, 'builtAt', built_at, 'patches', patches) FROM demo_pack_baseline`);
    if (!baseline) throw new Error('기준점이 없다 — 먼저 refresh 또는 install 을 돌린다');
    const xid32 = BigInt(baseline.xid) % 4294967296n;
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
        const where = `${full ? 'true' : fresh}${ROW_FILTER[t.name] ? ` AND ${ROW_FILTER[t.name]}` : ''}`;
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

    // 올린 파일 — 빌드 저장소 전체(빈 폴더로 띄운 빌드 서버라 모두 데모 로더·복원이 올린 것). 사이트 아이콘·미리보기·로고는 빼다 — 설치 구성의 몫(SiteBrandInstaller 가
    // 설치마다 새 버전 키로 넣고, 그것을 가리키는 site_settings 는 팩에 담지 않는다). 담으면 갱신할 때마다 한 벌씩 늘어난다
    const files = [];
    const walk = (dir) => {
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
            const path = join(dir, entry.name);
            if (entry.isDirectory()) walk(path);
            else files.push({ key: relative(STORAGE, path).split(sep).join('/'), size: statSync(path).size, path });
        }
    };
    const SITE_FILE = /^site-(icon|og|logo)\//;
    walk(STORAGE);
    const siteFiles = files.filter((f) => SITE_FILE.test(f.key)).length;
    if (siteFiles) files.splice(0, files.length, ...files.filter((f) => !SITE_FILE.test(f.key)));

    const operatorIds = sql(`SELECT id::text FROM operators ORDER BY id`).split('\n').filter(Boolean);
    const sharedVersion = sql(`SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1`);
    // 갱신(refresh)이면 지난 팩의 기준 시각 + 복원이 옮긴 시간(refresh ③), 처음부터 만들었으면 지금
    const builtAt = baseline.builtAt ? new Date(baseline.builtAt).toISOString() : new Date().toISOString();
    // 적용한 패치 — 갱신이면 기준점 표의 목록, 처음부터 만들었으면 폴더의 패치 전부(그 내용은 원고에 들어가 있어야 한다)
    const patches = baseline.patches ?? listPatches();
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
        patches, counts, files: files.map(({ key, size }) => ({ key, size })),
    };
    writeFileSync(join(OUT, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

    const fileBytes = files.reduce((s, f) => s + f.size, 0);
    console.log(`데모 팩 → ${OUT}`);
    console.log(`  표 ${out.length}개 · 행 ${Object.values(counts).reduce((a, b) => a + b, 0)}개 · data.json.gz ${(gz.length / 1024).toFixed(0)}KB · 파일 ${files.length}개 ${(fileBytes / 1048576).toFixed(1)}MB`);
    for (const [k, v] of Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 25)) console.log(`    ${k.padEnd(34)} ${v}`);
    if (changedStructure.length) console.warn(`⚠ 데모 로더가 구조 표를 바꿨다(팩에 담지 않음 — 구조 스냅숏을 다시 내보내야 하는지 본다): ${changedStructure.join(', ')}`);
    if (cipherCols.size) console.log(`  암호문 칸(설치 때 그곳의 키로 다시 잠근다): ${[...cipherCols].join(', ')}`);
    if (hash) console.log('  회원 비밀번호는 아무도 모르는 값의 해시로 바꿨다(--keep-passwords 면 그대로).');
    if (siteFiles) console.log(`  사이트 아이콘·미리보기·로고 파일 ${siteFiles}개는 담지 않았다(설치 구성의 몫)`);
    console.log(`  기준 시각 ${builtAt}${baseline.builtAt ? '(갱신 — 지난 팩 기준 시각 + 복원 이동)' : ''} · 적용한 패치 ${patches.length}개${!baseline.patches && patches.length ? '(처음부터 만든 팩 — 폴더의 패치를 모두 원고에 반영된 것으로 적었다)' : ''}`);
}
