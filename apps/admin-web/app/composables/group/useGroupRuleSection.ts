import type { Ref } from 'vue';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupRuleRange, GroupRuleResponseBase } from '~/composables/group/api/useGroupSettingsApi.types';
import type { GroupRuleForm, GroupRuleOrder, GroupRuleProblem } from '~/utils/util-group-rule';
import UtilGroupRule from '~/utils/util-group-rule';
import UtilGroupMessages from '~/utils/util-group-messages';

/**
 * 소모임 설정 구획 하나의 상태 — 불러오기 · 입력 · 미리 검사 · 저장. 개설 규칙·매니저·공개 범위·운영 한도 네 구획(`components/group/Group*RuleSection.vue`)이 같이 쓴다.
 * 상태는 구획마다 따로다(`useState` 가 아니라 `ref` — 화면을 떠나면 버린다). 서버 호출은 넘겨받은 `load`·`save`(`useGroupSettingsApi` 메서드)가 한다.
 *
 * - 저장은 **이 구획의 칸 모두**를 한 번에 보낸다(구획마다 따로 저장 — `forms.md` 2-4). 확인 창이 필요한 저장(끄기·켜기 등)은 구획이 `save()` 를 부르기 전에 묻는다.
 * - 서버가 거절한 칸(`400 VALIDATION fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 구획 안 한 줄(`saveError`). 그 칸을 고치면 서버 문구를 지운다.
 * - 서버가 거절한 칸은 저장을 막지 않는다(서버가 다시 판정한다). 화면이 미리 찾은 문제(범위·순서)는 저장을 막는다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다(오류 변환·번역 함수를 그때 붙잡는다).
 */
export interface GroupRuleSectionOptions<V extends object, R extends GroupRuleResponseBase<V>> {
    load: () => Promise<R>;
    save: (values: V) => Promise<R>;
    /** 숫자 칸의 범위 — 서버 `limits` 에서 */
    ranges: (response: R) => Record<string, GroupRuleRange>;
    /** 두 칸의 크기 순서 규칙 */
    orders?: readonly GroupRuleOrder[];
}

export function useGroupRuleSection<V extends object, R extends GroupRuleResponseBase<V>>(options: GroupRuleSectionOptions<V, R>) {
    const t = useTranslate();
    const toApiError = useApiErrorOf();

    /** 서버가 준 값. null = 아직 못 받았다 */
    const saved = ref<R | null>(null) as Ref<R | null>;
    const form = ref<GroupRuleForm>({});
    const loading = ref(false);
    const loadError = ref<ApiError | null>(null);
    const saving = ref(false);
    /** 칸에 붙지 않는 저장 오류 */
    const saveError = ref('');
    /** 방금 저장했다(저장 줄이 「저장했어요」를 보인다) — 다시 고치면 꺼진다 */
    const justSaved = ref(false);
    /** 서버가 거절한 칸별 문구 */
    const serverErrors = ref<Record<string, string>>({});

    const problems = computed<Partial<Record<string, GroupRuleProblem>>>(() => (saved.value ? UtilGroupRule.problems(form.value, options.ranges(saved.value), options.orders) : {}));
    const changedFields = computed(() => (saved.value ? UtilGroupRule.changedFields(saved.value.values, form.value) : []));
    const changeCount = computed(() => changedFields.value.length);
    const errorCount = computed(() => {
        if (!saved.value) return 0;
        return Object.keys(saved.value.values).filter((key) => problems.value[key] || serverErrors.value[key]).length;
    });
    const canSave = computed(() => !saving.value && changeCount.value > 0 && Object.keys(problems.value).length === 0);

    function apply(response: R): void {
        saved.value = response;
        form.value = UtilGroupRule.formOf(response.values);
        serverErrors.value = {};
        saveError.value = '';
    }

    async function load(): Promise<void> {
        loading.value = true;
        loadError.value = null;
        try {
            apply(await options.load());
        } catch (e) {
            loadError.value = toApiError(e);
        } finally {
            loading.value = false;
        }
    }

    /** 칸 하나를 바꾼다 — 그 칸의 서버 문구를 지운다 */
    function set(key: string, value: string | boolean): void {
        form.value = { ...form.value, [key]: value };
        justSaved.value = false;
        if (!serverErrors.value[key]) return;
        const { [key]: _removed, ...rest } = serverErrors.value;
        serverErrors.value = rest;
    }

    /** 칸 하나를 기본값(서버 `defaults`)으로 */
    function resetField(key: string): void {
        if (!saved.value) return;
        const value = UtilGroupRule.formOf(saved.value.defaults)[key];
        if (value !== undefined) set(key, value);
    }

    /** 이 칸이 기본값과 다른가 */
    function differs(key: string): boolean {
        if (!saved.value) return false;
        return !UtilGroupRule.same(form.value[key], (saved.value.defaults as Record<string, unknown>)[key]);
    }

    /** 이 칸의 오류 — 화면이 찾은 문제가 먼저(문구는 구획이 만든다), 없으면 서버 문구 */
    function serverError(key: string): string {
        return serverErrors.value[key] ?? '';
    }

    function revert(): void {
        if (!saved.value) return;
        form.value = UtilGroupRule.formOf(saved.value.values);
        serverErrors.value = {};
        saveError.value = '';
    }

    /**
     * 저장 — 칸 모두를 보낸다.
     * @returns 저장했으면 서버 응답, 아니면 null(오류는 칸 아래·`saveError` 에 섰다).
     */
    async function save(): Promise<R | null> {
        if (!saved.value || !canSave.value) return null;
        const values = UtilGroupRule.toValues(saved.value.values, form.value, problems.value);
        if (!values) return null;
        saving.value = true;
        saveError.value = '';
        serverErrors.value = {};
        try {
            const response = await options.save(values);
            apply(response);
            justSaved.value = true;
            return response;
        } catch (e) {
            const rejected = UtilGroupRule.fieldErrorsOf(e, Object.keys(saved.value.values));
            if (rejected) {
                serverErrors.value = rejected.fields;
                saveError.value = [Object.keys(rejected.fields).length ? t('group.ruleSaveFieldsRejected') : '', rejected.rest].filter(Boolean).join(' ');
            } else {
                saveError.value = `${t('group.ruleSaveFailed')} ${UtilGroupMessages.of(t, e)}`;
            }
            return null;
        } finally {
            saving.value = false;
        }
    }

    return { saved, form, loading, loadError, saving, saveError, justSaved, problems, changedFields, changeCount, errorCount, canSave, load, set, resetField, differs, serverError, revert, save };
}
