#!/usr/bin/env node
/**
 * 오픈소스 라이선스 고지 목록을 모은다(docs/features/oss-licenses-contract.md 「자동 수집」) — 결과는 두 앱의 `public/oss-licenses.json`(같은 내용)에 쓴다.
 * 공개 화면 `/licenses` 와 관리자 「오픈소스 라이선스」가 이 파일을 정적 파일로 읽는다. 저장소에 커밋해 두고, 의존성을 바꾸면 다시 돌린다.
 *
 *   node scripts/collect-licenses.mjs              # 두 화면을 빌드(nuxt generate)해 번들에 든 패키지 + 서버 런타임 의존성
 *   node scripts/collect-licenses.mjs --skip-build # 지난 빌드의 번들 목록(node_modules/.cache/roboyam-oss/*.json)을 다시 쓴다
 *   node scripts/collect-licenses.mjs --with-server # 서버 런타임 의존성도 싣는다(서버 프로그램을 남에게 나눠 줄 때만 — 아래 「고지 범위」)
 *
 * 고지 범위(사용자 2026-11-10 「진짜 필요한 고지만」): 고지 의무는 코드를 남에게 배포할 때 생긴다. 브라우저로 내려가는 화면 번들만 배포에 해당하고,
 * 우리 서버 안에서만 도는 서버 라이브러리는 배포가 아니라 기본으로 싣지 않는다(설치형으로 서버 프로그램을 나눠 줄 때만 --with-server).
 * 같은 패키지의 여러 버전은 한 줄로 합치고(버전은 쉼표로), 고지를 요구하지 않는 라이선스(0BSD·MIT-0·CC0-1.0·Unlicense — 「또는」 식에 하나라도 있으면 그것을 고른다)는 뺀다.
 *
 * - 화면: 클라이언트 번들에 실제로 들어간 바깥 패키지(`scripts/oss-licenses/nuxt-bundle-scan.mjs` 가 빌드 중에 적는다). 워크스페이스 패키지(`@roboyam/*`)와
 *   빌드 도구·devDependencies 는 번들에 모듈로 들어가지 않아 빠지고, 워크스페이스 패키지가 끌어온 바깥 의존성은 들어간다. 이름·버전·라이선스·저장소는 package.json,
 *   전문은 패키지 폴더의 LICENSE·COPYING(+ NOTICE) 파일. 파일이 없으면 SPDX 표준 문구로 대신하고 `textSource: 'template'` 로 표시한다.
 * - 서버: `gradlew :server:api:dependencies`·`:server:admin:dependencies`(runtimeClasspath — shared 의 의존성도 그 아래 잡힌다)의 좌표를 읽고, Gradle 캐시의
 *   POM(라이선스·저장소, 없으면 부모 POM)과 jar 안의 META-INF/LICENSE·NOTICE 를 읽는다. 플러그인을 들이지 않는다(Gradle 내장 `dependencies` 보고).
 *   JAVA_HOME 이 JDK 25 를 가리켜야 한다(`.claude/guides` 의 검증 명령과 같은 값).
 * - GPL·LGPL·EPL·MPL·CDDL 등 조건이 까다로운 라이선스, 라이선스를 모르는 것, 사용자 정의 라이선스(「SEE LICENSE IN …」)는 끝에 경고로 따로 출력한다(빼거나 바꾸지 않는다).
 * - 같은 전문은 `texts` 에 한 번만 싣고 항목은 그 번호(`textId`)만 가진다(Apache-2.0 전문이 수십 번 되풀이되지 않게).
 */
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';
import { permissive, restrictive, spdxFromPom, stripParens, TEMPLATES } from './oss-licenses/license-texts.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const CACHE = join(ROOT, 'node_modules', '.cache', 'roboyam-oss');
const OUTPUTS = [join(ROOT, 'apps', 'user-web', 'public', 'oss-licenses.json'), join(ROOT, 'apps', 'admin-web', 'public', 'oss-licenses.json')];
const WEB_APPS = [
    { app: 'user-web', usedIn: 'user' },
    { app: 'admin-web', usedIn: 'admin' },
];
const SERVER_PROJECTS = [':server:api', ':server:admin'];
const args = new Set(process.argv.slice(2));
const SKIP_BUILD = args.has('--skip-build');
const WITH_SERVER = args.has('--with-server');
/**
 * 같은 프로젝트(한 저장소·한 조직이 함께 내는 패키지)는 한 줄로 묶는다(사용자 2026-11-10 「같은 프로젝트면 묶어서」) — 이름 규칙 → 묶음 이름.
 * 규칙에 없는 패키지는 자기 이름 한 줄. 묶음 줄은 포함 패키지(이름·버전·라이선스)·저작권 줄을 모두 갖고, 전문이 서로 다르면 패키지 이름 머리를 달아 이어 붙인다(고지 조건은 그대로).
 */
