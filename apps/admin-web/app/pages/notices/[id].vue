<template>
    <!--
        공지 작성·수정(`/notices/new`·`/notices/{id}`) — 공지 게시판(slug `notice`)의 글 하나. `new` 는 새 글(group-buy-products/[id].vue 와 같은 방식).
        서버 호출은 `useNoticeApi`. 계약 `docs/features/notice-admin-contract.md`(화면 확정 2026-11-07).
        - 칸: 말머리(선택 — 공지 게시판의 켜진 말머리) · 제목(필수 1~100자, 글자 수) · 본문(RichEditor 고급, 필수, 이미지 올리기) · 「상단 고정」(한도가 차면 꺼짐 + 안내). 댓글 허용 칸은 없다.
        - 작성 계정: 새 글은 내 운영자 회원 계정으로 올린다. 수정은 작성자가 바뀌지 않는다(다른 운영자 회원 계정이 쓴 공지도 고친다 — 계약 정책 1).
          내 운영자 회원 계정이 없거나 멈췄으면 경고 띠 + 저장 막힘(새 글·수정 모두 — 서버도 `409 NOTICE_WRITER_NOT_LINKED`).
        - 수정 머리: 상태 알약 · 작성·마지막 수정 시각 · 조회수 · [공개 사이트에서 보기] · [게시글 관리에서 보기]. 지운 글이면 읽기 전용 + 「게시글 관리에서 복구」.
        - 저장: 바닥 줄 [취소] [저장](새 글은 「올리기」). 저장 중·이미지를 올리는 중·실패한 이미지가 있으면 막는다. 400 `fields.*` 는 그 칸 아래, 나머지는 줄 안 한 줄(서버 문구 그대로).
          성공하면 토스트 + 새 글은 목록으로, 수정은 그대로 머문다. 저장하지 않은 변경이 있으면 떠날 때 묻는다.
        - 권한: 보기 `post.read` · 쓰기·고치기 `post.manage`(없으면 읽기 전용 안내, 저장 줄 없음).
        - 전체 공지(2026-11-08): 「상단 고정」을 켜면 아래에 고정 범위 · 뺄 게시판 · 걸어 둘 기간(`NoticeGlobalOptions`). 고정을 끄면 범위·제외·기간도 사라진다(보낼 때 NOTICE_ONLY).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="pageTitle" :subtitle="isNew ? t('notice.newSubtitle') : t('notice.editSubtitle')">
            <template #actions>
                <Button as="router-link" to="/notices" :label="t('notice.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" />
                <template v-if="detail">
                    <Button v-if="detail.state === 'PUBLIC' && detail.publicUrl" as="a" :href="detail.publicUrl" target="_blank" rel="noopener noreferrer" :label="t('notice.viewPublic')" icon="pi pi-external-link" size="small" severity="secondary" variant="outlined" />
                    <Button as="router-link" :to="{ path: '/posts', query: { post: detail.id } }" :label="t('notice.viewInPosts')" icon="pi pi-file-edit" size="small" severity="secondary" variant="outlined" />
                </template>
            </template>
        </PageTitle>

        <AppGadget v-if="loading" :title="t('notice.secContent')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="3rem" /></div>
        </AppGadget>
        <ErrorState
            v-else-if="loadError"
            :code="loadError.status === 404 ? '404' : '!'"
            :title="t(loadError.status === 404 ? 'notice.notFound' : 'notice.loadOneFailed')"
            :description="loadError.message"
            :retry="loadError.status !== 404"
            :retrying="loading"
            @retry="load"
        />
        <EmptyState v-else-if="context && !boardReady" icon="pi pi-megaphone" :title="context.board ? t('notice.boardOffTitle') : t('notice.boardMissingTitle')" :description="context.board ? t('notice.boardOffHint') : t('notice.boardMissingHint')">
            <Button as="router-link" :to="context.board ? `/boards/${encodeURIComponent(context.board.slug)}` : '/boards'" :label="t('notice.toBoards')" size="small" severity="secondary" variant="outlined" />
        </EmptyState>

        <form v-else-if="context" :key="formKey" class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notice.readOnly') }}</Message>
            <Message v-if="deleted" severity="warn" :closable="false" size="small">
                <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    {{ t('notice.deletedNotice') }}
                    <NuxtLink :to="{ path: '/posts', query: { post: detail!.id } }" class="font-medium underline">{{ t('notice.restoreInPosts') }}</NuxtLink>
                </span>
            </Message>
            <NoticeWriterWarning v-if="canManage && !deleted" :writer="context.writer" />

            <!-- 수정 머리 — 상태·시각·조회수·작성자 -->
            <AppGadget v-if="detail" :title="t('notice.secInfo')">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-start gap-x-6 gap-y-2 text-[13px]">
                        <PostStateTag :item="detail" lines />
                        <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                            <dt class="text-muted-color">{{ t('notice.createdAt') }}</dt>
                            <dd class="m-0 tabular-nums"><time :datetime="detail.createdAt">{{ time.full(detail.createdAt) }}</time></dd>
                            <dt class="text-muted-color">{{ t('notice.editedAt') }}</dt>
                            <dd class="m-0 tabular-nums">
                                <time v-if="detail.editedAt" :datetime="detail.editedAt">{{ time.full(detail.editedAt) }}</time>
                                <span v-else class="text-muted-color">{{ t('notice.neverEdited') }}</span>
                            </dd>
                            <dt class="text-muted-color">{{ t('notice.views') }}</dt>
                            <dd class="m-0 tabular-nums">{{ detail.viewCount.toLocaleString(locale) }}</dd>
                        </dl>
                    </div>
                    <p class="m-0 text-[13px]">
                        <span class="text-muted-color">{{ t('notice.authorLabel') }}</span>
                        <b class="ml-2">{{ detail.author.name }}</b>
                        <span v-if="detail.author.operatorName" class="ml-1 text-xs text-muted-color">{{ t('notice.operatorName', { name: detail.author.operatorName }) }}</span>
                    </p>
                    <p v-if="!deleted" class="m-0 text-xs text-muted-color">{{ t('notice.authorKept') }}</p>
                </div>
            </AppGadget>

            <AppGadget :title="t('notice.secContent')">
                <div class="flex flex-col gap-4">
                    <!-- 새 글의 작성 계정 한 줄 -->
                    <i18n-t v-if="isNew && canWrite" keypath="notice.writerLineForm" tag="p" scope="global" class="m-0 flex items-center gap-1.5 text-[13px]">
                        <template #nickname><b>{{ context.writer.nickname }}</b></template>
                    </i18n-t>

                    <LabeledField :label="t('notice.fieldPrefix')" :help="t('notice.fieldPrefixHelp')" :error="fe('prefixId')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select
                                v-model="form.prefixId"
                                :input-id="id"
                                :options="prefixChoices"
                                option-label="label"
                                option-value="value"
                                show-clear
                                :placeholder="t('notice.prefixNone')"
                                :disabled="ro"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                class="w-full sm:w-60"
                            />
                        </template>
                    </LabeledField>

                    <LabeledField :label="t('notice.fieldTitle')" :hint="t('notice.titleCount', { n: titleLength, max: UtilNotice.TITLE_MAX })" :error="fe('title')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.title" :maxlength="UtilNotice.TITLE_MAX" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" :placeholder="t('notice.titlePlaceholder')" fluid />
                        </template>
                    </LabeledField>

                    <div class="flex flex-col gap-1.5">
                        <span :id="bodyLabelId" class="text-sm font-medium">{{ t('notice.fieldBody') }}</span>
                        <RichEditor
                            :key="`notice-body-${formKey}`"
                            v-model="form.body"
                            level="ADVANCED"
                            image-upload
                            :upload="richUpload"
                            :disabled="ro"
                            :labelledby="bodyLabelId"
                            :describedby="bodyMessageId"
                            :invalid="!!fe('body')"
                            :placeholder="t('notice.bodyPlaceholder')"
                            @ready="onEditorReady"
                            @media="media = $event"
                        />
                        <div :id="bodyMessageId" aria-live="polite" class="text-xs leading-relaxed">
                            <p v-if="fe('body')" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fe('body') }}</p>
                            <p v-else-if="mediaBlocked" class="m-0 text-amber-700 dark:text-amber-400">{{ t(media.uploading > 0 ? 'notice.mediaUploading' : 'notice.mediaFailed') }}</p>
                        </div>
                    </div>

                    <div class="flex flex-col gap-1">
                        <div class="flex items-center gap-2">
                            <Checkbox v-model="form.pinned" :input-id="pinId" binary :disabled="ro || !pinAllowed" :aria-describedby="pinHelpId" />
                            <label :for="pinId" class="text-sm" :class="ro || !pinAllowed ? 'text-muted-color' : ''">{{ t('notice.fieldPinned') }}</label>
                        </div>
                        <p :id="pinHelpId" class="m-0 pl-7 text-xs" :class="fe('pinned') ? 'text-red-600 dark:text-red-400' : pinBlockedReason ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">
                            {{ fe('pinned') || pinBlockedReason || t('notice.pinnedHelp', { n: context.board?.pinnedCount ?? 0, max: context.board?.pinnedMax ?? 0 }) }}
                        </p>
                    </div>
                    <NoticeGlobalOptions
                        v-if="form.pinned"
                        v-model:scope="form.scope"
                        v-model:excluded="form.excluded"
                        v-model:exclude-all-groups="form.excludeAllGroups"
                        v-model:period="form.period"
                        v-model:period-date="form.periodDate"
                        :global="context.global"
                        :live-global="liveGlobal"
                        :expired="globalExpiredKept"
                        :disabled="ro"
                        :errors="serverErrors"
                    />
                </div>
            </AppGadget>

            <!-- 바닥 줄 — [취소] [저장/올리기] -->
            <div v-if="canManage && !deleted" class="flex flex-col gap-2 rounded-lg border border-(--line) bg-surface-0 px-3 py-2.5 shadow-md md:sticky md:bottom-3 md:z-20 dark:bg-surface-900">
                <Message v-if="formError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ formError }}</Message>
                <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
                    <p class="m-0 min-w-0 flex-1 basis-48 text-sm" :class="dirty ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'" role="status">
                        {{ saveStatus }}<template v-if="errorCount > 0"> · <span class="text-red-600 dark:text-red-400">{{ t('common.saveBarErrors', { count: errorCount }) }}</span></template>
                    </p>
                    <div class="ml-auto flex flex-wrap items-center justify-end gap-2">
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="saving" @click="cancel" />
                        <Button type="submit" :label="isNew ? t('notice.publish') : t('common.save')" size="small" :loading="saving" :disabled="!!saveBlocked" />
                    </div>
                </div>
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { RichEditorMediaState, RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { NoticeContext, NoticeItem, NoticePinScope, NoticeSaveRequest } from '~/composables/notice/api/useNoticeApi.types';
import type { NoticePeriod } from '~/utils/util-notice';
import UtilNotice from '~/utils/util-notice';
import UtilPost from '~/utils/util-post';

interface NoticeForm {
    prefixId: string | null;
    title: string;
    body: string;
    pinned: boolean;
    /** 전체 공지 — 고정했을 때만 뜻이 있다 */
    scope: NoticePinScope;
    excluded: string[];
    excludeAllGroups: boolean;
    period: NoticePeriod;
    /** 날짜 고르기의 날(`YYYY-MM-DD`, 한국 시각) */
    periodDate: string;
}

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useNoticeApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const bodyLabelId = useId();
const bodyMessageId = useId();
const pinId = useId();
const pinHelpId = useId();

const idParam = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => idParam.value === 'new');
const canManage = computed(() => can('post.manage'));

