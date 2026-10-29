<template>
    <!--
        홈 가젯의 공통 껍데기(community `GadgetFrame`) — 가젯 꾸미기(`style`)를 그린다: 머리 모양(막대·밑줄·색 채움·장식 없음·머리 숨김) · 아이콘 · 제목 크기·색 · 부제 · 「더보기」 ·
        모바일 접기 · 카드 모양(테마 카드·평평·테두리 강조·옅은 색 바탕) · 비었을 때(영역 유지 + 안내/빈 틀). 내용은 기본 슬롯이다.
        모양 두 가지(`useHomeGadgetLook` — 템플릿이 정한다):
          · 기본형 `classic` — 구역 이름 줄(아래 선) + 내용 + 아래 가운데 「더보기 ›」 줄. 평평하지 않은 가젯은 테두리 카드(`gadget-card`).
          · 카드형 `card` — 카드(`gadget-card`, 값은 테마 역할 변수) + 높이 48 머리(제목 · 오른쪽 「더보기 ›」).
        제목은 `h2`(페이지 `h1` 은 사이트 이름) — 머리를 숨긴 가젯은 영역 이름(`aria-label`)으로만 읽힌다. 색은 컴포넌트에 박지 않는다(고른 색은 `--gx-*` 변수).
        클래스 `gadget-icon`(머리 아이콘)·`gadget-more`(「더보기」)·`gadget-head`(머리 줄)는 테마 CSS 가 모양을 바꾸는 고리다 — 데스크톱에는 기본 규칙이 없어 다른 테마에서는 아무것도 바뀌지 않는다(로보얌: 옅은 주 색상 칩 · 작은 글자, `main.css`).
        한 열(모바일, 격자 `stack`)에서는 머리 「더보기 ›」의 터치 영역을 44px 로 넓힌다(`main.css` `[data-grid-mode='stack'] .gadget-head .gadget-more` — 모든 테마).
    -->
    <section
        :class="rootClass"
        :style="rootStyle"
        :aria-labelledby="showHeader ? headingId : undefined"
        :aria-label="showHeader ? undefined : gadget.title || undefined"
        :data-gadget="gadget.type"
        :data-gadget-id="gadget.id"
        :data-look="look"
        data-testid="home-gadget"
    >
        <div v-if="showHeader" :class="['gadget-head', headerClass]" :style="headerStyle" data-testid="home-gadget-header">
            <i v-if="style.headerStyle === 'BAR'" class="gadget-bar block h-4 w-1 shrink-0 rounded-sm" aria-hidden="true" />
            <i v-if="style.icon" :class="['pi', style.icon, 'gadget-icon shrink-0 text-sm']" :style="titleColorStyle" aria-hidden="true" />
            <h2 :id="headingId" class="m-0 min-w-0 truncate font-bold" :class="UtilHomeGadget.titleSizeClass(style.titleSize, look)" :style="titleColorStyle" data-testid="home-gadget-title">{{ gadget.title }}</h2>
            <span v-if="style.subtitle" class="min-w-0 shrink truncate text-xs font-normal" :class="style.headerStyle === 'FILLED' ? 'opacity-80' : 'text-muted-color'">{{ style.subtitle }}</span>
            <span class="flex-1" />
            <!-- 카드형은 「더보기」가 머리 오른쪽 -->
            <NuxtLink
                v-if="more && look === 'card'"
                :to="more.to"
                class="gadget-more shrink-0 whitespace-nowrap rounded text-[0.8125rem] font-normal hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                :class="style.headerStyle === 'FILLED' ? '' : 'text-muted-color hover:text-color'"
                data-testid="home-gadget-more"
            >
                {{ moreLabel }} ›
            </NuxtLink>
            <button
                v-if="foldable"
                type="button"
                class="grid size-7 shrink-0 place-items-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :class="style.headerStyle === 'FILLED' ? '' : 'text-muted-color'"
                :aria-expanded="open"
                :aria-controls="bodyId"
                :aria-label="UtilHomeGadgetMessages.foldLabel(t, gadget.title, open)"
                data-testid="home-gadget-fold"
                @click="open = !open"
            >
                <i class="pi pi-chevron-down text-xs transition-transform motion-reduce:transition-none" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
            </button>
        </div>

        <!-- 내용 — 접어도 DOM 은 남긴다(안쪽 상태를 잃지 않게) -->
        <div v-show="bodyShown" :id="bodyId" class="@container min-w-0">
            <template v-if="showEmpty">
                <!-- 아이콘을 글 위 한 줄로 — `.pi` 가 inline-block 이라 `block` 클래스로는 줄이 바뀌지 않아 세로 flex 로 쌓는다 -->
                <p v-if="style.empty.message" class="m-0 flex flex-col items-center px-1 py-8 text-center text-sm text-muted-color" data-testid="home-gadget-empty">
                    <i :class="[emptyIcon, 'mb-2 text-xl']" aria-hidden="true" />{{ UtilHomeGadgetMessages.emptyTitle(t, gadget) }}
                </p>
                <!-- 안내를 끈 경우 — 틀은 남기고 높이만 조금 가진 빈 칸 -->
                <div v-else class="min-h-20" aria-hidden="true" data-testid="home-gadget-empty-frame" />
            </template>
            <slot v-else />
        </div>

        <!-- 기본형의 「더보기」 줄 -->
        <NuxtLink
            v-if="more && look === 'classic' && bodyShown && !showEmpty"
            :to="more.to"
            class="mt-1 block border-t border-surface py-2.5 text-center text-sm text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800"
            data-testid="home-gadget-more"
        >
            {{ moreLabel }}<i class="pi pi-angle-right ml-0.5 align-middle text-xs" aria-hidden="true" />
        </NuxtLink>
        <!-- 카드형인데 머리를 숨긴 가젯 — 「더보기」를 내용 아래 오른쪽에 -->
        <NuxtLink
            v-else-if="more && look === 'card' && !showHeader && bodyShown && !showEmpty"
            :to="more.to"
            class="gadget-more block rounded pt-2 text-right text-[0.8125rem] text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
            data-testid="home-gadget-more"
        >
            {{ moreLabel }} ›
        </NuxtLink>
    </section>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilHomeGadgetMessages from '~/utils/util-home-gadget-messages';

