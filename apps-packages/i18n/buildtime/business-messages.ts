/**
 * 화면 문구를 **쓰는 화면이 가져가게** 나누는 빌드 모듈(2026-11-12) — 이 레이어(`@irobit/i18n`)를 extends 한 앱(user-web · admin-web)에 붙는다.
 *
 * 예전에는 모든 업무의 문구를 한꺼번에(한국어만 user-web gzip 약 97KB) 첫 화면에서 받았다. 휴대폰에서 첫 화면이 느린 큰 이유였다.
 *
 * 하는 일
 * 1. 모든 레이어·앱의 `i18n/locales/{언어}/{업무}.ts` 를 모은다 — **파일 이름이 업무 이름**(키의 첫 마디, `t('account.title')` 의 `account`)이다. 따로 목록에 등록하지 않는다.
 * 2. 화면·컴포넌트·유틸 코드(이 저장소 안, node_modules 밖)에 `'업무.` · `"업무.` · `` `업무. `` 꼴의 글자가 있으면 그 모듈 맨 앞에
 *    `import 'virtual:irobit-messages/<업무>'` 를 넣는다. 그 가상 모듈은 기본 언어(`ko`) 문구를 들고 와 `registerMessages` 로 등록한다
 *    → 문구가 그 화면 조각과 같이 실려 오고, 화면을 그리기 전에 등록된다. 여러 화면이 같이 쓰는 업무는 번들러가 공통 조각으로 뺀다.
 *    키를 상수로 들고 다니는 경우(`labelKey: 'menu.home'`)도 그 상수가 있는 모듈에서 잡힌다. `.` 뒤 첫마디가 그 업무 파일의 맨 위 키일 때만 문구 키로 본다
 *    — 권한 코드(`'team.read'`)처럼 꼴만 같은 글자를 거른다(관리자 메뉴가 권한 코드를 모아 두어 모든 업무 문구를 첫 화면으로 끌고 왔다).
 *    **키 경로 단위로 싣는다(2026-11-17)** — `'business.home.title'` 만 쓰는 모듈은 `virtual:irobit-messages-part/business/home.title` 로 그 한 줄만 가져온다
 *    (코드에 적힌 경로를 문구 파일에서 따라가 있는 데까지 — `'business.status.' + x`·`` `business.status.${x}` `` 는 `status` 묶음 통째)
 *    (상단 바·푸터처럼 늘 뜨는 셸이 키 한두 개 때문에 업무 문구 통째를 첫 화면으로 끌고 왔다 — 홈 첫 방문 문구 gzip 약 95KB). 첫마디가 비었거나(`'board.' + x`)
 *    문구 파일을 빌드 때 값으로 읽지 못하면 예전처럼 업무 통째(`virtual:irobit-messages/<업무>`), `${` 로 끊긴 앞마디(`grade.level${n}`)는 그 앞마디로 시작하는 같은 층의 키들.
 *    문구 파일은 `export default { … }` 한 덩어리의 순수 값이어야 한다(가져오기·함수·TS 문법 없이 — 빌드가 값으로 읽는다. 못 읽으면 통째로 싣는다).
 *    일부 키만 받은 업무에서 코드가 못 찾은 키를 만나면 실행 쪽이 업무 통째를 받는다(3번과 같다).
 * 3. 다른 언어(`en`)와, 코드에서 못 찾은 키(서버가 내려주는 `nameKey` 등)는 실행 중에 업무 단위로 받는다(`virtual:irobit-messages-loaders` — `runtime/business-messages.ts`).
 *
 * ⚠ `.vue?macro=true`(페이지 `definePageMeta` 를 뽑는 모듈 — 경로 표가 엔트리에서 모두 import 한다)에는 넣지 않는다. 넣으면 모든 화면의 문구가 엔트리로 다시 몰린다.
 */
