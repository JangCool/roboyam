<template>
    <!--
        하이라이트 의견(찬성·반대) 관리 서랍 — 그 글의 입장 배지가 붙은 원댓글을 찬성·반대 탭으로 보고(고정 먼저 → 추천 많은 순 → 최근), 고정하거나 숨긴다.
        숨김은 신고 숨김과 같은 칸(까닭 STAFF)이라 공개 댓글 목록에 「숨긴 댓글」 자리만 남고 글의 댓글 수가 1 줄어든다. 숨김 풀기는 **운영자 숨김만** 풀린다(신고로 숨긴 의견은 여기서 풀지 않는다).
        고정은 지금 이 목록의 차례만 바꾼다(홈 카드는 홈 작업 때). 글쓴이에게 알리지 않는다.
        서버 호출은 페이지가 준 `actions` 로만 한다. 바꾸면 그 줄을 서버가 준 모양으로 갈아 끼우고 차례가 바뀔 수 있어 그 쪽을 다시 읽는다 — 하이라이트 목록의 수는 `changed` 로 페이지가 다시 읽는다.
        처리 권한(`highlight.manage`)이 없으면 단추 없이 읽기만 한다.
    -->
    <Drawer v-model:visible="open" position="right" :header="t('highlight.opinionsTitle')" :style="{ width: '44rem', maxWidth: '100vw' }">
        <div v-if="highlight" class="flex flex-col gap-3">
            <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                <p class="m-0 text-sm font-medium break-words">{{ UtilHighlight.displayTitle(highlight) }}</p>
                <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                    <Tag :value="data?.open ? t('highlight.debateOpen') : t('highlight.debateClosed')" :severity="data?.open ? 'success' : 'secondary'" class="text-[11px]!" />
                    <span>{{ t('highlight.opinionsLead') }}</span>
                </div>
            </div>

            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('highlight.opinionsReadOnly') }}</p>

            <SelectButton v-model="stance" :options="stanceOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('highlight.opinionsTabs')" class="self-start" @update:model-value="onStance" />

            <div v-if="loading && !data" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="4rem" />
            </div>
            <ErrorState v-else-if="loadError && !data" :title="t('highlight.opinionsLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            <template v-else-if="data">
                <EmptyState v-if="page.items.length === 0" icon="pi pi-comments" :title="t('highlight.opinionsEmpty')" />
                <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="loading">
                    <li v-for="op in page.items" :key="op.commentId" class="flex flex-col gap-1.5 py-2.5" :class="op.hidden ? 'opacity-70' : ''">
                        <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                            <Tag v-if="op.pinned" :value="t('highlight.pinnedTag')" icon="pi pi-thumbtack" severity="info" class="text-[11px]!" />
                            <Tag v-if="op.hidden" :value="t(`highlight.hiddenReason.${op.hiddenReason ?? 'STAFF'}`)" icon="pi pi-eye-slash" severity="warn" class="text-[11px]!" />
                            <span class="font-medium text-color">{{ op.authorKind === 'GUEST' ? t('content.guestAuthor', { name: op.authorName }) : op.authorName }}</span>
                            <span aria-hidden="true">·</span>
                            <time :datetime="op.createdAt" :title="time.full(op.createdAt)">{{ time.listLabel(op.createdAt) }}</time>
                            <span aria-hidden="true">·</span>
                            <span class="tabular-nums">{{ t('highlight.opinionStats', { likes: op.likeCount, replies: op.replyCount }) }}</span>
                        </div>
                        <p class="m-0 text-sm leading-relaxed break-words whitespace-pre-line">{{ op.bodyPreview }}</p>
                        <div v-if="canManage" class="flex flex-wrap gap-1">
                            <Button
                                type="button"
                                :label="op.pinned ? t('highlight.unpin') : t('highlight.pin')"
                                icon="pi pi-thumbtack"
                                size="small"
                                severity="secondary"
                                variant="text"
                                :loading="busy === `pin:${op.commentId}`"
                                :disabled="busy !== '' && busy !== `pin:${op.commentId}`"
                                @click="togglePin(op)"
                            />
                            <Button
                                v-if="!op.hidden"
                                type="button"
                                :label="t('highlight.hideOpinion')"
                                icon="pi pi-eye-slash"
                                size="small"
                                severity="danger"
                                variant="text"
                                :loading="busy === `hide:${op.commentId}`"
                                :disabled="busy !== '' && busy !== `hide:${op.commentId}`"
                                @click="toggleHide(op)"
                            />
                            <Button
                                v-else-if="op.hiddenReason === 'STAFF'"
                                type="button"
                                :label="t('highlight.unhideOpinion')"
                                icon="pi pi-eye"
                                size="small"
                                severity="secondary"
                                variant="text"
                                :loading="busy === `hide:${op.commentId}`"
                                :disabled="busy !== '' && busy !== `hide:${op.commentId}`"
                                @click="toggleHide(op)"
                            />
                            <span v-else class="self-center text-xs text-muted-color">{{ t('highlight.reportHiddenNote') }}</span>
                        </div>
                    </li>
                </ul>

                <div v-if="page.total > page.size || pageNo > 1" class="flex items-center justify-between gap-2 text-xs text-muted-color">
                    <span class="tabular-nums">{{ t('highlight.opinionsPage', { page: pageNo, total: page.total }) }}</span>
                    <div class="flex gap-1">
                        <Button type="button" icon="pi pi-angle-left" :aria-label="t('common.prev')" size="small" severity="secondary" variant="text" :disabled="pageNo <= 1 || loading" @click="go(pageNo - 1)" />
                        <Button type="button" icon="pi pi-angle-right" :aria-label="t('common.next')" size="small" severity="secondary" variant="text" :disabled="!page.hasNext || loading" @click="go(pageNo + 1)" />
                    </div>
                </div>
            </template>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { DebateStance, HighlightItem, HighlightOpinion, HighlightOpinionListView, HighlightOpinionPage } from '~/composables/content/api/useHighlightApi.types';
