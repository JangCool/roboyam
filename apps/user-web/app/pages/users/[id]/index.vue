<template>
    <div class="mx-auto max-w-2xl">
        <!-- 로딩 -->
        <div v-if="status === 'loading'" role="status" :aria-label="t('profile.publicLoading')" data-testid="profile-loading">
            <div class="flex items-start gap-3">
                <div class="size-[4.5rem] shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                <div class="flex-1 space-y-2 pt-1.5">
                    <div class="h-4 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-3.5 w-4/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-3 w-1/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </div>
            <div class="mt-4 h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="mt-4 h-11 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <FeedSkeleton :count="4" />
        </div>

        <!-- 없는 회원·탈퇴한 회원 (404 PROFILE_NOT_FOUND) — 서버는 둘을 가르지 않는다 -->
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('profile.notFoundTitle')" :description="t('profile.notFoundText')" data-testid="profile-notfound">
            <Button type="button" :label="t('profile.goBack')" severity="secondary" variant="outlined" @click="goBack" />
            <Button as="router-link" to="/" :label="t('profile.goHome')" />
        </ErrorState>

        <!-- 불러오기 실패 -->
        <ErrorState v-else-if="status === 'error'" :title="t('profile.publicLoadFailed')" :description="errorMessage" retry data-testid="profile-error" @retry="load" />

        <template v-else-if="profile">
            <!-- 차단한 회원 — 프로필은 그대로 열리고 여기서 차단을 풀 수 있다. 아래 작성글·댓글은 직접 찾아 들어온 화면이라 접지 않는다 -->
            <div
                v-if="menuTarget?.blocked"
                class="mb-3 flex flex-wrap items-center justify-between gap-x-3 gap-y-2 rounded-lg bg-surface-100 px-3 py-2.5 text-sm dark:bg-surface-800/60"
                role="status"
                data-testid="profile-blocked-banner"
            >
                <p class="m-0 min-w-0 flex-1 leading-snug">
                    <b>{{ t('block.profileBlockedTitle') }}</b>
                    <span class="block text-xs text-muted-color">{{ t('block.profileBlockedText') }}</span>
                </p>
                <Button type="button" :label="t('block.unblockAction')" size="small" severity="secondary" variant="outlined" :loading="unblocking" data-testid="profile-unblock" @click="onUnblock" />
            </div>

            <!-- 머리: 이니셜 · 뱃지+닉네임(+메모 칩) · 한 줄 소개 · 가입일 · (내 프로필이면) 프로필 편집·쪽지함 · (아니면) ⋯ -->
            <header class="flex flex-wrap items-start gap-x-3 gap-y-3">
                <span class="grid size-[4.5rem] shrink-0 place-items-center rounded-full bg-surface-100 text-2xl font-bold text-muted-color dark:bg-surface-800" aria-hidden="true">{{ initial }}</span>
                <div class="min-w-0 flex-1">
                    <h1 class="m-0 flex min-w-0 flex-wrap items-center gap-1.5 text-lg font-bold leading-tight">
                        <span class="min-w-0 break-all" data-testid="profile-nickname">{{ profile.nickname }}</span>
                        <StaffBadge v-if="profile.role !== 'USER'" :role="profile.role" class="shrink-0" />
                        <GradeBadge v-else :grade="profile.grade as Grade" :label="profile.gradeLabel" class="shrink-0" />
                        <!-- 이용자 메모 — 나에게만 보이는 노란 칩 -->
                        <span
                            v-if="menuTarget?.memo"
                            class="inline-flex min-w-0 max-w-[12rem] items-center rounded bg-amber-100 px-1.5 py-1 text-[0.6875rem] font-normal leading-none text-amber-900 dark:bg-amber-900/40 dark:text-amber-200"
                            :title="t('block.memoChipTitle', { memo: menuTarget.memo })"
                            data-testid="profile-memo"
                            ><span class="relative top-[-0.09em] truncate">{{ t('block.memoChip', { memo: menuTarget.memo }) }}</span></span
                        >
                    </h1>
                    <p v-if="profile.bio" class="mt-1 break-words text-sm leading-snug" data-testid="profile-bio">{{ profile.bio }}</p>
                    <p class="mt-1 text-xs text-muted-color">{{ t('profile.joined', { date: time.date(profile.joinedAt) }) }}</p>
                </div>
                <div v-if="profile.isMe" class="flex gap-2 max-md:w-full md:ml-auto">
                    <Button as="router-link" to="/settings/profile" :label="t('me.editProfile')" icon="pi pi-pencil" size="small" class="max-md:flex-1" data-testid="profile-edit" />
                    <Button
                        as="router-link"
                        :to="UtilDm.INBOX_PATH"
                        :label="t('notification.dmTitle')"
                        icon="pi pi-envelope"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        class="max-md:flex-1"
                        data-testid="profile-dm-inbox"
                    />
                </div>
                <!-- ⋯ 시트(이 사용자 차단·메모) — 로그인했고 남의 프로필일 때. 비회원은 누르면 로그인 유도 -->
                <button
                    v-else-if="!profile.isMe && (menuTarget || !user)"
                    type="button"
                    class="ml-auto grid size-9 place-items-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                    :aria-label="t('block.memberMenuOpen')"
                    aria-haspopup="dialog"
                    data-testid="profile-menu"
                    @click="openMenu"
                >
                    <i class="pi pi-ellipsis-h" aria-hidden="true" />
                </button>
            </header>

            <!-- 쪽지 보내기 — 남의 프로필에만. 프로필 본문과 따로 상태를 읽고, 못 읽으면 단추만 빠진다 -->
            <div v-if="!profile.isMe && dmMode !== 'none'" class="mt-4" data-testid="profile-dm">
                <template v-if="dmMode === 'guest'">
                    <Button type="button" :label="t('dm.sendToProfile')" icon="pi pi-envelope" class="max-md:w-full" :aria-describedby="dmHelpId" data-testid="profile-dm-guest" @click="prompt('dm')" />
                    <p :id="dmHelpId" class="m-0 mt-1.5 text-xs text-muted-color" data-testid="profile-dm-help">{{ t('dm.profileGuestHelp') }}</p>
                </template>
                <div v-else-if="dmMode === 'loading'" class="h-10 w-32 animate-pulse rounded-md bg-surface-200 max-md:w-full dark:bg-surface-800" role="status" :aria-label="t('dm.checking')" data-testid="profile-dm-loading" />
                <Button v-else-if="dmMode === 'enabled'" as="router-link" :to="dmTo" :label="t('dm.sendToProfile')" icon="pi pi-envelope" class="max-md:w-full" data-testid="profile-dm-send" />
                <template v-else>
                    <Button type="button" :label="t('dm.sendToProfile')" icon="pi pi-envelope" disabled class="max-md:w-full" :aria-describedby="dmHelpId" data-testid="profile-dm-disabled" />
                    <p :id="dmHelpId" class="m-0 mt-1.5 text-xs text-muted-color" data-testid="profile-dm-reason">{{ dmReasonLine }}</p>
                </template>
            </div>

            <!-- 보유 포인트 · 다음 등급까지 — 내 프로필에만. 포인트 화면·등급 안내로 가는 길을 겸한다. 둘 다 못 받으면 카드를 감춘다(프로필은 그대로) -->
            <section
                v-if="profile.isMe && progressVisible"
                class="mt-4 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900"
                :aria-label="t('profile.progressLabel')"
                data-testid="profile-progress"
            >
                <NuxtLink
                    v-if="!businessAccount"
                    to="/me/points"
                    class="flex min-h-11 items-center justify-between gap-2 rounded-lg focus-visible:outline-2 focus-visible:outline-primary"
                    :aria-label="balance !== null ? t('profile.pointsLink', { points: UtilPoint.points(balance) }) : t('profile.pointsLinkPlain')"
                    data-testid="profile-points-link"
                >
                    <span class="text-sm text-muted-color">{{ t('point.balance') }}</span>
                    <span class="flex items-center gap-1 text-base font-bold">
                        <span v-if="balance !== null" data-testid="profile-points">{{ UtilPoint.points(balance) }}</span>
                        <span v-else-if="pointStatus === 'error'" class="text-sm font-normal text-muted-color">{{ t('profile.pointsUnknown') }}</span>
                        <span v-else class="inline-block h-5 w-16 animate-pulse rounded bg-surface-200 align-middle dark:bg-surface-800" aria-hidden="true" />
                        <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                    </span>
                </NuxtLink>

                <!-- 다음 등급까지 — 받았을 때만 보이고, 못 받으면 이 줄만 빠진다 -->
                <div v-if="myGrade" :class="businessAccount ? '' : 'mt-3 border-t border-surface pt-3'" data-testid="profile-grade">
                    <GradeProgress :grade="myGrade" :show-grade="false" />
                    <p v-if="pausedCopy" class="mt-2 text-xs text-amber-700 dark:text-amber-400" data-testid="profile-grade-paused">{{ pausedCopy.title }}</p>
                </div>
                <div v-else-if="gradeStatus === 'loading' || gradeStatus === 'idle'" class="mt-3 space-y-2 border-t border-surface pt-3" role="status" :aria-label="t('profile.gradeLoading')">
                    <div class="h-4 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>

                <div class="mt-3 flex gap-2">
                    <Button v-if="!businessAccount" as="router-link" to="/me/points" :label="t('point.history')" icon="pi pi-wallet" size="small" class="flex-1" data-testid="profile-points-button" />
                    <Button as="router-link" to="/me/grade" :label="t('grade.title')" icon="pi pi-star" size="small" severity="secondary" variant="outlined" class="flex-1" data-testid="profile-grade-button" />
                </div>
            </section>

            <!-- 활동 수치 — 글·댓글·받은 추천(서버가 최대 60초 늦을 수 있다) -->
            <dl class="m-0 mt-4 grid grid-cols-3 divide-x divide-surface-200 rounded-xl border border-surface bg-surface-0 py-3 text-center dark:divide-surface-700 dark:bg-surface-900" data-testid="profile-stats">
                <div v-for="cell in statCells" :key="cell.key" class="flex min-w-0 flex-col px-1">
                    <dt class="order-2 text-xs text-muted-color">{{ cell.label }}</dt>
                    <dd class="order-1 m-0 text-base font-bold tabular-nums">{{ UtilProfile.count(profile.stats[cell.key]) }}</dd>
                </div>
            </dl>

            <!-- 탭 — 링크라서 주소(?tab=)만 바뀐다 -->
            <nav :aria-label="t('profile.tabsLabel')" class="mt-4 flex border-b border-surface">
                <NuxtLink
                    v-for="option in tabs"
                    :key="option.value"
                    :to="{ path: route.path, query: UtilProfile.buildTab(option.value) }"
                    replace
                    class="-mb-px min-h-11 flex-1 border-b-2 py-2.5 text-center text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="tab === option.value ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color hover:text-color'"
                    :aria-current="tab === option.value ? 'page' : undefined"
                    :data-testid="`tab-${option.value}`"
                >
                    {{ option.label }}
                </NuxtLink>
            </nav>

            <!-- 작성글 — 게시판이 섞인 목록과 같은 한 줄 -->
            <ProfileFeed v-if="tab === 'posts'" :key="`posts:${userId}`" :load="loadPosts" :label="t('profile.tabPosts')" :error-title="t('profile.postsErrorTitle')" class="mt-1">
                <template #item="{ item }">
                    <FeedPostItem :row="item" :date-display="display.dateDisplay" :new-post-hours="display.newPostHours" />
                </template>
                <template #empty>
                    <EmptyState
                        :title="t('profile.emptyPostsTitle')"
                        :description="profile.isMe ? t('profile.emptyPostsMine') : t('profile.emptyPostsOther', { nickname: profile.nickname })"
                        icon="pi pi-file"
                        data-testid="posts-empty"
                    >
                        <Button v-if="profile.isMe" as="router-link" to="/write" :label="t('profile.write')" />
                    </EmptyState>
                </template>
            </ProfileFeed>

            <!-- 댓글 — 댓글 앞 100자 / 게시판 · 글 제목 · 추천 · 시각. 누르면 그 글의 그 댓글로 -->
            <ProfileFeed v-else :key="`comments:${userId}`" :load="loadComments" :label="t('profile.tabComments')" :error-title="t('profile.commentsErrorTitle')" class="mt-1">
                <template #item="{ item }">
                    <li class="border-b border-surface last:border-b-0">
                        <NuxtLink
                            :to="`/posts/${item.postId}#comment-${item.id}`"
                            class="block px-4 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 dark:hover:bg-surface-800/60"
                            data-testid="profile-comment"
                        >
                            <p class="m-0 line-clamp-2 break-words text-sm leading-snug">
                                <span v-if="item.reply" class="mr-1 inline-flex items-center justify-center rounded bg-surface-200 px-1.5 py-1 align-[1px] text-[0.6875rem] leading-none text-surface-600 dark:bg-surface-700 dark:text-surface-300">
                                    <span class="relative top-[-0.09em]">{{ t('profile.replyTag') }}</span>
                                </span>
                                {{ item.bodyPreview }}
                            </p>
                            <p class="mt-1 flex items-center gap-1 text-xs text-muted-color">
                                <span class="min-w-0 truncate">{{ t('profile.commentMeta', { board: localizedName(item.board.name, item.board.nameEn), title: item.postTitle }) }}</span>
                                <span class="shrink-0" aria-hidden="true">·</span>
                                <span class="shrink-0">{{ t('board.likesShort', { n: item.likeCount }) }}</span>
                                <span class="shrink-0" aria-hidden="true">·</span>
                                <time class="shrink-0" :datetime="item.createdAt" :title="time.full(item.createdAt)">{{ time.listLabel(item.createdAt) }}</time>
                            </p>
                        </NuxtLink>
                    </li>
                </template>
                <template #empty>
                    <EmptyState :title="t('profile.emptyCommentsTitle')" :description="t('profile.emptyCommentsText')" icon="pi pi-comment" data-testid="comments-empty" />
                </template>
            </ProfileFeed>

            <p v-if="tab === 'posts'" class="mt-4 text-center text-xs text-muted-color">{{ t('profile.hiddenPostsNote') }}</p>

            <!-- 회원 ⋯ 시트 — 이 사용자 차단·차단 해제·메모(메모 작성은 셸의 메모 시트) -->
            <AppSheet v-model:visible="menuOpen" :title="t('block.memberMenuTitle', { nickname: profile.nickname })">
                <ul class="m-0 mt-3 list-none p-0">
                    <UserActionItems :target="menuTarget" @action="onAction" />
                </ul>
                <div class="px-5 pt-3">
                    <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="menuOpen = false" />
                </div>
            </AppSheet>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { ProfileStats, PublicProfile } from '~/composables/profile/api/useUserProfileApi.types';
