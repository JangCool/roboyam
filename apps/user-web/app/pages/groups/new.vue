<template>
    <!--
        소모임 만들기 `/groups/new` (메뉴 `GROUPS_NEW`) — 개설 신청. 반려된 신청을 고쳐 다시 낼 때는 `?from=<신청 id>`(내 소모임의 그 신청으로 폼을 채운다 — 동의 칸은 다시 확인).
        승인제(기본)는 `202 PENDING` → 이 화면이 「신청을 보냈어요」로 바뀌고, 자유 개설·운영자는 `201 ACTIVE` → 상세로 간다. 개설 방식·최소 등급·1인 상한·정원 범위는 `GET /api/groups/creation-rules`.
        분류는 `GET /api/groups/categories` — 분류에 켜진 게시판이 둘 이상이면 게시판도 고른다. 고른 게시판이 투자 고지면 추가 동의, 오프라인이면 개인 책임 고지 동의.
        비회원은 로그인 안내, 이메일 인증 전·등급 미달·상한 도달은 폼은 두고 단추만 끈다(정지 중은 서버가 `403 ACCOUNT_RESTRICTED` 로 막는다 — 안내 띠).
        공개 범위(5단계): 개설 규칙의 `visibilityChoice` 가 true 일 때만 「공개 범위」 라디오(비공개 소모임 기본 · 공개 소모임). 공개를 고르면 안내 띠(승인제 일반 회원이면 「운영팀이 공개 범위도 함께 확인해요」).
          고를 수 없는 서버에는 본문에 `visibility` 를 싣지 않는다. 승인 대기 화면에 신청한 공개 범위 한 줄.
    -->
    <div class="mx-auto max-w-xl">
        <NuxtLink :to="UtilGroup.LIST_PATH" class="inline-flex items-center gap-1 rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('group.backToList') }}
        </NuxtLink>
        <h1 v-if="view !== 'pending'" class="mb-3 mt-1 text-lg font-bold">{{ t('group.createTitle') }}</h1>

        <div v-if="view === 'loading'" class="space-y-4" role="status" :aria-label="t('common.loading')" data-testid="group-create-loading">
            <div v-for="n in 4" :key="n" class="h-11 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>

        <StatusPanel v-else-if="view === 'login'" icon="pi pi-lock" :title="t('group.createLoginTitle')" data-testid="group-create-login">
            {{ t('group.createLoginText') }}
            <template #actions>
                <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" fluid size="large" />
                <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.browse')" variant="outlined" severity="secondary" fluid />
            </template>
        </StatusPanel>

        <StatusPanel v-else-if="view === 'no-category'" icon="pi pi-list" :title="t('group.noCategoryTitle')" data-testid="group-create-no-category">
            {{ t('group.noCategoryText') }}
            <template #actions>
                <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.browse')" fluid size="large" />
            </template>
        </StatusPanel>

        <ErrorState v-else-if="view === 'error'" :title="t('group.categoriesError')" :description="categoriesError" retry data-testid="group-create-error" @retry="reloadCategories" />

        <!-- 신청 완료(승인제) — 승인 대기 -->
        <section v-else-if="view === 'pending'" class="mt-2 flex flex-col gap-4" :aria-labelledby="`${uid}-pending`" data-testid="group-create-pending">
            <h1 :id="`${uid}-pending`" ref="pendingHeading" tabindex="-1" class="m-0 text-lg font-bold">{{ t('group.pendingTitle') }}</h1>
            <div class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <div class="flex items-center gap-3">
                    <GroupInitial :name="sent.name" size="lg" />
                    <div class="min-w-0 flex-1">
                        <h2 class="m-0 break-words text-base font-bold leading-tight">{{ sent.name }}</h2>
                        <p class="m-0 mt-0.5 text-xs text-muted-color">{{ sent.summary }}</p>
                        <p v-if="sent.visibility" class="m-0 mt-0.5 text-xs text-muted-color" data-testid="group-create-pending-visibility">{{ UtilGroupVisibilityMessages.requestedLine(t, sent.visibility) }}</p>
                    </div>
                </div>
                <p class="m-0 mt-3 whitespace-pre-line break-words text-sm leading-relaxed">{{ sent.description }}</p>
            </div>
            <ol class="m-0 list-none space-y-3 p-0" :aria-label="t('group.pendingSteps')">
                <li v-for="step in steps" :key="step.title" class="flex items-start gap-3" :aria-current="step.state === 'now' ? 'step' : undefined">
                    <i class="pi mt-0.5 text-lg" :class="STEP_ICON[step.state]" aria-hidden="true" />
                    <div class="min-w-0">
                        <p class="m-0 text-sm" :class="step.state === 'wait' ? 'text-muted-color' : 'font-semibold'">{{ step.title }}</p>
                        <small class="block text-xs text-muted-color">{{ step.text }}</small>
                    </div>
                </li>
            </ol>
            <Button as="router-link" :to="UtilGroup.MY_PATH" :label="t('group.pendingMine')" fluid size="large" data-testid="group-create-pending-mine" />
            <p class="m-0 text-center text-xs text-muted-color">{{ t('group.pendingHint') }}</p>
        </section>

        <form v-else ref="formRoot" class="flex flex-col gap-5" novalidate :aria-label="t('group.createTitle')" data-testid="group-create-form" @submit.prevent="onSubmit">
            <GroupNotice v-if="notice">{{ notice }}</GroupNotice>

            <GroupNotice v-if="prefilled" tone="warn" data-testid="group-create-reopen">
                {{ t('group.reopenNotice') }}
                <template v-if="rejectReason"><br /><b class="font-semibold">{{ t('group.rejectReasonLabel') }}</b> {{ rejectReason }}</template>
            </GroupNotice>

            <!-- 만들 수 없는 사람(이메일 인증 전·등급 미달·상한) — 조건이 바뀌면 할 수 있으므로 폼은 두고 단추만 끈다 -->
            <GroupNotice v-if="blocked" tone="warn" :title="blocked.title" data-testid="group-create-blocked">{{ blocked.text }}</GroupNotice>

            <!-- 칸에 붙일 수 없는 오류(등급·개수·요청 제한·네트워크). 입력은 그대로 남는다 -->
            <GroupNotice v-if="failure" tone="danger" :title="failure.title" :data-kind="failure.kind" data-testid="group-create-failure">{{ failure.text }}</GroupNotice>

            <LabeledField :label="t('group.fieldName')" :error="errors.name">
                <template #aside>
                    <span class="text-xs tabular-nums" :class="nameCount > UtilGroup.NAME_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: nameCount, max: UtilGroup.NAME_MAX }) }}</span>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :placeholder="t('group.namePlaceholder')" :disabled="submitting" :invalid="invalid" :aria-describedby="describedBy" fluid data-field="name" data-testid="group-create-name" />
                </template>
            </LabeledField>

            <LabeledField :label="t('group.fieldDescription')" :error="errors.description">
                <template #aside>
                    <span class="text-xs tabular-nums" :class="descriptionCount > UtilGroup.DESCRIPTION_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: descriptionCount, max: UtilGroup.DESCRIPTION_MAX }) }}</span>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.description" name="description" rows="5" auto-resize :placeholder="t('group.descriptionPlaceholder')" :disabled="submitting" :invalid="invalid" :aria-describedby="describedBy" fluid data-field="description" data-testid="group-create-description" />
                </template>
            </LabeledField>

            <!-- 분류 — 관리자가 정한 이름·순서. 고르면 그 분류의 게시판이 아래 칸으로 채워진다 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend :id="`${uid}-category`" class="mb-1.5 p-0 text-sm font-medium">{{ t('group.fieldCategory') }}</legend>
                <div class="flex flex-wrap gap-2" role="radiogroup" :aria-labelledby="`${uid}-category`">
                    <button
                        v-for="(category, index) in categories ?? []"
                        :key="category.id"
                        type="button"
                        role="radio"
                        :aria-checked="form.categoryId === category.id"
                        :disabled="submitting"
                        class="min-h-9 rounded-full border px-3.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary disabled:opacity-60"
                        :class="form.categoryId === category.id ? 'border-primary bg-primary font-semibold text-primary-contrast' : 'border-surface bg-surface-0 hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800'"
                        :data-field="index === 0 ? 'categoryId' : undefined"
                        :data-testid="`group-create-category-${category.id}`"
                        @click="form.categoryId = category.id"
                    >
                        {{ category.name }}
                    </button>
                </div>
                <p v-if="errors.categoryId" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ errors.categoryId }}</p>
            </fieldset>

            <!-- 게시판 — 고른 분류에 켜진 게시판이 둘 이상일 때만(하나면 자동 선택·숨김) -->
            <fieldset v-if="showBoardChoice" class="m-0 min-w-0 border-0 p-0" data-testid="group-create-board">
                <legend :id="`${uid}-board`" class="mb-1.5 p-0 text-sm font-medium">
                    {{ t('group.fieldBoard') }} <span class="font-normal text-muted-color">· {{ t('group.boardHelp') }}</span>
                </legend>
                <div class="flex flex-wrap gap-2" role="radiogroup" :aria-labelledby="`${uid}-board`">
                    <button
                        v-for="(board, index) in selectedCategory?.boards ?? []"
                        :key="board.slug"
                        type="button"
                        role="radio"
                        :aria-checked="form.boardSlug === board.slug"
                        :disabled="submitting"
                        class="min-h-9 rounded-md border px-3.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary disabled:opacity-60"
                        :class="
                            form.boardSlug === board.slug
                                ? 'border-primary bg-primary-50 font-semibold text-primary-700 dark:bg-primary-950 dark:text-primary-300'
                                : 'border-surface bg-surface-0 hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800'
                        "
                        :data-field="index === 0 ? 'boardSlug' : undefined"
                        @click="form.boardSlug = board.slug"
                    >
                        {{ board.name }}
                    </button>
                </div>
                <p v-if="errors.boardSlug" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ errors.boardSlug }}</p>
            </fieldset>

            <!-- 투자 고지 게시판 — 공부·산업 해설 목적만. 투자 고지 + 추가 동의(필수) -->
            <div v-if="isStock" class="space-y-3" data-testid="group-create-stock">
                <GroupNotice tone="warn" :title="t('group.stockBoxTitle')">{{ t('group.stockBoxText') }}</GroupNotice>
                <BoardInvestmentNotice />
                <div>
                    <div class="flex items-start gap-2.5">
                        <Checkbox v-model="form.agreeStock" binary :input-id="`${uid}-stock`" :disabled="submitting" :invalid="Boolean(errors.agreeStock)" data-field="agreeStock" />
                        <label :for="`${uid}-stock`" class="cursor-pointer text-sm leading-snug">{{ t('group.agreeStock') }}</label>
                    </div>
                    <p v-if="errors.agreeStock" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">{{ errors.agreeStock }}</p>
                </div>
            </div>

            <GroupOptionCards v-model="form.joinType" :legend="t('group.fieldJoinType')" :options="joinTypeOptions" :disabled="submitting" :error="errors.joinType" testid="group-create-join" />

            <!-- 공개 범위(5단계) — 서버가 고르게 할 때만(`creation-rules.visibilityChoice`). 기본 비공개, 공개를 고르면 안내 띠. 개설 방식(승인제·자유)과 따로다 -->
            <div v-if="visibilityChoice" class="space-y-3" data-field="visibility" data-testid="group-create-visibility">
                <GroupOptionCards
                    :model-value="form.visibility"
                    :legend="t('groupVisibility.createLegend')"
                    :options="visibilityOptions"
                    :disabled="submitting"
                    :error="errors.visibility"
                    testid="group-create-visibility"
                    @update:model-value="(value: string) => (form.visibility = value === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE')"
                />
                <GroupNotice v-if="form.visibility === 'PUBLIC'" tone="warn" data-testid="group-create-visibility-notice">
                    {{ UtilGroupVisibilityMessages.createPublicNotice(t, !immediate && rules?.creationMode === 'APPROVAL') }}
                </GroupNotice>
            </div>

            <LabeledField :label="t('group.fieldCapacity')" :help="t('group.capacityHelp', { min: range.min, max: range.max })" :error="errors.capacity">
                <template #default="{ id, describedBy, invalid }">
                    <div class="flex items-center gap-2">
                        <InputText
                            :id="id"
                            :model-value="form.capacity"
                            name="capacity"
                            inputmode="numeric"
                            autocomplete="off"
                            maxlength="3"
                            class="w-24"
                            :disabled="submitting"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            data-field="capacity"
                            data-testid="group-create-capacity"
                            @update:model-value="setCapacity"
                        />
                        <span class="text-sm">{{ t('group.capacityUnit') }}</span>
                    </div>
                </template>
            </LabeledField>

            <!-- 오프라인 모임 — 개인 책임 고지 + 동의(필수) -->
            <div class="space-y-3">
                <GroupOptionCards
                    :model-value="form.offline ? 'YES' : 'NO'"
                    :legend="t('group.fieldOffline')"
                    :options="offlineOptions"
                    :disabled="submitting"
                    testid="group-create-offline"
                    @update:model-value="(value: string) => (form.offline = value === 'YES')"
                />
                <template v-if="form.offline">
                    <GroupNotice tone="warn" :title="t('group.offlineBoxTitle')">{{ t('group.offlineBoxText') }}</GroupNotice>
                    <div>
                        <div class="flex items-start gap-2.5">
                            <Checkbox v-model="form.agreeOffline" binary :input-id="`${uid}-offline`" :disabled="submitting" :invalid="Boolean(errors.agreeOffline)" data-field="agreeOffline" />
                            <label :for="`${uid}-offline`" class="cursor-pointer text-sm leading-snug">{{ t('group.agreeOffline') }}</label>
                        </div>
                        <p v-if="errors.agreeOffline" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">{{ errors.agreeOffline }}</p>
                    </div>
                </template>
            </div>

            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('group.fieldAgree') }}</legend>
                <div class="flex items-start gap-2.5">
                    <Checkbox v-model="form.agreeRules" binary :input-id="`${uid}-rules`" :disabled="submitting" :invalid="Boolean(errors.agreeRules)" data-field="agreeRules" data-testid="group-create-agree-rules" />
                    <label :for="`${uid}-rules`" class="cursor-pointer text-sm leading-snug">{{ t('group.agreeRules') }}</label>
                </div>
                <p v-if="errors.agreeRules" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">{{ errors.agreeRules }}</p>
            </fieldset>

            <div>
                <Button type="submit" fluid size="large" :label="submitLabel" :loading="submitting" :disabled="submitting || Boolean(blocked)" data-testid="group-create-submit" />
                <p v-if="submitting" class="mt-2 text-center text-xs text-muted-color" role="status">{{ t('group.submittingHint') }}</p>
                <p v-else class="mt-2 text-center text-xs text-muted-color">
                    {{ t('group.listHint') }}
                    <NuxtLink :to="UtilGroup.LIST_PATH" class="text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('group.listLink') }}</NuxtLink>
                </p>
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { GroupCreationRules, GroupVisibility } from '~/composables/group/api/useGroupApi.types';
import type { GroupOption } from '~/components/group/GroupOptionCards.types';
import UtilGroup, { type GroupCreateField, type GroupCreateForm } from '~/utils/util-group';
import UtilGroupMessages, { type GroupFailure } from '~/utils/util-group-messages';
import UtilGroupVisibilityMessages from '~/utils/util-group-visibility-messages';
import UtilRedirect from '~/utils/util-redirect';

