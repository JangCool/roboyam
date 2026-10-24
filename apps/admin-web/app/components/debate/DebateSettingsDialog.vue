<template>
    <!--
        글 토론 설정 대화창 — **글 번호 하나**(`postId`)로 그 글의 찬반 토론을 열고·설정을 바꾸고·닫는다(post-debate-contract, 2026-10-24 범위 변경).
        하이라이트 화면과 (다음 작업) 게시글 관리의 글 상세 서랍이 같이 쓴다. 서버 호출은 부른 화면이 준 `actions`(`useDebateSettingsActions`)로만 한다.
          열 때마다 `GET /api/admin/debates/{postId}` — 404 `DEBATE_NOT_FOUND` 면 「토론이 없어요」 + [토론 열기], 열려 있으면 연 쪽(글쓴이·운영자)·참여·의견·대기 고정 요청 한 줄과 두 설정.
          칸: 「중립도 받기」·「입장 바꾸기 허용」(새로 열 때 둘 다 켬, 바꾸기를 끄면 「한 번 고른 입장은 바꿀 수 없어요」). [저장](`PUT { enabled: true, … }`) · [토론 닫기](확인 → `PUT { enabled: false }` 204 — 운영자는 투표가 있어도 닫힌다, 이미 쓴 의견·입장은 남는다).
          바뀌면 `changed`(새 한 줄 또는 닫았으면 null)로 부른 화면이 자기 목록을 다시 읽게 한다.
        권한: 바꾸기 `post.manage`(`canManage`) — 없으면 칸을 막고 읽기만(안내 한 줄). 칸 오류(`fields.*`)·그 밖 거절은 대화상자 아래 한 줄(서버 문구 그대로).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="t('debate.settingsTitle')"
        :style="{ width: 'min(32rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <div class="flex flex-col gap-4">
            <p v-if="title" class="m-0 text-sm font-medium break-words">
                {{ title }} <span class="text-xs font-normal tabular-nums text-muted-color">#{{ postId }}</span>
            </p>

            <div v-if="loading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2.5rem" />
                <Skeleton height="4rem" />
            </div>
            <ErrorState v-else-if="loadError" :title="t('debate.settingsLoadFailed')" :description="loadError" retry @retry="load" />

            <form v-else :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
                <!-- 지금 상태 -->
                <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-xs text-muted-color">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <Tag :value="item ? t('debate.debateOpen') : t('debate.debateNone')" :severity="item ? 'success' : 'secondary'" class="text-[11px]!" />
                        <span v-if="item">{{ t(`debate.openedBy.${item.openedBy}`) }} · {{ time.listLabel(item.createdAt) }}</span>
                    </div>
                    <span v-if="item" class="tabular-nums">
                        <i18n-t keypath="debate.summaryLine" tag="span" scope="global">
                            <template #total>{{ item.votes.total }}</template>
                            <template #stances>
                                <template v-for="(stance, i) in UtilDebate.stancesOf(item)" :key="stance">
                                    <template v-if="i > 0"> · </template>
                                    <span class="font-medium" :class="UtilDebate.stanceTextClass(stance)">{{ t(`debate.stanceTab.${stance}`, { n: item.votes[UtilDebate.countKey(stance)] }) }}</span>
                                </template>
                            </template>
                            <template #opinions>{{ item.opinions }}</template>
                            <template #pending>{{ item.pendingPinRequests }}</template>
                        </i18n-t>
                    </span>
                    <span v-else>{{ t('debate.noneHint') }}</span>
                </div>

                <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('debate.settingsReadOnly') }}</p>

                <div class="flex flex-col gap-1">
                    <div class="flex items-center gap-3">
                        <ToggleSwitch v-model="form.neutralEnabled" :input-id="`${formId}-neutral`" :disabled="saving !== '' || !canManage" :aria-describedby="`${formId}-neutral-help`" />
                        <label :for="`${formId}-neutral`" class="text-sm font-medium">{{ t('debate.neutralLabel') }}</label>
                    </div>
                    <p :id="`${formId}-neutral-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('debate.neutralHelp') }}</p>
                </div>
                <div class="flex flex-col gap-1">
                    <div class="flex items-center gap-3">
                        <ToggleSwitch v-model="form.stanceChangeAllowed" :input-id="`${formId}-change`" :disabled="saving !== '' || !canManage" :aria-describedby="`${formId}-change-help`" />
                        <label :for="`${formId}-change`" class="text-sm font-medium">{{ t('debate.stanceChangeLabel') }}</label>
                    </div>
                    <p :id="`${formId}-change-help`" class="m-0 text-xs leading-relaxed" :class="form.stanceChangeAllowed ? 'text-muted-color' : 'font-medium text-amber-700 dark:text-amber-300'">
                        {{ form.stanceChangeAllowed ? t('debate.stanceChangeHelp') : t('debate.stanceChangeOff') }}
                    </p>
                </div>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false">{{ formError }}</Message>
            </form>
        </div>

        <template #footer>
            <Button
                v-if="canManage && item && !loading"
                type="button"
                :label="t('debate.close')"
                icon="pi pi-times-circle"
                severity="danger"
                variant="text"
                class="mr-auto"
                :loading="saving === 'close'"
                :disabled="saving !== ''"
                @click="closeDebate"
            />
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving !== ''" @click="emit('update:visible', false)" />
            <Button
                v-if="canManage && !loading && !loadError"
                type="submit"
                :form="formId"
                :label="item ? t('common.save') : t('debate.open')"
                :loading="saving === 'save'"
                :disabled="saving !== '' || (item !== null && !changed)"
            />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { DebateItem } from '~/composables/content/api/useDebateApi.types';
