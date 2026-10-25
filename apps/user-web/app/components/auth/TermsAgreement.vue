<template>
    <fieldset class="m-0 min-w-0 border-0 p-0">
        <legend class="sr-only">{{ t('signup.termsLegend') }}</legend>

        <!-- 전체 동의 -->
        <div class="flex items-center gap-2.5 border-b border-surface pb-3">
            <Checkbox :model-value="allChecked" binary :input-id="`${uid}-all`" :disabled="disabled" @update:model-value="setAll" />
            <label :for="`${uid}-all`" class="flex-1 cursor-pointer font-semibold">{{ t('signup.agreeAll') }}</label>
        </div>

        <div v-for="item in items" :key="item.key" class="py-2">
            <div class="flex items-center gap-2.5">
                <Checkbox
                    :model-value="model[item.key]"
                    binary
                    :input-id="`${uid}-${item.key}`"
                    :disabled="disabled"
                    :invalid="Boolean(message(item.key))"
                    :input-props="{ 'aria-describedby': message(item.key) ? `${uid}-${item.key}-err` : undefined }"
                    @update:model-value="(v: boolean) => set(item.key, v)"
                />
                <label :for="`${uid}-${item.key}`" class="flex-1 cursor-pointer text-sm">
                    <em class="mr-1 text-xs font-medium not-italic text-primary">{{ t('signup.requiredMark') }}</em>{{ item.label }}
                </label>
                <!-- 약관 본문 링크는 로그인 카드 아래(레이아웃 `auth`)와 같은 곳 — 라우터 밖 링크라 새 탭에서 열린다. 관리자 옵션(약관 요약의 `showLinks`)이 켜졌을 때만 -->
                <a v-if="showTermsLinks && item.href" :href="item.href" target="_blank" rel="noopener" class="shrink-0 text-xs text-primary hover:underline max-md:-my-1.5 max-md:py-1.5">
                    {{ t('signup.view') }}<span class="sr-only"> {{ t('signup.viewNewTab', { label: item.label }) }}</span>
                </a>
            </div>
            <p v-if="message(item.key)" :id="`${uid}-${item.key}-err`" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">
                {{ message(item.key) }}
            </p>
        </div>

        <p class="mt-1 text-xs leading-relaxed text-muted-color">{{ t('signup.ageNote') }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import type { Agreements } from '~/composables/auth/api/useAuthApi.types';

/**
 * 약관 동의 — 전체 동의 · 이용약관 · 개인정보처리방침 · 만 14세 이상 확인(모두 필수). 원본(community) `components/auth/TermsAgreement.vue` 를 옮겼다(문구는 i18n 키).
 * 소셜 가입 마무리와 이메일 가입 화면이 쓴다.
 *
 * 어긋난 항목의 안내는 그 항목을 건드린 뒤(또는 화면·서버가 지적했을 때 — `errors`)부터 보인다 — 처음부터 붉게 시작하지 않는다.
 */
type Key = keyof Agreements;

interface Props {
    /** 화면이 미리 검사했거나 서버가 지적한 항목의 문구(`fields.ageConfirmed` 등) — 체크하면 사라진다 */
    errors?: Partial<Record<Key, string>>;
    disabled?: boolean;
}

const model = defineModel<Agreements>({ required: true });
const props = withDefaults(defineProps<Props>(), {
    errors: () => ({}),
    disabled: false,
});

const { t } = useI18n();
const uid = useId();

/**
 * 약관 본문 「보기」 링크를 보일지 — 관리자 옵션 「가입·로그인 화면에 약관 보기 링크 표시」(약관 요약 `GET /api/terms/summary` 의 `showLinks`, `docs/features/terms-contract.md`).
 * 불러오기 전·실패 시 숨긴다. 동의 체크는 이 값과 관계없이 늘 필수다. 요약은 레이아웃 `auth` 와 같은 상태(`useTermsSummary`)를 쓴다.
 */
const { showLinks: showTermsLinks, ensureLoaded: ensureTermsSummary } = useTermsSummary();
void ensureTermsSummary();

/** 항목 — 언어를 바꾸면 다시 계산된다 */
const items = computed<{ key: Key; label: string; href?: string; missing: string }[]>(() => [
    { key: 'agreeTerms', label: t('common.terms'), href: '/terms', missing: t('signup.agreeTermsMissing') },
    { key: 'agreePrivacy', label: t('common.privacy'), href: '/privacy', missing: t('signup.agreePrivacyMissing') },
    { key: 'ageConfirmed', label: t('signup.ageConfirmedLabel'), missing: t('signup.ageConfirmedMissing') },
]);

const touched = reactive<Record<Key, boolean>>({ agreeTerms: false, agreePrivacy: false, ageConfirmed: false });

const allChecked = computed(() => model.value.agreeTerms && model.value.agreePrivacy && model.value.ageConfirmed);

function set(key: Key, value: boolean) {
    touched[key] = true;
    model.value = { ...model.value, [key]: value };
}

function setAll(value: boolean) {
    touched.agreeTerms = touched.agreePrivacy = touched.ageConfirmed = true;
    model.value = { agreeTerms: value, agreePrivacy: value, ageConfirmed: value };
}

function message(key: Key): string | undefined {
    if (model.value[key]) return undefined;
    if (props.errors[key]) return props.errors[key];
    return touched[key] ? items.value.find((i) => i.key === key)?.missing : undefined;
}
</script>
