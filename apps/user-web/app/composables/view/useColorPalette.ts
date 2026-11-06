import type { ColorPalette } from '~/utils/util-color-palette';
import UtilColorPalette from '~/utils/util-color-palette';

/**
 * 색상 테마(보기 설정 「색상」) — 화면 전체가 공유하는 값과 바꾸는 방법. `<html data-palette>`·`<html data-topbar-gradient>` 에 거는 것은 `plugins/view-theme.client.ts` 다.
 *  - `palette`: 색상 테마 7가지
 *  - `gradient`: 상단 그라데이션(로고 줄 위쪽에서 내려오는 그림자 띠, 색은 테마를 따른다 — 사용자 결정 2026-11-06)
 * ⚠ MOCK: 서버 보기 설정(`useViewSettings`)에 칸이 없어 이 브라우저(`localStorage`)에만 저장한다 — 로그인해 있어도 다른 기기로 따라가지 않는다.
 * 저장이 막혀 있으면 이번 방문 동안만 쓴다.
 */
function readStored(key: string): string | null {
    if (typeof window === 'undefined') return null;
    try {
        return window.localStorage.getItem(key);
    } catch {
        return null;
    }
}

function writeStored(key: string, value: string): 'device' | 'memory' {
    try {
        window.localStorage.setItem(key, value);
        return 'device';
    } catch {
        return 'memory';
    }
}

export function useColorPalette() {
    const palette = useState<ColorPalette>('color-palette', () => UtilColorPalette.sanitize(readStored(UtilColorPalette.STORAGE_KEY)));
    const gradient = useState<boolean>('color-palette-gradient', () => readStored(UtilColorPalette.GRADIENT_STORAGE_KEY) === 'on');

    /**
     * 색상 테마를 바꾼다 — 화면에 곧바로 반영하고 이 브라우저에 남긴다.
     * @returns 어디에 저장됐나 — 이 기기 · 이번 방문 동안만
     */
    function set(next: ColorPalette): 'device' | 'memory' {
        palette.value = UtilColorPalette.sanitize(next);
        return writeStored(UtilColorPalette.STORAGE_KEY, palette.value);
    }

    /** 상단 그라데이션을 켜고 끈다 */
    function setGradient(on: boolean): 'device' | 'memory' {
        gradient.value = on;
        return writeStored(UtilColorPalette.GRADIENT_STORAGE_KEY, on ? 'on' : 'off');
    }

    return { palette: readonly(palette), gradient: readonly(gradient), set, setGradient };
}
