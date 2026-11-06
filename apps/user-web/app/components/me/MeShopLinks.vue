<template>
    <!-- 상점 두 칸 — 내 정보 메뉴 맨 위(사용자 결정 2026-11-06: 목록 줄에서 빼고 위로 올림, 칸 높이는 두 줄 — 아이콘·이름 / 짧은 설명).
         포인트 상점은 사업자 전용 계정에 없다(포인트가 없다) — 그때는 로보티콘 상점 한 칸이 폭을 다 쓴다 -->
    <nav :aria-label="t('me.shopLinksLabel')" class="grid gap-2" :class="links.length > 1 ? 'grid-cols-2' : 'grid-cols-1'" data-testid="me-shop-links">
        <NuxtLink
            v-for="link in links"
            :key="link.key"
            :to="link.to"
            class="panel-card flex min-w-0 flex-col gap-0.5 px-3 py-2.5 hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
            :class="isActive(link.to) ? 'ring-1 ring-primary' : ''"
            :aria-current="isActive(link.to) ? 'page' : undefined"
            :data-testid="`me-shop-${link.key}`"
        >
            <span class="flex min-w-0 items-center gap-2 text-sm font-semibold">
                <i :class="[link.icon, 'text-primary']" aria-hidden="true" />
                <span class="truncate">{{ link.label }}</span>
            </span>
            <span class="truncate text-xs text-muted-color">{{ link.hint }}</span>
        </NuxtLink>
    </nav>
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilPointShop from '~/utils/util-point-shop';

const { t } = useI18n();
const route = useRoute();
const { user } = useAuth();
/** 포인트 상점 이름은 서비스 이름(`POINT_SHOP`) — 운영자가 바꾸면 따라 바뀐다 */
const { term } = useSiteTerms();

const links = computed(() => [
    ...(UtilBusiness.isBusinessAccount(user.value?.role) ? [] : [{ key: 'point', to: UtilPointShop.PATH, icon: 'pi pi-shopping-bag', label: term('POINT_SHOP'), hint: t('me.shopPointHint') }]),
    { key: 'emoticon', to: UtilEmoticon.PATH, icon: 'pi pi-face-smile', label: t('emoticon.shopTitle'), hint: t('me.shopEmoticonHint') },
]);

/** 지금 그 상점(또는 그 아래 화면)인가 */
function isActive(to: string): boolean {
    const path = route.path.replace(/\/$/, '');
    return path === to || path.startsWith(`${to}/`);
}
</script>
