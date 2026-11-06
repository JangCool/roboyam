#!/usr/bin/env node
/**
 * 로컬(또는 dev) DB 를 「실제 서비스 중인 커뮤니티」처럼 보이게 데모 데이터를 채운다 — 데이터는 seed/demo/, 설명은 docs/seed-demo.md.
 *
 *   node scripts/seed-demo.mjs            # 회원·운영자·설정은 늘 맞추고, 콘텐츠 단계는 아직 없을 때만 넣는다(단계마다 따로 판단)
 *   node scripts/seed-demo.mjs --force    # 글·댓글 단계를 이미 있어도 한 벌 더 넣는다(중복이 생긴다)
 *   node scripts/seed-demo.mjs --limit=1 --only=boards   # 시험: 원고마다 앞 1개만, 게시판 단계만
 * 같은 게시판에 같은 제목의 데모 회원 글이 이미 있으면 그 글(과 댓글)은 건너뛴다 — 중간에 멈췄으면 다시 돌려 이어서 넣는다.
 *
 * 순서: 회원 40명(SQL) → 데모 운영자(SQL, 이 실행 동안만 켬) → 게시판 기본 글 에디터 고급·소모임 공개 범위 켬(관리자 API) → 로보티콘 무료 팩 받기
 *      → 게시판 글·댓글·답글(공개 API, 그 회원으로) → 추천·조회수 → 핫딜 종료·품절 → 소모임 → 사업자 핫딜(신청→승인→계약서→입금→확인)
 *      → 공동구매(관리자 API) → 하이라이트 5개 → 광고(운영자 등록→승인→계약서·입금→확정, 홈 광고 가젯 셋). 끝나면 데모 운영자를 끈다.
 * 글·댓글은 공개 API 로 쓰고(서버 규칙·카운터·포인트·알림이 그대로), 바로 뒤에 SQL 로 작성 시각을 지난 21일 안의 계획 시각으로 옮긴다
 * (작성 간격·하루 한도 판정이 created_at 을 보므로 이렇게 해야 막히지 않고, 목록 차례(id)와 시각 차례도 같게 계획 차례대로 쓴다). 조회수는 SQL 로 넣는다.
 *
 * 안전장치는 seed-local.mjs 와 같다(서버는 localhost 만, 서버와 DB 의 설치 상태가 같아야 한다). 비밀번호는 출력하지 않는다.
 * 환경 변수(전부 선택): SEED_API(기본 http://localhost:8910) · SEED_ADMIN_API(기본 http://localhost:8920) · SEED_DB_CONTAINER · SEED_DB_NAME · SEED_DB_USER
 */
import { execFileSync, spawn } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_API = 'http://localhost:8910';
const API = (process.env.SEED_API ?? DEFAULT_API).replace(/\/+$/, '');
const ADMIN_API = (process.env.SEED_ADMIN_API ?? 'http://localhost:8920').replace(/\/+$/, '');
const DB_CONTAINER = process.env.SEED_DB_CONTAINER ?? 'roboyam-local-postgres';
const DB_NAME = process.env.SEED_DB_NAME ?? 'roboyam';
const DB_USER = process.env.SEED_DB_USER ?? 'roboyam';
const FORCE = process.argv.includes('--force');
/** --limit=N: 원고마다 앞 N개만(시험용) · --only=boards,groups,biz,groupbuys,highlights,ads: 그 단계만(회원·운영자·설정은 늘 한다) */
const LIMIT = Number(process.argv.find((a) => a.startsWith('--limit='))?.slice(8)) || Infinity;
const ONLY = process.argv.find((a) => a.startsWith('--only='))?.slice(7).split(',') ?? null;
const step = (name) => !ONLY || ONLY.includes(name);

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEMO = join(ROOT, 'seed', 'demo');
const SEED_NODE_ID = 1022n; // seed-local(1023)·앱 서버와 겹치지 않는 Snowflake 서버 번호
const SNOWFLAKE_EPOCH_MS = 1_767_225_600_000n;
const TERMS_VERSION = 'v1';
const DEMO_ROLE = 'ROLE_DEMO_SEED';
const SPAN_DAYS = 21; // 글을 흩는 기간
const DAY = 86_400_000;

const json = (file) => JSON.parse(readFileSync(file, 'utf8'));
const membersDoc = json(join(DEMO, 'members.json'));
const PASSWORDS = [membersDoc.password, membersDoc.operator.password, json(join(ROOT, 'seed', 'seed.json')).password];
const scrub = (text) => PASSWORDS.reduce((t, p) => t.split(p).join('***'), String(text));

function fail(message) {
    console.error(message);
    process.exitCode = 2;
}

// ── 난수(같은 원고면 같은 결과 — 다시 넣어도 모양이 같다) ──
let rngState = 20261106;
function rand() {
    rngState = (rngState + 0x6d2b79f5) | 0;
    let t = Math.imul(rngState ^ (rngState >>> 15), 1 | rngState);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const between = (a, b) => a + Math.floor(rand() * (b - a + 1));
const pick = (list) => list[Math.floor(rand() * list.length)];
function sample(list, n) {
    const copy = [...list];
    const out = [];
    while (copy.length && out.length < n) out.push(copy.splice(Math.floor(rand() * copy.length), 1)[0]);
    return out;
}

// ── DB ──
function sql(script, { sensitive = false } = {}) {
    try {
        return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', '-v', 'ON_ERROR_STOP=1', '-U', DB_USER, '-d', DB_NAME, '-f', '-'],
            { input: script, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 }).trim();
    } catch (e) {
        const stderr = String(e.stderr ?? e.message ?? '');
        const detail = sensitive ? (stderr.match(/ERROR:\s*([^\n]*)/)?.[1] ?? '알 수 없는 오류') : stderr.trim();
        throw new Error(`SQL 실패(${DB_CONTAINER}/${DB_NAME}): ${scrub(detail)}`);
    }
}
const q = (s) => (s === null || s === undefined ? 'NULL' : `'${String(s).replace(/'/g, "''")}'`);
const rows = (out) => (out ? out.split('\n').filter(Boolean).map((line) => line.split('|')) : []);
const ts = (ms) => `${q(new Date(ms).toISOString())}::timestamptz`;

/** 작성 시각 옮기기처럼 자주 하는 짧은 SQL — psql 하나를 열어 두고 줄마다 보낸다(docker exec 를 수천 번 띄우지 않게) */
class Psql {
    constructor() {
        this.proc = spawn('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', '-U', DB_USER, '-d', DB_NAME], { stdio: ['pipe', 'pipe', 'pipe'] });
        this.proc.stdout.setEncoding('utf8');
        this.proc.stderr.setEncoding('utf8');
        this.buffer = '';
        this.errors = [];
        this.waiters = [];
        this.seq = 0;
        this.proc.stdout.on('data', (chunk) => {
            this.buffer += chunk;
            for (const w of [...this.waiters]) {
                if (this.buffer.includes(w.mark)) {
                    this.waiters.splice(this.waiters.indexOf(w), 1);
                    w.resolve();
                }
            }
        });
        this.proc.stderr.on('data', (chunk) => this.errors.push(chunk.trim()));
    }

    send(statement) {
        this.proc.stdin.write(statement.trim().replace(/;?$/, ';') + '\n');
    }

    /** 보낸 문장이 다 끝날 때까지 기다린다 */
    flush() {
        const mark = `__seed_mark_${++this.seq}__`;
        return new Promise((resolve) => {
            this.waiters.push({ mark, resolve });
            this.proc.stdin.write(`\\echo ${mark}\n`);
        }).then(() => {
            this.buffer = '';
        });
    }

    async close() {
        await this.flush();
        this.proc.stdin.end();
        if (this.errors.length) console.error(`  SQL 경고 ${this.errors.length}건 — 첫 줄: ${this.errors[0]}`);
    }
}

// ── HTTP (서버마다·회원마다 쿠키 항아리 하나 — CSRF 는 XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로) ──
class Session {
    constructor(label, base = API, mePath = '/api/auth/me') {
        this.label = label;
        this.base = base;
        this.mePath = mePath;
        this.jar = new Map();
        this.uploads = [];
    }

