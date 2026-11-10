/**
 * 오픈소스 라이선스 수집(`scripts/collect-licenses.mjs`)용 Nuxt 모듈 — 화면 빌드(클라이언트 번들)에 실제로 들어간 바깥 패키지의 폴더를 적는다.
 *
 * - 환경 변수 `ROBOYAM_OSS_SCAN`(적을 파일의 절대 경로)이 있을 때만 일한다. 개발 서버·보통 빌드에서는 아무것도 하지 않는다.
 * - 클라이언트 빌드(Vite 환경 `client`)가 읽은 모듈 경로에서 `node_modules/<이름>` 꼴의 패키지 폴더만 모은다 — 워크스페이스 패키지(`apps-packages/*`)는
 *   실제 경로가 node_modules 밖이라 저절로 빠지고, 그 패키지들이 끌어온 바깥 의존성은 node_modules 안이라 잡힌다. 빌드 도구(Vite·Nuxt 빌더 등)는 번들에 모듈로 들어가지 않아 빠진다.
 * - 적는 모양: `{ "packages": ["<패키지 폴더 절대 경로>", …] }`(차례 정렬).
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

const MARK = '/node_modules/';

/** 모듈 경로 → 패키지 폴더(없으면 null) — 마지막 `node_modules/` 뒤의 이름(범위 이름이면 두 마디) */
function packageRoot(id) {
    if (!id || id.startsWith('\0')) return null;
    const path = id.split('?')[0].replaceAll('\\', '/');
    const at = path.lastIndexOf(MARK);
    if (at < 0) return null;
    const rest = path.slice(at + MARK.length).split('/');
    const take = rest[0]?.startsWith('@') ? 2 : 1;
    if (rest.length <= take || !rest[0] || rest[0] === '.pnpm' || rest[0] === '.cache') return null;
    return path.slice(0, at + MARK.length) + rest.slice(0, take).join('/');
}

export default function ossBundleScan(_options, nuxt) {
    const out = process.env.ROBOYAM_OSS_SCAN;
    if (!out) return;
    const roots = new Set();
    const plugin = {
        name: 'roboyam-oss-bundle-scan',
        apply: 'build',
        applyToEnvironment: (environment) => environment.name === 'client',
        buildEnd() {
            for (const id of this.getModuleIds()) {
                const root = packageRoot(id);
                if (root) roots.add(root);
            }
            mkdirSync(dirname(out), { recursive: true });
            writeFileSync(out, JSON.stringify({ packages: [...roots].sort() }, null, 2));
        },
    };
    nuxt.options.vite.plugins = [...(nuxt.options.vite.plugins ?? []), plugin];
}
