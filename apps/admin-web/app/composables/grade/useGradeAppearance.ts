import type { ApiError } from '~/composables/common/useApiClient';
import type { Grade, GradeKey } from '~/composables/grade/api/useGradeApi.types';
import UtilGrade from '~/utils/util-grade';

/**
 * 등급 목록(이름·서열·승급 조건·뱃지)을 앱 전체에서 **한 번 읽어 공유**하는 상태 — 회원 관리의 표·필터·상세, 역할 관리의 등급 역할, 등급 설정이 같은 값을 본다.
 * 등급은 순서 있는 목록이라(4단계 고정이 아니다) 화면은 이 목록에서 등급을 그린다. 등급 설정이 저장하면 `set` 으로 이 상태를 새 값으로 바꾸므로 다른 화면은 SPA 이동 후 바로 반영된다.
 *
 * `undefined` = 아직 안 물어봄, `null` = 읽지 못함(`error` 에 사유). 읽지 못하거나 목록에 없는 등급은 `badgeOf` 가 중립(gray) 뱃지 + 키 글자로 대신한다.
 * `ensureLoaded()` 는 읽은 적이 없거나 읽지 못했을 때만 요청한다(동시에 여럿이 불러도 요청은 하나). 이 상태 컴포저블이 서버 호출(`useGradeApi`)을 부르는 것은 "이미 받았으면 다시 묻지 않는" 공유 읽기 한 곳을 두기 위해서다.
 */
export function useGradeAppearance() {
    const api = useGradeApi();
    const list = useState<Grade[] | undefined | null>('grade-list', () => undefined);
    const error = useState<ApiError | null>('grade-list-error', () => null);
    const loading = useState<boolean>('grade-list-loading', () => false);

    /** 서열(level) 순서의 등급 목록. 아직 못 받았거나 읽지 못했으면 빈 목록 */
    const grades = computed<Grade[]>(() => [...(list.value ?? [])].sort((a, b) => a.level - b.level));

    /** 등급 목록을 받는다 — 이미 받았거나 받는 중이면 다시 묻지 않는다. 실패하면 `error` 에 사유를 두고 던지지 않는다 */
    async function ensureLoaded(): Promise<void> {
        if (Array.isArray(list.value) || loading.value) return;
        loading.value = true;
        try {
            list.value = (await api.load()).grades;
            error.value = null;
        } catch (e) {
            list.value = null;
            error.value = e as ApiError;
        } finally {
            loading.value = false;
        }
    }

    /** 등급 설정이 저장·조회한 최신 목록을 공유 상태에 올린다 */
    function set(next: Grade[]): void {
        list.value = next.map((grade) => ({ ...grade }));
        error.value = null;
    }

    /** 등급 하나 — 목록에 없으면 undefined */
    function gradeOf(key: GradeKey): Grade | undefined {
        return UtilGrade.gradeOf(list.value ?? [], key);
    }

    /**
     * 뱃지에 넘길 값 — `<GradeBadge v-bind="badgeOf(key)" />`. 로딩 전·읽기 실패·목록에 없는 등급은 중립(gray) 뱃지 + 키 글자로 대신한다.
     * @param {GradeKey} key - 등급 키.
     * @returns {{ label: string; color: string; icon: string | null }} 뱃지 값.
     */
    function badgeOf(key: GradeKey): { label: string; color: string; icon: string | null } {
        const grade = gradeOf(key);
        return grade ? { label: grade.label, color: grade.badgeColor, icon: grade.badgeIcon } : { label: key, ...UtilGrade.FALLBACK_BADGE };
    }

    return { grades, loading, error, ensureLoaded, set, gradeOf, badgeOf };
}