import type { DmCanSend } from '~/composables/dm/api/useDmApi.types';
import type { UserAction } from '~/composables/block/useUserActions';
import { ApiError } from '~/composables/common/useApiClient';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';
import type { ProfileTab } from '~/utils/util-profile';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBusiness from '~/utils/util-business';
import UtilGradeMessages from '~/utils/util-grade-messages';
import UtilPoint from '~/utils/util-point';
import UtilProfile from '~/utils/util-profile';

/**
 * 공개 회원 프로필 — `/users/{id}?tab=`(community `pages/users/[id]/index.vue`, `docs/api-profile.md`「공개 프로필」). **비회원도** 열린다.
 * 글 보기 머리·댓글·데스크톱 목록 표의 작성자 이름(`PostAuthorLabel link`)과 내 정보(`/me`)의 「내 프로필 보기」가 이곳으로 온다.
 * - 머리(프로필)와 탭의 목록은 따로 불러온다 — 목록이 실패해도 머리는 그대로, 그 목록만 다시 시도한다. 탭(작성글·댓글)은 주소의 쿼리가 상태다.
 * - 내 프로필(`isMe`)이면 「프로필 편집」과 보유 포인트·다음 등급까지 카드(`usePointSummary` 실제 · `useMyGrade` 목업)가 더해진다. 남의 프로필에는 포인트·진행도를 보이지 않는다.
 * - 남의 프로필: ⋯ 시트(이 사용자 차단·차단 해제·메모 — `useUserActions`), 차단한 회원이면 맨 위 「차단한 회원이에요 · 차단 해제」 띠, 닉네임 옆 메모 칩, 「쪽지 보내기」(보낼 수 있나를 따로 읽는다).
 *   내 프로필: 「프로필 편집」 옆 「쪽지함」. 계약 `docs/features/member-social-contract.md`.
 * 프로필·작성글·댓글(`useUserProfileApi`)과 차단·메모·쪽지(`useBlockApi`·`useMemoApi`·`useDmApi`)는 모두 실제 API 다. 공개 프로필 응답의 `blockedByMe`·`memo` 는 서버가 채운다(로그인한 사람 기준). 원본과 달라진 점: 프로필 이미지·닉네임 이미지·꾸미기 테두리, 프로필 이미지·닉네임 이미지 신고, 소모임·스크랩 탭, 인기글 수치는 로보얌에 없어 뺐다.
 * 모바일 ‹ 머리(`InnerHeader`)가 없어 셸의 머리 그대로다.
 */
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const time = useDisplayTime();
const api = useUserProfileApi();
const { user } = useAuth();
const { localizedName } = useLocalizedName();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
const points = usePointSummary();
const mine = useMyGrade();
const { summary: pointSummary, status: pointStatus } = points;
const { grade: myGrade, status: gradeStatus } = mine;
void ensureDisplay();

