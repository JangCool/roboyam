<template>
    <!--
        잠긴 가젯 — 로그인한 사람만 보는 가젯을 로그인하지 않은 방문자가 볼 때(서버가 `locked: true` 뼈대로 준다). 머리·카드 틀은 그대로, 내용 자리에 자물쇠 + 「로그인하면 볼 수 있어요」 + [로그인].
        종류 컴포넌트는 마운트하지 않는다(`HomeGadgetRenderer`). 로그인 뒤 돌아올 곳은 지금 페이지다(`UtilRedirect.toLogin`).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="false">
        <div class="flex min-h-24 flex-col items-center justify-center gap-2 px-3 py-6 text-center" data-testid="home-gadget-locked">
            <i class="pi pi-lock text-xl text-muted-color" aria-hidden="true" />
            <p class="m-0 text-sm text-muted-color">{{ t('home.lockedText') }}</p>
            <Button as="router-link" :to="loginTo" :label="t('home.lockedLogin')" size="small" />
        </div>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilRedirect from '~/utils/util-redirect';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});
void props;

const { t } = useI18n();
const route = useRoute();
const loginTo = computed(() => UtilRedirect.toLogin(route.fullPath));
</script>
