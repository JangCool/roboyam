<template>
    <div class="mx-auto max-w-md md:max-w-xl">
        <h1 class="mb-3 text-lg font-bold">{{ t('grade.title') }}</h1>

        <!-- 자동 승급이 멈춰 있다 — 정지 중 보류(빨강, 제재 안내로) · 운영자가 등급을 직접 바꿈(노랑). 운영자가 적은 사유는 이 화면에 없다 -->
        <Message v-if="paused" :severity="paused.sanction ? 'error' : 'warn'" :closable="false" size="small" class="mb-3" data-testid="grade-paused">
            <b>{{ paused.copy.title }}</b> {{ paused.copy.text }}
            <NuxtLink v-if="paused.sanction" :to="UtilSanction.PATH" class="ml-1 font-semibold underline" data-testid="grade-sanction-link">{{ t('grade.sanctionLink') }}</NuxtLink>
        </Message>

        <!-- 내 등급과 진행도 -->
        <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-label="t('grade.mineLabel')" data-testid="grade-mine">
            <div v-if="!loaded" class="space-y-3" role="status" :aria-label="t('grade.authLoading')">
                <div class="h-5 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>

            <!-- 비회원 -->
            <template v-else-if="!user">
                <p class="m-0 text-sm font-semibold">{{ t('grade.guestNote') }}</p>
                <div class="mt-3 flex gap-2">
                    <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" class="flex-1" data-testid="grade-login" />
                    <Button as="router-link" to="/signup" :label="t('common.signup')" severity="secondary" variant="outlined" class="flex-1" />
                </div>
            </template>

            <!-- 진행도를 받는 중 -->
            <div v-else-if="gradeStatus === 'loading' || gradeStatus === 'idle'" class="space-y-3" role="status" :aria-label="t('grade.progressLoading')" data-testid="grade-loading">
                <div class="h-5 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-10 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-10 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>

            <!-- 못 받았다 — 조건표는 그대로 볼 수 있다 -->
            <ErrorState
                v-else-if="gradeStatus === 'error' || !myGrade"
                :title="t('grade.progressErrorTitle')"
                :description="t('grade.progressErrorText')"
                retry
                class="!py-4"
                data-testid="grade-error"
                @retry="mine.load"
            />

            <GradeProgress v-else :grade="myGrade" />
        </section>

        <!-- 등급 조건 — 서버가 준 설정값 -->
        <section class="mt-5" :aria-labelledby="rulesTitleId">
            <h2 :id="rulesTitleId" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('grade.rulesHeading') }}</h2>

            <div v-if="rulesStatus === 'loading'" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" role="status" :aria-label="t('grade.rulesLoading')" data-testid="grade-rules-loading">
                <div v-for="n in 4" :key="n" class="py-2"><div class="h-5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-4/5' : 'w-3/5'" /></div>
            </div>

            <ErrorState
                v-else-if="rulesStatus === 'error'"
                :title="t('grade.rulesErrorTitle')"
                :description="rulesError"
                retry
                class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900"
                data-testid="grade-rules-error"
                @retry="loadRules"
            />

            <ul v-else class="m-0 list-none rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900" data-testid="grade-rules">
                <li
                    v-for="rule in rules"
                    :key="rule.code"
                    class="flex items-center gap-3 border-b border-surface px-4 py-3 last:border-b-0"
                    :class="rule.code === myCode ? 'bg-primary-50 dark:bg-primary-950/30' : ''"
                    :aria-current="rule.code === myCode ? 'true' : undefined"
                    :data-testid="`grade-rule-${rule.code}`"
                >
                    <GradeBadge :grade="rule.code as Grade" :label="rule.label" class="shrink-0" />
                    <p class="m-0 flex min-w-0 flex-1 flex-wrap items-center gap-x-1 text-sm">
                        {{ UtilGradeMessages.ruleText(t, rule) }}
                        <span
                            v-if="rule.code === myCode"
                            class="inline-flex items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-xs font-semibold leading-none text-muted-color dark:bg-surface-800"
                            data-testid="grade-my-tag"
                        >
                            <span class="relative top-[-0.09em]">{{ t('grade.myRuleTag') }}</span>
                        </span>
                    </p>
                </li>
            </ul>
        </section>

        <!-- 이렇게 계산해요 -->
        <section class="mt-4 rounded-xl bg-surface-100 p-4 text-xs leading-relaxed dark:bg-surface-800" :aria-labelledby="howTitleId" data-testid="grade-how">
            <h2 :id="howTitleId" class="m-0 text-sm font-semibold">{{ t('grade.howHeading') }}</h2>
            <ul class="m-0 mt-1 list-none p-0">
                <li v-for="key in HOW_KEYS" :key="key" class="flex gap-1.5"><span aria-hidden="true">·</span><span>{{ t(key) }}</span></li>
            </ul>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { GradeRule } from '~/composables/grade/api/useGradeApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGradeMessages from '~/utils/util-grade-messages';