const context = ref<NoticeContext | null>(null);
const detail = ref<NoticeItem | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const formKey = ref(0);
const media = ref<RichEditorMediaState>({ images: 0, videos: 0, uploading: 0, failed: 0 });

const pageTitle = computed(() => (isNew.value ? t('notice.newTitle') : t('notice.editTitle')));
useSeoMeta({ title: () => pageTitle.value });

const boardReady = computed(() => !!context.value?.board?.active);
const canWrite = computed(() => UtilNotice.canWrite(context.value?.writer));
const deleted = computed(() => detail.value?.state === 'DELETED');
/** 읽기 전용 — 권한 없음 · 지운 글 · 저장 중 */
const ro = computed(() => !canManage.value || deleted.value || saving.value);

const prefixChoices = computed(() => {
    const list = (context.value?.board?.prefixes ?? []).map((p) => ({ value: p.id as string | null, label: p.name }));
    // 지금 글의 말머리가 꺼졌으면(켜진 목록에 없음) 그 이름 그대로 남겨 보인다
    const own = detail.value?.prefix;
    if (own && !list.some((o) => o.value === own.id)) list.push({ value: own.id, label: own.name });
    return list;
});

/* ── 폼 ── */

function emptyForm(): NoticeForm {
    return { prefixId: null, title: '', body: '', pinned: false, scope: 'NOTICE_ONLY', excluded: [], excludeAllGroups: false, period: 'NONE', periodDate: '' };
}

