<template>
    <!--
        의심 후보 서랍 — 의심 후보 줄을 누르면(또는 `?suspicion=` 로 들어오면) 연다. 「보고 판단」하는 자리라 **새 제재 기능은 없고** 기존 화면으로 가는 바로가기만 둔다(03-hotdeal 7-1, 05 §2-18).
        회원 후보(`MEMBER`): 회원(탈퇴 Tag) · 판매처 · 기간 안 건수 · 다른 회원 수 · 처음 감지 → 그 회원의 그 판매처 글 최근 10개.
        판매처 후보(`STORE`): 판매처 · 올린 회원 수 · 기간 안 글 수 · 그 회원들의 다른 핫딜 비율 · 기간 · 처음 감지 → **회원별로 묶은** 그 판매처 글(회원마다 이 판매처 N건 · 다른 핫딜 M건).
        글 줄: 상태 · 제목(핫딜 글 서랍 `/hotdeals?deal=`) · 게시판 · 딜가 · 시각 · 신고 대기 · [글 관리에서 숨기기](기존 글 관리 서랍 `/posts?post=` — 숨김·사유 창은 그쪽 것을 그대로 쓴다, `post.read`).
        바닥 바로가기: [회원 제재로 가기](회원 관리 `/members`, `member.read`) · [이 몰 규칙 등록](링크 규칙 추가 창을 도메인 채워 연다 `/hotdeals/domains?new={host}`, `hotdeal.domain.manage`) · 신고 관리 · [무시](열린 후보 · `hotdeal.manage`).
        서버 호출은 하지 않는다(부른 화면이 한다).
    -->
    <Drawer
        :visible="open"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '38rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                <h2 :id="titleId" class="min-w-0 text-base font-semibold break-words">{{ head ? UtilHotdealMessages.suspicionTitle(t, head) : t('hotdeal.suspicion.title') }}</h2>
                <div v-if="head" class="flex flex-wrap items-center gap-2">
                    <Tag :value="t(`hotdeal.suspicion.kind.${head.kind}`)" :severity="head.kind === 'STORE' ? 'info' : 'secondary'" :icon="head.kind === 'STORE' ? 'pi pi-shop' : 'pi pi-user'" class="text-[11px]!" />
                    <Tag :value="t(`hotdeal.suspicion.status.${head.status}`)" :severity="head.status === 'OPEN' ? 'warn' : 'secondary'" class="text-[11px]!" />
                    <Tag v-if="head.kind === 'MEMBER' && head.member.withdrawn" :value="t('hotdeal.withdrawn')" severity="secondary" class="text-[11px]!" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('hotdeal.suspicion.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton v-for="n in 5" :key="n" height="1.25rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                <template v-if="s.kind === 'MEMBER'">
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.member') }}</dt>
                    <dd class="m-0 flex flex-wrap items-center gap-2">
                        <span :class="s.member.withdrawn ? 'text-muted-color' : ''">{{ s.member.nickname }}</span>
                        <span class="text-xs text-muted-color">#{{ s.member.id }}</span>
                    </dd>
                </template>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.store') }}</dt>
                <dd class="m-0">{{ s.storeName }} <span class="font-mono text-xs text-muted-color">{{ s.host }}</span></dd>
                <template v-if="s.kind === 'STORE'">
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.members') }}</dt>
                    <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.membersValue', { n: s.memberCount }) }}</dd>
                </template>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.count') }}</dt>
                <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.countValue', { n: s.postCount, days: s.windowDays }) }}</dd>
                <template v-if="s.kind === 'MEMBER'">
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.others') }}</dt>
                    <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.othersValue', { n: s.othersCount }) }}</dd>
                </template>
                <template v-else>
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.otherRatio') }}</dt>
                    <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.otherRatioValue', { n: s.otherRatioPercent }) }}</dd>
                </template>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.firstDetected') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(s.firstDetectedAt) }}</dd>
                <template v-if="s.status === 'DISMISSED'">
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.dismissedLabel') }}</dt>
                    <dd class="m-0">{{ t('hotdeal.suspicion.dismissedValue', { name: s.dismissedBy ?? '—', at: time.full(s.dismissedAt) }) }}</dd>
                    <template v-if="s.note">
                        <dt class="text-muted-color">{{ t('hotdeal.suspicion.noteLabel') }}</dt>
                        <dd class="m-0 break-words">{{ s.note }}</dd>
                    </template>
                </template>
            </dl>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(s.kind === 'STORE' ? 'hotdeal.suspicion.storeHint' : 'hotdeal.suspicion.othersHint') }}</p>

            <!-- 회원 후보 — 그 회원의 그 판매처 글 -->
            <section v-if="s.kind === 'MEMBER'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-posts`">
                <h3 :id="`${titleId}-posts`" class="text-sm font-semibold">{{ t('hotdeal.suspicion.postsTitle', { n: detail.posts.length }) }}</h3>
                <p v-if="detail.posts.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('hotdeal.suspicion.postsEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="p in detail.posts" :key="p.postId" class="px-3 py-2">
                        <HotdealSuspicionPostLine :post="p" :can-read-reports="canReadReports" :can-read-posts="canReadPosts" />
                    </li>
                </ol>
            </section>

            <!-- 판매처 후보 — 회원별로 묶은 그 판매처 글 -->
            <section v-else class="flex flex-col gap-3" :aria-labelledby="`${titleId}-posts`">
                <h3 :id="`${titleId}-posts`" class="text-sm font-semibold">{{ t('hotdeal.suspicion.storePostsTitle', { n: detail.posts.length, members: detail.members.length }) }}</h3>
                <p v-if="detail.posts.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('hotdeal.suspicion.postsEmpty') }}</p>
                <div v-for="g in groups" :key="g.member.id" class="flex flex-col gap-1 rounded-lg border border-(--line)">
                    <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 border-b border-(--line) bg-(--p-content-hover-background) px-3 py-1.5 text-[13px]">
                        <i class="pi pi-user text-[11px] text-muted-color" aria-hidden="true" />
                        <b class="font-medium" :class="g.member.withdrawn ? 'text-muted-color' : ''">{{ g.member.nickname }}</b>
                        <Tag v-if="g.member.withdrawn" :value="t('hotdeal.withdrawn')" severity="secondary" class="text-[11px]!" />
                        <span class="text-xs tabular-nums text-muted-color">{{ t('hotdeal.suspicion.memberLine', { n: g.postCount, other: g.otherDealCount }) }}</span>
                    </p>
                    <ol class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                        <li v-for="p in g.posts" :key="p.postId" class="px-3 py-2">
                            <HotdealSuspicionPostLine :post="p" :can-read-reports="canReadReports" :can-read-posts="canReadPosts" />
                        </li>
                    </ol>
                </div>
            </section>
        </div>

        <template v-if="detail && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p class="m-0 text-xs text-muted-color">{{ t('hotdeal.suspicion.sanctionHint') }}</p>
                <div class="flex flex-wrap items-center justify-end gap-2">
                    <Button v-if="canReadMembers && !(s.kind === 'MEMBER' && s.member.withdrawn)" type="button" :label="t('hotdeal.suspicion.goSanction')" icon="pi pi-user" size="small" severity="secondary" variant="outlined" @click="go({ path: '/members' })" />
                    <Button v-if="canDomain" type="button" :label="t('hotdeal.suspicion.addRule')" icon="pi pi-globe" size="small" severity="secondary" variant="outlined" @click="go({ path: '/hotdeals/domains', query: { new: s.host } })" />
                    <Button v-if="canReadReports" type="button" :label="t('hotdeal.suspicion.openReports')" icon="pi pi-flag" size="small" severity="secondary" variant="outlined" @click="go({ path: '/reports' })" />
                    <Button v-if="s.status === 'OPEN' && canManage" type="button" :label="t('hotdeal.suspicion.dismiss')" icon="pi pi-eye-slash" size="small" :disabled="busy || loading" @click="emit('dismiss')" />
                </div>
                <p v-if="s.status === 'OPEN' && !canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.readOnly') }}</p>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import type { ApiError } from '~/composables/common/useApiClient';