const PROJECTS = [
    [/^@tiptap\//, 'Tiptap'],
    [/^(prosemirror-.+|orderedmap|rope-sequence|w3c-keyname)$/, 'ProseMirror'],
    [/^(vue|@vue\/.+)$/, 'Vue'],
    [/^(@intlify\/.+|vue-i18n|@nuxtjs\/i18n)$/, 'Vue I18n'],
    [/^(primevue|@primevue\/.+|@primeuix\/.+|primeicons|@primeicons\/.+|@primeui\/.+)$/, 'PrimeVue · PrimeIcons'],
    [/^@floating-ui\//, 'Floating UI'],
    [/^(nuxt|@nuxt\/.+|h3|ofetch|ufo|defu|destr|ohash|radix3|unctx|hookable|cookie-es|uncrypto|klona|perfect-debounce|errx|node-mock-http|iron-webcrypto|devalue|fnv1a-64|object-identity|nostics|uqr|birpc|unhead|@unhead\/.+)$/, 'Nuxt · UnJS'],
    [/^(yjs|lib0)$/, 'Yjs'],
    [/^(echarts|zrender)$/, 'Apache ECharts'],
    [/^@tabler\//, 'Tabler Icons'],
    [/^(tailwindcss|@tailwindcss\/.+)$/, 'Tailwind CSS'],
    [/^@portone\//, 'PortOne SDK'],
];
function projectOf(name) {
    for (const [rule, project] of PROJECTS) if (rule.test(name)) return project;
    return name;
}
/** 고지(저작권·라이선스 문구 함께 주기)를 요구하지 않는 라이선스 */
const NO_NOTICE = new Set(['0BSD', 'MIT-0', 'CC0-1.0', 'Unlicense']);
function noticeFree(license) {
    return stripParens(String(license ?? ''))
        .split(/\s+OR\s+/i)
        .some((part) => NO_NOTICE.has(part.trim()));
}

/** 글꼴·아이콘 묶음(공개 화면 「글꼴·아이콘」 칩) — 이름으로 정한다 */
const FONT_ICON = new Set(['primeicons', '@primeicons/core', '@primeicons/vue', '@tabler/icons', '@tabler/icons-vue']);
/**
 * 번들 검사로 잡히지 않지만 화면에 실제로 실리는 것 — [이름, 쓰는 곳, 사유].
 * Tabler 아이콘 그림 조각은 관리자가 고르면 서버가 저장해 공개 화면이 그린다(가젯 제목 아이콘). Tailwind 의 기본 스타일(preflight)은 빌드가 CSS 로 넣는다.
 */
const EXTRA_USAGE = [
    ['@tabler/icons', 'user', '관리자가 고른 아이콘 그림 조각을 서버가 저장해 공개 화면(가젯 제목)이 그린다'],
    ['tailwindcss', 'user', '기본 스타일(preflight)을 빌드가 CSS 로 넣는다'],
    ['tailwindcss', 'admin', '기본 스타일(preflight)을 빌드가 CSS 로 넣는다'],
];

const LICENSE_FILE = /^(licen[cs]e|copying|unlicense)([-._].*)?$/i;
const NOTICE_FILE = /^notice([-._].*)?$/i;

// ── 작은 도우미 ─────────────────────────────────────────────────────────

function readJson(path) {
    return JSON.parse(readFileSync(path, 'utf8'));
}

function normalizeText(text) {
    return text.replace(/^﻿/, '').replace(/\r\n?/g, '\n').replace(/[ \t]+$/gm, '').trim();
}

/**
 * 저작권 줄(최대 5줄) — 「Copyright (c)·©·연도·대문자 이름」 또는 「(c)·© 연도」로 시작하는 줄. 여러 글(NOTICE → 라이선스 전문 차례)을 받아 겹치는 줄은 한 번.
 * Apache-2.0 전문 안의 「copyright license to …」「Copyright [yyyy] [name of copyright owner]」 같은 조항·빈칸은 고르지 않는다.
 */
function copyrightLines(...sources) {
    const lines = [];
    for (const text of sources) {
        if (!text) continue;
        for (const raw of text.split('\n')) {
            const line = raw.trim().replace(/^[#*>\s]+/, '').trim();
            const looks = /^(Copyright|COPYRIGHT)\s*(\([cC]\)|©|\d{4}|[A-Z])/.test(line) || /^(\([cC]\)|©)\s*\d{4}/.test(line);
            if (!looks || /\[(yyyy|name of copyright owner)\]|\{yyyy\}|<year>/i.test(line) || line.length > 200) continue;
            if (!lines.includes(line)) lines.push(line);
            if (lines.length >= 5) return lines;
        }
    }
    return lines;
}

/** 저장소 주소를 https 로 */
function repoUrl(repository) {
    let url = typeof repository === 'string' ? repository : repository?.url;
    if (!url) return null;
    url = url.trim();
    const shorthand = /^(github:|gitlab:|bitbucket:)?([\w.-]+)\/([\w.-]+)$/.exec(url);
    if (shorthand) {
        const host = { 'gitlab:': 'gitlab.com', 'bitbucket:': 'bitbucket.org' }[shorthand[1]] ?? 'github.com';
        return `https://${host}/${shorthand[2]}/${shorthand[3]}`;
    }
    url = url.replace(/^git\+/, '').replace(/^git:\/\//, 'https://').replace(/^ssh:\/\/git@/, 'https://').replace(/^git@([^:]+):/, 'https://$1/');
    url = url.replace(/^scm:git:/, '').replace(/\.git$/, '').replace(/^http:\/\//, 'https://');
    return /^https:\/\/[^\s]+$/.test(url) && !url.includes('${') ? url : null;
}

function npmLicense(pkg) {
    if (typeof pkg.license === 'string') return pkg.license.trim();
    if (pkg.license?.type) return pkg.license.type;
    if (Array.isArray(pkg.licenses) && pkg.licenses.length) {
        return pkg.licenses.map((l) => (typeof l === 'string' ? l : l.type)).filter(Boolean).join(' OR ');
    }
    return null;
}

function authorName(author) {
    if (!author) return null;
    if (typeof author === 'string') return author.replace(/\s*<[^>]*>/, '').replace(/\s*\([^)]*\)/, '').trim() || null;
    return author.name ?? null;
}

// ── 전문 묶음(같은 글은 한 번) ───────────────────────────────────────────

const texts = new Map(); // id → text
const textIds = new Map(); // hash → id

function textId(text) {
    if (!text) return null;
    const normalized = normalizeText(text);
    if (!normalized) return null;
    const hash = createHash('sha1').update(normalized).digest('hex');
    let id = textIds.get(hash);
    if (!id) {
        id = `t${textIds.size + 1}`;
        textIds.set(hash, id);
        texts.set(id, normalized);
    }
    return id;
}

/** 저작권자 자리가 없는 표준 전문(Apache-2.0 등)을 모은 것 중에서 빌린다 */
const standardTexts = new Map();
function rememberStandard(license, text) {
    if (license === 'Apache-2.0' && text && /TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION/.test(text) && !standardTexts.has(license)) {
        standardTexts.set(license, text);
    }
}

// ── 항목 모으기 ─────────────────────────────────────────────────────────

/** key(생태계:이름@버전) → 항목 */
const entries = new Map();
const notes = [];

function upsert(entry, usedIn) {
    const key = `${entry.ecosystem}:${entry.name}@${entry.version}`;
    const found = entries.get(key);
    if (found) {
        if (!found.usedIn.includes(usedIn)) found.usedIn.push(usedIn);
        return found;
    }
    const created = { ...entry, usedIn: [usedIn] };
    entries.set(key, created);
    return created;
}

// 화면 ────────────────────────────────────────────────────────────────

function buildWeb(app) {
    const out = join(CACHE, `${app}.json`);
    if (SKIP_BUILD) {
        if (!existsSync(out)) throw new Error(`${out} 이 없다 — --skip-build 없이 한 번 돌린다`);
        return readJson(out).packages;
    }
    mkdirSync(CACHE, { recursive: true });
    console.log(`· ${app} 빌드(nuxt generate) — 번들에 든 패키지를 적는다`);
    // 셸에 한 줄 명령으로 넘긴다(Windows 의 pnpm.cmd 를 찾게) — 들어가는 값은 이 파일이 정한 앱 이름뿐이다
    const result = spawnSync(`pnpm --filter ${app} exec nuxt generate`, {
        cwd: ROOT,
        env: { ...process.env, ROBOYAM_OSS_SCAN: out },
        stdio: ['ignore', 'ignore', 'inherit'],
        shell: true,
    });
    if (result.status !== 0) throw new Error(`${app} 빌드가 실패했다(종료 코드 ${result.status})`);
    return readJson(out).packages;
}

function packageFiles(dir) {
    const files = readdirSync(dir).filter((name) => {
        try {
            return statSync(join(dir, name)).isFile();
        } catch {
            return false;
        }
    });
    const license = files.filter((name) => LICENSE_FILE.test(name)).sort((a, b) => a.length - b.length)[0];
    const notice = files.filter((name) => NOTICE_FILE.test(name)).sort((a, b) => a.length - b.length)[0];
    return {
        license: license ? readFileSync(join(dir, license), 'utf8') : null,
        notice: notice ? readFileSync(join(dir, notice), 'utf8') : null,
        file: (name) => (files.includes(name) ? readFileSync(join(dir, name), 'utf8') : null),
    };
}

function npmEntry(dir) {
    const pkg = readJson(join(dir, 'package.json'));
    const files = packageFiles(dir);
    let license = stripParens(npmLicense(pkg));
    let custom = false;
    let licenseText = files.license;
    const seeFile = /^SEE LICEN[CS]E IN (.+)$/i.exec(license ?? '');
    if (seeFile) {
        licenseText = files.file(seeFile[1].trim()) ?? licenseText;
        const heading = /^#*\s*(.+)$/m.exec(licenseText ?? '')?.[1]?.trim();
        license = heading && heading.length <= 60 ? heading : 'SEE LICENSE';
        custom = true;
    }
    let textSource = licenseText ? 'package' : 'none';
    if (!licenseText && license && TEMPLATES[license]) {
        licenseText = TEMPLATES[license];
        textSource = 'template';
    }
    const copyright = copyrightLines(files.notice, licenseText);
    const author = authorName(pkg.author);
    if (!copyright.length && author) copyright.push(`Copyright (c) ${author}`);
    rememberStandard(license, licenseText);
    return {
        ecosystem: 'npm',
        name: pkg.name,
        version: pkg.version,
        license: license ?? 'UNKNOWN',
        custom,
        copyright: copyright.join('\n') || null,
        textSource,
        licenseText,
        notice: files.notice,
        repository: repoUrl(pkg.repository) ?? (typeof pkg.homepage === 'string' && /^https?:\/\//.test(pkg.homepage) ? pkg.homepage : null),
        category: FONT_ICON.has(pkg.name) ? 'fontIcon' : null,
    };
}

function collectWeb() {
    for (const { app, usedIn } of WEB_APPS) {
        const dirs = buildWeb(app);
        for (const dir of dirs) {
            if (!existsSync(join(dir, 'package.json'))) continue;
            const entry = npmEntry(dir);
            if (!entry.name || entry.name.startsWith('@roboyam/')) continue;
            upsert(entry, usedIn);
        }
        for (const [name, extraUsedIn, reason] of EXTRA_USAGE) {
            if (extraUsedIn !== usedIn) continue;
            const dir = findNodeModule(app, name);
            if (!dir) {
                notes.push(`(추가 항목) ${name} 을 ${app} 의 node_modules 에서 찾지 못했다`);
                continue;
            }
            upsert(npmEntry(dir), usedIn);
            notes.push(`(추가 항목) ${name} → ${usedIn}: ${reason}`);
        }
    }
}

/** 앱에서 보이는 패키지 폴더(앱 node_modules → 루트 node_modules → .pnpm 안) */
function findNodeModule(app, name) {
    for (const base of [join(ROOT, 'apps', app, 'node_modules'), join(ROOT, 'node_modules')]) {
        const dir = join(base, ...name.split('/'));
        if (existsSync(join(dir, 'package.json'))) return dir;
    }
    const pnpm = join(ROOT, 'node_modules', '.pnpm');
    const prefix = `${name.replace('/', '+')}@`;
    const hit = existsSync(pnpm) ? readdirSync(pnpm).filter((d) => d.startsWith(prefix)).sort().pop() : null;
    return hit ? join(pnpm, hit, 'node_modules', ...name.split('/')) : null;
}

// 서버 ────────────────────────────────────────────────────────────────

const GRADLE_FILES = join(process.env.GRADLE_USER_HOME ?? join(homedir(), '.gradle'), 'caches', 'modules-2', 'files-2.1');
const COORD = /^[|+\\\- ]*([\w.\-]+):([\w.\-]+)(?::([^\s:()]+))?(?::([\w.\-]+))?(?: -> ([^\s()]+))?(?: \(([*cn])\))?\s*$/;

function serverCoordinates() {
    const coords = new Map();
    for (const project of SERVER_PROJECTS) {
        console.log(`· ${project} runtimeClasspath 의존성 읽기`);
        const gradlew = process.platform === 'win32' ? join(ROOT, 'gradlew.bat') : './gradlew';
        const result = spawnSync(`"${gradlew}" -q ${project}:dependencies --configuration runtimeClasspath`, {
            cwd: ROOT,
            encoding: 'utf8',
            shell: true,
            maxBuffer: 64 * 1024 * 1024,
        });
        if (result.status !== 0) throw new Error(`${project}:dependencies 가 실패했다\n${result.stderr}`);
        for (const line of result.stdout.split(/\r?\n/)) {
            const m = COORD.exec(line);
            if (!m || line.includes('project ')) continue;
            const [, group, name, declared, classifier, resolvedVersion, flag] = m;
            if (flag === 'c' || flag === 'n') continue;
            const version = resolvedVersion ?? declared;
            if (!version) continue;
            coords.set(`${group}:${name}:${version}`, { group, name, version, classifier });
        }
    }
    return [...coords.values()];
}

function gradleFile(group, name, version, fileName) {
    const dir = join(GRADLE_FILES, group, name, version);
    if (!existsSync(dir)) return null;
    for (const hash of readdirSync(dir)) {
        const path = join(dir, hash, fileName);
        if (existsSync(path)) return path;
    }
    return null;
}

function xmlBlock(xml, tag) {
    return new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)</${tag}>`).exec(xml)?.[1] ?? null;
}

function xmlText(xml, tag) {
    const v = xmlBlock(xml, tag);
    return v == null ? null : v.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').replace(/\s+/g, ' ').trim();
}

/** POM 의 위쪽(하위 블록을 뺀) 칸 — <parent>·<dependencies> 등 안의 같은 이름 칸을 잡지 않게 */
function topLevel(xml) {
    return xml.replace(/<(parent|dependencies|dependencyManagement|build|profiles|reporting|distributionManagement|developers|contributors|organization|mailingLists|issueManagement|ciManagement|repositories|pluginRepositories|licenses|scm)(?:\s[^>]*)?>[\s\S]*?<\/\1>/g, '');
}

function readPom(group, name, version, depth = 0) {
    const path = gradleFile(group, name, version, `${name}-${version}.pom`);
    if (!path) return null;
    const xml = readFileSync(path, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const licensesXml = xmlBlock(xml, 'licenses');
    const licenses = licensesXml
        ? [...licensesXml.matchAll(/<license>([\s\S]*?)<\/license>/g)].map((m) => ({ name: xmlText(m[1], 'name'), url: xmlText(m[1], 'url') }))
        : [];
    const scm = xmlBlock(xml, 'scm');
    const info = {
        licenses,
        scmUrl: scm ? repoUrl(xmlText(scm, 'url')) : null,
        url: repoUrl(xmlText(topLevel(xml), 'url')),
        organization: xmlText(xmlBlock(xml, 'organization') ?? '', 'name'),
    };
    const parent = xmlBlock(xml, 'parent');
    if (parent && depth < 6 && (!info.licenses.length || (!info.scmUrl && !info.url) || !info.organization)) {
        const p = readPom(xmlText(parent, 'groupId'), xmlText(parent, 'artifactId'), xmlText(parent, 'version'), depth + 1);
        if (p) {
            if (!info.licenses.length) info.licenses = p.licenses;
            info.scmUrl ??= p.scmUrl;
            info.url ??= p.url;
            info.organization ??= p.organization;
        }
    }
    return info;
}

/** jar(zip) 에서 이름이 맞는 첫 파일들의 글 — 중앙 디렉터리만 읽는다(zip64 는 건너뛴다) */
function readJarTexts(path, wanted) {
    const buf = readFileSync(path);
    let eocd = -1;
    for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
        if (buf.readUInt32LE(i) === 0x06054b50) {
            eocd = i;
            break;
        }
    }
    if (eocd < 0) return {};
    const count = buf.readUInt16LE(eocd + 10);
    let offset = buf.readUInt32LE(eocd + 16);
    if (offset === 0xffffffff) return {};
    const found = {};
    for (let n = 0; n < count && offset + 46 <= buf.length; n++) {
        if (buf.readUInt32LE(offset) !== 0x02014b50) break;
        const method = buf.readUInt16LE(offset + 10);
        const compressed = buf.readUInt32LE(offset + 20);
        const nameLen = buf.readUInt16LE(offset + 28);
        const extraLen = buf.readUInt16LE(offset + 30);
        const commentLen = buf.readUInt16LE(offset + 32);
        const local = buf.readUInt32LE(offset + 42);
        const name = buf.toString('utf8', offset + 46, offset + 46 + nameLen);
        offset += 46 + nameLen + extraLen + commentLen;
        for (const [key, pattern] of Object.entries(wanted)) {
            if (found[key] || !pattern.test(name)) continue;
            const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
            const data = buf.subarray(start, start + compressed);
            try {
                found[key] = (method === 8 ? inflateRawSync(data) : data).toString('utf8');
            } catch {
                // 못 푼 파일은 없는 것으로
            }
        }
    }
    return found;
}

function collectServer() {
    for (const c of serverCoordinates()) {
        const jarName = c.classifier ? `${c.name}-${c.version}-${c.classifier}.jar` : `${c.name}-${c.version}.jar`;
        const jar = gradleFile(c.group, c.name, c.version, jarName) ?? gradleFile(c.group, c.name, c.version, `${c.name}-${c.version}.jar`);
        if (!jar) continue; // BOM·POM 묶음(jar 없음)
        const pom = readPom(c.group, c.name, c.version) ?? { licenses: [], scmUrl: null, url: null, organization: null };
        const inJar = readJarTexts(jar, {
            license: /^(META-INF\/)?(LICEN[CS]E|license)(\.txt|\.md)?$/,
            notice: /^(META-INF\/)?(NOTICE|notice)(\.txt|\.md)?$/,
            manifest: /^META-INF\/MANIFEST\.MF$/,
        });
        let ids = pom.licenses.map((l) => spdxFromPom(l.name)).filter(Boolean);
        if (!ids.length) {
            const bundle = /^Bundle-License: ?(.+)$/m.exec(inJar.manifest ?? '')?.[1]?.trim();
            if (bundle) ids = [spdxFromPom(bundle.split(';')[0])];
        }
        const license = ids.length ? [...new Set(ids)].join(' OR ') : 'UNKNOWN';
        let licenseText = inJar.license ?? null;
        let textSource = licenseText ? 'package' : 'none';
        if (!licenseText && TEMPLATES[license]) {
            licenseText = TEMPLATES[license];
            textSource = 'template';
        }
        if (!licenseText && standardTexts.has(license)) {
            licenseText = standardTexts.get(license);
            textSource = 'template';
        }
        rememberStandard(license, licenseText);
        const copyright = copyrightLines(inJar.notice, textSource === 'package' ? licenseText : null);
        if (!copyright.length && pom.organization) copyright.push(`Copyright (c) ${pom.organization}`);
        upsert(
            {
                ecosystem: 'maven',
                name: `${c.group}:${c.name}`,
                version: c.version,
                license,
                custom: false,
                copyright: copyright.join('\n') || null,
                textSource,
                licenseText,
                notice: inJar.notice ?? null,
                repository: pom.scmUrl ?? pom.url,
                licenseUrl: pom.licenses.map((l) => l.url).find((u) => u && /^https?:\/\//.test(u)) ?? null,
                category: null,
            },
            'server',
        );
    }
}

// ── 쓰기 ────────────────────────────────────────────────────────────────

function fillMissingTexts() {
    // 화면 쪽에서 Apache-2.0 전문을 못 찾았는데 서버 쪽에서 찾은 경우 등 — 뒤늦게 표준 전문으로 채운다
    for (const e of entries.values()) {
        if (!e.licenseText && standardTexts.has(e.license)) {
            e.licenseText = standardTexts.get(e.license);
            e.textSource = 'template';
        }
    }
}

function spdxPage(license) {
    return /^[A-Za-z0-9.\-+]+$/.test(license) && license !== 'UNKNOWN' ? `https://spdx.org/licenses/${license}.html` : null;
}

const dropped = [];
function finalItems() {
    const order = { npm: 0, maven: 1 };
    const merged = new Map();
    for (const e of entries.values()) {
        if (noticeFree(e.license)) {
            dropped.push(`${e.name}@${e.version} ${e.license}`);
            continue;
        }
        const key = `${e.ecosystem}:${e.name}`;
        const found = merged.get(key);
        if (!found) {
            merged.set(key, { ...e, versions: [e.version], usedIn: [...e.usedIn] });
            continue;
        }
        found.versions.push(e.version);
        for (const u of e.usedIn) if (!found.usedIn.includes(u)) found.usedIn.push(u);
        if (!found.licenseText && e.licenseText) Object.assign(found, { licenseText: e.licenseText, textSource: e.textSource });
        if (!found.notice && e.notice) found.notice = e.notice;
    }
    for (const e of merged.values()) e.version = [...new Set(e.versions)].sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).join(', ');
    // 같은 프로젝트를 한 줄로 — 패키지 목록·저작권 줄은 모두, 전문·NOTICE 는 서로 다르면 패키지 머리를 달아 잇는다
    const projects = new Map();
    for (const e of [...merged.values()].sort((a, b) => a.name.localeCompare(b.name))) {
        const key = `${e.ecosystem}:${projectOf(e.name)}`;
        const list = projects.get(key) ?? [];
        list.push(e);
        projects.set(key, list);
    }
    const joinTexts = (list, field) => {
        const distinct = [...new Map(list.filter((e) => e[field]).map((e) => [e[field], e])).values()];
        if (distinct.length <= 1) return distinct[0]?.[field] ?? null;
        return distinct.map((e) => `── ${list.filter((x) => x[field] === e[field]).map((x) => x.name).join(', ')} ──\n\n${e[field]}`).join('\n\n');
    };
    const grouped = [...projects.entries()].map(([key, list]) => {
        if (list.length === 1) return { ...list[0], packages: null };
        const first = list[0];
        const licenses = [...new Set(list.map((e) => e.license))];
        const copyright = [...new Set(list.flatMap((e) => String(e.copyright ?? '').split('\n')).map((l) => l.trim()).filter(Boolean))].join('\n');
        const text = joinTexts(list, 'licenseText');
        return {
            ...first,
            name: key.slice(key.indexOf(':') + 1),
            version: '',
            license: licenses.join(' · '),
            usedIn: [...new Set(list.flatMap((e) => e.usedIn))],
            category: list.every((e) => e.category === first.category) ? first.category : (list.some((e) => e.category === 'fontIcon') ? 'fontIcon' : null),
            copyright,
            licenseText: text,
            textSource: text ? (list.some((e) => e.licenseText && e.textSource === 'package') ? 'package' : first.textSource) : 'none',
            notice: joinTexts(list, 'notice'),
            repository: list.length && list.every((e) => e.repository === first.repository) ? first.repository : null,
            packages: list.map((e) => ({ name: e.name, version: e.version, license: e.license })),
        };
    });
    return grouped
        .sort((a, b) => order[a.ecosystem] - order[b.ecosystem] || a.name.localeCompare(b.name) || a.version.localeCompare(b.version, undefined, { numeric: true }))
        .map((e) => ({
            id: `${e.ecosystem}:${e.name}`,
            name: e.name,
            version: e.version,
            license: e.license,
            usedIn: ['user', 'admin', 'server'].filter((u) => e.usedIn.includes(u)),
            category: e.category ?? null,
            copyright: e.copyright,
            textId: textId(e.licenseText),
            textSource: e.licenseText ? e.textSource : 'none',
            noticeId: textId(e.notice),
            repository: e.repository ?? null,
            packages: e.packages ?? null,
            licenseUrl: e.licenseUrl ?? (e.licenseText ? null : spdxPage(e.license)),
        }));
}

/** 항목 한 줄씩(커밋 차이가 읽히게) */
function serialize(doc) {
    const lines = ['{', `"schema": ${doc.schema},`, `"collectedAt": ${JSON.stringify(doc.collectedAt)},`, '"items": ['];
    doc.items.forEach((item, i) => lines.push(JSON.stringify(item) + (i < doc.items.length - 1 ? ',' : '')));
    lines.push('],', '"texts": {');
    const entriesOfTexts = Object.entries(doc.texts);
    entriesOfTexts.forEach(([id, text], i) => lines.push(`${JSON.stringify(id)}: ${JSON.stringify(text)}` + (i < entriesOfTexts.length - 1 ? ',' : '')));
    lines.push('}', '}', '');
    return lines.join('\n');
}

/** 항목 id(이름까지) 의 사용자 정의 라이선스 여부 — 합친 버전 중 하나라도 */
function isCustom(id) {
    return [...entries.values()].some((e) => `${e.ecosystem}:${e.name}` === id && e.custom);
}

function report(items) {
    const by = (pred) => items.filter(pred).length;
    console.log(`\n모은 항목 ${items.length}개 — 공개 화면 ${by((i) => i.usedIn.includes('user'))} · 관리자 화면 ${by((i) => i.usedIn.includes('admin'))} · 서버 ${by((i) => i.usedIn.includes('server'))} · 글꼴·아이콘 ${by((i) => i.category === 'fontIcon')}`);
    const dist = new Map();
    for (const i of items) dist.set(i.license, (dist.get(i.license) ?? 0) + 1);
    console.log('라이선스 분포: ' + [...dist.entries()].sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} ${v}`).join(' · '));
    console.log(`전문 ${texts.size}종 · 표준 문구로 대신 ${by((i) => i.textSource === 'template')} · 전문 없음 ${by((i) => i.textSource === 'none')}`);
    const warn = items.filter((i) => restrictive(i.license));
    const unknown = items.filter((i) => i.license === 'UNKNOWN');
    const custom = items.filter((i) => isCustom(i.id));
    if (warn.length) {
        console.warn(`\n⚠ 조건이 까다로운 라이선스 ${warn.length}개(빼거나 바꾸지 않았다 — 검토 필요):`);
        for (const i of warn) console.warn(`  - ${i.name}@${i.version}  ${i.license}  [${i.usedIn.join(',')}]${permissive(i.license) ? '  (허용형으로 고를 수 있음)' : ''}`);
    }
    const other = items.filter((i) => !permissive(i.license) && !restrictive(i.license) && !isCustom(i.id) && i.license !== 'UNKNOWN');
    if (other.length) {
        console.warn(`\n· 그 밖의 라이선스(흔한 허용형이 아님 — 검토 권장) ${other.length}개:`);
        for (const i of other) {
            const apacheInside = i.textSource === 'package' && /Apache License\s+Version 2\.0/.test(texts.get(i.textId) ?? '');
            console.warn(`  - ${i.name}@${i.version}  ${i.license}  [${i.usedIn.join(',')}]${apacheInside ? '  (jar 안의 전문은 Apache-2.0)' : ''}`);
        }
    }
    if (custom.length) {
        console.warn(`\n⚠ 사용자 정의 라이선스(SEE LICENSE IN …) ${custom.length}개:`);
        for (const i of custom) console.warn(`  - ${i.name}@${i.version}  ${i.license}  [${i.usedIn.join(',')}]`);
    }
    if (unknown.length) {
        console.warn(`\n⚠ 라이선스를 모르는 항목 ${unknown.length}개:`);
        for (const i of unknown) console.warn(`  - ${i.name}@${i.version}  [${i.usedIn.join(',')}]`);
    }
    const noText = items.filter((i) => i.textSource === 'none');
    if (noText.length) {
        console.warn(`\n· 전문을 찾지 못해 라이선스 안내 주소만 싣는 항목 ${noText.length}개:`);
        for (const i of noText) console.warn(`  - ${i.name}@${i.version}  ${i.license}`);
    }
    if (dropped.length) console.log(`\n· 고지를 요구하지 않아 뺀 항목 ${dropped.length}개: ${dropped.join(' · ')}`);
    for (const note of notes) console.log(note);
}

function main() {
    if (WITH_SERVER) collectServer(); // 서버 먼저 — Apache-2.0 표준 전문을 jar 에서 먼저 얻는다(기본은 싣지 않는다 — 머리의 「고지 범위」)
    collectWeb();
    fillMissingTexts();
    const items = finalItems();
    const doc = { schema: 1, collectedAt: new Date().toISOString(), items, texts: Object.fromEntries(texts) };
    const body = serialize(doc);
    for (const out of OUTPUTS) {
        mkdirSync(dirname(out), { recursive: true });
        writeFileSync(out, body);
        console.log(`· 썼다: ${out.replace(ROOT, '.')} (${Math.round(Buffer.byteLength(body) / 1024)} KB)`);
    }
    report(items);
}

main();
