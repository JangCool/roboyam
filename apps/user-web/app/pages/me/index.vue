<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
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

            <!-- 바로가기 — 지금 만들어진 화면만(스크랩은 기능이 없어 넣지 않는다). 포인트 상점·내 아이템은 포인트 내역 다음(사업자 전용 계정 제외). 내 프로필 보기·등급 안내는 회원 활동 화면, 쪽지함은 소통 화면 -->
            <nav class="panel-card p-2" :aria-label="t('me.shortcuts')">
                <ul class="m-0 list-none p-0">
                    <li v-for="entry in entries" :key="entry.to">
                        <NuxtLink
                            :to="entry.to"
                            class="flex min-h-11 items-center justify-between gap-2 rounded-lg px-2 py-2.5 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                            :aria-label="entry.ariaLabel"
                            :data-testid="entry.testid"
                        >
                            <span class="flex items-center gap-2"><i :class="[entry.icon, 'text-muted-color']" aria-hidden="true" />{{ entry.label }}</span>
                            <span class="flex items-center gap-1.5 text-muted-color">
                                <span v-if="entry.hint" class="text-xs">{{ entry.hint }}</span>
                                <!-- 안 읽은 알림 수 — 숫자는 눈으로만(링크 이름 `ariaLabel` 에 수가 들어간다) -->
                                <span v-if="entry.badge" class="grid min-w-5 place-items-center rounded-full bg-red-600 px-1.5 text-[0.6875rem] font-bold leading-5 text-white" aria-hidden="true">
                                    {{ entry.badge }}
                                </span>
                                <i class="pi pi-angle-right" aria-hidden="true" />
                            </span>
                        </NuxtLink>
                    </li>
                </ul>
            </nav>

            <!-- 운영자·운영진만 — 관리자 앱(별도 사이트, 새 탭) -->
            <nav v-if="adminUrl" class="panel-card p-2" :aria-label="t('shell.adminApp')">
                <AdminAppLink class="min-h-11 rounded-lg px-2 py-2.5 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" />
            </nav>

            <div>
                <Button type="button" :label="t('common.logout')" icon="pi pi-sign-out" severity="secondary" variant="outlined" :loading="busy" data-testid="me-logout" @click="logout" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { PointCount } from '~/composables/point/api/usePointApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';
import UtilPointShop from '~/utils/util-point-shop';
import UtilProfile from '~/utils/util-profile';
import UtilSettings from '~/utils/util-settings';

/**
 * 내 정보 `/me` — 로그인 필요(`auth` 미들웨어). 하단 탭·헤더 프로필 메뉴·햄버거의 「내 정보」가 온다.
 * 프로필 요약(뱃지·닉네임·이메일 — 로그인 정보, 가입일·한 줄 소개 — 내 프로필 `GET /api/me/profile`) · 포인트 요약(`GET /api/me/points`) · 바로가기(포인트 내역·알림·설정 화면들) · 로그아웃.
 * 원본(community) `pages/me/index.vue` 의 구성에서 로보얌에 없는 것(스크랩·소모임·주문·쪽지·프로필 그림)을 뺐다. 포인트 상점(`/point-shop`)·내 아이템(`/me/items`) 바로가기가 있다. 바로가기에 내 공개 프로필(`/users/{내 ID}`)·등급 안내(`/me/grade`)가 있다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('me.title'), description: () => t('me.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const { user, adminUrl, emailVerified, restriction } = useAuth();
const { busy, run: logout } = useLogoutFlow();
const profileApi = useProfileApi();
const points = usePointSummary();
const { summary, status: pointStatus } = points;
const badge = useNotificationBadge();
const dmBadge = useDmBadge();
const { term } = useSiteTerms();

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

interface Entry {
    to: string;
    icon: string;
    label: string;
    hint: string;
    badge: string;
    ariaLabel?: string;
    testid: string;
}

/** 바로가기 — 포인트 내역(사업자 전용 계정 제외) · 알림(안 읽은 수) · 설정 묶음의 화면들 */
const entries = computed<Entry[]>(() => {
    const list: Entry[] = [];
    // 내 공개 프로필(남이 보는 모양) · 등급 안내 — `docs/features/member-activity-contract.md`
    if (user.value) list.push({ to: UtilProfile.path(user.value.id), icon: 'pi pi-id-card', label: t('me.entryProfile'), hint: '', badge: '', testid: 'me-entry-profile' });
    list.push({ to: '/me/grade', icon: 'pi pi-star', label: t('me.entryGrade'), hint: user.value?.role === 'USER' ? user.value.gradeLabel : '', badge: '', testid: 'me-entry-grade' });
    if (!businessAccount.value) {
        // 포인트·출석체크(커머스 1-3) — 출석 기능이 있으면 「포인트·출석체크」, 오른쪽에 보유 포인트(+ 오늘 출석 전이면 「· 출석 +10P」)
        const s = summary.value;
        list.push({
            to: '/me/points',
            icon: 'pi pi-wallet',
            label: s?.attendanceAvailable ? t('point.entry') : t('point.history'),
            hint: s ? UtilPointMessages.entryHint(t, s.balance, UtilPoint.attendable(s, { emailVerified: emailVerified.value, restriction: restriction.value }), s.rules.attendance) : '',
            badge: '',
            testid: 'me-entry-points',
        });
        // 포인트 상점 · 내 아이템(`commerce-stage1-contract.md` 「1-2 화면 목업」) — 공동구매(주문 내역)와 따로
        list.push({ to: UtilPointShop.PATH, icon: 'pi pi-shopping-bag', label: term('POINT_SHOP'), hint: '', badge: '', testid: 'me-entry-point-shop' });
        list.push({ to: UtilPointShop.MY_ITEMS_PATH, icon: 'pi pi-box', label: t('pointShop.myItems'), hint: '', badge: '', testid: 'me-entry-my-items' });
    }
    list.push({ to: '/notifications', icon: 'pi pi-bell', label: t('notification.title'), hint: '', badge: badge.text.value, ariaLabel: badge.label.value, testid: 'me-entry-notifications' });
    // 쪽지함 — 안 읽은 대화 수(`docs/features/member-social-contract.md`)
    list.push({ to: '/messages', icon: 'pi pi-envelope', label: t('notification.dmTitle'), hint: '', badge: dmBadge.text.value, ariaLabel: dmBadge.label.value, testid: 'me-entry-messages' });
    for (const entry of UtilSettings.ENTRIES) {
        list.push({ to: entry.to, icon: entry.icon, label: t(entry.labelKey), hint: '', badge: '', testid: `me-entry-${entry.testid}` });
    }
    return list;
});
</script>
