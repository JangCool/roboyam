/**
 * 앱에 딸린 그림(`public/brand`·`public/avatars`·`public/emoticons`)의 <b>내용 해시 목록</b>을 빌드 때 만들어 화면 코드에 싣는 Nuxt 모듈 — user-web · admin-web 이 `modules` 에 둔다.
 *
 * 화면이 이 그림 주소를 직접 만드는 곳(상단 기본 로고·대체 아바타·소개 화면·로보티콘 안내·관리자 아바타 미리보기)은 `assetUrl('/brand/…')` 로 내용 해시를 붙인다:
 *   import { assetUrl } from 'virtual:static-assets/core'   →   '/brand/irobit-logo-ko-light.svg?v=1a2b3c4d5e'
 * 서버(`StaticAssetUrls` — SHA-256 앞 10자리)와 같은 규칙이라 같은 그림이면 같은 주소가 되어 한 번만 받는다. 해시가 붙은 주소는 서버가 1년 + immutable 로 내린다
 * (`StaticAssetCacheInterceptor`, 사용자 결정 2026-11-14 — 그림이 바뀌면 주소가 바뀐다).
 *
 * 목록은 묶음별 가상 모듈로 나눈다 — 첫 화면에 실리는 것은 작은 `core`(로고·마스코트·대체 아바타)뿐이고, 큰 목록(아바타 340여 종·로보티콘)은 쓰는 화면 조각에만 실린다.
 *   - `virtual:static-assets/core`      `/brand/**` + `/avatars/irobit/basic-default.svg`
 *   - `virtual:static-assets/avatars`   `/avatars/**`
 *   - `virtual:static-assets/emoticons` `/emoticons/**`
 * 목록에 없는 주소는 그대로 돌려준다(서버도 해시 없는 주소는 하루 캐시).
 */
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';

const FOLDERS = ['brand', 'avatars', 'emoticons'];
const HASH_LENGTH = 10;
const PREFIX = 'virtual:static-assets/';
const GROUPS = {
    core: (path) => path.startsWith('/brand/') || path === '/avatars/irobit/basic-default.svg',
    avatars: (path) => path.startsWith('/avatars/'),
    emoticons: (path) => path.startsWith('/emoticons/'),
};

function files(dir) {
    const out = [];
    for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        if (statSync(full).isDirectory()) out.push(...files(full));
        else out.push(full);
    }
    return out;
}

function versionsOf(publicDir) {
    const map = {};
    for (const folder of FOLDERS) {
        const dir = join(publicDir, folder);
        if (!existsSync(dir)) continue;
        for (const file of files(dir)) {
            const path = '/' + relative(publicDir, file).split(sep).join('/');
            map[path] = createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, HASH_LENGTH);
        }
    }
    return map;
}

function moduleSource(versions) {
    return `const VERSIONS = ${JSON.stringify(versions)};
export function assetUrl(path) {
    const cut = path.indexOf('#');
    const bare = cut < 0 ? path : path.slice(0, cut);
    const version = VERSIONS[bare];
    return version ? bare + '?v=' + version + (cut < 0 ? '' : path.slice(cut)) : path;
}
`;
}

export default function staticAssetVersions(_options, nuxt) {
    const publicDir = resolve(nuxt.options.rootDir, nuxt.options.dir?.public ?? 'public');
    let versions = null;

    const plugin = {
        name: 'irobit-static-asset-versions',
        resolveId(id) {
            return id.startsWith(PREFIX) ? '\0' + id : null;
        },
        load(id) {
            if (!id.startsWith('\0' + PREFIX)) return null;
            const group = id.slice(PREFIX.length + 1);
            const pick = GROUPS[group];
            if (!pick) throw new Error(`모르는 그림 묶음: ${group} (core·avatars·emoticons)`);
            versions ??= versionsOf(publicDir);
            return moduleSource(Object.fromEntries(Object.entries(versions).filter(([path]) => pick(path))));
        },
    };

    nuxt.hook('vite:extendConfig', (config) => {
        config.plugins ??= [];
        config.plugins.push(plugin);
    });
    // 타입 — 별도 선언 파일(전역)로 둔다(nuxt.d.ts 본문은 모듈이라 와일드카드 모듈 선언을 받지 않는다)
    nuxt.hook('prepare:types', ({ references }) => {
        const file = join(nuxt.options.buildDir, 'types', 'static-assets.d.ts');
        mkdirSync(dirname(file), { recursive: true });
        writeFileSync(
            file,
            `declare module 'virtual:static-assets/*' {
  /** 앱에 딸린 그림 주소에 내용 해시(?v=)를 붙인다 — 목록에 없으면 그대로(scripts/nuxt/static-asset-versions.mjs) */
  export function assetUrl(path: string): string;
}
`,
        );
        references.push({ path: file });
    });
}