const { t } = useI18n();
useSeoMeta({ title: () => t('group.createTitle'), robots: 'noindex' });

const route = useRoute();
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { user, loaded } = useAuth();

const { items: categories, status: categoriesStatus, errorMessage: categoriesError, reload: reloadCategories } = useGroupCategories();
void reloadCategories();

/* ── 개설 규칙 — 로그인한 뒤 받는다(못 받으면 안내만 빠진다) ── */
const rules = ref<GroupCreationRules | null>(null);
let rulesSeq = 0;
async function reloadRules(): Promise<void> {
    if (!user.value) return;
    const mine = ++rulesSeq;
    try {
        const res = UtilGroup.normalizeRules(await api.creationRules());
        if (mine === rulesSeq) rules.value = res;
    } catch {
        // 이미 받은 값은 지우지 않는다
    }
}
watch(
    () => [loaded.value, user.value?.id] as const,
    ([ready, id]) => {
        if (ready && id) void reloadRules();
        else rules.value = null;
    },
    { immediate: true },
);

/* ── 폼 ── */
const form = reactive<GroupCreateForm>(UtilGroup.emptyCreateForm());
const touched = reactive<Record<GroupCreateField, boolean>>({
    name: false,
    description: false,
    categoryId: false,
    boardSlug: false,
    joinType: false,
    visibility: false,
    capacity: false,
    offline: false,
    agreeRules: false,
    agreeStock: false,
    agreeOffline: false,
});
const serverErrors = ref<Partial<Record<GroupCreateField, string>>>({});
const submitting = ref(false);
const failure = ref<GroupFailure | null>(null);
const prefilled = ref(false);
const prefilling = ref(false);
const rejectReason = ref<string | null>(null);
const pending = ref(false);