const profile = ref<PublicProfile | null>(null);
const status = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const errorMessage = ref('');

const userId = computed(() => String(route.params.id ?? ''));
const tab = computed<ProfileTab>(() => UtilProfile.parseTab(route.query));
const tabs = computed<{ value: ProfileTab; label: string }[]>(() => [
    { value: 'posts', label: t('profile.tabPosts') },
    { value: 'comments', label: t('profile.tabComments') },
]);
const statCells = computed<{ key: keyof ProfileStats; label: string }[]>(() => [
    { key: 'posts', label: t('profile.statPosts') },
    { key: 'comments', label: t('profile.statComments') },
    { key: 'likesReceived', label: t('profile.statLikes') },
]);
/** 프로필 자리표시 — 닉네임 첫 글자(프로필 그림 기능이 없다) */
const initial = computed(() => Array.from(profile.value?.nickname ?? '')[0] ?? '');

/* ── 내 프로필의 포인트·등급 카드 ── */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
const balance = computed(() => pointSummary.value?.balance ?? null);
const progressVisible = computed(() => !((businessAccount.value || pointStatus.value === 'error') && gradeStatus.value === 'error'));
const pausedCopy = computed(() => UtilGradeMessages.pausedNotice(t, myGrade.value?.autoPromotion));
watch(
    () => profile.value?.isMe ?? false,
    (isMe) => {
        if (!isMe) return;
        if (!businessAccount.value) void points.load();
        void mine.load();
    },
);

