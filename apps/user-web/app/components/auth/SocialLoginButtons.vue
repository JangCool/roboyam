<template>
    <!-- 서버가 알려 준 소셜 로그인만 보인다. 하나도 없으면 영역 자체를 그리지 않는다 -->
    <section v-if="list.length" :aria-label="t('login.socialAccounts')">
        <div v-if="divider" class="mb-3 flex items-center gap-2 text-xs text-muted-color" role="separator">
            <span class="h-px flex-1 bg-surface-200 dark:bg-surface-700" />
            {{ divider }}
            <span class="h-px flex-1 bg-surface-200 dark:bg-surface-700" />
        </div>

        <div class="flex flex-col gap-2">
            <Button
                v-for="p in list"
                :key="p.id"
                type="button"
                :variant="solid ? undefined : 'outlined'"
                :severity="solid ? undefined : 'secondary'"
                fluid
                class="justify-center gap-2"
                :disabled="disabled || redirecting"
                @click="go(p.id)"
            >
                <span class="grid size-6 place-items-center rounded-md text-xs font-bold" :class="UtilProvider.markClass(p.id)" aria-hidden="true">
                    {{ UtilProvider.mark(p.id) }}
                </span>
                <span class="font-semibold">{{ label(p) }}</span>
            </Button>
        </div>
    </section>
</template>

<script setup lang="ts">
import UtilProvider from '~/utils/util-provider';

/**
 * 소셜 로그인 버튼 묶음 (카카오·네이버·구글 — 서버가 키를 설정한 것만).
 * 누르면 요청이 아니라 **페이지 이동**이다 — 서버가 소셜 로그인 화면으로 보내고, 끝나면 화면 주소로 되돌려 보낸다.
 * 이동 주소는 `useAuthApi.startSocialLogin` 이 안다(`next` 는 서버 규칙에 맞을 때만 넘긴다). 원본이 이동 직전에 남기는 "소셜 로그인 시작" 표시(`useLoginLanding`)는 가져오지 않았다
 * — 정지 회원이 연결된 소셜로 로그인하면 서버가 바로 `next` 로 보내므로 제재 안내(`/sanction`) 착륙은 아직 없다(제재 안내 화면을 만들 때 함께 가져온다).
 */
interface Props {
    /** 버튼 글: 계속하기 · 로그인 · 가입 */
    mode?: 'continue' | 'login' | 'signup';
    /** 이 제공자 하나만 (예: 비밀번호 안내 화면의 "카카오로 로그인") */
    only?: string;
    disabled?: boolean;
    /** 위에 그을 구분선 글 (예: "소셜 계정으로 계속하기") */
    divider?: string;
    /** 채운 버튼 */
    solid?: boolean;
    /** 로그인 뒤 돌아갈 곳(주소의 `next` 쿼리 값) — 같은 사이트 상대 경로가 아니면 넘기지 않는다 */
    next?: string;
}

const props = withDefaults(defineProps<Props>(), {
    mode: 'continue',
    only: '',
    disabled: false,
    divider: '',
    solid: false,
    next: '',
});

/** 버튼 글 문구 키(`provider.ts`) — 이름에 조사가 붙는 문장이라 문장 전체가 키다 */
const LABEL_KEYS = { continue: 'provider.continueWith', login: 'provider.loginWith', signup: 'provider.signupWith' } as const;

const { t } = useI18n();
const api = useAuthApi();
const { providers, ensure } = useSocialProviders();
const redirecting = ref(false);

const list = computed(() => (providers.value ?? []).filter((p) => !props.only || p.id === props.only));

/** 서버가 준 이름(`p.name`)이 아니라 id 로 지금 언어의 이름을 고른다 — 모르는 제공자만 서버 이름을 쓴다 */
function label(p: { id: string; name: string }): string {
    return t(LABEL_KEYS[props.mode], UtilProvider.params(UtilProvider.name(t, p.id, p.name)));
}

function go(id: string) {
    redirecting.value = true; // 이동하는 동안 다른 버튼을 누르지 못하게
    api.startSocialLogin(id, props.next || undefined);
}

function onPageShow(e: PageTransitionEvent) {
    // 소셜 화면에서 "뒤로" 로 돌아오면(bfcache) 버튼이 잠긴 채 남지 않게 푼다
    if (e.persisted) redirecting.value = false;
}

onMounted(() => {
    void ensure();
    window.addEventListener('pageshow', onPageShow);
});
onBeforeUnmount(() => window.removeEventListener('pageshow', onPageShow));
</script>