import { existsSync, readFileSync, readdirSync, realpathSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { addTypeTemplate, addVitePlugin, defineNuxtModule } from 'nuxt/kit';

/** 문구 등록 가상 모듈 접두어 — 뒤에 업무 이름 */
const MESSAGES = 'virtual:irobit-messages/';
/** 업무·언어별 늦게 받기 함수 표 */
const LOADERS = 'virtual:irobit-messages-loaders';
/**
 * 늦게 받을 때 부르는 다시 내보내기 모듈 접두어 — 뒤에 `<업무>/<언어>`. 문구 파일을 `import()` 로 바로 부르면, 그 파일이 화면 조각에도 정적으로 묶여 있어
 * 번들러가 모듈 이름공간 객체를 만드는 도우미(`rolldown/runtime`)를 문구 조각마다 쓰게 된다 — 그 도우미가 큰 조각(관리자 화면: ECharts)에 들어가면
 * 첫 화면이 그 조각을 통째로 받는다(2026-11-12). `export default` 한 줄짜리 모듈을 거치면 이름 하나만 가져와 도우미가 필요 없다.
 */
const LAZY = 'virtual:irobit-messages-lazy/';
/** 업무의 키 경로 하나만 등록하는 가상 모듈 접두어 — 뒤에 `<업무>/<점으로 이은 키 경로(encodeURIComponent)>` */
const PART = 'virtual:irobit-messages-part/';
/** 화면 조각과 같이 싣는 언어 — `i18n.config.ts` 의 `fallbackLocale` 과 같다 */
const DEFAULT_LOCALE = 'ko';
/** 코드 안의 키 꼴 — 따옴표·백틱 바로 뒤의 `업무.첫마디`(첫마디는 비거나 `${` 로 끊길 수 있다) */
const KEY_PREFIX = /['"`]([A-Za-z][A-Za-z0-9]*)\.([A-Za-z0-9_$]*)((?:\.[A-Za-z0-9_$]*)*)/g;
/** 문구 파일의 맨 위 키 줄 — `export default {` 바로 안(4칸 들여쓰기) */
const TOP_KEY = /^ {4}(?:([A-Za-z0-9_$]+)|'([^']+)'|"([^"]+)")\??\s*:/gm;

/**
 * 문구 파일의 맨 위 키를 읽는다 — 키처럼 생겼지만 문구가 아닌 글자(권한 코드 `'team.read'`·`'batch.read'` 등)를 걸러 낸다.
 * (그런 글자까지 잡으면 관리자 메뉴처럼 권한 코드를 모아 둔 곳이 모든 업무 문구를 첫 화면으로 끌고 온다.)
 */
function topKeys(path: string): Set<string> {
    const keys = new Set<string>();
    for (const match of readFileSync(path, 'utf8').matchAll(TOP_KEY)) keys.add(match[1] ?? match[2] ?? match[3] ?? '');
    keys.delete('');
    return keys;
}
/**
 * 문구 파일을 값으로 읽는다 — `export default { … }` 한 덩어리의 순수 값(주석은 괜찮다). 읽지 못하면 null(그 업무는 통째로 싣는다).
 */
function evaluate(path: string): Record<string, unknown> | null {
    try {
        const source = readFileSync(path, 'utf8');
        if (!/^export default\s*\{/m.test(source) || /^\s*import\s/m.test(source)) return null;
        const value: unknown = new Function(source.replace(/^export default/m, 'return'))();
        return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
    } catch {
        return null;
    }
}
/** 키를 찾아볼 모듈(스크립트·SFC) */
const SOURCE = /\.(vue|ts|tsx|js|mjs)$/;

const toPosix = (path: string) => path.replaceAll('\\', '/');
/** 실제 경로 — 레이어가 node_modules 의 링크로 연결돼도 번들러가 보는 경로와 같게(같은 저장소 모듈이 둘로 갈라지지 않게) */
const real = (path: string) => toPosix(realpathSync(path));

export default defineNuxtModule({
    meta: { name: 'irobit:business-messages' },
    setup(_options, nuxt) {
        const registry = real(fileURLToPath(new URL('../runtime/business-messages.ts', import.meta.url)));

        /** 업무 이름 → 언어 → 파일 */
        const files = new Map<string, Map<string, string>>();
        for (const layer of nuxt.options._layers) {
            const root = resolve(layer.config.rootDir, 'i18n', 'locales');
            if (!existsSync(root)) continue;
            for (const dir of readdirSync(root, { withFileTypes: true })) {
                if (!dir.isDirectory()) continue;
                const locale = dir.name;
                for (const file of readdirSync(join(root, locale))) {
                    if (!file.endsWith('.ts') || file === 'index.ts') continue;
                    const name = file.slice(0, -'.ts'.length);
                    const byLocale = files.get(name) ?? new Map<string, string>();
                    const path = real(join(root, locale, file));
                    const other = byLocale.get(locale);
                    // 레이어 순서(앱 → 레이어)로 같은 파일 경로가 다시 올 수 있다 — 다른 파일이면 키가 겹친다
                    if (other && other !== path) {
                        throw new Error(`[irobit:business-messages] 문구 업무 '${name}'(${locale}) 이 두 곳에 있습니다 — ${other} · ${path}. 업무 이름(파일 이름)은 레이어를 통틀어 하나여야 합니다.`);
                    }
                    byLocale.set(locale, path);
                    files.set(name, byLocale);
                }
            }
        }
        for (const [name, byLocale] of files) {
            if (!byLocale.has(DEFAULT_LOCALE)) throw new Error(`[irobit:business-messages] 문구 업무 '${name}' 에 ${DEFAULT_LOCALE} 파일이 없습니다: ${[...byLocale.values()].join(', ')}`);
        }

        const workspace = real(nuxt.options.workspaceDir).replace(/\/?$/, '/');

        /** 업무 → 기본 언어 문구 값(처음 볼 때 읽는다 — 못 읽으면 null) */
        const values = new Map<string, Record<string, unknown> | null>();
        const valueOf = (name: string) => {
            if (!values.has(name)) values.set(name, evaluate(files.get(name)!.get(DEFAULT_LOCALE)!));
            return values.get(name) ?? null;
        };
        /**
         * 코드의 `업무.경로` 가 가리키는 키 경로들(문구 파일에 있는 데까지) — 업무 통째가 필요하면(첫마디가 비었거나 문구 파일을 값으로 못 읽음) null.
         * 문구 키가 아니라고 본 글자(`isMessageKey` 가 false)에는 부르지 않는다.
         */
        const keysFor = (name: string, segment: string, rest = ''): string[] | null => {
            const value = valueOf(name);
            if (!value) return null;
            const parts = [segment, ...rest.split('.').slice(1)];
            if (parts.length > 1 && parts[parts.length - 1] === '') parts.pop(); // 'board.status.' + x — 앞마디까지
            let node: unknown = value;
            const path: string[] = [];
            for (const part of parts) {
                const obj = node && typeof node === 'object' ? (node as Record<string, unknown>) : null;
                if (!obj) break;
                const cut = part.indexOf('$');
                const head = cut < 0 ? part : part.slice(0, cut);
                if (cut >= 0 || head === '') {
                    if (head === '') return path.length ? [path.join('.')] : null;
                    const hits = Object.keys(obj).filter((key) => key.startsWith(head)).map((key) => [...path, key].join('.'));
                    return hits.length ? hits : path.length ? [path.join('.')] : null;
                }
                if (!Object.hasOwn(obj, head)) break;
                path.push(head);
                node = obj[head];
            }
            return path.length ? [path.join('.')] : null;
        };
        /** 업무 → 맨 위 키(기본 언어 파일 기준, 처음 볼 때 읽는다) */
        const tops = new Map<string, Set<string>>();
        /**
         * `업무.첫마디` 가 그 업무의 문구 키인가 — 첫마디가 맨 위 키이면 그렇다.
         * 첫마디가 비었거나(`'board.' + x`) `${` 로 끊긴 앞마디(`grade.level${n}`)는 앞이 맞는 키가 있으면 그렇다고 본다(모르면 싣는 쪽 — 문구가 조금 더 실릴 뿐이다).
         */
        const isMessageKey = (name: string, segment: string): boolean => {
            let keys = tops.get(name);
            if (!keys) tops.set(name, (keys = topKeys(files.get(name)!.get(DEFAULT_LOCALE)!)));
            if (!keys.size) return true;
            const cut = segment.indexOf('$');
            if (cut < 0) return keys.has(segment) || segment === '';
            const head = segment.slice(0, cut);
            return [...keys].some((key) => key.startsWith(head));
        };

        addVitePlugin({
            name: 'irobit:business-messages',
            enforce: 'post',
            resolveId(id) {
                if (id === LOADERS || id.startsWith(MESSAGES) || id.startsWith(LAZY) || id.startsWith(PART)) return '\0' + id;
                return null;
            },
            load(id) {
                if (id === '\0' + LOADERS) {
                    const rows = [...files].map(([name, byLocale]) => {
                        const each = [...byLocale.keys()].map((locale) => `${JSON.stringify(locale)}: () => import(${JSON.stringify(`${LAZY}${name}/${locale}`)})`);
                        return `    ${JSON.stringify(name)}: { ${each.join(', ')} },`;
                    });
                    return `export default {\n${rows.join('\n')}\n};\n`;
                }
                if (id.startsWith('\0' + LAZY)) {
                    const [name = '', locale = ''] = id.slice(1 + LAZY.length).split('/');
                    const path = files.get(name)?.get(locale);
                    if (!path) return null;
                    return `import messages from ${JSON.stringify(path)};\nexport default messages;\n`;
                }
                if (id.startsWith('\0' + PART)) {
                    const rest = id.slice(1 + PART.length);
                    const slash = rest.indexOf('/');
                    const name = rest.slice(0, slash);
                    const keyPath = decodeURIComponent(rest.slice(slash + 1)).split('.');
                    let node: unknown = valueOf(name);
                    for (const part of keyPath) node = node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined;
                    if (node === undefined) return null;
                    const nested = keyPath.reduceRight<unknown>((inner, part) => ({ [part]: inner }), node);
                    return [
                        `import { registerMessages } from ${JSON.stringify(registry)};`,
                        `registerMessages(${JSON.stringify(name)}, ${JSON.stringify(DEFAULT_LOCALE)}, ${JSON.stringify(nested)}, false);`,
                        '',
                    ].join('\n');
                }
                if (id.startsWith('\0' + MESSAGES)) {
                    const name = id.slice(1 + MESSAGES.length);
                    const path = files.get(name)?.get(DEFAULT_LOCALE);
                    if (!path) return null;
                    return [
                        `import { registerMessages } from ${JSON.stringify(registry)};`,
                        `import messages from ${JSON.stringify(path)};`,
                        `registerMessages(${JSON.stringify(name)}, ${JSON.stringify(DEFAULT_LOCALE)}, messages);`,
                        '',
                    ].join('\n');
                }
                return null;
            },
            transform(code, id) {
                const file = toPosix(id.split('?')[0] ?? id);
                if (!file.startsWith(workspace) || file.includes('/node_modules/') || file.includes('/i18n/locales/') || file === registry) return null;
                if (!SOURCE.test(file) || id.includes('macro=true') || id.includes('type=style')) return null;
                /** 업무 → 쓰는 맨 위 키들(null = 업무 통째) */
                const used = new Map<string, Set<string> | null>();
                for (const match of code.matchAll(KEY_PREFIX)) {
                    const name = match[1];
                    if (!name || !files.has(name) || used.get(name) === null) continue;
                    const segment = match[2] ?? '';
                    // 첫마디가 빈 꼴은 따옴표가 바로 닫힐 때만 키 조립(`'board.' + x`)이다 — 주석의 `'business.…'` 같은 글자는 키가 아니다(업무 통째를 끌고 왔다)
                    if (segment === '' && !/['"`]/.test(code.charAt((match.index ?? 0) + match[0].length))) continue;
                    if (!isMessageKey(name, segment)) continue;
                    const keys = keysFor(name, segment, match[3] ?? '');
                    if (keys === null) {
                        used.set(name, null);
                        continue;
                    }
                    const set = used.get(name) ?? new Set<string>();
                    for (const key of keys) set.add(key);
                    used.set(name, set);
                }
                if (!used.size) return null;
                const ids: string[] = [];
                for (const [name, keys] of used) {
                    if (keys === null) ids.push(MESSAGES + name);
                    else for (const key of keys) ids.push(`${PART}${name}/${encodeURIComponent(key)}`);
                }
                const imports = ids
                    .sort()
                    .map((each) => `import ${JSON.stringify(each)};`)
                    .join('');
                // 첫 줄 앞에만 붙인다(줄 번호는 그대로) — 소스맵은 앞 단계 것을 쓴다
                return { code: imports + code, map: null };
            },
        });

        addTypeTemplate({
            filename: 'types/irobit-messages.d.ts',
            getContents: () =>
                [
                    `declare module '${LOADERS}' {`,
                    '    const loaders: Record<string, Partial<Record<string, () => Promise<{ default: Record<string, unknown> }>>>>;',
                    '    export default loaders;',
                    '}',
                    `declare module '${MESSAGES}*' {}`,
                    `declare module '${PART}*' {}`,
                    '',
                ].join('\n'),
        });
    },
});