let controller: AbortController | null = null;
let request = 0;

/** 머리(프로필)를 불러온다 — 앞 요청이 끝나기 전에 주소가 바뀌면 앞 요청을 끊고, 늦게 온 옛 결과는 버린다 */
async function load() {
    const asked = ++request;
    controller?.abort();
    controller = new AbortController();
    status.value = 'loading';
    try {
        const result = await api.profile(userId.value, controller.signal);
        if (asked !== request) return;
        profile.value = result;
        status.value = 'ready';
    } catch (e) {
        if (asked !== request) return;
        profile.value = null;
        if (e instanceof ApiError && e.code === 'PROFILE_NOT_FOUND') status.value = 'notfound';
        else {
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
        }
    }
}

watch(userId, load, { immediate: true });
onBeforeUnmount(() => {
    request++;
    controller?.abort();
});

/* ── 탭의 목록 — 한 쪽씩 커서로 ── */
const loadPosts = (cursor: string | undefined, signal: AbortSignal) => api.posts(userId.value, { cursor }, signal);
const loadComments = (cursor: string | undefined, signal: AbortSignal) => api.comments(userId.value, { cursor }, signal);

function goBack() {
    if (typeof window !== 'undefined' && window.history.state?.back) router.back();
    else void navigateTo('/');
}