import type { DealSuspicion, DealSuspicionDetail, DealSuspicionMember, DealSuspicionPost } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    item?: DealSuspicion | null;
    detail?: DealSuspicionDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    canManage?: boolean;
    canReadReports?: boolean;
    canReadMembers?: boolean;
    /** `post.read` — 글 관리 서랍(숨김·사유 창)으로 가는 바로가기 */
    canReadPosts?: boolean;
    /** `hotdeal.domain.manage` — 「이 몰 규칙 등록」 */
    canDomain?: boolean;
}

const props = withDefaults(defineProps<Props>(), { item: null, detail: null, loading: false, error: null, busy: false, canManage: false, canReadReports: false, canReadMembers: false, canReadPosts: false, canDomain: false });
const emit = defineEmits<{ retry: []; dismiss: []; closed: [] }>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const titleId = useId();

const head = computed(() => props.detail?.suspicion ?? props.item);
/** 본문이 그리는 후보 — 템플릿은 `detail` 이 있을 때만 읽는다 */
const s = computed(() => props.detail?.suspicion as DealSuspicion);

/** 판매처 후보 — 회원 요약 차례(글 많은 순)대로 글을 묶는다. 요약에 없는 회원은 끝에 */
const groups = computed<{ member: DealSuspicionMember; postCount: number; otherDealCount: number; posts: DealSuspicionPost[] }[]>(() => {
    const d = props.detail;
    if (!d) return [];
    const byId = new Map<string, DealSuspicionPost[]>();
    for (const p of d.posts) byId.set(p.author.id, [...(byId.get(p.author.id) ?? []), p]);
    const out = d.members.map((m) => ({ member: m.member, postCount: m.postCount, otherDealCount: m.otherDealCount, posts: byId.get(m.member.id) ?? [] }));
    for (const [id, posts] of byId) if (!d.members.some((m) => m.member.id === id)) out.push({ member: posts[0]!.author, postCount: posts.length, otherDealCount: 0, posts });
    return out;
});

function go(to: RouteLocationRaw): void {
    void navigateTo(to);
}

const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"]';
function onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (!target || target.closest(INSIDE)) return;
    open.value = false;
}
watch(
    open,
    (isOpen) => {
        if (isOpen) document.addEventListener('pointerdown', onPointerDown, true);
        else document.removeEventListener('pointerdown', onPointerDown, true);
    },
    { immediate: true },
);
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));
</script>