const form = ref<NoticeForm>(emptyForm());
const initial = ref('');
function snapshot(f: NoticeForm): string {
    // 고정을 끈 동안의 범위 칸은 보내지 않으므로 비교하지 않는다
    const global = f.pinned && f.scope === 'ALL' ? [[...f.excluded].sort(), f.excludeAllGroups, f.period, f.period === 'DATE' ? f.periodDate : ''] : null;
    return JSON.stringify([f.prefixId, f.title, f.body, f.pinned, f.pinned ? f.scope : null, global]);
}
const dirty = computed(() => initial.value !== '' && snapshot(form.value) !== initial.value);
const titleLength = computed(() => UtilNotice.titleLength(form.value.title));

/** 편집기는 받은 HTML 을 정규화한다 — 처음 만들어졌을 때 값이 기준과 같으면 기준도 정규화한 값으로(terms.vue 와 같은 방식) */
function onEditorReady(html: string): void {
    if (snapshot(form.value) !== initial.value) return;
    form.value.body = html;
    initial.value = snapshot(form.value);
}

function fe(field: string): string {
    return serverErrors.value[field] ?? '';
}
const errorCount = computed(() => Object.keys(serverErrors.value).length);

/* ── 고정 ── */

const pinFull = computed(() => {
    const b = context.value?.board;
    return !!b && b.pinnedCount >= b.pinnedMax;
});
/** 「상단 고정」을 켤 수 없는 까닭 — 이미 고정된 글은 풀 수 있다 */
const pinBlockedReason = computed(() => {
    if (detail.value?.pinned) return '';
    if (detail.value && detail.value.state !== 'PUBLIC') return t('notice.pinNotPublic');
    if (pinFull.value && !form.value.pinned) return t('notice.pinFullHint', { max: context.value?.board?.pinnedMax ?? 0 });
    return '';
});
const pinAllowed = computed(() => !pinBlockedReason.value);