const selectedCategory = computed(() => UtilGroup.categoryById(categories.value, form.categoryId));
const showBoardChoice = computed(() => UtilGroup.needsBoardChoice(selectedCategory.value));
const isStock = computed(() => Boolean(UtilGroup.boardOf(categories.value ?? [], form.categoryId, form.boardSlug)?.investmentNotice));
const range = computed(() => UtilGroup.capacityRange(rules.value));
const problems = computed(() => UtilGroup.createProblems(form, isStock.value, range.value));
const nameCount = computed(() => UtilGroup.trimmedLength(form.name));
const descriptionCount = computed(() => UtilGroup.trimmedLength(form.description));

/** 칸 아래 오류 — 서버가 지적한 것이 먼저, 그다음 건드린 칸의 화면 검사 */
const errors = computed<Partial<Record<GroupCreateField, string>>>(() => {
    const shown: Partial<Record<GroupCreateField, string>> = {};
    for (const field of UtilGroup.CREATE_ORDER) {
        const server = serverErrors.value[field];
        const problem = touched[field] ? problems.value[field] : undefined;
        const length = field === 'name' ? nameCount.value : field === 'description' ? descriptionCount.value : undefined;
        const message = server ?? (problem ? UtilGroupMessages.problem(t, field, problem, { range: range.value, length }) : undefined);
        if (message) shown[field] = message;
    }
    return shown;
});

