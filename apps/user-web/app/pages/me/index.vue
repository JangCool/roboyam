<template>
    <div class="content-card flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('me.title') }}</h1>

        <template v-if="user">
            <!-- 프로필 요약 — 뱃지·닉네임·이메일은 로그인 정보, 가입일·한 줄 소개는 내 프로필(`/api/me/profile`)에서 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-profile`" data-testid="me-profile">
                <h2 :id="`${uid}-profile`" class="sr-only">{{ t('me.profileTitle') }}</h2>
                <div class="flex items-start gap-3">
                    <!-- 내 프로필 아바타(고르지 않았으면 basic-default — 결정 14) + 장착한 프로필 테두리. 방금 고른·장착한 것을 겹친다(`useMyDecoration`) -->
                    <ProfileBorderAvatar :initial="initial" :src="myAvatar" size="md" surface="card" :border-style="myDecoration?.profileBorder ?? null" data-testid="me-avatar" />
                    <div class="min-w-0 flex-1">
                        <p class="flex items-center gap-1.5">
                            <GradeBadge v-if="user.role === 'USER'" :grade="user.grade" :label="user.gradeLabel" />
                            <StaffBadge v-else :role="user.role" />
                            <b class="min-w-0 break-all text-base" data-testid="me-nickname">{{ user.nickname }}</b>
                        </p>
                        <p class="mt-0.5 break-all text-sm text-muted-color">{{ user.email ?? t('me.noEmail') }}</p>
                        <p v-if="profile" class="mt-0.5 text-xs text-muted-color" data-testid="me-joined">{{ t('me.joined', { date: time.date(profile.joinedAt) }) }}</p>
                    </div>
                    <Button as="router-link" to="/settings/profile" :label="t('me.editProfile')" size="small" severity="secondary" variant="outlined" class="shrink-0" data-testid="me-edit-profile" />
                </div>

                <!-- 한 줄 소개 — 받는 중에는 자리만, 못 받았으면 한 줄 안내 + 다시 시도 -->
                <div class="mt-3 border-t border-surface pt-3 text-sm">
                    <div v-if="profileStatus === 'loading'" class="h-4 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('me.profileLoading')" />
                    <p v-else-if="profileStatus === 'error'" class="flex flex-wrap items-center gap-2 text-muted-color" data-testid="me-profile-error">
                        {{ t('me.profileLoadFailed') }}
                        <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="loadProfile" />
                    </p>
                    <p v-else-if="profile?.bio" class="whitespace-pre-line break-words" data-testid="me-bio">{{ profile.bio }}</p>
                    <p v-else class="text-muted-color" data-testid="me-bio-empty">{{ t('me.bioEmpty') }}</p>
                </div>
            </section>

            <!-- 포인트 요약 — 사업자 전용 계정은 포인트가 없다(서버 `403 BUSINESS_ACCOUNT_RESTRICTED`) -->
            <section v-if="!businessAccount" class="panel-card p-4" :aria-labelledby="`${uid}-points`" data-testid="me-points">
                <div class="flex items-center justify-between gap-2">
                    <h2 :id="`${uid}-points`" class="text-sm font-semibold">{{ t('point.title') }}</h2>
                    <NuxtLink to="/me/points" class="inline-flex min-h-8 items-center gap-1 rounded text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="me-points-link">
                        {{ t('point.history') }}<i class="pi pi-angle-right text-xs" aria-hidden="true" />
                    </NuxtLink>
                </div>

                <div v-if="!summary && (pointStatus === 'loading' || pointStatus === 'idle')" class="mt-3 flex flex-col gap-2" role="status" :aria-label="t('point.loading')">
                    <div class="h-8 w-32 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-4 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
                <div v-else-if="!summary" class="mt-3 flex flex-wrap items-center gap-2 text-sm text-muted-color" role="alert" data-testid="me-points-error">
                    {{ t('point.loadFailed') }}
                    <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="points.load" />
                </div>
                <template v-else>
                    <p class="mt-2 text-2xl font-bold tabular-nums" data-testid="me-points-balance">
                        <span class="sr-only">{{ t('point.balance') }} </span>{{ UtilPoint.points(summary.balance) }}
                    </p>
                    <!-- 오늘 적립 — 한국 시간 하루 기준 횟수/상한. 상한이 0(꺼짐)인 일은 빼고 보인다 -->
                    <p class="mt-1 text-xs text-muted-color">{{ t('point.todayTitle') }}</p>
                    <ul class="m-0 mt-1 flex list-none flex-wrap gap-x-4 gap-y-1 p-0 text-sm" data-testid="me-points-today">
                        <li v-for="row in todayRows" :key="row.key">
                            {{ t(row.labelKey) }} <b class="tabular-nums">{{ t('point.countOfCap', { count: row.value.count, cap: row.value.cap }) }}</b>
                        </li>
                    </ul>
                </template>
            </section>

            <!-- 내 정보 메뉴 — 모바일은 여기 목록으로. 데스크톱은 셸의 오른쪽 열(`MeFrame`)에 늘 있어 숨긴다 -->
            <MeMenu class="lg:hidden" :home="false" />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { PointCount } from '~/composables/point/api/usePointApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';

/**
 * 내 정보 `/me` — 로그인 필요(`auth` 미들웨어). 하단 탭·헤더 프로필 메뉴·햄버거의 「내 정보」가 온다.
 * 프로필 요약(뱃지·닉네임·이메일 — 로그인 정보, 가입일·한 줄 소개 — 내 프로필 `GET /api/me/profile`) · 포인트 요약(`GET /api/me/points`).
 * 메뉴(바로가기 묶음 · 관리자 앱 · 로그아웃)는 `MeMenu` — 모바일은 이 화면 아래 목록, 데스크톱은 셸의 오른쪽 열(`MeFrame`)에 늘 있다.
 * 원본(community) `pages/me/index.vue` 의 구성에서 로보얌에 없는 것(스크랩·소모임·주문·쪽지·프로필 그림)을 뺐다. 포인트 상점(`/point-shop`)·내 아이템(`/me/items`) 바로가기가 있다. 바로가기에 내 공개 프로필(`/users/{내 ID}`)·등급 안내(`/me/grade`)가 있다.
 */
definePageMeta({ middleware: 'auth', meMenu: true });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('me.title'), description: () => t('me.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const { user } = useAuth();
const profileApi = useProfileApi();
const points = usePointSummary();
const { summary, status: pointStatus } = points;

/** 사업자 전용 계정(H-2) — 포인트가 없다 */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

/** 닉네임 첫 글자 — 아바타 그림을 하나도 못 불러왔을 때만 보인다 */
const initial = computed(() => Array.from(user.value?.nickname ?? '')[0] ?? '');
/** 내 아바타·장착 장식 — 서버 값(후보)에 방금 고른·장착한 것을 겹친다 */
const decorations = useMyDecoration();
const decorationSync = useMyDecorationSync();
const myAvatar = computed(() => decorations.myAvatarUrl(profile.value?.avatarUrl));
const myDecoration = computed(() => decorations.mine());

const profile = ref<MyProfile | null>(null);
const profileStatus = ref<'loading' | 'ready' | 'error'>('loading');
let profileSeq = 0;

async function loadProfile() {
    const mine = ++profileSeq;
    profileStatus.value = 'loading';
    try {
        const value = await profileApi.me();
        if (mine !== profileSeq) return;
        profile.value = value;
        profileStatus.value = 'ready';
    } catch {
        if (mine === profileSeq) profileStatus.value = 'error';
    }
}

// 로그인한 사람이 정해지면 프로필·포인트를 받는다(포인트는 받아 둔 값이 있으면 그대로 보이면서 바꾼다)
watch(
    () => user.value?.id ?? null,
    (id) => {
        profile.value = null;
        if (!id) return;
        void loadProfile();
        void decorationSync.ensure();
        if (!businessAccount.value) void points.load();
    },
    { immediate: true },
);
onBeforeUnmount(() => profileSeq++);

/** 오늘 적립 줄 — 상한이 0(꺼짐)인 것은 뺀다 */
const todayRows = computed(() => {
    const today = summary.value?.today;
    if (!today) return [];
    const rows: { key: string; labelKey: string; value: PointCount }[] = [
        { key: 'post', labelKey: 'point.todayPost', value: today.post },
        { key: 'comment', labelKey: 'point.todayComment', value: today.comment },
        { key: 'likeBonus', labelKey: 'point.todayLikeBonus', value: today.likeBonus },
    ];
    return rows.filter((row) => row.value.cap > 0);
});
</script>
