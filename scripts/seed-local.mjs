#!/usr/bin/env node
/**
 * 로컬 개발 DB 에 시험 데이터를 채운다 — 데이터는 seed/seed.json(community 에서 넘어온 것), 설명은 docs/seed.md.
 *
 *   node scripts/seed-local.mjs            # 계정을 만들고(늘 멱등), 글이 없는 DB 면 글·댓글·추천까지 채운다
 *   node scripts/seed-local.mjs --force    # 글이 이미 있어도 글·댓글·추천을 덧붙인다(중복이 생긴다). 계정 단계는 그대로 멱등이다
 *
 * 1) 계정: 로보얌에는 공개 이메일 가입 API 가 없으므로 seed.json 의 users 를 로컬 DB 컨테이너에 SQL 로 넣는다 —
 *    users(비밀번호는 pgcrypto 의 bcrypt `crypt(pw, gen_salt('bf', 10))` — 서버 BCryptPasswordEncoder 와 호환, 상태·이메일 인증·가입 일수·필수 동의)
 *    + profiles(닉네임·기본 등급). 같은 이메일이 이미 있으면 건너뛴다. 만든 뒤 모든 계정을 공개 API 로 실제 로그인해 본다.
 *    `role: ADMIN` 도 회원으로만 만든다(로보얌의 운영자는 operators 표의 별도 계정 — 관리자 화면은 설치 때 만든 루트 계정으로 들어간다).
 * 2) 콘텐츠: 글·댓글·추천·지우기는 그 회원으로 로그인해 공개 API 로 한다(서버 규칙·카운터·포인트·회원 알림이 그대로 적용된다).
 *    API 로 못 하는 것만 SQL 로 한다: 작성 간격(429 *_TOO_FAST)에 걸리면 그 시드 회원의 최근 글·댓글 작성 시각을 기다릴 초만큼 앞으로 당긴다
 *    (로보얌의 작성 속도 판정은 따로 표가 없고 posts·comments 의 created_at 으로 센다).
 *
 * 안전장치: 서버(SEED_API)는 localhost 만, 서버 주소를 기본과 다르게 주면 SEED_DB_NAME 필수, 서버와 DB 의 설치 상태가 다르면(또는 설치 전이면)
 * 아무것도 바꾸기 전에 멈춘다. 설치는 하지 않는다(설치 마법사를 먼저 마친다). 비밀번호는 출력하지 않는다(psql 에는 명령 인자가 아니라 표준 입력으로 넘긴다).
 *
 * 환경 변수(전부 선택): SEED_API(기본 http://localhost:8910) · SEED_DB_CONTAINER(기본 roboyam-local-postgres) · SEED_DB_NAME(기본 roboyam) · SEED_DB_USER(기본 roboyam)
 * ⚠ 로컬 개발 전용이다. 운영·개발 서버에 쓰지 않는다.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_API = 'http://localhost:8910';
const API = (process.env.SEED_API ?? DEFAULT_API).replace(/\/+$/, '');
const DB_CONTAINER = process.env.SEED_DB_CONTAINER ?? 'roboyam-local-postgres';
const DB_NAME = process.env.SEED_DB_NAME ?? 'roboyam';
const DB_USER = process.env.SEED_DB_USER ?? 'roboyam';
const FORCE = process.argv.includes('--force');

/** 시드 글의 게시판이 이 DB 에 없을 때 옮길 게시판 — 이것도 없으면 그 글은 건너뛴다. 공지(notice)로는 옮기지 않는다(운영진 전용 쓰기) */
const FALLBACK_BOARD = 'free';
/** 가입 동의의 약관 버전 — 공개 가입(소셜 가입 완료, SocialAccountWriter.SIGNUP_TERMS_VERSION)과 같다 */
const TERMS_VERSION = 'v1';
/** 시드 계정 ID 의 Snowflake 서버 번호 — 앱 서버(local 기본 NODE_ID=1)와 겹치지 않게 맨 끝 번호를 쓴다 */
const SEED_NODE_ID = 1023n;
const SNOWFLAKE_EPOCH_MS = 1_767_225_600_000n; // 2026-01-01T00:00:00Z (SnowflakeIdGenerator.EPOCH_MS)

function fail(message) {
    console.error(message);
    process.exitCode = 2;
}

const seed = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'seed', 'seed.json'), 'utf8'));
const PASSWORD = seed.password;

