<template>
    <!--
        로보티콘 팩 서랍 — 팩 관리 화면(`/robotikon/packs`)에서 줄을 누르면 연다(`?id=`). 서버는 부르지 않는다 — 동작은 emit.
        보기: 요약(출처 · 작가 · 개수 · 받은 수 · 작가 보상 합계 · 승인/고친 시각) · 판매 중단 사유 · 새 판 심사 안내(심사 화면 링크) · 설명·태그 · 로보티콘 격자(크기·바탕 조절).
        아래: 판매 중이면 [판매 중단](사유 — 부모 대화상자), 운영자 판매 중단이면(`resumable`) [다시 판매] (`emoticon.review`). 심사 중·반려 팩은 심사 화면으로 잇는다.
        작가 탈퇴로 멈춘 팩(`suspendKind: CREATOR_WITHDRAWN`)은 딱지 「작가 탈퇴로 판매 중단」 · 처리한 사람 「시스템」 · [다시 판매]·[가격 바꾸기] 없음(후속 결정 6).
        운영 팩(`OFFICIAL`)은 [고치기]로 편집 상태가 된다(최소 구성): 이름·설명·태그 · 로보티콘 칸마다 이름표 입력 · 앞/뒤로 옮기기 · 빼기 · [로보티콘 올리기](GIF·PNG, 여럿).
        올리기는 부모가 한 장씩 서버에 올리고 결과를 `addItems`(노출 메서드)로 넣는다. 저장은 `save`(이름·설명·태그·로보티콘 차례와 이름표) — 부모가 만들기(POST)·고치기(PUT)를 고른다.
        `creating` 이면 빈 운영 팩을 처음부터 편집 상태로 연다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '52rem', maxWidth: '100vw' }">
        <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-3">
                <EmoticonPackCover v-if="head" :src="head.coverUrl" :alt="head.name" :size="44" />
                <div class="flex min-w-0 flex-col gap-1">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ creating ? t('emoticon.official.createTitle') : head?.name }}</h2>
                    <div v-if="head" class="flex flex-wrap items-center gap-1.5">
                        <Tag :value="UtilEmoticonMessages.packStatus(t, head.status, head.suspendKind ?? null)" :severity="UtilEmoticon.packStatusSeverity(head.status)" />
                        <Tag :value="t(`emoticon.source.${head.source}`)" :severity="head.source === 'OFFICIAL' ? 'contrast' : 'secondary'" />
                    </div>
                </div>
            </div>
        </template>

        <DrawerSummary v-if="head && !creating" :label="t('drawerSummary.label')" :items="summaryItems">
            <template #item-creator>
                <NuxtLink v-if="head.creator && !head.creator.withdrawn" :to="UtilEmoticon.memberLink(head.creator.memberNo)" class="text-primary hover:underline">{{ head.creator.nickname }}</NuxtLink>
                <span v-else-if="head.creator" class="text-muted-color">{{ head.creator.nickname }}</span>
                <span v-else class="text-muted-color">{{ t('emoticon.officialCreator') }}</span>
            </template>
            <template v-if="head.salesCount !== null" #links>
                <NuxtLink :to="{ path: '/robotikon/sales', query: { packId: head.id } }" class="text-primary hover:underline">{{ t('emoticon.goSales') }} ›</NuxtLink>
            </template>
        </DrawerSummary>

        <ErrorState v-if="error && !creating" :title="t('emoticon.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail && !creating" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <!-- ── 운영 팩 편집 ─ -->
        <form v-else-if="editing" :id="formId" class="flex min-w-0 flex-col gap-5" novalidate @submit.prevent="submit">
            <Message severity="info" :closable="false"><span class="text-[13px]">{{ t('emoticon.official.editNote') }}</span></Message>

            <LabeledField :label="t('emoticon.official.name')" :hint="t('emoticon.official.nameHint', { min: SPEC.nameMin, max: SPEC.nameMax })" :error="fieldError('name')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="draft.name" :maxlength="SPEC.nameMax + 10" :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" fluid />
                </template>
            </LabeledField>
            <LabeledField :label="t('emoticon.official.description')" :error="fieldErrors.description ?? ''">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="draft.description" rows="2" auto-resize :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" fluid />
                </template>
            </LabeledField>
            <LabeledField :label="t('emoticon.official.tags')" :hint="t('emoticon.official.tagsHint', { max: SPEC.tagsMax })" :help="t('emoticon.official.tagsHelp')" :error="fieldError('tags')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="draft.tags" :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" fluid />
                </template>
            </LabeledField>

            <!-- 대표 이미지 — 200×200 PNG · 1MB(올리면 부모가 서버에 보내 실제로 읽어 검사한다) -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-edit-cover`">
                <h3 :id="`${titleId}-edit-cover`" class="m-0 text-sm font-semibold">{{ t('emoticon.official.cover') }}</h3>
                <div class="flex flex-wrap items-center gap-3">
                    <EmoticonPackCover v-if="draft.cover" :src="draft.cover.imageUrl" :alt="t('emoticon.official.cover')" :size="72" />
                    <span v-else class="inline-grid size-[72px] place-items-center rounded-lg border border-dashed border-(--line) text-muted-color"><i class="pi pi-image" aria-hidden="true" /></span>
                    <div class="flex flex-col gap-1">
                        <span v-if="draft.cover" class="text-[11px] tabular-nums text-muted-color">{{ draft.cover.width }}×{{ draft.cover.height }} · {{ UtilEmoticon.fileSize(draft.cover.bytes) }}</span>
                        <input ref="coverInput" type="file" :accept="UtilEmoticonImage.COVER_ACCEPT" class="sr-only" :aria-label="t('emoticon.official.coverUpload')" @change="onCoverFile" />
                        <Button type="button" :label="draft.cover ? t('emoticon.official.coverReplace') : t('emoticon.official.coverUpload')" icon="pi pi-upload" size="small" severity="secondary" variant="outlined" :loading="coverUploading" :disabled="saving" @click="coverInput?.click()" />
                    </div>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('emoticon.official.coverHelp', { size: SPEC.size, max: UtilEmoticon.fileSize(SPEC.coverMaxBytes) }) }}</p>
                <p v-if="coverError || fieldErrors.cover || (submitted && !draft.cover)" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ coverError || fieldErrors.cover || t('emoticon.official.coverRequired') }}</p>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-edit-items`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-edit-items`" class="m-0 text-sm font-semibold">
                        {{ t('emoticon.secItems', { count: draft.items.length }) }}
                        <span class="ml-1 text-xs font-normal" :class="countOk ? 'text-muted-color' : 'text-red-600 dark:text-red-400'">{{ t('emoticon.official.countRule', { min: itemMin, max: itemMax }) }}</span>
                    </h3>
                    <div class="flex items-center gap-2">
                        <input ref="fileInput" type="file" accept="image/gif,image/png" multiple class="sr-only" :aria-label="t('emoticon.official.upload')" @change="onFiles" />
                        <Button type="button" :label="t('emoticon.official.upload')" icon="pi pi-upload" size="small" severity="secondary" variant="outlined" :loading="uploading" :disabled="saving" @click="fileInput?.click()" />
                    </div>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('emoticon.official.uploadHelp', { size: SPEC.size, frames: SPEC.maxFrames, max: UtilEmoticon.fileSize(SPEC.maxBytes), recommended: UtilEmoticon.fileSize(SPEC.recommendedBytes) }) }}</p>
                <p v-if="uploadError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ uploadError }}</p>
                <p v-if="fieldErrors.items" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.items }}</p>
                <EmptyState v-if="!draft.items.length" icon="pi pi-images" :title="t('emoticon.official.noItems')" :description="t('emoticon.official.noItemsHint')" />
                <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] gap-2 p-0">
                    <li v-for="(item, index) in draft.items" :key="item.id" class="flex min-w-0 flex-col gap-1.5 rounded-lg border border-(--line) p-2">
                        <div class="relative grid place-items-center rounded-md border border-neutral-200 bg-white p-1 dark:border-neutral-700 dark:bg-neutral-900">
                            <img :src="item.imageUrl" :alt="item.alt" width="64" height="64" loading="lazy" class="block size-16" />
                        </div>
                        <!-- 파일에서 읽은 값 + 규격 검사(권장 넘음 등 — 어긴 것은 올릴 때 이미 막힌다) -->
                        <span class="text-[10px] leading-tight tabular-nums text-muted-color">{{ item.width }}×{{ item.height }} · {{ item.frames ? t('emoticon.official.framesShort', { frames: item.frames }) : item.format }} · {{ UtilEmoticon.fileSize(item.bytes) }}</span>
                        <ul v-if="itemIssueTexts(item).length" class="m-0 flex list-none flex-col gap-0.5 p-0">
                            <li v-for="(text, k) in itemIssueTexts(item)" :key="k" class="text-[10px] leading-tight" :class="text.level === 'error' ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ text.text }}</li>
                        </ul>
                        <label class="sr-only" :for="`${formId}-alt-${index}`">{{ t('emoticon.official.altLabel', { no: index + 1 }) }}</label>
                        <InputText
                            :id="`${formId}-alt-${index}`"
                            v-model="item.alt"
                            size="small"
                            :maxlength="SPEC.altMax + 5"
                            :invalid="Boolean(altError(index))"
                            :disabled="saving"
                            :placeholder="t('emoticon.official.altPlaceholder')"
                            fluid
                        />
                        <small v-if="altError(index)" class="text-[11px] text-red-600 dark:text-red-400">{{ altError(index) }}</small>
                        <div class="flex items-center justify-between gap-1">
                            <span class="text-[11px] tabular-nums text-muted-color">{{ index + 1 }}</span>
                            <span class="flex items-center">
                                <Button type="button" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" :disabled="saving || index === 0" :aria-label="t('emoticon.official.moveBefore', { no: index + 1 })" @click="move(index, -1)" />
                                <Button type="button" icon="pi pi-arrow-right" size="small" severity="secondary" variant="text" :disabled="saving || index === draft.items.length - 1" :aria-label="t('emoticon.official.moveAfter', { no: index + 1 })" @click="move(index, 1)" />
                                <Button type="button" icon="pi pi-times" size="small" severity="danger" variant="text" :disabled="saving" :aria-label="t('emoticon.official.remove', { no: index + 1 })" @click="removeAt(index)" />
                            </span>
                        </div>
                    </li>
                </ul>
            </section>

            <Message v-if="saveError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ saveError }}</Message>
        </form>

        <!-- ── 보기 ─ -->
        <div v-else-if="detail" class="flex min-w-0 flex-col gap-6">
            <Message v-if="detail.suspension" severity="error" :closable="false">
                <span class="text-[13px]">{{ UtilEmoticonMessages.suspensionNote(t, detail.suspension, detail.suspendKind ?? null, time.shortDateTime(detail.suspension.at)) }}</span>
            </Message>
            <Message v-if="detail.pendingReviewId" severity="info" :closable="false">
                <span class="text-[13px]">
                    {{ detail.pendingUpdate ? t('emoticon.pendingUpdateNote') : t('emoticon.pendingFirstNote') }}
                    <NuxtLink :to="{ path: '/robotikon/reviews', query: { status: detail.pendingUpdate ? 'UPDATE' : 'PENDING', id: detail.pendingReviewId } }" class="ml-1 font-medium whitespace-nowrap text-primary hover:underline">{{ t('emoticon.goReview') }} ›</NuxtLink>
                </span>
            </Message>
            <Message v-if="detail.source === 'OFFICIAL'" severity="secondary" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.officialNoReward') }}</span>
            </Message>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-info`">
                <h3 :id="`${titleId}-info`" class="m-0 text-sm font-semibold">{{ t('emoticon.secInfo') }}</h3>
                <p class="m-0 text-[13px] leading-relaxed [overflow-wrap:anywhere]">{{ detail.description || t('emoticon.noDescription') }}</p>
                <ul class="m-0 flex list-none flex-wrap gap-1 p-0">
                    <li v-for="tag in detail.tags" :key="tag"><Tag :value="`#${tag}`" severity="secondary" /></li>
                </ul>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-items`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-items`" class="m-0 text-sm font-semibold">{{ t('emoticon.secItems', { count: detail.items.length }) }}</h3>
                    <EmoticonPreviewControls v-model:size="size" v-model:dark="dark" />
                </div>
                <EmoticonGrid :items="detail.items" :size="size" :dark="dark" />
            </section>

            <!-- 처리 기록 — 가격 전환(언제·누가·얼마 → 얼마·사유). 작가 팩만 -->
            <section v-if="detail.source === 'CREATOR'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-events`">
                <h3 :id="`${titleId}-events`" class="m-0 text-sm font-semibold">{{ t('emoticon.secEvents') }}</h3>
                <p v-if="!detail.priceEvents.length" class="m-0 text-[13px] text-muted-color">{{ t('emoticon.priceEventsEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="(ev, i) in detail.priceEvents" :key="`${ev.at}-${i}`" class="flex flex-col gap-0.5 text-[13px]">
                        <span>
                            <b class="font-medium">{{ t('emoticon.priceEventLine', { from: priceText(ev.from), to: priceText(ev.to) }) }}</b>
                            <span class="text-muted-color"> · {{ ev.by ?? t('emoticon.priceEventByUnknown') }} · {{ time.shortDateTime(ev.at) }}</span>
                        </span>
                        <!-- 사유가 없으면 새 판 승인으로 바뀐 가격이다(서버 「S2 구현 결과」 결정 5) -->
                        <span class="text-xs text-muted-color [overflow-wrap:anywhere]">{{ ev.reason ?? t('emoticon.priceEventRevision') }}</span>
                    </li>
                </ul>
            </section>
        </div>

        <template v-if="footer" #footer>
            <div class="flex flex-wrap items-center justify-end gap-2">
                <template v-if="footer === 'edit'">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="cancelEdit" />
                    <Button type="submit" :form="formId" :label="creating ? t('emoticon.official.create') : t('common.save')" icon="pi pi-check" :loading="saving" :disabled="uploading" />
                </template>
                <template v-else>
                    <Button v-if="footer.includes('officialEdit')" type="button" class="mr-auto" :label="t('emoticon.official.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="startEdit" />
                    <Button v-if="footer.includes('price')" type="button" :label="t('emoticon.actChangePrice')" icon="pi pi-tag" severity="secondary" variant="outlined" :disabled="busy" @click="emit('price')" />
                    <Button v-if="footer.includes('suspend')" type="button" :label="t('emoticon.actSuspend')" icon="pi pi-ban" severity="danger" variant="outlined" :disabled="busy" @click="emit('suspend')" />
                    <Button v-if="footer.includes('resume')" type="button" :label="t('emoticon.actResume')" icon="pi pi-replay" :loading="busy" :disabled="busy" @click="emit('resume')" />
                    <Button
                        v-if="footer.includes('toReview') && detail?.pendingReviewId"
                        as="router-link"
                        :to="{ path: '/robotikon/reviews', query: { status: detail.status === 'REJECTED' ? 'REJECTED' : 'PENDING', id: detail.pendingReviewId } }"
                        :label="t('emoticon.goReview')"
                        icon="pi pi-arrow-right"
                        icon-pos="right"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                    />
                </template>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonCoverView, EmoticonItemView, EmoticonOfficialPackSaveRequest, EmoticonPackDetail, EmoticonPackRow } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonImage from '~/utils/util-emoticon-image';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary?: EmoticonPackRow | null;
    detail?: EmoticonPackDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 판매 중단·다시 판매 요청 중 */
    busy?: boolean;
    /** 판매 중단·다시 판매·운영 팩 고치기 권한(`emoticon.review`) */
    canManage?: boolean;
    /** 운영 팩 만들기 — 빈 편집 상태로 연다 */
    creating?: boolean;
    saving?: boolean;
    uploading?: boolean;
    saveError?: string;
    uploadError?: string;
    /** 서버가 거절한 칸(`name`·`tags`·`items[3].alt` …) */
    fieldErrors?: Record<string, string>;
    /** 대표 이미지 올리는 중 · 올리기 실패 문구(서버 문구 그대로) */
    coverUploading?: boolean;
    coverError?: string;
    /** 설정의 팩당 개수(운영 팩도 같은 규칙) */
    itemMin?: number;
    itemMax?: number;
}

const props = withDefaults(defineProps<Props>(), {
    summary: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    canManage: false,
    creating: false,
    saving: false,
    uploading: false,
    saveError: '',
    uploadError: '',
    coverUploading: false,
    coverError: '',
    itemMin: 8,
    itemMax: 40,
    fieldErrors: () => ({}),
});
const emit = defineEmits<{ retry: []; suspend: []; resume: []; price: []; upload: [files: File[]]; 'upload-cover': [file: File]; save: [body: EmoticonOfficialPackSaveRequest] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const formId = useId();
const SPEC = UtilEmoticon.SPEC;

const size = ref<number>(SPEC.displaySize);
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
        { key: 'source', label: t('emoticon.col.source'), value: t(`emoticon.source.${h.source}`) },
        { key: 'creator', label: t('emoticon.col.creator') },
        { key: 'count', label: t('emoticon.col.count'), value: t('emoticon.countValue', { count: h.itemCount }) },
        { key: 'price', label: t('emoticon.col.price'), value: h.price > 0 ? t('emoticon.wonValue', { amount: h.price.toLocaleString(locale.value) }) : t('emoticon.free'), valueClass: h.price > 0 ? 'font-semibold' : 'text-muted-color' },
        { key: 'acquire', label: t('emoticon.col.acquire'), value: t('emoticon.countPeople', { count: h.acquireCount.toLocaleString(locale.value) }) },
        { key: 'reward', label: t('emoticon.col.reward'), value: h.rewardTotal === null ? t('emoticon.noReward') : t('emoticon.pointValue', { amount: h.rewardTotal.toLocaleString(locale.value) }), valueClass: h.rewardTotal === null ? 'text-muted-color' : undefined },
        {
            key: 'sales',
            label: t('emoticon.col.sales'),
            value: h.salesCount === null || h.salesAmount === null ? '—' : t('emoticon.salesValue', { count: h.salesCount.toLocaleString(locale.value), amount: h.salesAmount.toLocaleString(locale.value) }),
            valueClass: h.salesCount === null ? 'text-muted-color' : undefined,
        },
        { key: 'updatedAt', label: t('emoticon.col.updatedAt'), value: time.value.shortDateTime(h.updatedAt) },
    ];
    if (props.detail?.approvedAt) items.push({ key: 'approvedAt', label: t('emoticon.approvedAt'), value: time.value.shortDateTime(props.detail.approvedAt), valueClass: UtilBizApplication.EMPH.date });
    return items;
});

