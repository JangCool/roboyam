<template>
    <!--
        로보티콘 심사 서랍 — 심사 화면(`/robotikon/reviews`)에서 줄을 누르면 연다(`?id=`). 서버는 부르지 않는다 — 승인·반려는 emit, 부모가 확인·사유 대화상자를 띄운다.
        위에서 아래로: 요약(작가 · 개수 · 신청 시각 · 판 · 작가의 심사 대기) · 처리 기록(반려 사유 / 승인) · 규격 검사(화면이 계산 — `UtilEmoticon.packIssues`)
        · 새 판 비교(새 판일 때 — 더해진·빠진·그림 바뀐·이름표 바뀐 것, 이름·설명·태그·대표·차례) · 로보티콘 격자(크기 100/80/64 · 밝은/어두운 바탕, 칸 아래 이름표)
        · 설명·태그·대표 · 유료 희망이면 상품화 계약 동의(판·시각)·정산 정보 등록 여부 · 작가 동의 3개 기록. 아래: 심사 대기면 [반려]·[승인](`emoticon.review` — 부모가 가격 고르기 대화상자 `EmoticonPriceDialog` 를 띄운다). 규격 어긋남이 있으면 승인 단추 위에 한 줄 경고(막지는 않는다).
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '52rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 items-center gap-3">
                <EmoticonPackCover :src="head.coverUrl" :alt="head.packName" :size="44" />
                <div class="flex min-w-0 flex-col gap-1">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.packName }}</h2>
                    <div class="flex flex-wrap items-center gap-1.5">
                        <Tag :value="t(`emoticon.reviewStatus.${head.status}`)" :severity="UtilEmoticon.reviewStatusSeverity(head.status)" />
                        <Tag :value="head.kind === 'UPDATE' ? t('emoticon.kindUpdate', { version: head.version }) : t('emoticon.kindFirst')" :severity="head.kind === 'UPDATE' ? 'contrast' : 'secondary'" />
                    </div>
                </div>
            </div>
        </template>

        <DrawerSummary v-if="head" :label="t('drawerSummary.label')" :items="summaryItems">
            <template #item-creator>
                <NuxtLink v-if="!head.creator.withdrawn" :to="UtilEmoticon.memberLink(head.creator.memberNo)" class="text-primary hover:underline">{{ head.creator.nickname }}</NuxtLink>
                <span v-else class="text-muted-color">{{ head.creator.nickname }}</span>
            </template>
        </DrawerSummary>

        <ErrorState v-if="error" :title="t('emoticon.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <!-- 처리 기록 -->
            <Message v-if="detail.decision?.status === 'REJECTED'" severity="warn" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.rejectedNote', { by: detail.decision.decidedBy, at: time.shortDateTime(detail.decision.decidedAt) }) }} — {{ detail.decision.reason }}</span>
            </Message>
            <Message v-else-if="detail.decision?.status === 'APPROVED'" severity="success" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.approvedNote', { by: detail.decision.decidedBy, at: time.shortDateTime(detail.decision.decidedAt) }) }}</span>
            </Message>
            <Message v-if="detail.kind === 'UPDATE' && detail.status === 'PENDING'" severity="info" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.updateNote') }}</span>
            </Message>

            <!-- 규격 검사 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-spec`">
                <h3 :id="`${titleId}-spec`" class="m-0 text-sm font-semibold">{{ t('emoticon.secSpec') }}</h3>
                <p v-if="!issues.length" class="m-0 flex items-center gap-1.5 text-[13px] text-green-700 dark:text-green-400">
                    <i class="pi pi-check-circle text-xs" aria-hidden="true" />{{ t('emoticon.spec.allClear', { count: detail.next.items.length }) }}
                </p>
                <template v-else>
                    <p class="m-0 text-[13px]">
                        <span v-if="errorCount" class="font-semibold text-red-700 dark:text-red-400">{{ t('emoticon.spec.errorCount', { count: errorCount }) }}</span>
                        <span v-if="errorCount && warnCount" class="text-muted-color"> · </span>
                        <span v-if="warnCount" class="font-semibold text-amber-700 dark:text-amber-400">{{ t('emoticon.spec.warnCount', { count: warnCount }) }}</span>
                    </p>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0">
                        <li v-for="(issue, i) in issues" :key="`${issue.code}-${issue.itemId ?? 'pack'}-${i}`" class="flex items-start gap-2 text-[13px]">
                            <i class="pi mt-0.5 text-xs" :class="issue.level === 'error' ? 'pi-times-circle text-red-600 dark:text-red-400' : 'pi-exclamation-circle text-amber-600 dark:text-amber-400'" aria-hidden="true" />
                            <span class="min-w-0">
                                <b v-if="issue.itemId" class="font-medium">{{ itemLabel(issue.itemId) }}</b>
                                <b v-else class="font-medium">{{ t('emoticon.spec.packLevel') }}</b>
                                — {{ UtilEmoticonMessages.specIssue(t, issue) }}
                            </span>
                        </li>
                    </ul>
                </template>
                <p class="m-0 text-xs text-muted-color">{{ t('emoticon.spec.basis', { min: detail.rules.itemMin, max: detail.rules.itemMax }) }}</p>
            </section>

            <!-- 새 판 비교 -->
            <section v-if="diff" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-diff`">
                <h3 :id="`${titleId}-diff`" class="m-0 text-sm font-semibold">{{ t('emoticon.secDiff', { from: detail.current!.version, to: detail.next.version }) }}</h3>
                <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-4">
                    <div v-for="cell in diffCells" :key="cell.key" class="rounded-lg border border-(--line) px-3 py-2">
                        <dt class="text-xs text-muted-color">{{ cell.label }}</dt>
                        <dd class="m-0 mt-0.5 text-[13px] font-semibold tabular-nums" :class="cell.count ? '' : 'font-normal text-muted-color'">{{ cell.count }}</dd>
                    </div>
                </dl>
                <ul v-if="fieldChanges.length" class="m-0 flex list-none flex-wrap gap-1.5 p-0">
                    <li v-for="change in fieldChanges" :key="change"><Tag :value="change" severity="info" /></li>
                </ul>
                <!-- 작가 가격 변경 요청 메모 — 가격은 운영자만 바꾼다(승인 대화상자에서 단계를 고르거나 팩 관리의 [가격 바꾸기]) -->
                <div v-if="detail.next.priceRequestMemo" class="flex items-start gap-2 rounded-lg border border-sky-300 px-3 py-2 text-[13px] dark:border-sky-800">
                    <i class="pi pi-comment mt-0.5 text-xs text-sky-600 dark:text-sky-400" aria-hidden="true" />
                    <div class="flex min-w-0 flex-col gap-0.5">
                        <span class="text-xs text-muted-color">{{ t('emoticon.diff.priceMemo', { price: priceText(detail.currentPrice) }) }}</span>
                        <span class="[overflow-wrap:anywhere]">{{ detail.next.priceRequestMemo }}</span>
                    </div>
                </div>
                <div v-if="diff.descriptionChanged" class="grid grid-cols-1 gap-2 text-[13px] sm:grid-cols-2">
                    <div class="rounded-lg border border-(--line) px-3 py-2">
                        <p class="m-0 text-xs text-muted-color">{{ t('emoticon.diff.before') }}</p>
                        <p class="m-0 mt-0.5 [overflow-wrap:anywhere]">{{ detail.current!.description }}</p>
                    </div>
                    <div class="rounded-lg border border-sky-300 px-3 py-2 dark:border-sky-800">
                        <p class="m-0 text-xs text-muted-color">{{ t('emoticon.diff.after') }}</p>
                        <p class="m-0 mt-0.5 [overflow-wrap:anywhere]">{{ detail.next.description }}</p>
                    </div>
                </div>
                <ul v-if="diff.changedAlt.length" class="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                    <li v-for="c in diff.changedAlt" :key="c.item.id">
                        {{ t('emoticon.diff.altLine', { before: c.before, after: c.item.alt }) }}
                    </li>
                </ul>
                <div v-if="diff.removed.length" class="flex flex-col gap-1.5">
                    <h4 class="m-0 text-xs text-muted-color">{{ t('emoticon.diff.removedTitle', { count: diff.removed.length }) }}</h4>
                    <EmoticonGrid :items="diff.removed" :size="64" :dark="dark" removed />
                </div>
                <p v-if="!diffCells.some((c) => c.count) && !fieldChanges.length" class="m-0 text-[13px] text-muted-color">{{ t('emoticon.diff.none') }}</p>
            </section>

            <!-- 로보티콘 격자 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-items`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-items`" class="m-0 text-sm font-semibold">{{ t('emoticon.secItems', { count: detail.next.items.length }) }}</h3>
                    <EmoticonPreviewControls v-model:size="size" v-model:dark="dark" />
                </div>
                <EmoticonGrid :items="detail.next.items" :size="size" :dark="dark" :marks="diff?.marks ?? {}" :levels="levels" />
                <p class="m-0 text-xs text-muted-color">{{ t('emoticon.preview.hint') }}</p>
            </section>

            <!-- 설명·태그·대표 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-info`">
                <h3 :id="`${titleId}-info`" class="m-0 text-sm font-semibold">{{ t('emoticon.secInfo') }}</h3>
                <div class="flex items-start gap-3">
                    <figure class="m-0 flex shrink-0 flex-col items-center gap-1">
                        <EmoticonPackCover :src="detail.next.cover.imageUrl" :alt="detail.next.name" :size="72" />
                        <figcaption class="text-[11px] tabular-nums text-muted-color">{{ detail.next.cover.width }}×{{ detail.next.cover.height }} · {{ UtilEmoticon.fileSize(detail.next.cover.bytes) }}</figcaption>
                    </figure>
                    <div class="flex min-w-0 flex-col gap-1.5">
                        <p class="m-0 text-[13px] leading-relaxed [overflow-wrap:anywhere]">{{ detail.next.description || t('emoticon.noDescription') }}</p>
                        <ul class="m-0 flex list-none flex-wrap gap-1 p-0">
                            <li v-for="tag in detail.next.tags" :key="tag"><Tag :value="`#${tag}`" severity="secondary" /></li>
                        </ul>
                    </div>
                </div>
            </section>

            <!-- 유료 희망 — 상품화 계약 동의 · 정산 정보 등록(가정 1: 둘 다 있어야 유료로 승인한다) -->
            <section v-if="detail.next.saleWish === 'PAID'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-paid`">
                <h3 :id="`${titleId}-paid`" class="m-0 text-sm font-semibold">{{ t('emoticon.secPaidReady') }}</h3>
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li class="flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                        <i class="pi mt-0.5 text-xs" :class="detail.contractConsent?.current ? 'pi-check-square text-green-600 dark:text-green-400' : 'pi-exclamation-triangle text-red-600 dark:text-red-400'" aria-hidden="true" />
                        <div class="flex min-w-0 flex-col gap-0.5">
                            <span>{{ t('emoticon.contractConsent') }}</span>
                            <span class="text-[11px] tabular-nums text-muted-color">
                                <template v-if="detail.contractConsent">{{ t('emoticon.contractAgreed', { version: detail.contractConsent.version, at: time.full(detail.contractConsent.agreedAt) }) }}<template v-if="!detail.contractConsent.current"> · {{ t('emoticon.contractOutdated') }}</template></template>
                                <template v-else>{{ t('emoticon.contractMissing') }}</template>
                            </span>
                        </div>
                    </li>
                    <li class="flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                        <i class="pi mt-0.5 text-xs" :class="detail.payoutRegistered ? 'pi-check-square text-green-600 dark:text-green-400' : 'pi-exclamation-triangle text-red-600 dark:text-red-400'" aria-hidden="true" />
                        <div class="flex min-w-0 flex-col gap-0.5">
                            <span>{{ t('emoticon.payoutInfo') }}</span>
                            <span class="text-[11px] text-muted-color">{{ detail.payoutRegistered ? t('emoticon.payoutRegistered') : t('emoticon.payoutMissing') }}</span>
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 작가 동의 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-consent`">
                <h3 :id="`${titleId}-consent`" class="m-0 text-sm font-semibold">{{ t('emoticon.secConsent') }}</h3>
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="c in consentRows" :key="c.kind" class="flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                        <i class="pi mt-0.5 text-xs" :class="c.agreedAt ? 'pi-check-square text-green-600 dark:text-green-400' : 'pi-stop text-red-600 dark:text-red-400'" aria-hidden="true" />
                        <div class="flex min-w-0 flex-col gap-0.5">
                            <span>{{ t(`emoticon.consent.${c.kind}`) }}</span>
                            <span class="text-[11px] tabular-nums text-muted-color">{{ c.agreedAt ? t('emoticon.consent.agreedAt', { at: time.full(c.agreedAt), version: c.textVersion }) : t('emoticon.consent.missing') }}</span>
                        </div>
                    </li>
                </ul>
            </section>
        </div>

        <template v-if="detail && detail.status === 'PENDING' && canReview" #footer>
            <div class="flex flex-col gap-2">
                <p v-if="errorCount" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400"><i class="pi pi-exclamation-triangle" aria-hidden="true" />{{ t('emoticon.approveSpecWarn', { count: errorCount }) }}</p>
                <div class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('emoticon.actReject')" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                    <Button type="button" :label="t('emoticon.actApprove')" icon="pi pi-check" :loading="busy && approving" :disabled="busy" @click="emit('approve', errorCount)" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonReviewDetail, EmoticonReviewRow } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import { EMOTICON_CONSENT_KINDS } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 목록 줄 — 상세가 오기 전 머리·요약에 쓴다 */
    summary?: EmoticonReviewRow | null;
    detail?: EmoticonReviewDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    /** 승인 요청 중(단추 돌기) */
    approving?: boolean;
    /** 승인·반려 권한(`emoticon.review`) */
    canReview?: boolean;
}

const props = withDefaults(defineProps<Props>(), { summary: null, detail: null, loading: false, error: null, busy: false, approving: false, canReview: false });
const emit = defineEmits<{ retry: []; approve: [specErrors: number]; reject: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

/** 격자 보기 — 서랍을 다시 열어도 마지막 값을 쓴다(한 화면 안에서만) */
const size = ref<number>(UtilEmoticon.SPEC.displaySize);
const dark = ref(false);

const head = computed(() => props.detail ?? props.summary);

/** 가격 글자 — 0 은 「무료」 */
function priceText(price: number): string {
    return price > 0 ? t('emoticon.wonValue', { amount: price.toLocaleString(locale.value) }) : t('emoticon.free');
}

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    const items: DrawerSummaryItem[] = [
        { key: 'creator', label: t('emoticon.col.creator'), title: h.creator.nickname },
        { key: 'count', label: t('emoticon.col.count'), value: t('emoticon.countValue', { count: h.itemCount }) },
        { key: 'saleWish', label: t('emoticon.col.saleWish'), value: t(`emoticon.saleWish.${h.saleWish}`), valueClass: h.saleWish === 'PAID' ? 'font-semibold' : 'text-muted-color' },
        ...(h.kind === 'UPDATE' ? [{ key: 'currentPrice', label: t('emoticon.col.currentPrice'), value: priceText(h.currentPrice) }] : []),
        { key: 'submittedAt', label: t('emoticon.col.submittedAt'), value: time.value.shortDateTime(h.submittedAt), valueClass: UtilBizApplication.EMPH.date },
        { key: 'version', label: t('emoticon.col.version'), value: h.kind === 'UPDATE' ? t('emoticon.kindUpdate', { version: h.version }) : t('emoticon.kindFirst') },
    ];
    if (props.detail) items.push({ key: 'pending', label: t('emoticon.creatorPending'), value: t('emoticon.countCase', { count: props.detail.creatorPendingCount.toLocaleString(locale.value) }) });
    items.push({ key: 'id', label: t('emoticon.reviewId'), value: h.id, code: true });
    return items;
});

const issues = computed(() => (props.detail ? UtilEmoticon.packIssues(props.detail.next, props.detail.rules) : []));
const errorCount = computed(() => issues.value.filter((i) => i.level === 'error').length);
const warnCount = computed(() => issues.value.filter((i) => i.level === 'warn').length);
const levels = computed(() => UtilEmoticon.issueLevels(issues.value));

/** 로보티콘 아이디 → 「#3 헉」 */
function itemLabel(id: string): string {
    const items = props.detail?.next.items ?? [];
    const index = items.findIndex((it) => it.id === id);
    if (index < 0) return id;
    return t('emoticon.spec.itemLabel', { no: index + 1, alt: items[index]!.alt });
}

const diff = computed(() => (props.detail?.current ? UtilEmoticon.diff(props.detail.current, props.detail.next) : null));
const diffCells = computed(() => {
    const d = diff.value;
    if (!d) return [];
    return [
        { key: 'added', label: t('emoticon.diff.added'), count: d.added.length },
        { key: 'removed', label: t('emoticon.diff.removed'), count: d.removed.length },
        { key: 'changedImage', label: t('emoticon.diff.changedImage'), count: d.changedImage.length },
        { key: 'changedAlt', label: t('emoticon.diff.changedAlt'), count: d.changedAlt.length },
    ];
});
const fieldChanges = computed(() => {
    const d = diff.value;
    if (!d) return [];
    const out: string[] = [];
    if (d.nameChanged) out.push(t('emoticon.diff.nameChanged'));
    if (d.descriptionChanged) out.push(t('emoticon.diff.descriptionChanged'));
    if (d.tagsChanged) out.push(t('emoticon.diff.tagsChanged'));
    if (d.priceChanged && props.detail?.current && props.detail.current.saleWish !== props.detail.next.saleWish) out.push(t('emoticon.diff.wishChanged', { before: t(`emoticon.saleWish.${props.detail.current.saleWish}`), after: t(`emoticon.saleWish.${props.detail.next.saleWish}`) }));
    if (d.coverChanged) out.push(t('emoticon.diff.coverChanged'));
    if (d.orderChanged) out.push(t('emoticon.diff.orderChanged'));
    return out;
});

/** 동의 셋을 늘 같은 차례로 — 서버가 빠뜨린 것은 「기록 없음」으로 보인다 */
const consentRows = computed(() => EMOTICON_CONSENT_KINDS.map((kind) => props.detail?.consents.find((c) => c.kind === kind) ?? { kind, agreedAt: null, textVersion: '' }));
</script>
