<template>
    <!-- 상단 바 — 모바일: ☰ · 로고 · 검색 · 로그인/닉네임, 데스크톱: 로고 · 검색 · 알림 종 · 쪽지 아이콘 · 로그인·가입 또는 계정 메뉴.
         알림 종은 데스크톱에만 있다(모바일은 하단 탭의 "알림"이 같은 일을 한다). 쪽지 아이콘도 데스크톱에만 — 모바일은 메뉴·내 정보의 "쪽지함" 줄로 들어간다 (design 01-shell 6) -->
    <header class="app-nav sticky top-0 z-30 border-b">
        <div class="mx-auto flex h-12 w-full max-w-[var(--content-max)] items-center gap-1 px-2 md:px-4">
            <button
                type="button"
                class="flex size-10 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary md:hidden dark:hover:bg-surface-800"
                :aria-label="t('shell.openMenu')"
                aria-haspopup="dialog"
                @click="$emit('open-menu')"
            >
                <i class="pi pi-bars text-lg" aria-hidden="true" />
            </button>

            <NuxtLink to="/" class="flex min-w-0 items-center rounded px-1 text-base font-bold tracking-wider focus-visible:outline-2 focus-visible:outline-primary max-md:min-h-10 max-md:min-w-8 md:px-0" :aria-label="t('shell.homeLink', { site: siteLabel })"><SiteBrand /></NuxtLink>

            <span class="flex-1" />

            <NuxtLink
                to="/search"
                class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('common.search')"
            >
                <i class="pi pi-search text-lg" aria-hidden="true" />
            </NuxtLink>

            <!-- 알림 종 + 알림 패널(드롭다운) — 로그인한 사람에게만, 데스크톱에서만 -->
            <NotificationBell v-if="loaded && user" />

            <!-- 쪽지 아이콘 + 안 읽은 대화 수 — 로그인한 사람에게만, 데스크톱에서만 -->
            <AppMessageLink v-if="loaded && user" />

            <nav v-if="loaded" class="flex items-center gap-2 text-sm" :aria-label="t('shell.accountNav')">
                <template v-if="user">
                    <button
                        type="button"
                        class="flex max-w-48 items-center gap-1.5 rounded-lg px-2 py-1.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        aria-haspopup="menu"
                        :aria-expanded="menuOpen"
                        :aria-label="t('shell.accountMenu')"
                        @click="toggleMenu"
                    >
                        <GradeBadge v-if="user.role === 'USER'" :grade="user.grade" :label="user.gradeLabel" />
                        <StaffBadge v-else :role="user.role" />
                        <span class="min-w-0 truncate font-medium">{{ user.nickname }}</span>
                        <i class="pi pi-angle-down text-xs text-muted-color" aria-hidden="true" />
                    </button>
                    <Menu ref="menu" :model="menuItems" popup @show="onMenuShow" @hide="menuOpen = false">
                        <!-- 보유 포인트 요약(8단계) — 요약을 받았을 때만. 메뉴가 열릴 때 최신으로 받는다 -->
                        <template v-if="pointLine" #start>
                            <p class="m-0 border-b border-surface px-3 py-2 text-xs text-muted-color" data-testid="menu-points">{{ pointLine }}</p>
                        </template>
                        <!-- 항목 모양은 PrimeVue 기본과 같다. 주소(`url`)가 있는 항목("관리자 앱")만 새 탭 링크라 `rel` 을 붙이고 바깥으로 나간다는 아이콘을 더한다.
                             `props.action` 을 붙여야 키보드 Enter 가 이 링크를 누른다 -->
                        <template #item="{ item, label, props }">
                            <a v-bind="props.action" :href="item.url" :target="item.target" :rel="item.target === '_blank' ? UtilAdminLink.REL : undefined">
                                <span v-bind="props.icon" />
                                <span v-bind="props.label">{{ item.text ?? label }}</span>
                                <i v-if="item.target === '_blank'" class="pi pi-external-link ml-auto text-xs text-muted-color" aria-hidden="true" />
                            </a>
                        </template>
                    </Menu>
                </template>
                <template v-else>
                    <Button as="router-link" :to="loginTarget" :label="t('common.login')" size="small" severity="secondary" variant="outlined" class="nav-btn-outline max-md:min-h-8" />
                    <Button as="router-link" to="/signup" :label="t('common.signup')" size="small" class="nav-btn-solid max-md:hidden" />
                </template>
            </nav>
        </div>
    </header>
</template>

<script setup lang="ts">
import UtilAdminLink from '~/utils/util-admin-link';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';
import UtilDm from '~/utils/util-dm';
import UtilPoint from '~/utils/util-point';
import UtilRedirect from '~/utils/util-redirect';
import UtilPointShop from '~/utils/util-point-shop';

defineEmits<{ 'open-menu': [] }>();

const { t } = useI18n();
const route = useRoute();
const { user, loaded, adminUrl, emailVerified, restriction } = useAuth();
/** 서비스 이름 — 프로필 드롭다운의 포인트 상점 줄(`POINT_SHOP`) */
const { term } = useSiteTerms();
const { siteLabel } = useSiteName();
const { run: logout } = useLogoutFlow();
const { text: dmText } = useDmBadge();
const points = usePointSummary();