import type { HighlightOpinionActions } from '~/components/highlight/HighlightOpinionsDrawer.types';
import UtilHighlight from '~/utils/util-highlight';

interface Props {
    highlight?: HighlightItem | null;
    actions: HighlightOpinionActions;
    canManage?: boolean;
}

const props = withDefaults(defineProps<Props>(), { highlight: null, canManage: false });
const emit = defineEmits<{ changed: [] }>();
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();

const stance = ref<DebateStance>('PRO');
const pageNo = ref(1);
const data = ref<HighlightOpinionListView | null>(null);
const loading = ref(false);
const loadError = ref('');
const busy = ref('');

const EMPTY_PAGE: HighlightOpinionPage = { total: 0, page: 1, size: UtilHighlight.OPINION_PAGE_SIZE, hasNext: false, items: [] };
const page = computed<HighlightOpinionPage>(() => (data.value ? (stance.value === 'PRO' ? data.value.pro : data.value.con) : EMPTY_PAGE));

// ⚠ `computed` 다 — 언어를 바꾸면 다시 계산된다. 수는 받은 목록의 것
const stanceOptions = computed(() =>
    (['PRO', 'CON'] as const).map((value) => ({ value, label: t(`highlight.stanceTab.${value}`, { n: data.value ? data.value[value === 'PRO' ? 'pro' : 'con'].total : 0 }) })),
);

let request = 0;
async function load(): Promise<void> {
    if (!props.highlight) return;
    const mine = ++request;
    loading.value = true;
    loadError.value = '';
    try {
        const found = await props.actions.load({ stance: stance.value, page: pageNo.value, size: UtilHighlight.OPINION_PAGE_SIZE });
        if (mine !== request) return;
        data.value = found;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = e instanceof ApiError ? e.message : t('highlight.opinionsLoadFailed');
        if (data.value) toast.error(loadError.value, 8000);
    } finally {
        if (mine === request) loading.value = false;
    }
}

function onStance(): void {
    pageNo.value = 1;
    void load();
}

function go(next: number): void {
    pageNo.value = next;
    void load();
}

async function act(op: HighlightOpinion, kind: 'pin' | 'hide'): Promise<void> {
    if (busy.value) return;
    busy.value = `${kind}:${op.commentId}`;
    try {
        if (kind === 'pin') await props.actions.pin(op.commentId, !op.pinned);
        else await props.actions.hide(op.commentId, !op.hidden);
        toast.success(t(`highlight.opinionDone.${kind}${kind === 'pin' ? (op.pinned ? 'Off' : 'On') : op.hidden ? 'Off' : 'On'}`));
        emit('changed');
        await load();
    } catch (e) {
        toast.error(e instanceof ApiError ? e.message : t('highlight.opinionActionFailed'), 8000);
        if (e instanceof ApiError && e.code === 'DEBATE_OPINION_NOT_FOUND') void load();
    } finally {
        busy.value = '';
    }
}

const togglePin = (op: HighlightOpinion) => act(op, 'pin');
const toggleHide = (op: HighlightOpinion) => act(op, 'hide');

// 열 때마다 찬성 첫 쪽부터
watch(
    () => [open.value, props.highlight?.id] as const,
    ([visible]) => {
        if (!visible || !props.highlight) return;
        stance.value = 'PRO';
        pageNo.value = 1;
        data.value = null;
        void load();
    },
    { immediate: true },
);
</script>