// ── 운영 팩 편집 ─
interface Draft {
    name: string;
    description: string;
    tags: string;
    items: EmoticonItemView[];
    /** 대표 이미지 — 새 팩은 올리기 전까지 null */
    cover: EmoticonCoverView | null;
}
const editing = ref(false);
const draft = reactive<Draft>({ name: '', description: '', tags: '', items: [], cover: null });
const fileInput = ref<HTMLInputElement | null>(null);
const submitted = ref(false);

function fill(): void {
    const d = props.detail;
    draft.name = d?.name ?? '';
    draft.description = d?.description ?? '';
    draft.tags = d?.tags.join(', ') ?? '';
    draft.items = d ? d.items.map((it) => ({ ...it })) : [];
    draft.cover = d ? { ...d.cover } : null;
    submitted.value = false;
}

function startEdit(): void {
    fill();
    editing.value = true;
}

function cancelEdit(): void {
    if (props.creating) {
        open.value = false;
        return;
    }
    editing.value = false;
}

// 서랍을 열 때(만들기면 바로 편집), 닫을 때 편집 상태를 정리한다
watch(
    () => [open.value, props.creating] as const,
    ([visible, creating]) => {
        if (visible && creating) startEdit();
        else if (!visible) editing.value = false;
    },
    { immediate: true },
);