// 칸을 고치면 건드린 것으로 보고, 서버가 지적한 그 칸의 오류와 위의 실패 띠를 걷는다
for (const field of UtilGroup.CREATE_ORDER) {
    watch(
        () => form[field],
        () => {
            touched[field] = true;
            if (serverErrors.value[field]) {
                const rest = { ...serverErrors.value };
                delete rest[field];
                serverErrors.value = rest;
            }
            failure.value = null;
        },
    );
}

// 분류·게시판 선택을 분류 목록에 맞춘다(분류 목록을 못 받았으면 건드리지 않는다 — 반려된 신청에서 채운 값을 지우지 않게)
watch(
    () => [categories.value, form.categoryId, form.boardSlug] as const,
    ([items, categoryId, boardSlug]) => {
        if (items === null) return;
        const next = UtilGroup.resolveSelection(items, categoryId, boardSlug);
        if (next.categoryId !== categoryId) form.categoryId = next.categoryId;
        if (next.boardSlug !== boardSlug) form.boardSlug = next.boardSlug;
    },
    { immediate: true },
);
// 투자 고지 동의는 그 게시판에 대한 동의다 — 분류·게시판이 바뀌면 거둔다
watch([() => form.categoryId, () => form.boardSlug], () => {
    form.agreeStock = false;
});
watch(
    () => form.offline,
    (offline) => {
        if (!offline) form.agreeOffline = false;
    },
);

