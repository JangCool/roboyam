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
/** 화면 조각과 같이 싣는 언어 — `i18n.config.ts` 의 `fallbackLocale` 과 같다 */
const DEFAULT_LOCALE = 'ko';
/** 코드 안의 키 꼴 — 따옴표·백틱 바로 뒤의 `업무.첫마디`(첫마디는 비거나 `${` 로 끊길 수 있다) */
const KEY_PREFIX = /['"`]([A-Za-z][A-Za-z0-9]*)\.([A-Za-z0-9_$]*)/g;
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
                if (id === LOADERS || id.startsWith(MESSAGES) || id.startsWith(LAZY)) return '\0' + id;
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
                const used = new Set<string>();
                for (const match of code.matchAll(KEY_PREFIX)) {
                    const name = match[1];
                    if (!name || used.has(name) || !files.has(name)) continue;
                    if (isMessageKey(name, match[2] ?? '')) used.add(name);
                }
                if (!used.size) return null;
                const imports = [...used]
                    .sort()
                    .map((name) => `import ${JSON.stringify(MESSAGES + name)};`)
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
                    '',
                ].join('\n'),
        });
    },
});