import type { DebateSettingsActions } from '~/components/debate/DebateSettingsDialog.types';
import UtilDebate, { type DebateSettingsForm } from '~/utils/util-debate';

interface Props {
    visible: boolean;
    /** 다룰 글 번호 — 없으면 아무것도 읽지 않는다 */
    postId?: string | null;
    /** 머리 아래 보일 글 제목(없으면 줄을 감춘다) */
    title?: string;
    actions: DebateSettingsActions;
    /** 바꾸기 권한(`post.manage`) */
    canManage?: boolean;
}

const props = withDefaults(defineProps<Props>(), { postId: null, title: '', canManage: false });
const emit = defineEmits<{ 'update:visible': [value: boolean]; changed: [item: DebateItem | null] }>();

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const formId = useId();

/** 그 글의 열린 토론 — 없으면(닫힘) null */
const item = ref<DebateItem | null>(null);
const form = ref<DebateSettingsForm>(UtilDebate.emptySettings());
const loading = ref(false);
const loadError = ref('');
const saving = ref<'' | 'save' | 'close'>('');
const formError = ref('');

const changed = computed(() => (item.value ? form.value.neutralEnabled !== item.value.neutralEnabled || form.value.stanceChangeAllowed !== item.value.stanceChangeAllowed : true));

function apply(next: DebateItem | null): void {
    item.value = next;
    form.value = next ? UtilDebate.settingsOf(next) : UtilDebate.emptySettings();
}

let request = 0;
async function load(): Promise<void> {
    const postId = props.postId;
    if (!postId) return;
    const mine = ++request;
    loading.value = true;
    loadError.value = '';
    formError.value = '';
    try {
        const found = await props.actions.get(postId);
        if (mine !== request) return;
        apply(found);
    } catch (e) {
        if (mine !== request) return;
        // 닫힌 토론 — 새로 열 수 있는 상태
        if (e instanceof ApiError && e.code === 'DEBATE_NOT_FOUND') apply(null);
        else loadError.value = e instanceof ApiError ? e.message : t('debate.settingsLoadFailed');
    } finally {
        if (mine === request) loading.value = false;
    }
}

/** 실패 — 칸 오류도 칸이 따로 없어 아래 한 줄로(서버 문구 그대로) */
function failed(e: unknown): void {
    if (e instanceof ApiError && e.fields && Object.keys(e.fields).length) formError.value = Object.values(e.fields).join(' ');
    else formError.value = e instanceof ApiError ? e.message : t('debate.settingsSaveFailed');
}

async function submit(): Promise<void> {
    const postId = props.postId;
    if (!postId || !props.canManage || saving.value || (item.value && !changed.value)) return;
    const opening = !item.value;
    saving.value = 'save';
    formError.value = '';
    try {
        const saved = await props.actions.save(postId, UtilDebate.openRequest(form.value));
        apply(saved);
        toast.success(opening ? t('debate.opened') : t('debate.saved'));
        emit('changed', saved);
        emit('update:visible', false);
    } catch (e) {
        failed(e);
    } finally {
        saving.value = '';
    }
}

async function closeDebate(): Promise<void> {
    const postId = props.postId;
    if (!postId || !props.canManage || saving.value) return;
    const ok = await confirm({ title: t('debate.closeTitle'), message: t('debate.closeMessage'), confirmLabel: t('debate.close'), tone: 'danger' });
    if (!ok) return;
    saving.value = 'close';
    formError.value = '';
    try {
        await props.actions.save(postId, UtilDebate.closeRequest());
        apply(null);
        toast.success(t('debate.closed'));
        emit('changed', null);
        emit('update:visible', false);
    } catch (e) {
        failed(e);
    } finally {
        saving.value = '';
    }
}

// 열 때마다 그 글의 지금 토론을 읽는다
watch(
    () => [props.visible, props.postId] as const,
    ([open]) => {
        if (!open) return;
        apply(null);
        void load();
    },
    { immediate: true },
);
</script>