/* ── 차단·메모 ⋯ 시트 (`docs/features/member-social-contract.md`) ── */
const { prompt } = useLoginPrompt();
const { targetOf, run, unblock } = useUserActions();
/** ⋯ 메뉴가 다룰 회원 — 나·탈퇴는 null. 메모·차단 여부에는 이 기기에서 방금 바꾼 것이 겹쳐 있다 */
const menuTarget = computed(() =>
    profile.value ? targetOf({ id: profile.value.id, nickname: profile.value.nickname, role: profile.value.role, memo: profile.value.memo ?? null }, profile.value.blockedByMe ?? false) : null,
);
const menuOpen = ref(false);
const unblocking = ref(false);

function openMenu() {
    if (!user.value) {
        prompt('block');
        return;
    }
    menuOpen.value = true;
}

function onAction(action: UserAction) {
    menuOpen.value = false;
    void run(action);
}

async function onUnblock() {
    if (!menuTarget.value || unblocking.value) return;
    unblocking.value = true;
    try {
        await unblock(menuTarget.value);
    } finally {
        unblocking.value = false;
    }
}

/* ── 쪽지 보내기 단추 — 프로필 본문과 따로 읽는다(실패하면 단추만 숨긴다) ── */
const dmApi = useDmApi();
const { loaded } = useAuth();
const dmHelpId = `${useId()}-dm-help`;
const dmInfo = ref<DmCanSend | null>(null);
const dmLoading = ref(false);
let dmSeq = 0;
const dmView = computed(() => UtilDm.button(dmInfo.value, userId.value));
const dmTo = computed(() => (dmView.value.kind === 'enabled' ? dmView.value.to : ''));
const dmReasonLine = computed(() =>
    businessAccount.value
        ? t('dm.lockBusiness')
        : dmView.value.kind === 'disabled'
          ? UtilDmMessages.profileReason(t, dmView.value.reason, time.value, { availableFrom: dmInfo.value?.availableFrom, newPartnerLimit: dmInfo.value?.quota.newPartnerLimit })
          : '',
);
/** 무엇을 그리나 — 로그인 상태를 모르면 아무것도, 비회원이면 로그인 유도, 로그인했으면 읽은 상태대로 */
const dmMode = computed<'none' | 'guest' | 'loading' | 'enabled' | 'disabled'>(() => {
    if (!loaded.value) return 'none';
    if (!user.value) return 'guest';
    if (businessAccount.value) return 'disabled';
    if (dmLoading.value) return 'loading';
    return dmView.value.kind === 'hidden' ? 'none' : dmView.value.kind;
});

async function loadDm(): Promise<void> {
    const mine = ++dmSeq;
    if (!user.value || businessAccount.value || user.value.id === userId.value) {
        dmInfo.value = null;
        dmLoading.value = false;
        return;
    }
    if (dmInfo.value === null) dmLoading.value = true;
    try {
        const res = await dmApi.canSend(userId.value);
        if (mine === dmSeq) dmInfo.value = res;
    } catch {
        if (mine === dmSeq) dmInfo.value = null;
    } finally {
        if (mine === dmSeq) dmLoading.value = false;
    }
}

watch(
    [userId, () => user.value?.id ?? null],
    () => {
        dmInfo.value = null;
        void loadDm();
    },
    { immediate: true },
);
// 차단 상태가 바뀌었다 — 차단 관계(`UNAVAILABLE`)가 생기거나 풀렸는지 다시 읽는다
watch(
    () => menuTarget.value?.blocked ?? false,
    (now, before) => {
        if (now !== before) void loadDm();
    },
);

useSeoMeta({
    title: () => profile.value?.nickname ?? t('profile.publicTitle'),
    description: () => (profile.value ? profile.value.bio || t('profile.publicSeoDescription', { nickname: profile.value.nickname }) : undefined),
    robots: 'noindex',
});
</script>