    #store(res) {
        for (const line of res.headers.getSetCookie?.() ?? []) {
            const [pair] = line.split(';');
            const eq = pair.indexOf('=');
            const name = pair.slice(0, eq).trim();
            const value = pair.slice(eq + 1).trim();
            if (/max-age=0/i.test(line) || value === '') this.jar.delete(name);
            else this.jar.set(name, value);
        }
    }

    /** @param body JSON 으로 보낼 값, 또는 FormData(multipart) */
    async request(method, path, body, retried = false) {
        if (method !== 'GET' && !this.jar.has('XSRF-TOKEN')) await this.request('GET', this.mePath);
        const headers = { Accept: 'application/json' };
        if (method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(this.jar.get('XSRF-TOKEN') ?? '');
        if (this.jar.size) headers.Cookie = [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
        const form = body instanceof FormData;
        if (body !== undefined && !form) headers['Content-Type'] = 'application/json';
        const res = await fetch(this.base + path, { method, headers, body: body === undefined ? undefined : form ? body : JSON.stringify(body) });
        this.#store(res);
        const text = await res.text();
        let parsed = null;
        try {
            parsed = text ? JSON.parse(text) : null;
        } catch {
            parsed = null;
        }
        const code = parsed?.error?.code ?? null;
        if (res.status === 403 && code === 'CSRF_INVALID' && !retried) {
            this.jar.delete('XSRF-TOKEN');
            await this.request('GET', this.mePath);
            return this.request(method, path, body, true);
        }
        return { status: res.status, data: parsed?.data ?? null, code, retryAfter: parsed?.error?.retryAfterSeconds ?? null, error: parsed?.error ?? null };
    }
}

const ok = (r) => r.status >= 200 && r.status < 300;
const why = (r) => `${r.status} ${r.code ?? ''} ${r.error?.fields ? JSON.stringify(r.error.fields) : r.error?.message ?? ''}`.trim();
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
function fileForm(path, field = 'file', extra = {}) {
    const fd = new FormData();
    const type = path.endsWith('.pdf') ? 'application/pdf' : 'image/jpeg';
    fd.append(field, new Blob([readFileSync(path)], { type }), path.split(/[\\/]/).pop());
    for (const [k, v] of Object.entries(extra)) fd.append(k, v);
    return fd;
}

let snowSeq = 0;
function snowflake() {
    snowSeq = (snowSeq + 1) % 4096;
    if (snowSeq === 0) execFileSync(process.execPath, ['-e', 'setTimeout(()=>{},2)']); // 같은 밀리초 안에서 번호가 돌면 1ms 넘긴다
    return ((BigInt(Date.now()) - SNOWFLAKE_EPOCH_MS) << 22n) | (SEED_NODE_ID << 12n) | BigInt(snowSeq);
}

async function pool(items, size, worker) {
    let i = 0;
    await Promise.all(Array.from({ length: size }, async () => {
        while (i < items.length) await worker(items[i++]);
    }));
}

const escapeHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const kstDate = (ms) => new Date(ms + 9 * 3_600_000).toISOString().slice(0, 10);
const stats = { posts: 0, images: 0, comments: 0, likes: 0, commentLikes: 0, failed: [] };
function failed(what, r) {
    stats.failed.push(`${what}: ${scrub(typeof r === 'string' ? r : why(r))}`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
async function main() {
    if (!/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(API) || !/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(ADMIN_API)) {
        return fail(`로컬 서버(localhost)에만 쓸 수 있다: ${API} · ${ADMIN_API}`);
    }
    if (API !== DEFAULT_API && !process.env.SEED_DB_NAME) {
        return fail(`SEED_API 를 기본과 다르게 줬는데 SEED_DB_NAME 이 없다(seed-local.mjs 와 같은 규칙).`);
    }
    const status = await fetch(`${API}/api/system/installation/status`).then((r) => r.json()).catch(() => null);
    if (typeof status?.data?.installed !== 'boolean') return fail(`공개 서버(${API})가 떠 있지 않다.`);
    const adminUp = await fetch(`${ADMIN_API}/api/admin/auth/me`).then((r) => r.ok).catch(() => false);
    if (!adminUp) return fail(`관리자 서버(${ADMIN_API})가 떠 있지 않다.`);
    const [[dbName, installed]] = rows(sql(`SELECT current_database(), coalesce((SELECT status = 'COMPLETED' FROM installation WHERE id = 1), false)`));
    if (dbName !== DB_NAME || (installed === 't') !== status.data.installed || installed !== 't') {
        return fail(`서버와 DB(${DB_NAME})의 설치 상태가 다르거나 설치 전이다 — 아무것도 바꾸지 않았다.`);
    }
    const [[logStatement, logMinDuration]] = rows(sql(`SELECT current_setting('log_statement'), current_setting('log_min_duration_statement')`));
    if (logStatement === 'all' || logMinDuration === '0') return fail('DB 가 모든 SQL 을 로그에 남기는 설정이라 멈춘다(비밀번호 해시를 DB 안에서 만든다).');
    if (!existsSync(join(DEMO, 'images'))) return fail('seed/demo/images 가 없다 — 먼저 scripts/seed-demo-images.ps1 로 그림을 만든다.');
    console.log(`대상: 공개 ${API} · 관리자 ${ADMIN_API} · DB ${DB_CONTAINER}/${DB_NAME}`);

    const members = await seedMembers();
    if (!members) return;
    const admin = await enableOperator();
    if (!admin) return;
    const psql = new Psql();
    try {
        await adminSettings(admin);
        const emoticons = await acquireEmoticons(members);
        const boards = new Map(((await new Session('anon').request('GET', '/api/boards')).data?.boards ?? []).map((b) => [b.slug, b]));
        if (step('boards')) await seedBoards(members, boards, emoticons, psql);
        if (step('groups')) await seedGroups(members, admin, emoticons, psql);
        if (step('biz')) await seedBusinessDeals(admin, boards);
        if (step('groupbuys')) await seedGroupBuys(admin);
        if (step('highlights')) await seedHighlights(admin);
        if (step('ads')) await seedAds(admin);
    } finally {
        await psql.close();
        sql(`UPDATE operators SET enabled = false, updated_at = now() WHERE email = ${q(membersDoc.operator.email)}`);
        console.log('\n데모 운영자를 껐다(다음 실행 때 다시 켠다).');
    }
    console.log(`\n요약: 글 ${stats.posts} · 이미지 ${stats.images} · 댓글 ${stats.comments} · 글 추천 ${stats.likes} · 댓글 추천 ${stats.commentLikes}`);
    if (stats.failed.length) {
        console.log(`못 한 것 ${stats.failed.length}건:`);
        for (const f of stats.failed.slice(0, 40)) console.log(`  - ${f}`);
        if (stats.failed.length > 40) console.log(`  … ${stats.failed.length - 40}건 더`);
    }
    console.log('인기글은 DB 배치(popular-rank, 5분마다)가 다음 차례에 채운다.');
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 1) 회원 — SQL(공개 가입 API 가 없다). 같은 이메일이 있으면 건너뛴다. 등급은 수동 지정, 성인 표시 회원은 본인인증 기록(FAKE)을 둔다.
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedMembers() {
    console.log('\n회원');
    const list = membersDoc.members.map((m, i) => ({ ...m, key: `demo${String(i + 1).padStart(2, '0')}`, email: `demo${String(i + 1).padStart(2, '0')}@example.com` }));
    const existing = new Set(rows(sql(`SELECT email FROM users WHERE email LIKE 'demo__@example.com'`)).map(([e]) => e));
    const fresh = list.filter((m) => !existing.has(m.email));
    if (fresh.length) {
        const values = fresh.map((m) => `(${snowflake()}, ${q(m.email)}, ${q(m.nickname)}, ${m.ageDays}, ${q(m.grade)})`);
        sql([
            'BEGIN;',
            'CREATE EXTENSION IF NOT EXISTS pgcrypto;',
            `SELECT crypt(${q(membersDoc.password)}, gen_salt('bf', 10)) AS h \\gset`,
            'ROLLBACK;',
            'BEGIN;',
            `WITH src (id, email, nickname, age_days, grade) AS (VALUES ${values.join(', ')}),`,
            '     ins AS (',
            '         INSERT INTO users (id, email, password_hash, role, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, created_at, updated_at)',
            `         SELECT s.id, s.email, :'h', 'USER', 'ACTIVE', t.at, ${q(TERMS_VERSION)}, t.at, t.at, t.at, t.at`,
            '           FROM src s CROSS JOIN LATERAL (SELECT now() - make_interval(days => s.age_days) AS at) t',
            '         ON CONFLICT (email) WHERE email IS NOT NULL DO NOTHING',
            '         RETURNING id, created_at)',
            'INSERT INTO profiles (user_id, nickname, grade_key, grade_manual, created_at, updated_at)',
            'SELECT i.id, s.nickname, s.grade, true, i.created_at, i.created_at FROM ins i JOIN src s ON s.id = i.id;',
            'COMMIT;',
        ].join('\n'), { sensitive: true });
    }
    // 성인 표시 회원 = 본인인증(FAKE, 1990년생) — 19금 글쓴이
    for (const m of list.filter((x) => x.adult)) {
        sql(`INSERT INTO identity_verifications (user_id, di_hash, birth_date, provider, provider_tx_id, verified_at)
             SELECT u.id, encode(sha256(('demo-di-' || u.id)::bytea), 'hex'), DATE '1990-03-01', 'FAKE', 'demo-' || u.id, now() - interval '30 days'
               FROM users u WHERE u.email = ${q(m.email)} ON CONFLICT DO NOTHING`);
    }
    const sessions = [];
    for (const m of list) {
        const s = new Session(m.key);
        const r = await s.request('POST', '/api/auth/login', { email: m.email, password: membersDoc.password });
        if (r.status !== 200 || r.data?.authenticated !== true) {
            console.error(`  ${m.email} 로그인 ✕ ${why(r)} — 회원 단계에서 멈춘다`);
            process.exitCode = 1;
            return null;
        }
        Object.assign(s, { userId: r.data.user.id, nickname: m.nickname, grade: m.grade, level: { SEEDLING: 1, REGULAR: 2, LOYAL: 3, VETERAN: 4 }[m.grade], adult: !!m.adult });
        sessions.push(s);
    }
    console.log(`  회원 ${list.length}명 — 새로 만듦 ${fresh.length} · 이미 있음 ${existing.size} · 로그인 ${sessions.length}/${list.length}`);
    return sessions;
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 2) 데모 운영자 — 루트는 하나뿐이라 데모 전용 역할(운영자 권한 전부)을 붙인 일반 운영자. 이 실행 동안만 켜고 끝나면 끈다.
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function enableOperator() {
    console.log('\n데모 운영자');
    const op = membersDoc.operator;
    const opId = snowflake();
    const base = snowflake();
    sql([
        'BEGIN;',
        'CREATE EXTENSION IF NOT EXISTS pgcrypto;',
        `SELECT crypt(${q(op.password)}, gen_salt('bf', 10)) AS h \\gset`,
        'ROLLBACK;',
        'BEGIN;',
        `INSERT INTO roles (role_id, role_type_id, axis, role_name, description, scope, grant_mode, builtin, created_at, updated_at)
         VALUES (${q(DEMO_ROLE)}, 'op_post', 'OPERATOR', '데모 데이터 넣기', 'scripts/seed-demo.mjs 전용 — 실행할 때만 켜는 데모 운영자', 'SITE', 'MANUAL', false, now(), now())
         ON CONFLICT (role_id) DO NOTHING;`,
        `INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
         SELECT ${base} + row_number() OVER (ORDER BY code), ${q(DEMO_ROLE)}, code, now() FROM permissions
          WHERE axis = 'OPERATOR' AND code NOT IN (SELECT permission_code FROM role_permissions WHERE role_id = ${q(DEMO_ROLE)});`,
        `INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved, locked, expired, enabled, created_at, updated_at)
         VALUES (${opId}, ${q(op.email)}, ${q(op.name)}, :'h', false, true, false, false, true, now(), now())
         ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, approved = true, locked = false, expired = false, enabled = true, updated_at = now();`,
        `INSERT INTO operator_roles (id, operator_id, role_id, assigned_at)
         SELECT ${opId} + 1, o.id, ${q(DEMO_ROLE)}, now() FROM operators o
          WHERE o.email = ${q(op.email)} AND NOT EXISTS (SELECT 1 FROM operator_roles r WHERE r.operator_id = o.id AND r.role_id = ${q(DEMO_ROLE)});`,
        'COMMIT;',
    ].join('\n'), { sensitive: true });
    const admin = new Session('admin', ADMIN_API, '/api/admin/auth/me');
    const r = await admin.request('POST', '/api/admin/auth/login', { email: op.email, password: op.password });
    if (!ok(r) || r.data?.authenticated !== true) {
        console.error(`  데모 운영자 로그인 ✕ ${why(r)} (2차 인증이 켜져 있으면 끄거나 운영자 계정으로 직접 처리한다)`);
        sql(`UPDATE operators SET enabled = false WHERE email = ${q(op.email)}`);
        process.exitCode = 1;
        return null;
    }
    console.log(`  ${op.email} 로그인 ○ (역할 ${DEMO_ROLE})`);
    return admin;
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 3) 설정 — 게시판 기본 글 에디터 고급(이미지·유튜브), 소모임 공개 범위 켬
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function adminSettings(admin) {
    console.log('\n설정');
    const d = await admin.request('GET', '/api/admin/boards/defaults');
    const defaults = d.data?.defaults;
    if (defaults && (defaults.postEditor !== 'ADVANCED' || defaults.imageUploadAllowed !== true)) {
        const r = await admin.request('PUT', '/api/admin/boards/defaults', { ...defaults, postEditor: 'ADVANCED', imageUploadAllowed: true });
        console.log(`  게시판 기본 글 에디터 → 고급 ${ok(r) ? '○' : `✕ ${why(r)}`}`);
        if (ok(r)) await sleep(16_000); // 공개 서버의 게시판 카탈로그 사본이 바뀌기까지(최대 15초)
    } else {
        console.log(`  게시판 기본 글 에디터 ${defaults ? '이미 고급' : `읽지 못함 ${why(d)}`}`);
    }
    const v = await admin.request('GET', '/api/admin/group-settings/visibility-rules');
    const cur = v.data?.values ?? v.data;
    if (cur && cur.visibilityEnabled !== true) {
        const r = await admin.request('PUT', '/api/admin/group-settings/visibility-rules', { ...cur, visibilityEnabled: true });
        console.log(`  소모임 공개 범위 → 켬 ${ok(r) ? '○' : `✕ ${why(r)}`}`);
    } else {
        console.log(`  소모임 공개 범위 ${cur ? '이미 켬' : `읽지 못함 ${why(v)}`}`);
    }
}

// 4) 로보티콘 — 무료 공개 팩을 회원마다 받는다(이미 받았으면 그대로). 댓글의 {emo} 를 그 팩의 토큰으로 바꾼다
async function acquireEmoticons(members) {
    const packs = rows(sql(`SELECT p.id FROM emoticon_packs p WHERE p.price = 0 AND p.status = 'APPROVED' ORDER BY p.id LIMIT 2`)).map(([id]) => id);
    const ids = packs.length ? rows(sql(`SELECT id FROM emoticons WHERE pack_id IN (${packs.join(',')}) ORDER BY id`)).map(([id]) => id) : [];
    await pool(members, 8, async (s) => {
        for (const p of packs) await s.request('POST', `/api/emoticons/packs/${p}/acquire`);
    });
    console.log(`\n로보티콘: 무료 팩 ${packs.length}개를 회원마다 받음 · 토큰 후보 ${ids.length}개`);
    return ids;
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 5) 게시판 글·댓글 — 계획(시각·글쓴이)을 먼저 다 세우고 시각 차례대로 쓴다
// ════════════════════════════════════════════════════════════════════════════════════════════════
const FILLERS = ['ㅋㅋㅋㅋ 이거 보고 한참 웃었네요', '그냥 공유하고 싶어서 올려 봐요', '다들 어떻게 생각하세요?', '사진으로 대신합니다 ㅎㅎ'];
const LIKES = { hot: [16, 34], normal: [3, 12], quiet: [0, 2] };
const VIEWS = { hot: [1200, 4800], normal: [150, 900], quiet: [30, 220] };

function plainLength(post) {
    return (post.paragraphs ?? []).join('').replace(/\s/g, '').length;
}

function postHtml(post, imageIds) {
    const paras = [...(post.paragraphs ?? [])].filter((p) => String(p).trim());
    if (plainLength(post) < 12) paras.push(pick(FILLERS));
    const blocks = paras.map((p) => `<p>${escapeHtml(p)}</p>`);
    const images = imageIds.map((id) => `<img data-image-id="${id}">`);
    const at = Math.min(Math.max(0, Number(post.imageAt) || 0), blocks.length);
    blocks.splice(at, 0, ...images);
    if (post.youtube) blocks.splice(Math.min(1, blocks.length), 0, `<div data-youtube-id="${post.youtube}"></div>`);
    return blocks.join('');
}

function commentHtml(text, youtube, emoticons) {
    let html = escapeHtml(text).replace(/\{emo\}/g, () => (emoticons.length ? `:emo-${pick(emoticons)}:` : ''));
    html = `<p>${html.trim() || 'ㅎㅎ'}</p>`;
    if (youtube) html += `<div data-youtube-id="${youtube}"></div>`;
    return html;
}

/** 글 하나의 댓글 계획 — 시각은 글 뒤(대부분 몇 시간 안), 답글은 그 댓글 뒤. 지금을 넘지 않는다 */
function planComments(post, postAt, pool, now, author) {
    const out = [];
    for (const c of post.comments ?? []) {
        const r = rand();
        const delay = r < 0.6 ? between(2, 180) * 60_000 : r < 0.9 ? between(3, 24) * 3_600_000 : between(1, 3) * DAY;
        const at = Math.min(postAt + delay, now - between(2, 30) * 60_000);
        const plan = { kind: 'comment', at, text: c.text, youtube: c.youtube ?? null, author: pick(pool.filter((m) => m !== author)), replies: [] };
        out.push(plan);
        let last = at;
        for (const reply of c.replies ?? []) {
            const rat = Math.min(last + between(3, 240) * 60_000, now - between(1, 10) * 60_000);
            last = Math.max(rat, last + 1000);
            // 답글의 3분의 1쯤은 글쓴이가 단다
            const ra = rand() < 0.35 ? author : pick(pool.filter((m) => m !== plan.author));
            out.push({ kind: 'comment', at: last, text: reply, youtube: null, author: ra, parent: plan });
        }
    }
    return out;
}

function writersFor(slug, members) {
    if (slug === 'hotdeal') return members.filter((m) => m.level >= 2);
    return members;
}

async function seedBoards(members, boards, emoticons, psql) {
    console.log('\n게시판 글·댓글');
    // 이미 넣은 글(같은 게시판·같은 제목의 데모 회원 글)은 건너뛴다
    const done = FORCE ? new Set() : new Set(rows(sql(`SELECT b.slug, p.title FROM posts p JOIN users u ON u.id = p.author_id JOIN boards b ON b.id = p.board_id
         WHERE u.email LIKE 'demo__@example.com' AND p.group_id IS NULL`)).map(([slug, ...title]) => `${slug}|${title.join('|')}`)); // 제목에 | 가 있어도 되게
    let skipped = 0;
    const now = Date.now();
    const plans = [];
    const files = readdirSync(join(DEMO, 'content')).filter((f) => f.endsWith('.json') && !['groups.json', 'biz-hotdeal.json'].includes(f));
    for (const f of files) {
        const doc = json(join(DEMO, 'content', f));
        const board = boards.get(doc.board);
        if (!board) {
            failed(`게시판 ${doc.board}`, '공개 게시판 목록에 없음');
            continue;
        }
        const writers = writersFor(doc.board, members);
        // 게시판마다 단골 몇 명이 글을 더 많이 쓴다
        const regulars = sample(writers, 6);
        const n = doc.posts.length;
        const times = doc.posts.map((_, i) => now - SPAN_DAYS * DAY + ((i + 0.3 + rand() * 0.5) / n) * (SPAN_DAYS * DAY - 40 * 60_000)).sort((a, b) => a - b);
        doc.posts.forEach((post, i) => {
            if (i >= LIMIT) return;
            if (done.has(`${doc.board}|${post.title.slice(0, 100)}`)) {
                skipped++;
                return;
            }
            const adult = post.adult === true;
            const pool = adult ? members.filter((m) => m.adult) : writers;
            const author = adult ? pick(pool) : rand() < 0.45 ? pick(regulars) : pick(pool);
            const at = times[i];
            const commenters = doc.board === 'hotdeal' ? members.filter((m) => m.level >= 2) : adult ? members.filter((m) => m.adult) : members;
            const plan = { kind: 'post', at, board, post, author, comments: [] };
            plan.comments = planComments(post, at, adult ? members.filter((m) => m.adult) : commenters, now, author).map((c) => Object.assign(c, { postPlan: plan }));
            plans.push(plan);
        });
    }
    if (skipped) console.log(`  이미 있는 글 ${skipped}개는 건너뜀`);
    await runTimeline(plans, emoticons, psql, (plan) => ({ path: '/api/posts', boardSlug: plan.board.slug }));
    await likesAndViews(plans, members, psql);
    await dealStates(plans);
}

/** 계획한 글·댓글을 시각 차례대로 쓰고 작성 시각을 옮긴다 */
async function runTimeline(plans, emoticons, psql, target) {
    const events = [...plans, ...plans.flatMap((p) => p.comments)].sort((a, b) => a.at - b.at);
    let done = 0;
    for (const ev of events) {
        if (ev.kind === 'post') await writePost(ev, psql, target(ev));
        else await writeComment(ev, emoticons, psql);
        if (++done % 250 === 0) {
            await psql.flush();
            console.log(`  … ${done}/${events.length}`);
        }
    }
    await psql.flush();
    console.log(`  글 ${plans.filter((p) => p.id).length}/${plans.length} · 댓글 ${events.length - plans.length}개 처리`);
}

async function uploadImage(session, file) {
    // 회원마다 1분 20장 — 넘기 전에 기다린다
    const cutoff = Date.now() - 60_000;
    session.uploads = session.uploads.filter((t) => t > cutoff);
    if (session.uploads.length >= 19) await sleep(session.uploads[0] + 61_000 - Date.now());
    for (let attempt = 0; attempt < 3; attempt++) {
        const r = await session.request('POST', '/api/images', fileForm(file));
        session.uploads.push(Date.now());
        if (ok(r)) {
            stats.images++;
            return r.data.id;
        }
        if (r.status === 429 && r.code === 'IMAGE_RATE_LIMITED') {
            await sleep((Number(r.retryAfter) || 30) * 1000 + 500);
            continue;
        }
        failed(`이미지 ${file.split(/[\\/]/).pop()}`, r);
        return null;
    }
    return null;
}

function imageFiles(post) {
    const specs = post.images ?? (post.image ? [post.image] : []);
    return specs.map((_, i) => join(DEMO, 'images', `${post.key}-${i + 1}.jpg`)).filter((f) => existsSync(f));
}

async function writePost(plan, psql, { path, boardSlug }) {
    const { post, author } = plan;
    const imageIds = [];
    for (const f of imageFiles(post)) {
        const id = await uploadImage(author, f);
        if (id) imageIds.push(id);
    }
    const prefix = post.prefix ? plan.board.prefixes?.find((p) => p.name === post.prefix) : null;
    const body = { boardSlug, prefixId: prefix?.id ?? null, title: post.title.slice(0, 100), contentFormat: 'HTML', content: postHtml(post, imageIds), adult: post.adult === true };
    if (post.deal) {
        body.deal = {
            storeName: post.deal.storeName, productUrl: post.deal.productUrl, normalPrice: post.deal.normalPrice ?? null, dealPrice: post.deal.dealPrice,
            shippingFee: post.deal.shippingFee ?? null, couponNote: post.deal.couponNote ?? null, stockState: 'IN_STOCK', affiliate: false,
        };
    }
    const r = await author.request('POST', path, body);
    if (!ok(r)) {
        failed(`글 ${post.key}(${author.nickname})`, r);
        return;
    }
    plan.id = r.data.id;
    plan.body = body;
    stats.posts++;
    psql.send(`UPDATE posts SET created_at = ${ts(plan.at)}, updated_at = ${ts(plan.at)} WHERE id = ${plan.id}`);
    psql.send(`UPDATE files SET created_at = ${ts(plan.at)}, used_at = ${ts(plan.at)} WHERE post_id = ${plan.id}`);
}

async function writeComment(c, emoticons, psql) {
    const postId = c.postPlan.id;
    if (!postId || (c.parent && !c.parent.id)) return;
    const body = { contentFormat: 'HTML', content: commentHtml(c.text, c.youtube, emoticons) };
    if (c.parent) Object.assign(body, { parentId: c.parent.id, replyToId: c.parent.id });
    const r = await c.author.request('POST', `/api/posts/${postId}/comments`, body);
    if (!ok(r)) {
        failed(`댓글 ${c.postPlan.post.key}(${c.author.nickname})`, r);
        return;
    }
    c.id = r.data.id;
    stats.comments++;
    psql.send(`UPDATE comments SET created_at = ${ts(c.at)}, updated_at = ${ts(c.at)} WHERE id = ${c.id}`);
}

async function likesAndViews(plans, members, psql) {
    const written = plans.filter((p) => p.id);
    const jobs = [];
    for (const p of written) {
        // 오늘(KST) 시각의 글은 반응을 보통 이상으로 — 인기글 「실시간」 탭이 오늘 글만 보기 때문(원고 끝쪽은 조용한 글이 많다)
        const today = Date.parse(`${kstDate(Date.now())}T00:00:00+09:00`);
        const raw = p.post.popularity in LIKES ? p.post.popularity : 'normal';
        const pop = p.at >= today && raw === 'quiet' ? 'normal' : raw;
        const voters = p.post.adult ? members.filter((m) => m.adult) : members;
        for (const m of sample(voters.filter((x) => x !== p.author), between(...LIKES[pop]))) jobs.push({ s: m, path: `/api/posts/${p.id}/like`, post: true });
        for (const c of p.comments.filter((x) => x.id)) {
            const n = rand() < 0.35 ? between(1, pop === 'hot' ? 9 : 4) : 0;
            for (const m of sample(voters.filter((x) => x !== c.author), n)) jobs.push({ s: m, path: `/api/comments/${c.id}/like` });
        }
        const [lo, hi] = VIEWS[pop];
        psql.send(`UPDATE posts SET view_count = greatest(view_count, ${between(lo, hi)}) WHERE id = ${p.id}`);
    }
    await pool(jobs, 8, async (j) => {
        const r = await j.s.request('POST', j.path, {});
        if (ok(r)) {
            if (j.post) stats.likes++;
            else stats.commentLikes++;
        }
    });
    await psql.flush();
    console.log(`  추천: 글 ${stats.likes} · 댓글 ${stats.commentLikes} · 조회수 넣음`);
}

/** 핫딜 — 원고의 state 대로 작성자 종료·품절 */
async function dealStates(plans) {
    let ended = 0;
    for (const p of plans.filter((x) => x.id && x.post.deal)) {
        const state = p.post.deal.state;
        if (state === 'ended') {
            const r = await p.author.request('POST', `/api/posts/${p.id}/deal/end`);
            ok(r) ? ended++ : failed(`핫딜 종료 ${p.post.key}`, r);
        } else if (state === 'soldout') {
            const r = await p.author.request('PUT', `/api/posts/${p.id}`, { ...p.body, deal: { ...p.body.deal, stockState: 'SOLD_OUT' } });
            ok(r) ? ended++ : failed(`핫딜 품절 ${p.post.key}`, r);
        }
    }
    console.log(`  핫딜 종료·품절 ${ended}건`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 6) 소모임 — 개설 신청 → 관리자 승인 → 가입 → 글·댓글(멤버만). 공개 소모임이라 비멤버에게도 글이 보인다
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedGroups(members, admin, emoticons, psql) {
    console.log('\n소모임');
    const doc = json(join(DEMO, 'content', 'groups.json'));
    const names = doc.groups.map((g) => g.name);
    // 글까지 있는 소모임은 건너뛰고, 만들어만 둔 소모임(앞 실행이 중간에 멈춤)은 이어서 채운다
    const found = new Map(rows(sql(`SELECT g.name, g.id, g.leader_id, (SELECT count(*) FROM posts p WHERE p.group_id = g.id) FROM groups g WHERE g.name IN (${names.map(q).join(',')}) AND g.deleted_at IS NULL`)).map(([n, id, leader, posts]) => [n, { id, leader, posts: Number(posts) }]));
    const have = new Set([...found].filter(([, v]) => v.posts > 0).map(([n]) => n));
    if (have.size) console.log(`  글까지 있는 소모임 ${have.size}개는 건너뜀`);
    const categories = new Map(rows(sql(`SELECT b.slug, c.category_id FROM group_category_boards c JOIN boards b ON b.id = c.board_id`)));
    const groupBoards = new Map(((await new Session('anon').request('GET', '/api/boards')).data?.boards ?? []).map((b) => [b.slug, b]));
    const now = Date.now();
    const owners = sample(members.filter((m) => m.level >= 2), doc.groups.length);
    const plans = [];
    for (const [gi, g] of doc.groups.entries()) {
        if ((have.has(g.name) && !FORCE) || gi >= LIMIT) continue;
        const resume = found.get(g.name);
        let owner = owners[gi];
        let id;
        let joined;
        if (resume && !FORCE) {
            owner = members.find((m) => String(m.userId) === resume.leader) ?? owner;
            id = resume.id;
            const ids = new Set(rows(sql(`SELECT user_id FROM group_members WHERE group_id = ${id} AND status = 'ACTIVE'`)).map(([u]) => u));
            joined = members.filter((m) => ids.has(String(m.userId)));
            if (!joined.includes(owner)) joined.unshift(owner);
        } else {
        const r = await owner.request('POST', '/api/groups', {
            name: g.name, description: g.description, categoryId: categories.get(g.board), boardSlug: g.board, joinType: g.joinType ?? 'OPEN', capacity: g.capacity ?? 50,
            offline: false, visibility: 'PUBLIC', agreeRules: true, agreeStock: g.board === 'stock-groups', agreeOffline: false,
        });
        if (!ok(r)) {
            failed(`소모임 ${g.name}`, r);
            continue;
        }
        id = r.data.id;
        if (r.data.status === 'PENDING') {
            const a = await admin.request('POST', `/api/admin/groups/${id}/approve`);
            if (!ok(a)) {
                failed(`소모임 승인 ${g.name}`, a);
                continue;
            }
        }
        joined = [owner];
        for (const m of sample(members.filter((x) => x !== owner), between(9, 18))) {
            const j = await m.request('POST', `/api/groups/${id}/join`, { message: '잘 부탁드려요!' });
            if (j.status === 202) {
                const ap = await owner.request('POST', `/api/groups/${id}/applications/${m.userId}/approve`);
                if (ok(ap)) joined.push(m);
            } else if (ok(j)) {
                joined.push(m);
            }
        }
        }
        const opened = now - between(17, 20) * DAY;
        sql(`UPDATE groups SET requested_at = ${ts(opened - 3_600_000)}, opened_at = ${ts(opened)}, reviewed_at = CASE WHEN reviewed_at IS NULL THEN NULL ELSE ${ts(opened)} END WHERE id = ${id};
             UPDATE group_members SET applied_at = CASE WHEN applied_at IS NULL THEN NULL ELSE ${ts(opened + DAY)} END,
                    joined_at = ${ts(opened)} + (random() * interval '3 days') WHERE group_id = ${id}`);
        // 소모임 게시판은 공개 게시판 목록에 없다 — 말머리는 표에서 읽는다
        const board = groupBoards.get(g.board) ?? { slug: g.board, prefixes: rows(sql(`SELECT p.id, p.name FROM board_prefixes p JOIN boards b ON b.id = p.board_id WHERE b.slug = ${q(g.board)} AND p.active AND NOT p.staff_only`)).map(([id, name]) => ({ id, name })) };
        const n = g.posts.length;
        g.posts.forEach((post, i) => {
            // 가입인사는 앞쪽에, 나머지는 지난 14일에 흩는다
            const at = now - 14 * DAY + ((i + 0.3 + rand() * 0.5) / n) * (14 * DAY - 60 * 60_000);
            const author = post.prefix === '가입인사' ? pick(joined.filter((m) => m !== owner)) ?? owner : rand() < 0.3 ? owner : pick(joined);
            const plan = { kind: 'post', at, board, post, author, groupId: id, comments: [] };
            plan.comments = planComments(post, at, joined, now, author).map((c) => Object.assign(c, { postPlan: plan }));
            plans.push(plan);
        });
        console.log(`  ${g.name} — 모임장 ${owner.nickname} · 멤버 ${joined.length}명`);
    }
    await runTimeline(plans, emoticons, psql, (plan) => ({ path: `/api/groups/${plan.groupId}/posts`, boardSlug: plan.board.slug }));
    await likesAndViews(plans, members, psql);
    sql(`UPDATE groups g SET last_activity_at = sub.at FROM (SELECT group_id, max(created_at) AS at FROM posts WHERE group_id IS NOT NULL GROUP BY group_id) sub
          WHERE sub.group_id = g.id AND g.name IN (${names.map(q).join(',')})`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 7) 사업자 핫딜 — 사업자(e2e.veteran1 = 파이언넷)가 신청 → 관리자 승인(수동 결제) → 계약서 → 입금 → 확정 → 게시 배치(1분마다)가 글을 올린다
// ════════════════════════════════════════════════════════════════════════════════════════════════
const PDF = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');

async function seedBusinessDeals(admin, boards) {
    console.log('\n사업자 핫딜');
    const doc = json(join(DEMO, 'content', 'biz-hotdeal.json'));
    const seedUsers = json(join(ROOT, 'seed', 'seed.json'));
    const bizUser = seedUsers.users.find((u) => u.key === 'e2e.veteran1');
    const have = new Set(rows(sql(`SELECT title FROM biz_applications WHERE title IN (${doc.deals.map((d) => q(d.title)).join(',')})`)).map(([t]) => t));
    const todo = doc.deals.filter((d) => !have.has(d.title)).slice(0, LIMIT);
    if (!todo.length) {
        console.log('  데모 사업자 핫딜이 이미 있어 건너뛴다.');
        return;
    }
    const biz = new Session('biz');
    const login = await biz.request('POST', '/api/auth/login', { email: bizUser.email, password: seedUsers.password });
    if (!ok(login)) return failed('사업자 로그인(e2e.veteran1 — seed-local.mjs 를 먼저 돌린다)', login);
    const hotdeal = boards.get('hotdeal');
    const adPolicy = rows(sql(`SELECT ad_policy_version FROM biz_applications WHERE ad_policy_version IS NOT NULL ORDER BY created_at DESC LIMIT 1`))[0]?.[0] ?? '2026-11-02';
    let published = 0;
    for (const d of todo) {
        let imageFileId = null;
        const img = imageFiles(d)[0];
        if (img) {
            const u = await biz.request('POST', '/api/business/ad-images', fileForm(img));
            if (ok(u)) imageFileId = u.data.fileId;
            else failed(`사업자 이미지 ${d.key}`, u);
        }
        const endsOn = kstDate(Date.now() + 14 * DAY);
        const a = await biz.request('POST', '/api/business/applications/deals', {
            title: d.title, paymentMethod: 'MANUAL', agreedAdPolicyVersion: adPolicy, agreedRefundPolicyVersion: '2026-11-02',
            deal: {
                linkUrl: d.productUrl, shopName: d.storeName, listPrice: d.normalPrice || null, dealPrice: d.dealPrice, shippingFee: d.shippingFee ?? null, endsOn,
                stock: 'IN_STOCK', body: (d.paragraphs ?? []).join('\n\n'), placement: 'NONE', boardId: hotdeal?.id, prefixId: null, imageFileId,
            },
        });
        if (!ok(a)) {
            failed(`사업자 신청 ${d.key}`, a);
            continue;
        }
        const id = a.data.id;
        const start = new Date(Math.ceil((Date.now() + 2 * 60_000) / 60_000) * 60_000);
        const end = new Date(Date.parse(`${endsOn}T15:00:00Z`)); // KST 다음 날 0시
        const steps = [
            ['승인', () => admin.request('POST', `/api/admin/biz-applications/${id}/approve`, {
                method: 'MANUAL', supply: 30000, deadlineAt: new Date(Date.now() + 3 * DAY).toISOString(), period: { startAt: start.toISOString(), endAt: end.toISOString() },
                memo: '데모 — 수동 결제',
            })],
            ['계약서', () => {
                const fd = new FormData();
                fd.append('files', new Blob([PDF], { type: 'application/pdf' }), `${d.key}-contract.pdf`);
                return admin.request('POST', `/api/admin/biz-applications/${id}/contracts`, fd);
            }],
            ['입금', () => admin.request('PUT', `/api/admin/biz-applications/${id}/deposit`, { amount: 33000, depositedOn: kstDate(Date.now()), depositorName: d.storeName.slice(0, 60) })],
            ['확정', () => admin.request('POST', `/api/admin/biz-applications/${id}/confirm`)],
        ];
        let good = true;
        for (const [name, run] of steps) {
            const r = await run();
            if (!ok(r)) {
                failed(`사업자 ${name} ${d.key}`, r);
                good = false;
                break;
            }
        }
        if (!good) continue;
        // 확정 뒤 시작 시각을 지금으로 당긴다 — 게시 배치(biz-hotdeal-publish, 1분마다)가 곧 올린다
        sql(`UPDATE biz_applications SET start_at = date_trunc('minute', now()) WHERE id = ${id};
             UPDATE biz_application_items SET start_at = date_trunc('minute', now()) WHERE application_id = ${id}`);
        published++;
    }
    console.log(`  ${published}/${todo.length}건 확정 — 게시 배치가 1분 안에 핫딜 글로 올린다`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 8) 공동구매 — 상품(고시 전부 채움) → 공구 → 공개(모집 시작). 원고 state: open · upcoming · endedEarly
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedGroupBuys(admin) {
    console.log('\n공동구매');
    const doc = json(join(DEMO, 'groupbuys.json'));
    const have = new Set(rows(sql(`SELECT name FROM group_buy_products WHERE name IN (${doc.products.map((p) => q(p.name)).join(',')})`)).map(([n]) => n));
    const todo = doc.products.filter((p) => !have.has(p.name)).slice(0, LIMIT);
    if (!todo.length) {
        console.log('  데모 공구가 이미 있어 건너뛴다.');
        return;
    }
    const cats = new Map(((await admin.request('GET', '/api/admin/group-buys/categories')).data ?? []).map((c) => [c.name, c.id]));
    const catalog = json(join(ROOT, 'server', 'shared', 'src', 'main', 'resources', 'groupbuy', 'notice-catalog-2023-01-01.json'));
    const now = Date.now();
    let made = 0;
    for (const p of todo) {
        const images = [];
        for (const f of imageFiles(p)) {
            const u = await admin.request('POST', '/api/admin/group-buys/images', fileForm(f));
            if (ok(u)) images.push(u.data.mediaId);
            else failed(`공구 이미지 ${p.key}`, u);
        }
        const category = catalog.categories.find((c) => c.code === p.notice);
        const values = category.items.map((item) => ({
            custom: false, itemKey: item.key, unavailable: false, unavailableReason: null,
            value: item.kind === 'DATE_LIKE' ? '제조일·유통기한: 받으신 날로부터 7일 이상 남은 상품을 보냅니다(일반원칙 ④ 대체 표기)'
                : item.kind === 'KC_CERT' ? 'KC 인증번호 R-R-TST-ROBOYAM0001(데모 번호)'
                    : item.kind === 'QUALITY_WARRANTY' ? '소비자분쟁해결기준(공정거래위원회 고시)에 따릅니다'
                        : `${item.name}: 상세 설명 참고(데모 — ${p.name})`,
        }));
        const pr = await admin.request('POST', '/api/admin/group-buy-products', {
            categoryId: cats.get(p.category), name: p.name, summary: p.description[0], spec: `구성: ${p.skus.map((s) => s.name ?? s.values.join(' / ')).join(', ')}`,
            description: p.description.map((t) => `<p>${escapeHtml(t)}</p>`).join(''), normalPrice: p.normalPrice, normalPriceBasis: 'OFFICIAL_STORE', normalPriceSource: '데모 데이터',
            withdrawalRestriction: null, optionTypeCodes: p.options,
            skus: p.skus.map((s) => ({ name: s.name ?? s.values.join(' / '), values: s.values, defaultSupplyPrice: s.supply, onSale: true })),
            images, notices: [{ categoryCode: p.notice, title: p.name, skuNames: [], values }],
        });
        if (!ok(pr)) {
            failed(`공구 상품 ${p.key}`, pr);
            continue;
        }
        const g = p.groupBuy;
        const startAt = new Date(now + g.start * DAY - (g.start <= 0 ? 60_000 : 0));
        const endDays = g.state === 'endedEarly' ? Math.max(g.end, 4) : g.end;
        const endAt = new Date(now + endDays * DAY);
        startAt.setUTCSeconds(0, 0);
        endAt.setUTCSeconds(0, 0);
        const gr = await admin.request('POST', '/api/admin/group-buys', {
            productId: pr.data.id, title: g.title, groupPrice: g.price, shippingPolicyCode: 'STANDARD', minQty: g.min, maxQty: g.max, maxQtyPerUser: g.perUser,
            startAt: startAt.toISOString(), endAt: endAt.toISOString(), extensionMode: 'DEMAND', fulfillment: g.fulfillment,
            shipPlannedAt: g.fulfillment === 'AFTER_CLOSE' ? kstDate(endAt.getTime() + 7 * DAY) : null, shipWithinBusinessDays: g.fulfillment === 'IMMEDIATE' ? 3 : null,
            returnPolicy: null, roundLimitShared: false, skus: [], addons: [], marginConfirmed: true,
        });
        if (!ok(gr)) {
            failed(`공구 ${p.key}`, gr);
            continue;
        }
        const st = await admin.request('POST', `/api/admin/group-buys/${gr.data.id}/start`, { marginConfirmed: true });
        if (!ok(st)) {
            failed(`공구 공개 ${p.key}`, st);
            continue;
        }
        if (g.state === 'endedEarly') {
            const ce = await admin.request('POST', `/api/admin/group-buys/${gr.data.id}/close-early`, { reason: '데모 — 준비 수량이 다 나가 일찍 마감했어요' });
            if (!ok(ce)) failed(`공구 조기 마감 ${p.key}`, ce);
        }
        made++;
    }
    console.log(`  상품·공구 ${made}/${todo.length}개`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 9) 하이라이트 — 이미지가 있는 인기 글 다섯(19금 아님)
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedHighlights(admin) {
    console.log('\n하이라이트');
    const picks = rows(sql(`
        SELECT p.id, replace(p.title, '|', '/'), regexp_replace(left(coalesce(p.content_text, ''), 300), '[
|]+', ' ', 'g'), f.storage_key -- psql 출력의 줄·칸 구분자가 섞이지 않게
          FROM posts p JOIN users u ON u.id = p.author_id JOIN boards b ON b.id = p.board_id
          JOIN LATERAL (SELECT storage_key FROM files WHERE post_id = p.id AND status = 'IN_USE' ORDER BY id LIMIT 1) f ON true
         WHERE u.email LIKE 'demo__@example.com' AND p.group_id IS NULL AND NOT p.adult AND p.deleted_at IS NULL AND b.slug <> 'hotdeal'
           AND NOT EXISTS (SELECT 1 FROM highlights h WHERE h.post_id = p.id)
         ORDER BY p.like_count * 3 + p.comment_count * 2 DESC LIMIT 5`));
    const existing = Number(sql(`SELECT count(*) FROM highlights h JOIN posts p ON p.id = h.post_id JOIN users u ON u.id = p.author_id WHERE u.email LIKE 'demo__@example.com'`));
    if (existing >= 5 && !FORCE) {
        console.log(`  데모 하이라이트가 이미 ${existing}개 있어 건너뛴다.`);
        return;
    }
    let made = 0;
    for (const [postId, title, text] of picks) {
        const local = findLocalImage(title);
        let imageFileId = null;
        if (local) {
            const u = await admin.request('POST', '/api/admin/files', fileForm(local, 'file', { purpose: 'HIGHLIGHT_IMAGE' }));
            if (ok(u)) imageFileId = u.data.fileId;
            else failed('하이라이트 이미지', u);
        }
        const r = await admin.request('POST', '/api/admin/highlights', {
            postId, title: title.slice(0, 40), summary: text.replace(/\s+/g, ' ').slice(0, 90), imageFileId, visible: true,
        });
        ok(r) ? made++ : failed(`하이라이트 ${postId}`, r);
    }
    console.log(`  ${made}개 만듦`);
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 10) 광고 — 운영자 등록(심사 요청) → 승인(수동 결제 — 이때 소재가 승인된다) → 계약서 → 입금 → 확정 → 시작 시각을 지금으로(공개 사본 20초 뒤 나온다).
//     게시판 목록 한 줄·글 보기 사이는 자리 설정 기본값(한 줄 광고)이 그대로 쓰고, 홈에는 광고 가젯 셋(네모·한 줄·띠)을 놓는다
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedAds(admin) {
    console.log('\n광고');
    const doc = json(join(DEMO, 'ads.json'));
    const have = new Set(rows(sql(`SELECT title FROM biz_applications WHERE title IN (${doc.ads.map((a) => q(a.title)).join(',')}) AND status <> 'CANCELED'`)).map(([t]) => t));
    const advertiserIds = new Map();
    async function advertiserOf(ad) {
        const name = ad.business ?? doc.advertisers.find((a) => a.key === ad.advertiser)?.name;
        if (advertiserIds.has(name)) return advertiserIds.get(name);
        const found = (await admin.request('GET', `/api/admin/ads/advertisers?q=${encodeURIComponent(name)}`)).data ?? [];
        let id = (Array.isArray(found) ? found : found.items ?? []).find((o) => o.name === name)?.id ?? null;
        if (!id && !ad.business) {
            const a = doc.advertisers.find((x) => x.key === ad.advertiser);
            const r = await admin.request('POST', '/api/admin/ads/advertisers', { name: a.name, contactName: a.contactName, contactPhone: a.contactPhone, email: a.email, memo: '데모 광고주' });
            id = ok(r) ? r.data?.id : null;
            if (!id) failed(`광고주 ${a.name}`, r);
        }
        advertiserIds.set(name, id);
        return id;
    }
    let made = 0;
    for (const ad of doc.ads) {
        if (have.has(ad.title) && !FORCE) continue;
        const advertiserId = await advertiserOf(ad);
        if (!advertiserId) continue;
        const creative = { linkUrl: ad.linkUrl, altText: ad.altText };
        if (ad.type === 'TEXT_LINE') {
            creative.text = ad.text;
        } else {
            const up = await admin.request('POST', '/api/admin/ads/images', fileForm(join(DEMO, 'images', `ad-${ad.key}.png`)));
            if (!ok(up)) {
                failed(`광고 그림 ${ad.key}`, up);
                continue;
            }
            creative.imageFileId = up.data.id;
        }
        const start = new Date(Math.ceil((Date.now() + 3 * 60_000) / 60_000) * 60_000);
        const end = new Date(start.getTime() + 14 * DAY);
        const period = { startAt: start.toISOString(), endAt: end.toISOString() };
        const quote = (await admin.request('GET', `/api/admin/ads/availability?type=${ad.type}&startAt=${encodeURIComponent(period.startAt)}&endAt=${encodeURIComponent(period.endAt)}`)).data?.quote;
        const reg = await admin.request('POST', '/api/admin/ads/manual', { advertiserId, type: ad.type, period, weight: 5, creative, supply: quote?.supply ?? null, contractFileIds: [], memo: '데모 광고' });
        if (!ok(reg)) {
            failed(`광고 등록 ${ad.key}`, reg);
            continue;
        }
        const id = reg.data.id;
        sql(`UPDATE biz_applications SET title = ${q(ad.title)} WHERE id = ${id}`);
        const supply = Number(rows(sql(`SELECT coalesce(proposed_supply, quote_supply) FROM biz_applications WHERE id = ${id}`))[0]?.[0]) || quote?.supply;
        const payer = ad.business ?? doc.advertisers.find((a) => a.key === ad.advertiser).name;
        const steps = [
            ['승인', () => admin.request('POST', `/api/admin/biz-applications/${id}/approve`, {
                method: 'MANUAL', supply, deadlineAt: new Date(Date.now() + 3 * DAY).toISOString(), period, memo: '데모 데이터 — 운영자 등록 건을 같은 운영자가 승인',
            })],
            ['계약서', () => {
                const fd = new FormData();
                fd.append('files', new Blob([PDF], { type: 'application/pdf' }), `${ad.key}-contract.pdf`);
                return admin.request('POST', `/api/admin/biz-applications/${id}/contracts`, fd);
            }],
            ['입금', () => {
                const total = Number(rows(sql(`SELECT approved_total FROM biz_applications WHERE id = ${id}`))[0]?.[0]);
                return admin.request('PUT', `/api/admin/biz-applications/${id}/deposit`, { amount: total, depositedOn: kstDate(Date.now()), depositorName: payer });
            }],
            ['확정', () => admin.request('POST', `/api/admin/biz-applications/${id}/confirm`)],
        ];
        let good = true;
        for (const [name, run] of steps) {
            const r = await run();
            if (!ok(r)) {
                failed(`광고 ${name} ${ad.key}`, r);
                good = false;
                break;
            }
        }
        if (!good) continue;
        sql(`UPDATE biz_applications SET start_at = date_trunc('minute', now()) WHERE id = ${id};
             UPDATE biz_application_items SET start_at = date_trunc('minute', now()) WHERE application_id = ${id}`);
        made++;
    }
    console.log(`  광고 ${made}건 확정(시작 = 지금)`);
    if (doc.placements) {
        // 광고 자리 설정 — 원고의 칸만 덮어쓴다(같은 값이면 서버가 기록을 남기지 않는다)
        const cur = (await admin.request('GET', '/api/admin/ads/placements')).data;
        if (cur) {
            const body = { postInline: cur.postInline, wings: cur.wings, boardList: cur.boardList, adult: cur.adult };
            for (const [k, v] of Object.entries(doc.placements)) body[k] = { ...body[k], ...v };
            const r = await admin.request('PUT', '/api/admin/ads/placements', body);
            ok(r) ? console.log(`  광고 자리 설정: ${JSON.stringify(doc.placements)}`) : failed('광고 자리 설정', r);
        }
    }
    await placeAdGadgets(admin, '홈', '/api/admin/home-layouts/roboyam', doc.homeGadgets);
    await placePageAdGadgets(admin, doc.pageGadgets);
}

/**
 * 홈(roboyam 테마)·가젯 페이지에 광고 가젯을 놓는다(페이지마다 따로 — 사용자 결정 2026-11-06). 같은 제목의 광고 가젯이 이미 있으면 그 가젯만 건너뛴다.
 * 자리: `place.top` = 맨 위 전체 폭(모든 가젯을 그만큼 내림) · `place.afterFirst` = 첫 가젯 바로 아래 · 아니면 `place.after` 가젯 아래(폭은 그 가젯, 원고의 x·w 가 있으면 그것 — 아래 같은 열 가젯을 내림).
 */
async function placeAdGadgets(admin, label, layoutPath, specs) {
    if (!specs?.length) return;
    const cur = (await admin.request('GET', layoutPath)).data;
    if (!cur) return failed(`${label} 배치 읽기`, layoutPath);
    const titles = new Set(cur.gadgets.filter((g) => g.type === 'AD' || g.type === 'AD_TEXT_LINE').map((g) => g.title));
    const todo = specs.filter((spec) => !titles.has(spec.title));
    if (!todo.length) {
        console.log(`  ${label}: 광고 가젯이 이미 다 있어 배치는 건너뛴다`);
        return;
    }
    const items = cur.layout.items.map((i) => ({ ...i }));
    const typeOf = new Map(cur.gadgets.map((g) => [g.id, g.type]));
    const overlapsX = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w;
    // 서버의 종류별 최소 높이(HomeGadgetKinds.minSizes)에 못 미치는 칸은 저장이 거절된다 — 최소값으로 올리고 아래 같은 열 가젯을 그만큼 내린다
    const MIN_H = { HIGHLIGHT: 8, POST_LIST: 6, BOARD_CARDS: 8, GALLERY: 6, GROUPS: 6, GROUP_BUY: 8, HOT_DEALS: 8, QUICK_LINKS: 2, NOTICE_TEXT: 3, AD: 2, AD_TEXT_LINE: 2 };
    for (const it of [...items].sort((a, b) => a.y - b.y)) {
        const need = MIN_H[typeOf.get(it.gadgetId)] ?? 2;
        if (it.h >= need) continue;
        const grow = need - it.h;
        for (const o of items) if (o !== it && overlapsX(o, it) && o.y >= it.y + it.h) o.y += grow;
        it.h = need;
        console.log(`  ${label}: 가젯 ${it.gadgetId} 높이를 최소값 ${need}칸으로 올림(종류 ${typeOf.get(it.gadgetId)})`);
    }
    const added = [];
    todo.forEach((spec, n) => {
        let box;
        let y;
        if (spec.place.top) {
            box = { x: 0, w: cur.layout.columns };
            y = 0;
        } else {
            // afterFirst = 맨 위 첫 가젯(광고 아님) 바로 아래
            const firstId = [...items].filter((i) => !['AD', 'AD_TEXT_LINE'].includes(typeOf.get(i.gadgetId))).sort((m, k) => m.y - k.y || m.x - k.x)[0]?.gadgetId;
            const after = items.find((i) => i.gadgetId === (spec.place.afterFirst ? firstId : spec.place.after));
            if (!after) return failed(`${label} 광고 가젯 ${spec.key}`, '기준 가젯 없음');
            // 폭은 기준 가젯 폭, 원고에 x·w 가 있으면 그것(예: 나란한 두 가젯 아래를 가로지르는 띠 배너). 위 끝 = 그 폭에 걸친 기준 줄 가젯들의 아래 끝
            box = { x: spec.place.x ?? after.x, w: spec.place.w ?? after.w };
            y = Math.max(...items.filter((i) => overlapsX(i, box) && i.y <= after.y).map((i) => i.y + i.h));
        }
        for (const i of items) if (overlapsX(i, box) && i.y >= y) i.y += spec.place.h;
        const id = `new-${n + 1}`;
        items.push({ gadgetId: id, x: box.x, y, w: box.w, h: spec.place.h });
        added.push({ id, type: spec.type, title: spec.title, config: spec.config });
    });
    const body = {
        layout: { columns: cur.layout.columns, items },
        gadgets: [...cur.gadgets.map((g) => ({ id: g.id, type: g.type, title: g.title, desktop: g.desktop, mobile: g.mobile })), ...added],
    };
    const r = await admin.request('PUT', layoutPath, body);
    ok(r) ? console.log(`  ${label}: 광고 가젯 ${added.length}개를 놓았다`) : failed(`${label} 광고 가젯 저장`, r);
}

/** 가젯 페이지(주소 key — community·topics …)의 roboyam 테마 배치에 광고 가젯을 놓는다 */
async function placePageAdGadgets(admin, specs) {
    const pages = (await admin.request('GET', '/api/admin/gadget-pages')).data;
    const list = Array.isArray(pages) ? pages : (pages?.items ?? pages?.pages ?? Object.values(pages ?? {}).find(Array.isArray) ?? []);
    for (const key of [...new Set((specs ?? []).map((s) => s.page))]) {
        const page = list.find((p) => (p.key ?? p.slug) === key);
        if (!page) {
            failed(`가젯 페이지 ${key}`, '없음');
            continue;
        }
        await placeAdGadgets(admin, `가젯 페이지 ${key}`, `/api/admin/gadget-pages/${page.id}/layouts/roboyam`, specs.filter((s) => s.page === key));
    }
}

/** 글 제목 → 그 원고의 첫 데모 그림(하이라이트 대표 이미지는 저장소 파일 대신 원본 그림을 다시 올린다) */
function findLocalImage(title) {
    for (const f of readdirSync(join(DEMO, 'content'))) {
        const post = (json(join(DEMO, 'content', f)).posts ?? []).find((x) => x.title.slice(0, 100).replace(/\|/g, '/') === title);
        if (post) return imageFiles(post)[0] ?? null;
    }
    return null;
}

main().catch((e) => {
    console.error(scrub(e?.stack ?? e));
    process.exitCode = 1;
});
