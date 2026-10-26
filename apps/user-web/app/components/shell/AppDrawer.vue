<template>
    <!-- 햄버거 메뉴(모바일) — 맨 위에 로그인 상태, 아래에 게시판 메뉴, 맨 아래에 약관 링크 -->
    <Drawer v-model:visible="open" position="left" :style="{ width: '17rem', maxWidth: '85vw' }" :aria-label="t('shell.menu')">
        <template #header>
            <SiteBrand class="font-bold tracking-wider" />
        </template>

        <div class="-mx-5 -mt-2 flex flex-col">
            <div v-if="loaded" class="mx-4 mb-2 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                <template v-if="user">
                    <p class="flex items-center gap-1.5">
                        <GradeBadge v-if="user.role === 'USER'" :grade="user.grade" :label="user.gradeLabel" />
                        <StaffBadge v-else :role="user.role" />
                        <b class="min-w-0 truncate text-sm">{{ user.nickname }}</b>
                    </p>
                    <!-- 보유 포인트 · 오늘 출석하기(8단계) — 포인트 화면으로. 요약을 받기 전·못 받았으면 이 줄만 빠진다 -->
                    <NuxtLink
                        v-if="pointLine"
                        to="/me/points"
                        class="mt-1 block rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        data-testid="drawer-points"
                        @click="close"
                    >
                        {{ pointLine }}
                    </NuxtLink>
                    <!-- 포인트 상점 — 포인트로 닉네임 색·프로필 테두리·닉네임 변경권을 산다(공동구매 주문 내역과 따로). 사업자 전용 계정은 포인트가 없어 줄이 없다 -->
                    <NuxtLink
                        v-if="!businessAccount"
                        :to="UtilPointShop.PATH"
                        class="mt-1 flex items-center gap-1 rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        data-testid="drawer-point-shop"
                        @click="close"
                    >
                        <i class="pi pi-shopping-bag text-xs" aria-hidden="true" />{{ term('POINT_SHOP') }}
                    </NuxtLink>
                    <!-- 주문 내역(9단계, 공동구매) — 사업자 전용 계정은 살 수 없어 줄이 없다 -->
                    <NuxtLink
                        v-if="!businessAccount"
                        :to="UtilCommerce.ORDERS_PATH"
                        class="mt-1 flex items-center gap-1 rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        data-testid="drawer-orders"
                        @click="close"
                    >
                        <i class="pi pi-receipt text-xs" aria-hidden="true" />{{ t('commerce.ordersTitle') }}
                    </NuxtLink>
                    <!-- 쪽지함 — 안 읽은 대화가 있으면 수가 붙는다(모바일은 헤더에 쪽지 아이콘이 없다) -->
                    <NuxtLink
                        to="/messages"
                        class="mt-1 flex items-center gap-1 rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="dm.label.value"
                        data-testid="drawer-messages"
                        @click="close"
                    >
                        <i class="pi pi-envelope text-xs" aria-hidden="true" />{{ dm.text.value ? t('shell.messageBoxUnread', { count: dm.text.value }) : t('shell.messageBox') }}
                    </NuxtLink>
                    <div class="mt-2 flex gap-2">
                        <Button type="button" :label="t('shell.myInfo')" size="small" severity="secondary" variant="outlined" class="flex-1" @click="go('/me')" />
                        <Button type="button" :label="t('common.logout')" size="small" severity="secondary" variant="text" :loading="busy" @click="onLogout" />
                    </div>
                    <!-- 사업자 센터(신청했거나 사업자 전용 계정) · 사업자 신청(일반 회원, 신청 전) — H-2 -->
                    <NuxtLink
                        v-if="businessEntry"
                        :to="UtilBusiness.CENTER_PATH"
                        class="mt-2 flex min-h-11 items-center gap-2 rounded-lg px-2 text-sm font-medium hover:bg-surface-200 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-700"
                        data-testid="drawer-business"
                        @click="close"
                    >
                        <i :class="[UtilBusinessMessages.ENTRY_CENTER_ICON, 'text-muted-color']" aria-hidden="true" />
                        {{ businessEntry === 'CENTER' ? t('business.centerEntry') : t('business.applyEntry') }}
                    </NuxtLink>
                    <!-- 운영자·운영진에게만 — 관리자 앱(별도 사이트)이 새 탭에서 열린다 -->
                    <AdminAppLink
                        class="mt-2 min-h-11 rounded-lg px-2 text-sm font-medium hover:bg-surface-200 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-700"
                        @click="close"
                    />
                </template>
                <template v-else>
                    <p class="text-sm font-semibold">{{ t('shell.loginInvite') }}</p>
                    <p class="mt-0.5 text-xs text-muted-color">{{ t('shell.loginInviteHint') }}</p>
                    <div class="mt-2 flex gap-2">
                        <Button type="button" :label="t('common.login')" size="small" class="flex-1" @click="go(UtilRedirect.toLogin(route.fullPath))" />
                        <Button type="button" :label="t('common.signup')" size="small" severity="secondary" variant="outlined" class="flex-1" @click="go('/signup')" />
                    </div>
                </template>
            </div>

            <AppMenuList @navigate="close" />

            <!-- 약관 본문 페이지는 아직 없다(19-info). 라우터 밖 링크라 새 탭에서 열린다 -->
            <p class="mt-4 flex gap-3 px-4 pb-2 text-xs text-muted-color">
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold hover:underline">{{ t('common.privacy') }}</a>
            </p>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';
import UtilCommerce from '~/utils/util-commerce';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';
import UtilRedirect from '~/utils/util-redirect';
import UtilPointShop from '~/utils/util-point-shop';

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const route = useRoute();
const { user, loaded, emailVerified, restriction } = useAuth();
/** 안 읽은 쪽지 대화 수 — 셸이 주기로 조회해 둔다(`layouts/default.vue`) */
const dm = useDmBadge();
/** 서비스 이름 — 포인트 상점 줄의 이름(`POINT_SHOP`) */
const { term } = useSiteTerms();
const { busy, run } = useLogoutFlow();
const points = usePointSummary();

/** 사업자 전용 계정인가(H-2) — 포인트·출석이 없다(서버가 `403 BUSINESS_ACCOUNT_RESTRICTED`) */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
/** 사업자 줄 — 신청했거나 사업자 전용 계정이면 "사업자 센터", 일반 회원이고 신청 전이면 "사업자 신청" */
const businessEntry = computed(() => UtilBusiness.entryOf(user.value));

/** "포인트 1,280P · 오늘 출석하기 +10P" — 요약을 받았을 때만. 출석을 이미 했거나 정지 중·이메일 인증 전이면 출석 안내를 뺀다(출석이 안 된다). 사업자 전용 계정은 포인트가 없어 줄이 없다 */
const pointLine = computed(() => {
    const summary = points.summary.value;
    if (!summary || !user.value || businessAccount.value) return '';
    const attendable = UtilPoint.attendable(summary, { emailVerified: emailVerified.value, restriction: restriction.value });
    return UtilPointMessages.summaryLine(t, summary.balance, attendable, summary.rules);
});

// 메뉴를 열 때마다 최신 포인트를 받는다(받아 둔 값이 있으면 그대로 보이면서 바꾼다)
watch(open, (visible) => {
    if (visible && user.value && !businessAccount.value) void points.load();
});

function close() {
    open.value = false;
}

async function go(to: RouteLocationRaw) {
    close();
    await navigateTo(to);
}

async function onLogout() {
    close();
    await run();
}

// 화면이 바뀌면 메뉴를 닫는다(뒤로 가기 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