/* ── 전체 공지 ── */

/** 저장된 끝 시각(수정 화면) — 날짜를 그대로 두면 이 값을 다시 보낸다 */
const originalExpiresAt = computed(() => detail.value?.global?.expiresAt ?? null);
/** 이 글이 지금 살아 있는 전체 공지 — 한도가 차도 범위를 그대로 고를 수 있다 */
const liveGlobal = computed(() => !!detail.value?.global && !detail.value.global.expired);
/** 저장된 기간이 이미 지났고 그 날짜를 그대로 두었다 */
const globalExpiredKept = computed(() => {
    const g = detail.value?.global;
    return !!g?.expired && !!g.expiresAt && form.value.period === 'DATE' && form.value.periodDate === UtilNotice.kstDateOf(g.expiresAt);
});

/* ── 저장 막기 ── */

const mediaBlocked = computed(() => media.value.uploading > 0 || media.value.failed > 0);
/** 저장을 막는 까닭(없으면 빈 글자) */
const saveBlocked = computed(() => {
    if (saving.value) return 'saving';
    if (!canWrite.value) return 'writer';
    if (mediaBlocked.value) return 'media';
    if (!isNew.value && !dirty.value) return 'clean';
    return '';
});
const saveStatus = computed(() => {
    if (!canWrite.value) return t('notice.saveBlockedWriter');
    if (mediaBlocked.value) return t(media.value.uploading > 0 ? 'notice.mediaUploading' : 'notice.mediaFailed');
    if (!dirty.value) return t('common.saveBarClean');
    return t('common.saveBarDirty');
});

/* ── 불러오기 ── */