/** 저장한 뒤 부모가 새 상세를 주면 보기로 돌아간다 */
watch(
    () => props.detail,
    () => {
        if (!props.creating) editing.value = false;
    },
);

function move(index: number, delta: number): void {
    const to = index + delta;
    if (to < 0 || to >= draft.items.length) return;
    const [moved] = draft.items.splice(index, 1);
    if (moved) draft.items.splice(to, 0, moved);
}

function removeAt(index: number): void {
    draft.items.splice(index, 1);
}

function onFiles(e: Event): void {
    const input = e.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length) emit('upload', files);
}

/** 부모가 올린 결과를 끝에 붙인다 */
function addItems(items: EmoticonItemView[]): void {
    draft.items.push(...items);
}
/** 부모가 올린 대표 이미지를 넣는다 */
function setCover(next: EmoticonCoverView): void {
    draft.cover = next;
}
defineExpose({ addItems, setCover });

const coverInput = ref<HTMLInputElement | null>(null);
function onCoverFile(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) emit('upload-cover', file);
}

function fieldError(name: 'name' | 'tags'): string {
    if (submitted.value || name === 'tags') {
        if (name === 'name') {
            const len = draft.name.trim().length;
            if (len < SPEC.nameMin || len > SPEC.nameMax) return t('emoticon.official.nameProblem', { min: SPEC.nameMin, max: SPEC.nameMax });
        } else if (UtilEmoticon.parseTags(draft.tags).length > SPEC.tagsMax) {
            return t('emoticon.official.tagsProblem', { max: SPEC.tagsMax });
        }
    }
    return props.fieldErrors[name] ?? '';
}