const joinTypeOptions = computed<GroupOption[]>(() => [
    { value: 'OPEN', label: t('group.joinOpen'), hint: t('group.joinOpenHint') },
    { value: 'APPROVAL', label: t('group.joinApproval'), hint: t('group.joinApprovalHint') },
]);
/** 공개 범위를 고를 수 있나(서버 공개 범위 기능) — 규칙을 못 받았으면 라디오가 없다(비공개로 열린다) */
const visibilityChoice = computed(() => rules.value?.visibilityChoice === true);
const visibilityOptions = computed<GroupOption[]>(() => UtilGroupVisibilityMessages.createOptions(t));

/** 정원 칸 — 숫자만 3자리까지 */
function setCapacity(value: string | undefined): void {
    form.capacity = UtilGroup.digits(value ?? '');
}

const offlineOptions = computed<GroupOption[]>(() => [
    { value: 'NO', label: t('common.none'), hint: t('group.offlineNoHint') },
    { value: 'YES', label: t('group.offlineYes'), hint: t('group.offlineYesHint') },
]);

/* ── 화면 ── */
type View = 'loading' | 'login' | 'error' | 'no-category' | 'form' | 'pending';
const view = computed<View>(() => {
    if (pending.value) return 'pending';
    if (!loaded.value) return 'loading';
    if (!user.value) return 'login';
    if (categoriesStatus.value === 'error') return 'error';
    if (categories.value === null) return 'loading';
    if (categories.value.length === 0) return 'no-category';
    return prefilling.value ? 'loading' : 'form';
});

