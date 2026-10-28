<template>
    <!--
        공구 수정 이력 서랍(오른쪽, `components.md` 2-5) — 표시·광고 기록 6개월 보관(commerce.md 10-2). 판마다 종류(모집 시작 첫 판 · 설명·이미지 · 수량 · 발송 예정일) · 바뀐 칸 · 운영자 · 시각,
        펼치면 그 판의 제목·요약·설명(서버가 정화한 HTML 사본)·이미지 수·수량 사본. 서버 호출은 부르는 쪽 몫이다 — 더 보기는 `more` 로 올린다.
    -->
    <Drawer v-model:visible="open" position="right" :header="t('groupBuy.revisionsTitle')" :style="{ width: '36rem', maxWidth: '100vw' }">
        <ErrorState v-if="error && !items.length" :title="t('groupBuy.revisionsLoadFailed')" :description="error.message" retry :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="loading && !items.length" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 4" :key="n" height="3.5rem" />
        </div>
        <EmptyState v-else-if="!items.length" icon="pi pi-history" :title="t('groupBuy.revisionsEmpty')" :description="t('groupBuy.revisionsEmptyHint')" />
        <div v-else class="flex flex-col gap-3">
            <p class="m-0 text-xs text-muted-color">{{ t('groupBuy.revisionsLead') }}</p>
            <ol class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="rev in items" :key="rev.revisionNo" class="rounded-lg border border-(--line)">
                    <button type="button" class="flex w-full items-start gap-2 px-3 py-2 text-left" :aria-expanded="openNo === rev.revisionNo" @click="openNo = openNo === rev.revisionNo ? null : rev.revisionNo">
                        <span class="text-[12px] font-semibold tabular-nums">#{{ rev.revisionNo }}</span>
                        <span class="min-w-0 flex-1">
                            <span class="flex flex-wrap items-center gap-1.5">
                                <Tag :value="t(`groupBuy.revisionKind.${rev.kind}`)" severity="secondary" />
                                <span v-if="rev.changedFields.length" class="text-xs text-muted-color">{{ rev.changedFields.map((f) => fieldLabel(f)).join(' · ') }}</span>
                            </span>
                            <span class="mt-0.5 block text-[11px] text-muted-color">{{ rev.operator }} · {{ time.full(rev.createdAt) }}</span>
                        </span>
                        <i class="pi mt-1 text-xs text-muted-color" :class="openNo === rev.revisionNo ? 'pi-chevron-up' : 'pi-chevron-down'" aria-hidden="true" />
                    </button>
                    <div v-if="openNo === rev.revisionNo" class="flex flex-col gap-2 border-t border-(--line) px-3 py-2 text-[13px]">
                        <div><span class="text-xs text-muted-color">{{ t('groupBuy.fieldTitle') }}</span> {{ rev.title }}</div>
                        <div v-if="rev.summary"><span class="text-xs text-muted-color">{{ t('groupBuy.fieldSummary') }}</span> {{ rev.summary }}</div>
                        <!-- 서버가 정화한 HTML 사본이다(글 본문과 같은 정화 — 결정 2) -->
                        <div class="max-h-60 overflow-auto rounded border border-(--line) p-2 text-[13px]" v-html="rev.description" />
                        <div class="text-xs text-muted-color">
                            {{ t('groupBuy.revisionSnapshot', { images: rev.imageCount, max: rev.maxQty, added: rev.addedQty }) }}
                            <template v-if="rev.optionMax.length"> · {{ rev.optionMax.map((o) => `${o.name} ${o.maxQty ?? '∞'}`).join(', ') }}</template>
                            <template v-if="rev.shipPlannedAt"> · {{ t('groupBuy.revisionShipDate', { date: rev.shipPlannedAt }) }}</template>
                        </div>
                    </div>
                </li>
            </ol>
            <Button v-if="items.length < total" type="button" :label="t('groupBuy.loadMore')" size="small" severity="secondary" variant="outlined" :loading="loading" @click="emit('more')" />
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyRevision } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    items: GroupBuyRevision[];
    total: number;
    loading?: boolean;
    error?: ApiError | null;
}

withDefaults(defineProps<Props>(), { loading: false, error: null });
const emit = defineEmits<{ more: []; retry: [] }>();

const { t, te } = useI18n();
const time = useDisplayTime();
const openNo = ref<number | null>(null);

/** 바뀐 칸 이름(서버 열 이름) → 화면 이름. 모르는 이름은 그대로 */
function fieldLabel(field: string): string {
    const key = `groupBuy.revisionField.${field}`;
    return te(key) ? t(key) : field;
}
</script>
