<template>
    <!-- 다크·라이트 바꾸기 단추 — 상단 바(모바일·데스크톱, 알림 종 왼쪽). 보기 설정의 야간 모드(`theme`)를 지금 보이는 것의 반대(`DARK`·`LIGHT`)로 정한다(`AUTO` 면 기기 설정을 풀어 정한다).
         로그인했으면 서버, 아니면 이 브라우저에 남는다(`useViewSettings().update`). 기기 설정은 서버 렌더에서 알 수 없어 아이콘은 붙은 뒤에 그린다(어긋난 아이콘 깜빡임 방지) -->
    <button
        type="button"
        class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
        :aria-label="dark ? t('shell.lightMode') : t('shell.darkMode')"
        :aria-pressed="dark"
        data-testid="color-scheme-toggle"
        @click="toggle"
    >
        <i v-if="mounted" :class="dark ? 'pi pi-sun' : 'pi pi-moon'" class="text-lg" aria-hidden="true" />
    </button>
</template>

<script setup lang="ts">
import UtilViewSettings from '~/utils/util-view-settings';

const { t } = useI18n();
const values = useViewSettingsValues();
const { update } = useViewSettings();

const mounted = ref(false);
/** 기기 설정이 다크인가 — `AUTO` 일 때만 쓴다 */
const systemDark = ref(false);

const dark = computed(() => UtilViewSettings.resolveScheme(values.value.theme, systemDark.value) === 'dark');

let query: MediaQueryList | null = null;
const onSystemChange = (e: MediaQueryListEvent) => {
    systemDark.value = e.matches;
};
onMounted(() => {
    query = window.matchMedia('(prefers-color-scheme: dark)');
    systemDark.value = query.matches;
    query.addEventListener('change', onSystemChange);
    mounted.value = true;
});
onBeforeUnmount(() => query?.removeEventListener('change', onSystemChange));

function toggle(): void {
    void update({ theme: dark.value ? 'LIGHT' : 'DARK' });
}
</script>
