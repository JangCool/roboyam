<template>
    <div>
        <!-- 위: 이전 / 건너뛰기 -->
        <div class="mb-3 flex items-center justify-between text-sm">
            <button
                v-if="index > 0"
                type="button"
                class="-ml-1 inline-flex items-center gap-1 rounded px-1 py-0.5 text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary max-md:py-1.5"
                data-testid="onboarding-prev"
                @click="go(index - 1)"
            >
                <i class="pi pi-angle-left" aria-hidden="true" /> {{ t('common.prev') }}
            </button>
            <span v-else />
            <button
                type="button"
                class="rounded px-1 py-0.5 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary max-md:py-1.5"
                data-testid="onboarding-skip"
                @click="finish"
            >
                {{ t('common.skip') }}
            </button>
        </div>

        <!-- 1. 환영 -->
        <section v-if="index === 0" :aria-labelledby="`${uid}-title`">
            <div class="flex flex-col items-center gap-3 text-center">
                <div class="grid size-14 place-items-center rounded-full bg-primary-50 text-lg font-bold text-primary-700 dark:bg-primary-950 dark:text-primary-300" aria-hidden="true">
                    {{ t('onboarding.avatar') }}
                </div>
                <h1 :id="`${uid}-title`" ref="heading" tabindex="-1" class="text-lg font-semibold">
                    <i18n-t keypath="onboarding.welcome" scope="global" tag="span">
                        <template #name>
                            <span class="inline-flex items-center gap-1 align-middle">
                                <GradeBadge v-if="user" :grade="user.grade" :label="user.gradeLabel" />
                                <span class="break-all">{{ user?.nickname }}</span>
                            </span>
                        </template>
                    </i18n-t>
                </h1>
                <p class="text-sm leading-relaxed text-muted-color">
                    {{ t('onboarding.intro') }}<br />
                    {{ t('onboarding.introRules', { count: RULES.length }) }}
                </p>
            </div>
            <ol class="mt-4 flex flex-col gap-2">
                <li v-for="(item, i) in rules" :key="item.key" class="flex items-center gap-3 rounded-lg border border-surface px-3 py-2.5 text-sm">
                    <span class="grid size-6 shrink-0 place-items-center rounded-full text-xs font-bold leading-none" :class="item.badge" aria-hidden="true">{{ i + 1 }}</span>
                    {{ item.short }}
                </li>
            </ol>
        </section>

        <!-- 2~4. 운영 원칙 -->
        <section v-else-if="rule" :aria-labelledby="`${uid}-title`">
            <p class="text-xs text-muted-color">{{ t('onboarding.ruleCount', { current: index, total: RULES.length }) }}</p>
            <div class="mt-2 flex flex-col gap-3">
                <span class="grid size-8 place-items-center rounded-full text-sm font-bold leading-none" :class="rule.badge" aria-hidden="true">{{ index }}</span>
                <h1 :id="`${uid}-title`" ref="heading" tabindex="-1" class="text-lg font-semibold">{{ rule.title }}</h1>
                <p class="text-sm leading-relaxed text-muted-color">{{ rule.body }}</p>

                <div v-if="rule.example" class="rounded-lg bg-surface-100 p-3 text-sm dark:bg-surface-800">
                    <template v-if="rule.example.tag">
                        <span class="mr-1.5 inline-flex items-center rounded bg-primary-50 px-1.5 py-0.5 text-xs font-semibold leading-none text-primary-700 dark:bg-primary-950 dark:text-primary-300"><span class="relative -top-[0.09em]">{{ rule.example.tag }}</span></span>
                        {{ rule.example.text }}
                        <small class="mt-1 block text-xs text-muted-color">{{ rule.example.note }}</small>
                    </template>
                    <template v-else>{{ rule.example.text }}</template>
                </div>
                <p v-if="rule.invest" class="rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs leading-relaxed text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100">
                    {{ rule.invest }}
                </p>
            </div>
        </section>

        <!-- 진행 점 + 다음 -->
        <div class="mt-6 flex items-center justify-center gap-1.5" role="img" :aria-label="t('onboarding.stepLabel', { current: index + 1, total: TOTAL })">
            <span v-for="n in TOTAL" :key="n" class="h-1.5 rounded-full transition-all" :class="n - 1 === index ? 'w-4 bg-primary' : 'w-1.5 bg-surface-300 dark:bg-surface-600'" />
        </div>
        <Button class="mt-4" :label="last ? t('onboarding.start') : t('common.next')" fluid size="large" data-testid="onboarding-next" @click="last ? finish() : go(index + 1)" />
    </div>
</template>

<script setup lang="ts">
/**
 * 온보딩 `/onboarding` — 가입 직후 첫 화면(이메일 인증 완료 화면의 「시작하기」가 온다). 원본(community) `pages/onboarding.vue` 를 옮기고 원칙의 예시만 로보얌 주제(로봇·AI)로 바꿨다(문구는 i18n `onboarding.*`).
 * 환영 → 운영 원칙 3장 → 시작하기. 「건너뛰기」로 언제든 홈으로 나간다. 서버 호출은 없다(로그인 정보만 — `auth` 미들웨어).
 * 운영 원칙 문구는 원본 시안의 초안 그대로이며 전문 검토는 오픈 전 할 일이다(`member-account-contract.md` 결정 2차). 원본 주석의 「기본 로봇 프로필 이미지 고르기」 단계는 원본에도 없고 로보얌에도 없다.
 */
definePageMeta({ layout: 'auth', middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('onboarding.seoTitle'), description: () => t('onboarding.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

interface Rule {
    key: string;
    short: string;
    title: string;
    body: string;
    badge: string;
    example?: { tag?: string; text: string; note?: string };
    invest?: string;
}

/** 원칙 차례와 배지 색 — 글은 그릴 때 `t` 로(언어를 바꾸면 다시 계산된다) */
const RULES = [
    { key: 'source', badge: 'bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-300' },
    { key: 'respect', badge: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300' },
    { key: 'invest', badge: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300' },
] as const;

const rules = computed<Rule[]>(() => [
    {
        key: RULES[0].key,
        badge: RULES[0].badge,
        short: t('onboarding.sourceShort'),
        title: t('onboarding.sourceTitle'),
        body: t('onboarding.sourceBody'),
        example: { tag: t('onboarding.sourceTag'), text: t('onboarding.sourceExample'), note: t('onboarding.sourceNote') },
    },
    {
        key: RULES[1].key,
        badge: RULES[1].badge,
        short: t('onboarding.respectShort'),
        title: t('onboarding.respectTitle'),
        body: t('onboarding.respectBody'),
        example: { text: t('onboarding.respectExample') },
    },
    {
        key: RULES[2].key,
        badge: RULES[2].badge,
        short: t('onboarding.investShort'),
        title: t('onboarding.investTitle'),
        body: t('onboarding.investBody'),
        invest: t('onboarding.investNotice'),
    },
]);

const TOTAL = RULES.length + 1; // 환영 + 원칙 3장

const { user } = useAuth();
const uid = useId();
const index = ref(0);
const last = computed(() => index.value === TOTAL - 1);
const rule = computed<Rule | undefined>(() => (index.value > 0 ? rules.value[index.value - 1] : undefined));

// 화면이 넘어가면 제목으로 초점을 옮겨 새 내용을 읽게 한다
const heading = ref<HTMLElement | null>(null);
async function go(next: number) {
    index.value = Math.min(Math.max(next, 0), TOTAL - 1);
    await nextTick();
    heading.value?.focus({ preventScroll: true });
}

async function finish() {
    await navigateTo('/');
}
</script>