/** 비밀번호가 혹시 오류 문구에 섞여도 화면에 나가지 않게 가린다 */
const scrub = (text) => (PASSWORD ? String(text).split(PASSWORD).join('***') : String(text));

// ── DB (로컬 컨테이너의 psql — SQL 은 표준 입력으로 넘긴다: 비밀번호가 프로세스 인자에 보이지 않게) ──
function sql(script, { sensitive = false } = {}) {
    try {
        return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', '-v', 'ON_ERROR_STOP=1', '-U', DB_USER, '-d', DB_NAME, '-f', '-'],
            { input: script, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
    } catch (e) {
        // 비밀번호가 실린 SQL 의 오류는 psql 이 문장 일부(LINE …)를 함께 찍을 수 있어 내용을 버리고 첫 줄의 오류 종류만 남긴다
        const stderr = String(e.stderr ?? e.message ?? '');
        const detail = sensitive ? (stderr.match(/ERROR:\s*([^\n]*)/)?.[1] ?? '알 수 없는 오류') : stderr.trim();
        throw new Error(`SQL 실패(${DB_CONTAINER}/${DB_NAME}): ${scrub(detail)}`);
    }
}
const q = (s) => (s === null || s === undefined ? 'NULL' : `'${String(s).replace(/'/g, "''")}'`);
const rows = (out) => (out ? out.split('\n').filter(Boolean).map((line) => line.split('|')) : []);

/**
 * 시드 글 본문(community 의 HTML)을 평문 게시판(`postEditor` NONE)에 맞는 글자로 바꾼다 — 블록 태그·`<br>` 는 줄바꿈, 나머지 태그는 떼고, 자주 쓰는 문자 참조만 푼다.
 * 그대로 보내면 TEXT 글 본문에 `<p>` 가 글자 그대로 남아 글 상세·검색 미리보기에 보인다.
 */
function htmlToText(html) {
    return String(html ?? '')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/(p|div|li|h[1-6]|blockquote)>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

// ── HTTP (회원마다 쿠키 항아리 하나 — CSRF 는 XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 되돌린다). 응답은 ApiResponse{success,data|error} ──
class Session {
    constructor(label) {
        this.label = label;
        this.jar = new Map();
    }

    #cookie() {
        return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
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

    async request(method, path, body, retried = false) {
        if (method !== 'GET' && !this.jar.has('XSRF-TOKEN')) await this.request('GET', '/api/auth/me');
        const headers = { Accept: 'application/json' };
        if (method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(this.jar.get('XSRF-TOKEN') ?? '');
        if (this.jar.size) headers.Cookie = this.#cookie();
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
        this.#store(res);
        const text = await res.text();
        let json = null;
        try {
            json = text ? JSON.parse(text) : null;
        } catch {
            json = null;
        }
        const code = json?.error?.code ?? null;
        if (res.status === 403 && code === 'CSRF_INVALID' && !retried) {
            this.jar.delete('XSRF-TOKEN');
            await this.request('GET', '/api/auth/me');
            return this.request(method, path, body, true);
        }
        return { status: res.status, data: json?.data ?? null, code, retryAfter: json?.error?.retryAfterSeconds ?? null };
    }
}

function snowflake(index) {
    return ((BigInt(Date.now()) - SNOWFLAKE_EPOCH_MS) << 22n) | (SEED_NODE_ID << 12n) | BigInt(index);
}

async function main() {
    // ── 0) 안전장치 ──
    if (!/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(API)) {
        return fail(`로컬 서버(localhost)에만 쓸 수 있다: ${API}`);
    }
    // 서버 주소를 바꿨는데 SQL 대상 DB 를 정하지 않았으면 멈춘다 — 로더의 SQL 이 기본 DB(개발 DB roboyam)에 닿는 사고를 막는다
    if (API !== DEFAULT_API && !process.env.SEED_DB_NAME) {
        return fail(`SEED_API 를 기본(${DEFAULT_API})과 다르게 줬는데 SEED_DB_NAME 이 없다. 그 서버가 붙은 DB 이름을 SEED_DB_NAME 으로 준다(일부러 기본 DB 면 SEED_DB_NAME=roboyam).`);
    }
    if (!PASSWORD || !Array.isArray(seed.users)) {
        return fail('seed/seed.json 에 password 또는 users 가 없다');
    }

    const status = await fetch(`${API}/api/system/installation/status`).then((r) => r.json()).catch(() => null);
    if (typeof status?.data?.installed !== 'boolean') {
        return fail(`서버(${API})가 떠 있지 않거나 로보얌 공개 서버가 아니다(/api/system/installation/status 응답 없음).`);
    }
    let dbInstalled;
    try {
        const [[dbName, installed]] = rows(sql(`SELECT current_database(), coalesce((SELECT status = 'COMPLETED' FROM installation WHERE id = 1), false)`));
        if (dbName !== DB_NAME) return fail(`SQL 대상 DB 이름이 다르다: ${dbName} ≠ ${DB_NAME}`);
        dbInstalled = installed === 't';
    } catch (e) {
        return fail(`DB 에 접속하지 못했다 — 컨테이너 ${DB_CONTAINER}, DB ${DB_NAME}, 사용자 ${DB_USER}\n${e.message}`);
    }
    if (status.data.installed !== dbInstalled) {
        return fail(`서버(${API})의 설치 상태(${status.data.installed ? '설치됨' : '설치 전'})와 SQL 대상 DB(${DB_NAME})의 설치 상태(${dbInstalled ? '설치됨' : '설치 전'})가 다르다 — 서버가 붙은 DB 와 SEED_DB_NAME 이 같은지 확인한다. 아무것도 바꾸지 않았다.`);
    }
    if (!dbInstalled) {
        return fail('설치 전 DB 다. 로더는 설치하지 않는다 — 공개 사이트의 /install 에서 설치 마법사를 먼저 마치고 다시 돌린다. 아무것도 바꾸지 않았다.');
    }
    // 서버가 보여 주는 게시판이 모두 이 DB 에 있어야 한다(같은 DB 인지 한 번 더 본다)
    const listed = await fetch(`${API}/api/boards`).then((r) => r.json()).catch(() => null);
    const serverSlugs = (listed?.data?.boards ?? []).map((b) => b.slug);
    const dbSlugs = new Set(rows(sql('SELECT slug FROM boards')).map(([slug]) => slug));
    const foreign = serverSlugs.filter((slug) => !dbSlugs.has(slug));
    if (foreign.length) {
        return fail(`서버의 게시판(${foreign.join(', ')})이 SQL 대상 DB(${DB_NAME})에 없다 — 서버와 DB 가 다르다. 아무것도 바꾸지 않았다.`);
    }
    // 비밀번호가 DB 서버 로그에 남는 설정이면 멈춘다(bcrypt 해시를 DB 안에서 만들기 때문에 SELECT 한 줄에 비밀번호가 실린다)
    const [[logStatement, logMinDuration]] = rows(sql(`SELECT current_setting('log_statement'), current_setting('log_min_duration_statement')`));
    if (logStatement === 'all' || logMinDuration === '0') {
        return fail(`DB 가 모든 SQL 을 로그에 남기는 설정이다(log_statement=${logStatement}, log_min_duration_statement=${logMinDuration}) — 비밀번호가 DB 로그에 남지 않게 멈춘다.`);
    }
    console.log(`대상: 서버 ${API} · DB ${DB_CONTAINER}/${DB_NAME} (설치됨)`);

    // ── 1) 계정(늘 멱등) ──
    const results = await seedAccounts();
    if (results === null) return;

    // ── 2) 콘텐츠 ──
    const skipped = listUnsupported();
    const existingPosts = Number(sql('SELECT count(*) FROM posts'));
    if (existingPosts > 0 && !FORCE) {
        console.log(`\n콘텐츠: DB 에 글이 이미 ${existingPosts}개 있어 글·댓글·추천 단계는 건너뛴다(덧붙이려면 --force — 중복이 생긴다).`);
        printUnsupported(skipped);
        return;
    }
    await seedContent(results);
    printUnsupported(skipped);
    printSummary();
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 1) 계정
// ════════════════════════════════════════════════════════════════════════════════════════════════
async function seedAccounts() {
    console.log('\n계정');
    const users = seed.users.map((u) => ({ ...u, email: u.email.trim().toLowerCase() }));
    const existing = new Set(rows(sql(`SELECT email FROM users WHERE email IN (${users.map((u) => q(u.email)).join(', ')})`)).map(([e]) => e));
    const fresh = users.filter((u) => !existing.has(u.email));
    // 닉네임이 다른 회원에게 이미 쓰이고 있으면(대소문자 무시) 그 계정은 만들지 않는다 — 닉네임 유일 색인에 막힌다
    const takenNicknames = fresh.length
        ? new Set(rows(sql(`SELECT lower(nickname) FROM profiles WHERE lower(nickname) IN (${fresh.map((u) => q(u.nickname.toLowerCase())).join(', ')})`)).map(([n]) => n))
        : new Set();
    const toCreate = fresh.filter((u) => !takenNicknames.has(u.nickname.toLowerCase()));
    const status = new Map(users.map((u) => [u.key, existing.has(u.email) ? 'existing' : takenNicknames.has(u.nickname.toLowerCase()) ? 'nickname-taken' : 'new']));

    if (toCreate.length) {
        const hashVars = toCreate.map((_, i) => `h${i}`);
        const ids = toCreate.map((_, i) => snowflake(i));
        const values = toCreate.map((u, i) => `(${ids[i]},${q(u.email)}, ${q(u.nickname)}, ${q(u.status ?? 'ACTIVE')}, ${u.emailVerified ? 'true' : 'false'}, `
            + `${Math.max(0, Math.trunc(Number(u.ageDays) || 0))}, ${q(u.grade ?? null)}, :'${hashVars[i]}')`);
        // 1단계: pgcrypto 로 bcrypt 해시를 만든다(확장이 없으면 만들고, 해시를 psql 변수에 받은 뒤 되돌린다 — DB 에 확장이 남지 않는다).
        // 2단계: 해시로 users·profiles 를 한 트랜잭션에 넣는다. 가입 시각·인증 시각·동의 시각은 모두 "지금 − 가입 일수"다(공개 가입과 같은 모양: 약관·개인정보·만 14세 동의 = 가입 시각)
        const script = [
            'BEGIN;',
            'CREATE EXTENSION IF NOT EXISTS pgcrypto;',
            `SELECT ${hashVars.map((h) => `crypt(${q(PASSWORD)}, gen_salt('bf', 10)) AS ${h}`).join(', ')} \\gset`,
            'ROLLBACK;',
            'BEGIN;',
            `WITH src (id, email, nickname, status, verified, age_days, grade, hash) AS (VALUES ${values.join(', ')}),`,
            '     ins AS (',
            '         INSERT INTO users (id, email, password_hash, role, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, created_at, updated_at)',
            `         SELECT s.id, s.email, s.hash, 'USER', s.status, CASE WHEN s.verified THEN t.at END, ${q(TERMS_VERSION)}, t.at, t.at, t.at, t.at`,
            "           FROM src s CROSS JOIN LATERAL (SELECT now() - make_interval(days => s.age_days) AS at) t",
            "          WHERE left(s.hash, 7) = '$2a$10$'",
            '         ON CONFLICT (email) WHERE email IS NOT NULL DO NOTHING',
            '         RETURNING id, email, created_at)',
            'INSERT INTO profiles (user_id, nickname, grade_key, grade_manual, created_at, updated_at)',
            // 등급: 시드에 grade(등급 키)가 있고 그 등급이 있으면 그것(수동 지정으로 남긴다), 없으면 첫 등급
            "SELECT i.id, s.nickname, coalesce((SELECT grade_key FROM grades WHERE grade_key = s.grade), (SELECT grade_key FROM grades ORDER BY level LIMIT 1)),",
            "       (s.grade IS NOT NULL AND EXISTS (SELECT 1 FROM grades WHERE grade_key = s.grade)), i.created_at, i.created_at",
            '  FROM ins i JOIN src s ON s.id = i.id',
            'RETURNING user_id;',
            'COMMIT;',
        ].join('\n');
        const created = new Set(rows(sql(script, { sensitive: true })).map(([id]) => id));
        toCreate.forEach((u, i) => status.set(u.key, created.has(String(ids[i])) ? 'created' : 'not-created'));
        if (created.size !== toCreate.length) {
            console.error(`  계정 ${toCreate.length}개 중 ${created.size}개만 만들어졌다 — 해시를 만들지 못했거나 그 사이 같은 이메일이 생겼다`);
        }
    }

    // 모든 시드 계정을 공개 API 로 실제 로그인해 본다(서버 BCryptPasswordEncoder 와 맞는지, 서버가 이 DB 를 보는지)
    const results = new Map();
    let createdButFailed = 0;
    for (const u of users) {
        const s = new Session(u.key);
        const r = await s.request('POST', '/api/auth/login', { email: u.email, password: PASSWORD });
        const state = status.get(u.key);
        const ok = r.status === 200 && r.data?.authenticated === true;
        const label = { created: '만듦', existing: '이미 있음', 'nickname-taken': '건너뜀(닉네임을 다른 회원이 씀)', 'not-created': '만들지 못함' }[state] ?? state;
        const note = [];
        if (u.role === 'ADMIN') note.push('회원으로만 — 운영자 계정은 만들지 않음');
        if (ok) {
            const restricted = r.data.restriction?.status;
            note.unshift(`로그인 ○${restricted ? ` (${restricted} 제한)` : ''}`, r.data.user?.emailVerified ? '이메일 인증됨' : '이메일 미인증', `등급 ${r.data.user?.gradeLabel ?? '?'}`);
            s.userId = r.data.user?.id ?? null;
            results.set(u.key, s);
        } else {
            note.unshift(`로그인 ✕ ${r.status} ${r.code ?? ''}`.trim());
            if (state === 'created') createdButFailed++;
        }
        console.log(`  ${u.key.padEnd(15)} ${label} · ${note.join(' · ')}`);
    }
    console.log(`계정 ${users.length}개 — 만듦 ${[...status.values()].filter((v) => v === 'created').length} · 이미 있음 ${existing.size} · 로그인 ${results.size}/${users.length}`);
    if (createdButFailed > 0) {
        console.error(`방금 만든 계정 ${createdButFailed}개가 로그인되지 않는다 — 서버가 다른 DB 를 보거나 비밀번호 해시가 서버와 맞지 않는다. 콘텐츠 단계는 하지 않는다.`);
        process.exitCode = 1;
        return null;
    }
    return results;
}

// ════════════════════════════════════════════════════════════════════════════════════════════════
// 2) 글·댓글·추천·지우기 (공개 API)
// ════════════════════════════════════════════════════════════════════════════════════════════════
const createdPosts = new Map(); // seed key → { id, author }
const createdComments = new Map(); // seed key → { id, postId, author }
const counts = { posts: 0, comments: 0, likes: 0, deleted: 0, skipped: [] };

/**
 * 작성 간격(429 POST_TOO_FAST·COMMENT_TOO_FAST)에 걸리면 <b>그 시드 회원</b>이 간격 안에 쓴 글(또는 댓글)의 작성 시각을 기다릴 초만큼 앞으로 당기고 다시 쓴다.
 * 로보얌은 작성 속도를 따로 표에 두지 않고 posts·comments 의 created_at 최댓값으로 판정한다(PostRepositoryQDSL.rate·CommentRepositoryQDSL.rate).
 * 당기는 줄은 그 회원(author_id)의 최근(간격 안) 줄뿐이다 — 하루 한도(*_DAILY_LIMIT)는 비우지 않는다(시드 양으로는 닿지 않는다).
 */
async function writeWithGap(session, kind, path, body) {
    let r;
    for (let attempt = 0; attempt < 4; attempt++) {
        r = await session.request('POST', path, body);
        if (r.status !== 429 || !/_TOO_FAST$/.test(r.code ?? '') || !session.userId) return r;
        const secs = Math.max(1, Number(r.retryAfter) || 60) + 1;
        const table = kind === 'post' ? 'posts' : 'comments';
        sql(`UPDATE ${table} SET created_at = created_at - make_interval(secs => ${secs}), updated_at = updated_at - make_interval(secs => ${secs})
              WHERE author_id = ${BigInt(session.userId)} AND created_at > now() - interval '1 hour'`);
    }
    return r;
}

async function seedContent(sessions) {
    console.log('\n글');
    const boardList = (await new Session('anon').request('GET', '/api/boards')).data?.boards ?? [];
    const have = new Set(boardList.map((b) => b.slug));
    const detailCache = new Map(); // `${author}|${slug}` → 게시판 상세(말머리·쓰기 판정)

    async function boardFor(author, slug) {
        const key = `${author}|${slug}`;
        if (!detailCache.has(key)) detailCache.set(key, (await sessions.get(author).request('GET', `/api/boards/${encodeURIComponent(slug)}`)).data);
        return detailCache.get(key);
    }

    for (const p of seed.posts ?? []) {
        const session = sessions.get(p.author);
        if (!session) {
            counts.skipped.push(`글 ${p.key} — 글쓴이 ${p.author} 로 로그인하지 못했다`);
            continue;
        }
        // 게시판 규칙: 시드 게시판이 있으면 그대로, 없으면 free 로 옮긴다(free 도 없으면 건너뜀). notice 로는 옮기지 않는다
        const target = have.has(p.board) ? p.board : have.has(FALLBACK_BOARD) ? FALLBACK_BOARD : null;
        if (!target) {
            counts.skipped.push(`글 ${p.key} — 게시판 ${p.board} 도 ${FALLBACK_BOARD} 도 없다`);
            continue;
        }
        const board = await boardFor(p.author, target);
        if (!board?.viewer?.canWrite) {
            counts.skipped.push(`글 ${p.key} — ${p.author} 가 ${target} 에 쓸 수 없다(${board?.viewer?.writeBlockedReason ?? '게시판 정보 없음'})`);
            continue;
        }
        // 말머리 규칙: 옮긴 게시판에 같은 이름의 말머리가 있고 운영진 전용이 아니면 그것, 아니면 말머리 없이
        const prefix = p.prefix ? (board.prefixes ?? []).find((x) => x.name === p.prefix && !x.staffOnly) : null;
        // 본문 형식: 에디터 게시판(SIMPLE·ADVANCED)이면 HTML 그대로, 평문 게시판(NONE)이면 태그를 뗀 글자(board-public-contract 「본문 형식」)
        const html = board.postEditor && board.postEditor !== 'NONE';
        const r = await writeWithGap(session, 'post', '/api/posts', {
            boardSlug: target, prefixId: prefix?.id ?? null, title: p.title,
            contentFormat: html ? 'HTML' : 'TEXT', content: html ? p.bodyHtml : htmlToText(p.bodyHtml), adult: false,
        });
        if (r.status !== 201 || !r.data?.id) {
            counts.skipped.push(`글 ${p.key} — ${r.status} ${r.code ?? ''}`);
            continue;
        }
        createdPosts.set(p.key, { id: r.data.id, author: p.author });
        counts.posts++;
        const moved = target !== p.board ? ` (게시판 ${p.board} → ${target})` : '';
        const prefixNote = p.prefix && !prefix ? ` (말머리 ${p.prefix} 없음 → 말머리 없이)` : '';
        console.log(`  ${p.key} → ${r.data.id} · ${target}${moved}${prefixNote}${p.poll ? ' · 투표는 건너뜀' : ''}`);
    }

    console.log('\n댓글');
    for (const c of seed.comments ?? []) {
        const session = sessions.get(c.author);
        const post = createdPosts.get(c.post);
        if (!session || !post) {
            counts.skipped.push(`댓글 ${c.key} — ${!post ? `글 ${c.post} 를 만들지 않았다` : `${c.author} 로 로그인하지 못했다`}`);
            continue;
        }
        const body = { content: c.body, parentId: null, replyToId: null };
        if (c.replyTo) {
            // 답글은 1단계 — 원댓글(parentId)은 답한 댓글의 원댓글, 답한 댓글은 replyToId
            const target = createdComments.get(c.replyTo);
            if (!target) {
                counts.skipped.push(`댓글 ${c.key} — 답할 댓글 ${c.replyTo} 를 만들지 않았다`);
                continue;
            }
            body.parentId = target.rootId;
            body.replyToId = target.id;
        }
        const r = await writeWithGap(session, 'comment', `/api/posts/${post.id}/comments`, body);
        if (r.status !== 201 || !r.data?.id) {
            counts.skipped.push(`댓글 ${c.key} — ${r.status} ${r.code ?? ''}`);
            continue;
        }
        const rootId = body.parentId ?? r.data.id;
        createdComments.set(c.key, { id: r.data.id, postId: post.id, author: c.author, rootId });
        counts.comments++;
    }
    console.log(`  ${counts.comments}/${(seed.comments ?? []).length}개`);

    console.log('\n추천');
    for (const l of seed.likes ?? []) {
        const session = sessions.get(l.user);
        const isComment = createdComments.has(l.target) || (seed.comments ?? []).some((c) => c.key === l.target);
        const target = isComment ? createdComments.get(l.target) : createdPosts.get(l.target);
        if (!session || !target) {
            counts.skipped.push(`추천 ${l.user} → ${l.target} — ${!target ? '대상을 만들지 않았다' : '로그인하지 못했다'}`);
            continue;
        }
        const path = isComment ? `/api/comments/${target.id}/like` : `/api/posts/${target.id}/like`;
        const r = await session.request('POST', path, {});
        if (r.status === 200) counts.likes++;
        else counts.skipped.push(`추천 ${l.user} → ${l.target} — ${r.status} ${r.code ?? ''}`);
    }
    console.log(`  ${counts.likes}/${(seed.likes ?? []).length}개`);

    // 지우기(글쓴이 본인) — 추천이 쌓인 뒤 지운 글(목록·순위에서 빠지는지 보는 시험 글)
    for (const p of (seed.posts ?? []).filter((x) => x.deleted)) {
        const post = createdPosts.get(p.key);
        const session = sessions.get(p.author);
        if (!post || !session) continue;
        const r = await session.request('POST', `/api/posts/${post.id}/delete`, {});
        if (r.status === 200) {
            counts.deleted++;
            console.log(`\n지우기: 글 ${p.key} (${post.id})`);
        } else counts.skipped.push(`지우기 글 ${p.key} — ${r.status} ${r.code ?? ''}`);
    }
    for (const c of (seed.comments ?? []).filter((x) => x.deleted)) {
        const comment = createdComments.get(c.key);
        const session = sessions.get(c.author);
        if (!comment || !session) continue;
        const r = await session.request('POST', `/api/comments/${comment.id}/delete`, {});
        if (r.status === 200 || r.status === 204) counts.deleted++;
        else counts.skipped.push(`지우기 댓글 ${c.key} — ${r.status} ${r.code ?? ''}`);
    }

    if (counts.skipped.length) {
        console.log('\n못 한 것');
        for (const line of counts.skipped) console.log(`  ${line}`);
    }
}

/** 로보얌에 아직 없는 기능의 시드 데이터 — 건너뛴다 */
function listUnsupported() {
    const out = [];
    const polls = (seed.posts ?? []).filter((p) => p.poll).map((p) => p.key);
    if (polls.length) out.push(`투표 ${polls.length}개(글 ${polls.join('·')} — 글은 투표 없이 쓴다)`);
    const admins = seed.users.filter((u) => u.role === 'ADMIN').map((u) => u.key);
    if (admins.length) out.push(`운영자 역할(${admins.join('·')} — 회원으로만 만든다. 관리자 화면은 설치 때 만든 루트 계정으로)`);
    const labels = {
        drafts: '임시저장', commerce: '공동구매', adBanners: '광고 직접 배너', hotdealBusiness: '핫딜 입점(사업자)', promoBump: '직접홍보·끌올',
        homeGadgets: '홈 가젯', roboyamConcept: 'ROBOYAM 운영 예시(관리자 설정)', galleryPosts: '영상·짤 시험 글',
    };
    for (const [key, label] of Object.entries(labels)) {
        const v = seed[key];
        if (v === undefined || v === null) continue;
        out.push(`${label}(${key}${Array.isArray(v) ? ` ${v.length}개` : ''})`);
    }
    return out;
}

function printUnsupported(list) {
    if (!list.length) return;
    console.log('\n건너뛴 것(로보얌에 아직 없는 기능)');
    for (const line of list) console.log(`  ${line}`);
}

function printSummary() {
    const emails = seed.users.map((u) => q(u.email.trim().toLowerCase())).join(', ');
    const lines = rows(sql(`SELECT p.nickname, coalesce(b.balance, 0), (SELECT count(*) FROM member_notifications n WHERE n.user_id = u.id)
                              FROM users u JOIN profiles p ON p.user_id = u.id LEFT JOIN point_balances b ON b.user_id = u.id
                             WHERE u.email IN (${emails}) ORDER BY u.created_at, u.id`));
    console.log(`\n결과: 글 ${counts.posts} · 댓글 ${counts.comments} · 추천 ${counts.likes} · 지우기 ${counts.deleted}`);
    console.log('회원별 포인트·알림');
    for (const [nickname, balance, notifications] of lines) {
        if (balance !== '0' || notifications !== '0') console.log(`  ${nickname.padEnd(12)} ${balance}P · 알림 ${notifications}`);
    }
}

try {
    await main();
} catch (e) {
    console.error(scrub(e?.message ?? e));
    process.exitCode = 1;
}