function altError(index: number): string {
    const item = draft.items[index];
    if (!item) return '';
    const len = item.alt.trim().length;
    if ((submitted.value || len > SPEC.altMax) && (len < SPEC.altMin || len > SPEC.altMax)) return t('emoticon.official.altProblem', { min: SPEC.altMin, max: SPEC.altMax });
    return props.fieldErrors[`items[${index}].alt`] ?? '';
}

/** 개수 — 설정의 팩당 최소~최대(작가 팩과 같은 규칙) */
const countOk = computed(() => draft.items.length >= props.itemMin && draft.items.length <= props.itemMax);

const hasProblem = computed(() => {
    const name = draft.name.trim().length;
    if (name < SPEC.nameMin || name > SPEC.nameMax) return true;
    if (UtilEmoticon.parseTags(draft.tags).length > SPEC.tagsMax) return true;
    if (!draft.cover || !countOk.value) return true;
    return draft.items.some((it) => {
        const len = it.alt.trim().length;
        return len < SPEC.altMin || len > SPEC.altMax;
    });
});

/** 칸 하나의 규격 문제(파일에서 읽은 값) — 이름표는 칸 아래 따로 */
function itemIssueTexts(item: EmoticonItemView): { level: 'error' | 'warn'; text: string }[] {
    return UtilEmoticon.itemIssues(item)
        .filter((i) => i.code !== 'ALT_LENGTH')
        .map((i) => ({ level: i.level, text: UtilEmoticonMessages.specIssue(t, i) }));
}

