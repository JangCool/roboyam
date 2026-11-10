<template>
    <!--
        [제재하기] 대화창 — 제재 화면 상단 단추·회원 상세 제재 구획이 연다(흐름은 `useSanctionIssue`, 서버 호출은 부른 화면이 한다).
        ① 회원 찾기(닉네임 2자 이상 — 이미 회원이 정해져 있으면 건너뛴다) → ② 현황·단계(`SanctionStepPicker` — 경고 n/2·진행 중·다음 단계·막힘, NEXT 권장·SKIP 은 `sanction.ban`+근거 확인·LOWER/BLOCKED 비활성,
        영구 정지는 닉네임 직접 입력) → ③ 사유 1~500자(회원에게 그대로 전달된다는 안내).
        서버 거절: `SANCTION_STEP_TOO_LOW`·`SANCTION_ALREADY_SUSPENDED`·`SANCTION_ALREADY_BANNED` 는 현황을 새로 받고 대화창 안에 서버 문구, `fields.*` 는 칸 아래, 그 밖은 대화창 안 한 줄. 쓴 사유는 그대로 둔다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :closable="!view.busy" :header="t('sanction.issueTitle')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- ① 회원 -->
            <section v-if="!view.member" class="flex flex-col gap-2" :aria-label="t('sanction.findMember')">
                <div class="flex gap-2">
                    <InputText
                        v-model="query"
                        type="search"
                        :maxlength="UtilSanction.SEARCH_MAX"
                        :placeholder="t('sanction.findPlaceholder')"
                        :aria-label="t('sanction.findMember')"
                        :invalid="Boolean(queryError || view.targetsError)"
                        autocomplete="off"
                        class="min-w-0 flex-1"
                        @keydown.enter.prevent="find"
                    />
                    <Button type="button" icon="pi pi-search" :label="t('common.search')" size="small" severity="secondary" variant="outlined" :loading="view.targetsLoading" @click="find" />
                </div>
                <p v-if="queryError || view.targetsError" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ queryError || view.targetsError }}</p>
                <p v-else-if="view.targets && view.targets.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('sanction.findEmpty') }}</p>
                <ul v-if="view.targets && view.targets.length" class="m-0 flex max-h-56 list-none flex-col divide-y divide-(--line) overflow-y-auto rounded-lg border border-(--line) p-0">
                    <li v-for="target in view.targets" :key="target.id">
                        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] hover:bg-(--p-content-hover-background) focus-visible:outline-2 focus-visible:outline-primary" @click="emit('pick', target)">
                            <span class="min-w-0 flex-1 truncate font-medium">{{ target.nickname }}</span>
                            <Tag v-if="target.status !== 'ACTIVE'" :value="t(`member.status.${target.status}`)" severity="danger" class="text-[11px]!" />
                            <span class="shrink-0 text-xs text-muted-color tabular-nums">{{ UtilSanctionMessages.warningCount(t, target.warningCount) }}</span>
                        </button>
                    </li>
                </ul>
            </section>

            <template v-else>
                <div class="flex items-center gap-2 rounded-lg bg-(--p-content-hover-background) px-3 py-2">
                    <!-- 봉인된 익명 작성자(「이 글 작성자」 — anonymous-board-contract)는 자물쇠 + 부른 화면이 준 이름 -->
                    <i :class="view.member.anonymous ? 'pi pi-lock' : 'pi pi-user'" class="text-muted-color" aria-hidden="true" />
                    <b class="min-w-0 flex-1 truncate text-sm">{{ view.member.nickname }}</b>
                    <Button v-if="!view.fixed" type="button" :label="t('sanction.changeMember')" size="small" severity="secondary" variant="text" :disabled="view.busy" @click="emit('unpick')" />
                </div>
                <p v-if="view.member.anonymous" class="m-0 text-xs leading-relaxed text-muted-color" data-testid="sanction-issue-sealed-note">{{ t('anonymousAuthor.sanctionNote') }}</p>

                <!-- ② 현황·단계 -->
                <ErrorState v-if="view.previewError" :title="t('sanction.previewFailed')" :description="view.previewError.message" :code="view.previewError.status === 404 ? '404' : '!'" :retry="view.previewError.status !== 404" :retrying="view.previewLoading" @retry="emit('retry-preview')" />
                <div v-else-if="!view.preview" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')" aria-busy="true">
                    <Skeleton height="4.5rem" />
                    <Skeleton v-for="n in 3" :key="n" height="2.25rem" />
                </div>
                <template v-else>
                    <SanctionStepPicker v-model="step" :preview="view.preview" :perm="perm" :field-errors="view.fieldErrors" :tried="tried" :disabled="view.busy" />

                    <!-- ③ 사유 -->
                    <LabeledField v-if="UtilSanction.hasAnyOption(view.preview)" :label="t('sanction.reasonLabel')" :hint="t('sanction.textCounter', { n: UtilSanction.textLength(reason), max: UtilSanction.TEXT_MAX })" :help="t('sanction.reasonHelp')" :error="reasonError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="reason" rows="4" auto-resize fluid :placeholder="t('sanction.reasonPlaceholder')" :disabled="view.busy" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </template>
            </template>

            <Message v-if="view.error" severity="error" size="small" variant="simple" :closable="false">{{ view.error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="view.busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="submitLabel" :icon="step.kind === 'BAN' ? 'pi pi-ban' : 'pi pi-check'" :severity="step.kind && step.kind !== 'WARNING' ? 'danger' : undefined" :loading="view.busy" :disabled="!canSubmit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { SanctionCreateRequest, SanctionTarget } from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionIssueView, SanctionStepValue } from '~/composables/sanction/useSanctionIssue.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    view: SanctionIssueView;
    perm: { canManage: boolean; canBan: boolean };
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{
    search: [q: string];
    pick: [target: SanctionTarget];
    unpick: [];
    'retry-preview': [];
    submit: [body: Omit<SanctionCreateRequest, 'userId' | 'reportCaseId'>];
}>();

const { t } = useI18n();
const formId = useId();

const query = ref('');
const queryError = ref('');
const step = ref<SanctionStepValue>({ kind: null, skipConfirmed: false, confirmNickname: '' });
const reason = ref('');
const tried = ref(false);

// 열 때 칸을 비운다(앞 회원에게 쓰던 사유가 다음 회원에게 딸려 가면 안 된다)
watch(open, (isOpen) => {
    if (!isOpen) return;
    query.value = '';
    queryError.value = '';
    reason.value = '';
    tried.value = false;
    step.value = { kind: null, skipConfirmed: false, confirmNickname: '', sealedBanConfirmed: false };
});
// 현황이 오면(회원을 고름·새로 받음) 누를 수 있는 다음 단계를 골라 둔다 — 고른 단계가 여전히 누를 수 있으면 그대로
watch(
    () => props.view.preview,
    (preview) => {
        const keep = preview && step.value.kind && UtilSanction.choices(preview, props.perm).some((c) => c.enabled && c.option.kind === step.value.kind);
        if (!keep) step.value = UtilSanction.emptyStep(preview, props.perm);
    },
);

const reasonError = computed(() => props.view.fieldErrors.reason || (tried.value ? UtilSanctionMessages.textProblem(t, UtilSanction.textProblem(reason.value), t('sanction.reasonLabel')) : ''));
const canSubmit = computed(() => Boolean(props.view.preview) && !props.view.busy && UtilSanction.hasAnyOption(props.view.preview!) && UtilSanction.canIssue(props.perm));
const submitLabel = computed(() => (step.value.kind ? t('sanction.issueSubmit', { kind: UtilSanctionMessages.kindName(t, step.value.kind) }) : t('sanction.issue')));

function find(): void {
    const parsed = UtilSanction.parseSearch('NICKNAME', query.value);
    if (parsed.kind !== 'OK') {
        queryError.value = parsed.kind === 'NONE' ? t('sanction.searchTooShort', { min: UtilSanction.SEARCH_MIN }) : UtilSanctionMessages.searchProblem(t, parsed.kind);
        return;
    }
    queryError.value = '';
    emit('search', parsed.text);
}

function submit(): void {
    const preview = props.view.preview;
    if (!preview || props.view.busy) return;
    tried.value = true;
    if (!UtilSanction.stepReady(step.value, preview, props.perm) || UtilSanction.textProblem(reason.value)) return;
    emit('submit', { ...UtilSanction.stepRequest(step.value, preview), reason: reason.value.trim() });
}
</script>