/** 운영진·운영자는 규칙과 상관없이 바로 열린다(서버 `201 ACTIVE`) */
const immediate = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'STAFF');
const notice = computed(() => UtilGroupMessages.creationNotice(t, rules.value?.creationMode ?? null, immediate.value));
const blocked = computed(() => {
    if (user.value && !user.value.emailVerified) return { title: t('group.unverifiedTitle'), text: t('group.unverifiedText') };
    return UtilGroupMessages.ruleBlock(t, rules.value, () => user.value?.gradeLabel ?? '');
});
const submitLabel = computed(() => {
    if (submitting.value) return t('group.submitting');
    if (failure.value?.kind === 'generic') return t('common.retry');
    return UtilGroupMessages.submitLabel(t, rules.value?.creationMode ?? null, immediate.value);
});

/* ── 반려된 신청을 고쳐 다시 내기(`?from=`) ── */
const from = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''));
watch(
    () => [user.value?.id, from.value] as const,
    async ([userId, requestId]) => {
        if (!userId || !requestId) return;
        prefilling.value = true;
        try {
            const mine = await api.mine();
            const found = mine.requests.find((r) => r.id === requestId);
            if (found) {
                Object.assign(form, UtilGroup.createFormOf(found));
                rejectReason.value = found.status === 'REJECTED' ? found.rejectReason : null;
                prefilled.value = true;
            }
        } catch {
            // 못 받으면 빈 폼 그대로 — 새로 쓰면 된다
        } finally {
            prefilling.value = false;
        }
    },
    { immediate: true },
);

/* ── 제출 ── */
const formRoot = ref<HTMLFormElement | null>(null);
const pendingHeading = ref<HTMLElement | null>(null);
const sent = reactive<{ name: string; description: string; summary: string; visibility: GroupVisibility | null }>({ name: '', description: '', summary: '', visibility: null });

async function focusField(field: GroupCreateField | null) {
    if (!field) return;
    await nextTick();
    const el = formRoot.value?.querySelector<HTMLElement>(`[data-field="${field}"]`);
    const target = el?.matches('input, textarea, button') ? el : el?.querySelector<HTMLElement>('input, textarea, button');
    (target ?? el)?.focus();
}

async function onSubmit() {
    if (submitting.value || blocked.value) return;
    for (const field of UtilGroup.CREATE_ORDER) touched[field] = true;
    const first = UtilGroup.firstField(problems.value, UtilGroup.CREATE_ORDER);
    if (first) return focusField(first);

    submitting.value = true;
    failure.value = null;
    serverErrors.value = {};
    try {
        const created = await api.create(UtilGroup.toCreateRequest(form, isStock.value, visibilityChoice.value));
        if (created.status === 'ACTIVE') {
            toast.success(t('group.openedToast'));
            await navigateTo(UtilGroup.detailPath(created.id), { replace: true });
            return;
        }
        sent.name = UtilGroup.normalizeName(form.name);
        sent.description = form.description.trim();
        sent.summary = UtilGroupMessages.pendingSummaryLine(t, selectedCategory.value?.name ?? '', form.joinType, UtilGroup.parseCapacity(form.capacity) ?? 0);
        sent.visibility = visibilityChoice.value ? form.visibility : null;
        pending.value = true;
        if (typeof window !== 'undefined') window.scrollTo({ top: 0 });
        await nextTick();
        pendingHeading.value?.focus({ preventScroll: true });
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_NAME_TAKEN') && e instanceof ApiError) {
            serverErrors.value = { name: e.message };
            return focusField('name');
        }
        const fields = UtilGroup.serverFields(e, UtilGroup.CREATE_ORDER);
        const firstServer = UtilGroup.firstField(fields, UtilGroup.CREATE_ORDER);
        if (firstServer) {
            serverErrors.value = fields;
            return focusField(firstServer);
        }
        failure.value = UtilGroupMessages.createFailure(t, e, time.value);
        // 규칙을 못 지켜 거절됐으면 서버의 지금 규칙으로 안내를 다시 그린다
        if (failure.value.kind === 'permission' || failure.value.kind === 'grade' || failure.value.kind === 'limit') void reloadRules();
    } finally {
        submitting.value = false;
    }
}

/* ── 신청 완료 단계 ── */
const STEP_ICON = { done: 'pi-check-circle text-green-600 dark:text-green-400', now: 'pi-clock text-primary', wait: 'pi-circle text-surface-400' } as const;
const steps = computed(() => [
    { title: t('group.pendingStep1'), text: t('group.pendingStep1Text'), state: 'done' as const },
    { title: t('group.pendingStep2'), text: t('group.pendingStep2Text'), state: 'now' as const },
    { title: t('group.pendingStep3'), text: t('group.pendingStep3Text'), state: 'wait' as const },
]);
</script>