/** 오늘 출석을 권할 만한가(요약이 있고 출석 전이며 정지 중·인증 전이 아닐 때) — 메뉴 항목에 받을 포인트를 붙인다 */
const attendable = computed(() => UtilPoint.attendable(points.summary.value, { emailVerified: emailVerified.value, restriction: restriction.value }));
/** 사업자 전용 계정인가(H-2) — 포인트·출석 줄이 없다(서버가 `403 BUSINESS_ACCOUNT_RESTRICTED`) */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
/** 사용자 메뉴의 사업자 줄 — 신청했거나 사업자 전용 계정이면 "사업자 센터", 일반 회원이고 신청 전이면 "사업자 신청" */
const businessEntry = computed(() => UtilBusiness.entryOf(user.value));

/** 메뉴 맨 위의 "포인트 1,280P" 한 줄 — 요약을 받기 전에는 빈 글자(줄을 그리지 않는다). 사업자 전용 계정은 포인트가 없다 */
const pointLine = computed(() => (!businessAccount.value && points.summary.value ? t('point.balanceLine', { points: UtilPoint.points(points.summary.value.balance) }) : ''));

const menu = ref<{ toggle: (event: Event) => void } | null>(null);
const menuOpen = ref(false);

/** 로그인하면 지금 보던 화면으로 돌아온다 */
const loginTarget = computed(() => UtilRedirect.toLogin(route.fullPath));

/**
 * 프로필 드롭다운 — 맨 위에 보유 포인트, "내 정보" 다음에 "계정·로그인 연결"(`/settings/account`), "포인트·출석체크"(오늘 출석 전이면 "· +10P", 출석 기능이 없으면(`attendanceAvailable=false`) 그냥 "포인트")(8단계), "포인트 상점"(공동구매와 따로),
 * "사업자 센터"(신청했거나 사업자 전용 계정)·"사업자 신청"(일반 회원, 신청 전)(H-2). 사업자 전용 계정에는 포인트 줄·포인트 요약이 없다.
 * 운영자·운영진(`adminUrl` 이 있을 때)에게만 "관리자 앱"이 더해진다 — 별도 사이트라 `url`·`target` 으로 새 탭에서 연다.
 * 스크린리더 이름(`label`)에 새 탭이라는 말을 담고 보이는 글자(`text`)는 "관리자 앱"이다.
 */
const menuItems = computed(() => [
    { label: t('shell.myInfo'), icon: 'pi pi-user', command: () => void navigateTo('/me') },
    // 계정·로그인 연결(소셜 계정 연결·해제) — 셸 고정 항목이다(이 앱은 메뉴 관리의 마이페이지 그룹을 그리지 않는다)
    { label: t('account.title'), icon: 'pi pi-link', command: () => void navigateTo('/settings/account') },
    ...(businessAccount.value
        ? []
        : [
              {
                  label:
                      attendable.value && points.summary.value
                          ? t('point.entryWithReward', { amount: UtilPoint.number(points.summary.value.rules.attendance) })
                          : points.summary.value?.attendanceAvailable
                            ? t('point.entry')
                            : t('point.entryNoAttendance'),
                  icon: 'pi pi-wallet',
                  command: () => void navigateTo('/me/points'),
              },
              // 포인트 상점 — 포인트로 닉네임 색·프로필 테두리·닉네임 변경권을 산다(공동구매 주문 내역과 따로)
              { label: term('POINT_SHOP'), icon: 'pi pi-shopping-bag', command: () => void navigateTo(UtilPointShop.PATH) },
          ]),
    // 쪽지함 — 안 읽은 대화가 있으면 수가 붙는다("쪽지함 · 안 읽음 3", design 01-shell 6)
    { label: dmText.value ? t('shell.messageBoxUnread', { count: dmText.value }) : t('shell.messageBox'), icon: 'pi pi-envelope', command: () => void navigateTo(UtilDm.INBOX_PATH) },
    // 사업자 센터(신청했거나 사업자 전용 계정) · 사업자 신청(일반 회원, 신청 전) — H-2
    ...(businessEntry.value
        ? [
              {
                  label: businessEntry.value === 'CENTER' ? t('business.centerEntry') : t('business.applyEntry'),
                  icon: UtilBusinessMessages.ENTRY_CENTER_ICON,
                  command: () => void navigateTo(UtilBusiness.CENTER_PATH),
              },
          ]
        : []),
    ...(adminUrl.value ? [{ label: t('shell.adminAppNewTab'), text: t('shell.adminApp'), icon: UtilAdminLink.ICON, url: adminUrl.value, target: '_blank' }] : []),
    { separator: true },
    { label: t('common.logout'), icon: 'pi pi-sign-out', command: () => void logout() },
]);

function toggleMenu(event: Event) {
    menu.value?.toggle(event);
}

/** 메뉴가 열렸다 — 최신 포인트를 받는다(받아 둔 값이 있으면 그대로 보이면서 바꾼다) */
function onMenuShow() {
    menuOpen.value = true;
    if (user.value && !businessAccount.value) void points.load();
}
</script>
