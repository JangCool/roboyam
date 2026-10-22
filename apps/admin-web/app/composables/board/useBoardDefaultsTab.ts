import type { Ref } from 'vue';
import type { BoardDefaultsTabResponse } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue, BoardTabForm } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

/** 하위 화면이 넘기는 것 — 읽기·저장 한 쌍과 숫자 칸 밖의 검사 */
export interface BoardDefaultsTabSource<T extends object, R extends BoardDefaultsTabResponse<T>> {
    load: () => Promise<R>;
    save: (body: T) => Promise<R>;
    /** 숫자 칸 밖의 화면 검사(금지어·시각 글자 등) — 칸 → 보일 문구 */
    validate?: (form: BoardTabForm<T>) => Record<string, string>;
    /** 저장 직전 손질(금지어 글자 줄 → 정리된 목록 등) */
    prepare?: (body: T) => T;
    /** 저장 성공 토스트 문구 키 */
    savedKey: string;
}

/**
 * 게시판 기본 설정 하위 화면(신고 정책·보안·알림·개인정보 보유기간)의 **자기 요청·자기 저장** 흐름 — 네 화면이 같은 모양이라 한 곳에 둔다.
 * 읽기(로딩·오류) · 입력(숫자 칸은 글자) · 바뀐 칸 · 화면 검사 + 서버 `fields` 오류 · 저장(PUT 한 번) · 되돌리기 · 저장 안 한 변경이 있으면 나가기 전에 묻기.
 * 권한: 고치기 `board.settings`(없으면 화면이 저장 줄 대신 읽기 전용 안내). 서버 호출은 화면이 넘긴 `useBoardApi` 메서드만 부른다(요청 세부를 모른다).
 * ⚠ 컴포넌트 setup 에서 부른다(`onBeforeRouteLeave`·확인창을 그때 붙잡는다).
 */
export function useBoardDefaultsTab<T extends object, R extends BoardDefaultsTabResponse<T>>(source: BoardDefaultsTabSource<T, R>) {
    const t = useTranslate();
    const toast = useAppToast();
    const { confirm } = useConfirmDialog();
    const { can } = useAdminPermission();

    const canEdit = computed(() => can('board.settings'));
    /** 서버가 준 마지막 응답. `null` = 아직 못 받았다 */
    const response = shallowRef<R | null>(null);
    const form = ref(null) as Ref<BoardTabForm<T> | null>;
    const loading = ref(false);
    const loadError = ref('');
    const saving = ref(false);
    const formError = ref('');
    /** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
    const serverFields = ref<Record<string, string>>({});

    /** 화면 검사 — 숫자 칸 범위 + 화면이 넘긴 검사 */
    const localErrors = computed<Record<string, string>>(() => {
        if (!form.value) return {};
        const out: Record<string, string> = { ...(source.validate?.(form.value) ?? {}) };
        for (const [key, problem] of Object.entries(UtilBoard.tabProblems(form.value))) out[key] = UtilBoardMessages.numProblem(t, key, problem);
        return out;
    });
    const changed = computed<string[]>(() => (form.value && response.value ? UtilBoard.tabChanged(form.value, response.value.settings) : []));
    const dirty = computed(() => changed.value.length > 0);
    const errorCount = computed(() => new Set([...Object.keys(localErrors.value), ...Object.keys(serverFields.value)]).size);
    /** 서버가 거절한 칸은 저장을 막지 않는다 — 서버가 다시 판정한다 */
    const canSave = computed(() => canEdit.value && dirty.value && Object.keys(localErrors.value).length === 0);

    function apply(res: R): void {
        response.value = res;
        form.value = UtilBoard.tabFormOf(res.settings);
        serverFields.value = {};
    }

    async function load(): Promise<void> {
        loading.value = true;
        loadError.value = '';
        try {
            apply(await source.load());
        } catch (e) {
            loadError.value = UtilBoardMessages.of(t, e);
        } finally {
            loading.value = false;
        }
    }

    function revert(): void {
        if (!response.value) return;
        form.value = UtilBoard.tabFormOf(response.value.settings);
        serverFields.value = {};
        formError.value = '';
    }

    /** 칸 아래 오류 — 화면 검사가 먼저, 없으면 서버 문구 */
    function errorOf(key: string): string {
        return localErrors.value[key] ?? serverFields.value[key] ?? '';
    }

    /** 칸 하나를 바꾼다(부품 `BoardSettingField` 의 값) */
    function set(key: string, value: BoardFieldValue | unknown): void {
        if (!form.value) return;
        (form.value as Record<string, unknown>)[key] = value;
        clearServer(key);
    }

    function clearServer(key: string): void {
        if (!serverFields.value[key]) return;
        const { [key]: _removed, ...rest } = serverFields.value;
        serverFields.value = rest;
    }

    /** 구획의 바뀐 칸 수 · 오류 칸 수 */
    function changesIn(keys: readonly string[]): number {
        return keys.filter((k) => changed.value.includes(k)).length;
    }
    function invalidIn(keys: readonly string[]): number {
        return keys.filter((k) => Boolean(errorOf(k))).length;
    }

    async function save(): Promise<void> {
        if (saving.value || !canSave.value || !form.value) return;
        const values = UtilBoard.tabValuesOf<T>(form.value);
        if (!values) return;
        const body = source.prepare ? source.prepare(values) : values;
        formError.value = '';
        serverFields.value = {};
        saving.value = true;
        try {
            apply(await source.save(body));
            toast.success(t(source.savedKey));
        } catch (e) {
            const rejected = UtilBoard.fieldErrorsOf(e);
            if (rejected) {
                serverFields.value = rejected.fields;
                formError.value = t('board.saveFieldsRejected');
            } else {
                formError.value = `${t('board.saveFailed')} — ${UtilBoardMessages.of(t, e)}`;
            }
        } finally {
            saving.value = false;
        }
    }

    // 저장하지 않은 변경이 있는 채 다른 화면(다른 하위 메뉴 포함)으로 가려 하면 묻는다
    onBeforeRouteLeave(async () => {
        if (!dirty.value) return true;
        return confirm({ title: t('board.leaveTitle'), message: t('board.leaveMessage'), confirmLabel: t('board.leaveConfirm'), cancelLabel: t('board.leaveCancel'), tone: 'danger' });
    });

    return { canEdit, response, form, loading, loadError, saving, formError, serverFields, changed, dirty, errorCount, canSave, load, revert, save, errorOf, set, changesIn, invalidIn };
}