function apply(item: NoticeItem, body: string): void {
    detail.value = item;
    const g = item.global;
    form.value = {
        prefixId: item.prefix?.id ?? null,
        title: item.title,
        body,
        pinned: item.pinned,
        scope: g ? 'ALL' : 'NOTICE_ONLY',
        excluded: g ? [...g.excludedBoardIds] : [],
        excludeAllGroups: g?.excludeAllGroupBoards ?? false,
        period: g?.expiresAt ? 'DATE' : 'NONE',
        periodDate: g?.expiresAt ? UtilNotice.kstDateOf(g.expiresAt) : '',
    };
    initial.value = snapshot(form.value);
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        if (isNew.value) {
            context.value = await api.context();
            detail.value = null;
            form.value = emptyForm();
            initial.value = snapshot(form.value);
            serverErrors.value = {};
            formError.value = '';
            formKey.value += 1;
            return;
        }
        const [ctx, found] = await Promise.all([api.context(), api.detail(idParam.value)]);
        context.value = ctx;
        apply(found.notice, found.content.body);
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

onMounted(() => void load());
watch(idParam, () => void load());

/** 본문 이미지 — 서버가 `{ id, url, width, height }` 를 주면 RichEditor 가 `<img data-image-id>` 로 넣는다. 진행률은 다 올라간 뒤 한 번 */
const richUpload: RichImageUploader = async (file, opts) => {
    const r = await api.uploadImage(file, opts.signal);
    opts.onProgress(1);
    return r;
};

/* ── 저장 ── */

/** 화면 검사 — 빈 제목·빈 본문만 미리 알린다(판정은 서버 — 본문 글자 수·이미지·영상 상한은 서버 `fields.body` 문구를 칸에 붙인다) */
function precheck(f: NoticeForm): Record<string, string> {
    const problems: Record<string, string> = {};
    if (UtilNotice.titleLength(f.title) === 0) problems.title = t('notice.titleRequired');
    if (UtilPost.isHtmlEmpty(f.body)) problems.body = t('notice.bodyRequired');
    if (f.pinned && f.scope === 'ALL' && f.period === 'DATE' && !f.periodDate) problems.expiresAt = t('notice.periodDateRequired');
    return problems;
}

function toRequest(f: NoticeForm): NoticeSaveRequest {
    const all = f.pinned && f.scope === 'ALL';
    return {
        prefixId: f.prefixId,
        title: f.title.trim(),
        body: f.body,
        pinned: f.pinned,
        // 고정을 끄면 범위·제외·기간도 사라진다
        scope: all ? 'ALL' : 'NOTICE_ONLY',
        excludedBoardIds: all ? [...f.excluded] : [],
        excludeAllGroupBoards: all && f.excludeAllGroups,
        expiresAt: all ? UtilNotice.expiresAt(f.period, f.periodDate, originalExpiresAt.value) : null,
    };
}

/** 칸에 붙는 서버 오류 키 */
const FIELD_KEYS = ['prefixId', 'title', 'body', 'pinned', 'scope', 'excludedBoardIds', 'expiresAt'];
/** 떠나기 확인을 건너뛴다(저장 뒤 목록으로 갈 때) */
let leaving = false;

async function save(): Promise<void> {
    if (saveBlocked.value) return;
    const problems = precheck(form.value);
    if (Object.keys(problems).length) {
        serverErrors.value = problems;
        formError.value = '';
        return;
    }
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        const body = toRequest(form.value);
        if (isNew.value) {
            await api.create(body);
            toast.success(t('notice.created'));
            leaving = true;
            await router.push('/notices');
            return;
        }
        const id = detail.value!.id;
        await api.update(id, body);
        toast.success(t('notice.saved'));
        const [ctx, found] = await Promise.all([api.context(), api.detail(id)]);
        context.value = ctx;
        apply(found.notice, found.content.body);
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields && Object.keys(failure.fields).length) {
            const fields = { ...failure.fields };
            serverErrors.value = Object.fromEntries(Object.entries(fields).filter(([k]) => FIELD_KEYS.includes(k)));
            const rest = Object.entries(fields).filter(([k]) => !FIELD_KEYS.includes(k)).map(([, v]) => v);
            formError.value = rest.length ? rest.join(' ') : t('notice.saveFieldsRejected');
        } else {
            formError.value = failure.message;
            // 그 사이 연결이 끊겼거나 고정 수가 바뀌었다 — 경고 띠·고정 한도를 새로
            if (failure.code === 'NOTICE_WRITER_NOT_LINKED' || failure.code === 'PIN_LIMIT' || failure.code === 'NOTICE_GLOBAL_LIMIT') {
                try {
                    context.value = await api.context();
                } catch {
                    /* 띠는 다음에 — 저장 실패 한 줄은 이미 보인다 */
                }
            }
        }
    } finally {
        saving.value = false;
    }
}

function cancel(): void {
    void router.push('/notices');
}

onBeforeRouteLeave(async () => {
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notice.leaveTitle'), message: t('notice.leaveMessage'), confirmLabel: t('notice.leaveConfirm'), cancelLabel: t('notice.leaveCancel'), tone: 'danger' });
});
</script>
