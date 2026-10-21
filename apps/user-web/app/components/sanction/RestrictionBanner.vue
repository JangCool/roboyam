<template>
    <!-- 정지 중 전역 배너 (design 20-system 3) — 헤더 바로 아래. 기간 정지·영구 정지 계정에만 보이고 "자세히 보기"는 제재 안내(`/sanction`)로 간다.
         글쓰기 버튼 등은 건드리지 않는다(쓰기는 서버가 `403 ACCOUNT_RESTRICTED` 로 막고 화면이 그 오류를 안내한다) -->
    <section
        v-if="copy && !onSanctionPage"
        class="border-b border-red-200 bg-red-50 text-red-950 dark:border-red-900 dark:bg-red-950/40 dark:text-red-100"
        :aria-label="t('sanction.bannerLabel')"
        data-testid="restriction-banner"
    >
        <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2.5 text-sm">
            <p class="m-0 min-w-0 flex-1 leading-relaxed" data-testid="restriction-banner-text">
                <b>{{ copy.title }}</b> {{ sentence }}
            </p>
            <NuxtLink
                :to="UtilSanction.PATH"
                class="inline-flex min-h-11 shrink-0 items-center text-xs underline focus-visible:outline-2 focus-visible:outline-primary"
                data-testid="restriction-banner-link"
            >
                {{ t('sanction.bannerLink') }}
            </NuxtLink>
        </div>
    </section>
</template>

<script setup lang="ts">
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * 정지 중 전역 배너 — `layouts/default.vue` 의 헤더 아래에 한 번 놓는다. 로그인 정보의 `restriction`(7-2)이 `SUSPENDED`/`BANNED` 일 때만 보인다.
 * 기간 정지는 "이용이 정지된 계정이에요. 읽기만 할 수 있어요. 해제 예정 2026.10.02(금)", 영구 정지는 해제 예정 없이 "계정이 영구 정지됐어요. 읽기만 할 수 있어요."
 * 제재 안내 화면(`/sanction…`)에서는 같은 내용을 크게 보이므로 배너를 접는다. 확인하지 않은 경고만 있는 계정(`ACTIVE`)에는 보이지 않는다 — 경고는 이용을 막지 않는다.
 */
const { t } = useI18n();
const { restriction } = useAuth();
const time = useDisplayTime();
const route = useRoute();

const copy = computed(() => UtilSanctionMessages.banner(t, restriction.value, time.value));
const onSanctionPage = computed(() => UtilSanction.isSanctionPath(route.path));
/**
 * 제목 뒤 문장 — 안내 문장과 "해제 예정 …" 을 한 표현식으로 잇는다.
 * 템플릿에서 `<template v-if> {{ release }}</template>` 로 쓰면 Vue 컴파일러가 첫 자식의 앞 공백을 지워 "읽기만 할 수 있어요.해제 예정" 으로 붙는다.
 */
const sentence = computed(() => (copy.value?.release ? `${copy.value.text} ${copy.value.release}` : (copy.value?.text ?? '')));
</script>
