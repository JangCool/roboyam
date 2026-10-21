<template>
    <!-- 내 정보 — 제목 + 왼쪽 서브메뉴(`AppSideNav`: 일반 · 개인 설정 · 로그인 이력 · 비밀번호) + 탭 본문. 탭은 `?tab=general|personal|login|password` 로 들어오고 유지된다(모르는 값은 general).
         탭 내용은 `components/profile/` 의 탭 컴포넌트가 맡고 이 페이지는 전환만 한다. 본문 폭은 화면 설정(꽉 채우기/가운데 제한)이 정한다 — 이 화면이 따로 최대 폭을 걸지 않는다. -->
    <div class="flex w-full flex-col gap-4">
        <PageTitle :title="t('profile.title')" :subtitle="t('profile.subtitle')" />

        <div class="flex flex-col gap-4 md:flex-row md:items-start">
            <AppSideNav :model-value="activeTab" :items="TABS" :aria-label="t('profile.menuLabel')" @update:model-value="(value: string) => (activeTab = value as MeTab)" />

            <div class="min-w-0 flex-1">
                <ProfileGeneralTab v-if="activeTab === 'general'" />
                <ProfilePersonalTab v-else-if="activeTab === 'personal'" />
                <ProfileLoginTab v-else-if="activeTab === 'login'" />
                <ProfilePasswordTab v-else />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SideNavItem } from '~/components/common/AppSideNav.types';

type MeTab = 'general' | 'personal' | 'login' | 'password';

const { t } = useI18n();
useSeoMeta({ title: () => t('profile.seoTitle') });

const route = useRoute();
const router = useRouter();

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다(i18n.md)
const TABS = computed<SideNavItem[]>(() => [
    { value: 'general', label: t('profile.tabGeneral'), icon: 'pi pi-user' },
    { value: 'personal', label: t('profile.tabPersonal'), icon: 'pi pi-cog' },
    { value: 'login', label: t('profile.tabLogin'), icon: 'pi pi-history' },
    { value: 'password', label: t('profile.tabPassword'), icon: 'pi pi-key' },
]);

/** 지금 탭 — 주소의 `?tab=` 이 정한다. 모르는 값(없거나 엉뚱한 값)은 general. 프로필 메뉴의 "개인 설정"처럼 이미 이 화면에서 `?tab=` 만 바뀌어도 따라온다 */
const activeTab = computed<MeTab>({
    get: () => {
        const tab = route.query.tab;
        return tab === 'personal' || tab === 'login' || tab === 'password' ? tab : 'general';
    },
    // 탭을 누르면 주소에 반영한다(뒤로 가기 목록을 쌓지 않는다). 다른 쿼리(`mock` 등)는 그대로 둔다
    set: (tab) => void router.replace({ query: { ...route.query, tab } }),
});
</script>