function submit(): void {
    submitted.value = true;
    if (props.saving || props.uploading || props.coverUploading || hasProblem.value || !draft.cover) return;
    emit('save', {
        name: draft.name.trim(),
        description: draft.description.trim(),
        tags: UtilEmoticon.parseTags(draft.tags),
        items: draft.items.map((it) => ({ id: it.id, alt: it.alt.trim() })),
        coverId: draft.cover.id,
    });
}

/** 서랍 아래 칸 — 편집 중이면 [취소]·[저장], 아니면 상태·권한으로 고른 단추들 */
const footer = computed<'edit' | string[] | null>(() => {
    if (editing.value) return 'edit';
    const d = props.detail;
    if (!d || !props.canManage) return null;
    const out: string[] = [];
    if (d.source === 'OFFICIAL') out.push('officialEdit');
    // 판매 중 작가 팩이면 무료든 유료든 [가격 바꾸기](유료 ↔ 무료 전환 포함). 운영 팩은 늘 무료
    // `UtilEmoticon.PRICE_CHANGE_READY` — S2 서버(`POST …/packs/{id}/price`)가 붙어 true
    // 작가 탈퇴로 멈춘 팩(`resumable: false`)은 판매 중단이라 가격 단추가 이미 없고, [다시 판매]도 두지 않는다(서버도 `409 EMOTICON_PACK_CREATOR_WITHDRAWN`)
    if (UtilEmoticon.PRICE_CHANGE_READY && d.status === 'APPROVED' && d.source === 'CREATOR') out.push('price');
    if (d.status === 'APPROVED') out.push('suspend');
    if (d.status === 'SUSPENDED' && d.resumable) out.push('resume');
    if ((d.status === 'PENDING' || d.status === 'REJECTED') && d.pendingReviewId) out.push('toReview');
    return out.length ? out : null;
});
</script>