import UtilRedirect from '~/utils/util-redirect';
import UtilSanction from '~/utils/util-sanction';

/**
 * 등급 안내 — `/me/grade`(community `pages/me/grade.vue`). 등급 조건표(`GET /api/grade-rules`, **비회원도 본다**)와 내 등급·다음 등급 진행도(`GET /api/me/grade`, 로그인한 사람만).
 * - 수치는 운영자 설정이라 서버가 준 값으로만 그린다. 진행도 막대는 필요 값을 넘지 않게 자른다.
 * - 자동 승급이 멈춰 있으면(`PAUSED_SANCTION`·`PAUSED_ADMIN`) 안내를 띄우고 진행도를 흐리게 한다. 정지 중이면 제재 안내로 가는 길.
 * - 조건표와 진행도는 따로 받는다 — 한쪽이 실패해도 다른 쪽은 그대로 보이고 그쪽만 다시 시도한다.
 * API 는 `useGradeApi`(`GET /api/grade-rules`·`GET /api/me/grade`). 원본과 달라진 점: 인기글 조건·닉네임 이미지 신청·공식 뱃지 안내는 로보얌에 없어 뺐다. 모바일 ‹ 머리(`InnerHeader`)가 없어 제목을 그대로 보인다.
 * 메뉴의 「등급 안내」(BUILTIN `MY_GRADE`)는 로그인 필요 항목이지만 화면은 비회원에게도 조건표를 보인다(community 와 같다).
 */
const { t } = useI18n();
const route = useRoute();
const { user, loaded } = useAuth();
const gradeApi = useGradeApi();
const mine = useMyGrade();
const { grade: myGrade, status: gradeStatus } = mine;
const rulesTitleId = useId();
const howTitleId = useId();

const HOW_KEYS = ['grade.how1', 'grade.how2', 'grade.how3', 'grade.how4'] as const;

/* ── 조건표 — 비회원도 받는다 ── */
const rules = ref<GradeRule[]>([]);
const rulesStatus = ref<'loading' | 'ready' | 'error'>('loading');
const rulesError = ref('');
let rulesRequest = 0;

async function loadRules() {
    const asked = ++rulesRequest;
    rulesStatus.value = 'loading';
    try {
        const result = await gradeApi.rules();
        if (asked !== rulesRequest) return;
        rules.value = result.rules;
        rulesStatus.value = 'ready';
    } catch (e) {
        if (asked !== rulesRequest) return;
        rulesError.value = UtilBoardMessages.of(t, e);
        rulesStatus.value = 'error';
    }
}
void loadRules();

/* ── 내 진행도 — 로그인한 사람만. 열 때마다 최신으로 받는다 ── */
watch(
    () => user.value?.id ?? null,
    (id) => {
        if (id) void mine.load();
        else mine.reset();
    },
    { immediate: true },
);
onBeforeUnmount(() => rulesRequest++);

/** 내 등급 키 — 진행도를 받기 전에도 로그인 정보의 등급으로 조건표에서 내 줄을 짚는다 */
const myCode = computed(() => myGrade.value?.grade.code ?? user.value?.grade ?? null);

const paused = computed(() => {
    const state = myGrade.value?.autoPromotion;
    const copy = UtilGradeMessages.pausedNotice(t, state);
    return copy ? { copy, sanction: state === 'PAUSED_SANCTION' } : null;
});

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('grade.title'), description: () => t('grade.seoDescription', { site: siteLabel.value }) });
</script>