interface Props {
    gadget: HomeGadget;
    /** 비었을 때 안내의 아이콘(PrimeIcons 클래스) */
    emptyIcon?: string;
    /** 서버의 `gadget.empty` 대신 화면이 판정한 「비었다」 — 주지 않으면(null) `gadget.empty` */
    empty?: boolean | null;
}

const props = withDefaults(defineProps<Props>(), { emptyIcon: 'pi pi-inbox', empty: null });

defineSlots<{ default?(): unknown }>();

const { t } = useI18n();
const look = useHomeGadgetLook();
const { wide } = useHomeGadgetShared();

const uid = useId();
const headingId = `${uid}-title`;
const bodyId = `${uid}-body`;

const style = computed(() => props.gadget.style);
const showHeader = computed(() => style.value.headerStyle !== 'HIDDEN');
/** 잠긴 가젯에는 「더보기」가 없다(서버도 비운다) */
const more = computed(() => (props.gadget.locked ? null : props.gadget.more));
const moreLabel = computed(() => more.value?.label ?? t('home.more'));

/** 접을 수 있는가 — 모바일 폭에서만(데스크톱은 늘 펼침) */
const foldable = computed(() => !wide.value && UtilHomeGadget.foldable(style.value));
const open = ref(props.gadget.style.mobileFold !== 'CLOSED');
watch(
    () => props.gadget.id,
    () => {
        open.value = props.gadget.style.mobileFold !== 'CLOSED';
    },
);
const bodyShown = computed(() => !foldable.value || open.value);

/** 비었는데 영역 유지 — 머리·틀은 그대로, 내용 자리에 안내 또는 빈 틀(영역 유지가 꺼져 있으면 템플릿이 이미 걸렀다) */
const showEmpty = computed(() => !props.gadget.locked && (props.empty ?? props.gadget.empty) && style.value.empty.keep);

/** 제목·아이콘 글자색 — 색을 골랐을 때만(채움 머리는 채움 위 글자색을 쓴다) */
const titleColorStyle = computed(() => (style.value.titleColor !== null && style.value.headerStyle !== 'FILLED' ? { color: 'var(--gx-c)' } : undefined));

const rootStyle = computed(() => {
    const vars: Record<string, string> = UtilHomeGadget.frameVars(style.value);
    const accent = 'var(--gx-card-c, var(--p-primary-color))';
    if (style.value.cardLook === 'OUTLINE') {
        vars.borderColor = accent;
        vars.borderWidth = '2px';
    }
    if (style.value.cardLook === 'TINT') vars.backgroundColor = `color-mix(in srgb, ${accent} 8%, var(--gadget-card-bg))`;
    return vars;
});

/** 카드 — 평평한 가젯만 카드 없이. 나머지는 테마가 값을 채우는 `gadget-card` */
const rootClass = computed(() => (style.value.cardLook === 'FLAT' ? 'min-w-0' : 'gadget-card'));

const headerClass = computed(() => {
    const h = style.value.headerStyle;
    const base = 'flex min-w-0 items-center gap-1.5';
    if (look === 'card') {
        if (h === 'FILLED') return `${base} my-1.5 h-10 rounded-lg px-3`;
        return `${base} h-11 md:h-12 ${h === 'UNDERLINE' ? 'border-b-2' : ''}`;
    }
    if (h === 'FILLED') return `${base} mb-1 rounded-md px-3 py-1.5`;
    if (h === 'UNDERLINE') return `${base} border-b-2 pb-2`;
    return `${base} border-b border-surface pb-2`;
});

const headerStyle = computed(() => {
    const h = style.value.headerStyle;
    if (h === 'UNDERLINE') return { borderBottomColor: 'var(--gx-c, var(--p-primary-color))' };
    if (h === 'FILLED') return { background: 'var(--gx-c, var(--p-primary-color))', color: 'var(--gx-on, var(--p-primary-contrast-color))' };
    return undefined;
});
</script>
