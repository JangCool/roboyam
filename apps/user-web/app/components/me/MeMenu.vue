<template>
    <!-- 내 정보 메뉴 — 묶음마다 한 카드 안에서 줄로 나눈다. 지금 화면 줄은 짚어 둔다(aria-current). 아래에 관리자 앱(운영자·운영진만) · 로그아웃 -->
    <div class="flex flex-col gap-4" data-testid="me-menu">
        <!-- 상점 두 칸 — 메뉴 맨 위(모바일은 포인트 요약 바로 아래, 데스크톱은 오른쪽 열 맨 위) -->
        <MeShopLinks />

        <nav class="panel-card p-2" :aria-label="t('me.menuLabel')">
            <template v-for="(group, gi) in shown" :key="group.key">
                <p v-if="group.title" class="m-0 px-2 pb-1 text-xs font-semibold text-muted-color" :class="gi > 0 ? 'mt-2 border-t border-surface pt-3' : 'pt-1'">{{ group.title }}</p>
                <div v-else-if="gi > 0" class="mx-2 my-2 border-t border-surface" aria-hidden="true" />
                <ul class="m-0 list-none p-0">
                    <li v-for="entry in group.entries" :key="entry.to">
                        <NuxtLink
                            :to="entry.to"
                            class="flex min-h-10 items-center justify-between gap-2 rounded-lg px-2 py-2 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                            :class="entry.active ? 'bg-surface-100 font-semibold text-color dark:bg-surface-800' : ''"
                            :aria-current="entry.active ? 'page' : undefined"
                            :aria-label="entry.ariaLabel"
                            :data-testid="entry.testid"
                        >
                            <span class="flex min-w-0 items-center gap-2">
                                <i :class="[entry.icon, 'text-muted-color']" aria-hidden="true" />
                                <span class="truncate">{{ entry.label }}</span>
                            </span>
                            <span class="flex shrink-0 items-center gap-1.5 text-muted-color">
                                <span v-if="entry.hint" class="text-xs">{{ entry.hint }}</span>
                                <span v-if="entry.badge" class="grid min-w-5 place-items-center rounded-full bg-red-600 px-1.5 text-[0.6875rem] font-bold leading-5 text-white" aria-hidden="true">
                                    {{ entry.badge }}
                                </span>
                                <i class="pi pi-angle-right text-xs" aria-hidden="true" />
                            </span>
                        </NuxtLink>
                    </li>
                </ul>
            </template>
        </nav>

        <!-- 운영자·운영진만 — 관리자 앱(별도 사이트, 새 탭) -->
        <nav v-if="adminUrl" class="panel-card p-2" :aria-label="t('shell.adminApp')">
            <AdminAppLink class="min-h-10 rounded-lg px-2 py-2 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" />
        </nav>

        <div>
            <Button type="button" :label="t('common.logout')" icon="pi pi-sign-out" severity="secondary" variant="outlined" :loading="busy" data-testid="me-logout" @click="logout" />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';

/**
 * 내 정보 메뉴 — 데스크톱은 셸의 오른쪽 열(`MeFrame`)에 늘 서 있고 줄을 누르면 왼쪽 화면만 바뀐다. 모바일은 `/me` 가 이 메뉴를 목록으로 그린다(`home=false` — 「내 정보」 줄은 뺀다).
 * 줄과 묶음은 `useMeMenu`. 포인트 줄의 보유 포인트는 받아 둔 요약이 없으면 한 번 받는다(사업자 전용 계정 제외).
 */
const props = withDefaults(defineProps<{ home?: boolean }>(), { home: true });

const { t } = useI18n();
const { user, adminUrl } = useAuth();
const { busy, run: logout } = useLogoutFlow();
const { groups } = useMeMenu();
const points = usePointSummary();

/** 모바일 목록은 「내 정보」 줄을 뺀다. 「보기 설정 · 설정」 묶음은 데스크톱 열에도 둔다(사용자 결정 2026-11-12 — 보기 설정을 찾기 쉽게) */
const shown = computed(() => groups.value.filter((group) => props.home || group.key !== 'home'));

onMounted(() => {
    if (user.value && !UtilBusiness.isBusinessAccount(user.value.role) && points.status.value === 'idle') void points.load();
});
</script>
