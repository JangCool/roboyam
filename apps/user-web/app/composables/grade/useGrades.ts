import type { GradeSpriteKey } from '~/composables/grade/api/useGradeApi.types';

/** 등급 하나를 그릴 값 — 이름 · 서열 · 도트 */
export interface GradeLook {
    key: string;
    label: string;
    level: number;
    sprite: GradeSpriteKey;
}

/**
 * 사이트 정보에 등급 목록이 없을 때(옛 서버)의 기본 4키 — 11단계의 서열 1·3·6·9 자리 도트(grade-level-contract 2절 전환 규칙). 이름은 서버가 준 이름, 없으면 기본 이름 키
 */
const FALLBACK: Readonly<Record<string, { level: number; sprite: GradeSpriteKey; nameKey: string }>> = {
    SEEDLING: { level: 1, sprite: 'SPROUT', nameKey: 'shell.gradeSeedling' },
    REGULAR: { level: 3, sprite: 'ANTENNA', nameKey: 'shell.gradeRegular' },
    LOYAL: { level: 6, sprite: 'HEART', nameKey: 'shell.gradeLoyal' },
    VETERAN: { level: 9, sprite: 'CROWN', nameKey: 'shell.gradeVeteran' },
};

/**
 * 등급 키 → 이름·도트(grade-level-contract 3·5절). 따로 받지 않고 공개 사이트 정보(`GET /api/site/info` 의 `grades[]`, 앱 시작 때 한 번 받는다)를 읽는다.
 * 목록에 없는 키: 기본 4키(`SEEDLING`·`REGULAR`·`LOYAL`·`VETERAN`)는 서열 1·3·6·9 의 도트로, 그 밖(운영자가 만든 `GRADE_n` 을 목록 없이 받은 경우)은 null — 화면은 도트를 그리지 않는다.
 * setup 에서 부른다.
 */
export function useGrades() {
    const { t } = useI18n();
    const state = useSiteInfoState();

    /**
     * @param {string | null | undefined} key - 등급 키(작성자·회원의 `grade`).
     * @param {string | null | undefined} [label] - 서버가 같이 준 등급 이름(`gradeLabel`) — 목록에 없는 키의 이름으로 쓴다.
     * @returns {GradeLook | null} 그릴 값. 키가 없거나 모르는 키면 null.
     */
    function gradeOf(key: string | null | undefined, label?: string | null): GradeLook | null {
        if (!key) return null;
        const found = state.value?.grades?.find((g) => g.key === key);
        if (found) return found;
        const fallback = FALLBACK[key];
        if (!fallback) return null;
        return { key, label: label || t(fallback.nameKey), level: fallback.level, sprite: fallback.sprite };
    }

    return { gradeOf };
}
