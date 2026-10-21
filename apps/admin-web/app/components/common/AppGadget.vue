<template>
    <!--
        화면의 카드 한 장(제목 + 내용) = 구획 — 화면 코드는 PrimeVue Card 를 직접 쓰지 않고 이 래퍼를 쓴다. 테두리는 셸의 선 토큰(`--line`).
        `anchor` 를 주면 이 구획의 주소(`id`)가 되고, **스스로 우측 구역 내비(`SectionNav`)에 등록한다**(`useSectionNav`) — 값을 고치고 저장하는 구획에만 준다.
        화면이 바뀌거나 구획이 사라지면 등록은 스스로 풀린다. 구획이 2개 미만인 화면은 내비가 숨는다. 구획 위쪽 여백은 `scroll-margin-top`(`--section-nav-offset`)이라 이동했을 때 제목이 가리지 않는다.
    -->
    <Card :id="anchor" class="rounded-xl! border border-(--line) shadow-none! [--p-card-body-padding:1.25rem]" :class="[anchor ? 'scroll-mt-(--section-nav-offset)' : '', fill ? 'h-full min-h-0 overflow-hidden' : '']" :pt="fillPt" :aria-labelledby="titleId" :data-app-section="anchor ? '' : undefined">
        <template #title>
            <div class="flex items-center gap-2">
                <h2 :id="titleId" class="text-sm font-medium" :tabindex="anchor ? -1 : undefined">{{ title }}</h2>
                <slot name="title-extra" />
                <div v-if="$slots.actions" class="ml-auto flex items-center gap-2">
                    <slot name="actions" />
                </div>
            </div>
        </template>
        <template #content>
            <slot />
        </template>
    </Card>
</template>

<script setup lang="ts">
interface Props {
    title: string;
    /** 구획의 주소(`id`, 소문자·숫자·하이픈, 화면 안 유일) — 있으면 우측 구역 내비에 스스로 등록하고 이동·현재 위치 표시에 쓴다. 없으면 `id` 를 걸지 않는다 */
    anchor?: string;
    /** `anchor` 가 있어도 구역 내비 목록에는 올리지 않는다(보조 구획) — 기본 true */
    nav?: boolean;
    /** 내비 항목에 쓸 짧은 이름 — 없으면 `title` */
    navLabel?: string;
    /** 이 구획에서 저장 전에 바뀐 칸 수 — 내비 항목에 점(2칸 이상이면 글자로 칸 수)으로 보인다. 화면 하나로 저장하는 화면이 구획마다 넘긴다 */
    changes?: number;
    /** 이 구획의 입력 오류 칸 수 — 내비 항목에 빨강 숫자로 보인다 */
    invalid?: number;
    /**
     * 부모가 정한 높이를 채우고, 제목 줄은 고정한 채 **내용만 카드 안에서 스크롤**한다(여러 카드가 한 행에서 같은 높이로 서는 화면 — 역할 관리).
     * 부모(그리드 칸)가 높이를 줘야 한다. 기본은 내용 높이만큼 자란다.
     */
    fill?: boolean;
}

const props = withDefaults(defineProps<Props>(), { anchor: undefined, nav: true, navLabel: undefined, changes: 0, invalid: 0, fill: false });

/** `fill` 일 때만 PrimeVue Card 안쪽(body·content)에 줄어들 수 있는 flex 칸과 스크롤을 건다 */
const fillPt = computed(() => (props.fill ? { body: { class: 'min-h-0 flex-1' }, content: { class: 'min-h-0 flex-1 overflow-y-auto' } } : undefined));

const titleId = useId();
const sectionNav = useSectionNav();

// 우측 구역 내비 등록 — 마운트된 뒤(화면 위치를 알 수 있을 때) 올리고, 이름·주소가 바뀌면 다시 올리고, 구획이 사라지면 푼다
let unregister: (() => void) | null = null;
let mounted = false;
function syncNav(): void {
    unregister?.();
    unregister = null;
    if (!mounted || !props.anchor || !props.nav) return;
    unregister = sectionNav.register({ key: titleId, anchor: props.anchor, label: props.navLabel ?? props.title, changes: props.changes, invalid: props.invalid });
}
onMounted(() => {
    mounted = true;
    syncNav();
});
watch(() => [props.anchor, props.nav, props.navLabel, props.title], syncNav);
// 바뀐·오류 칸 수만 바뀌면 다시 등록하지 않고 항목의 상태만 바꾼다(줄 세우기·관찰을 다시 하지 않는다)
watch(
    () => [props.changes, props.invalid],
    () => {
        if (unregister) sectionNav.setStatus(titleId, { changes: props.changes, invalid: props.invalid });
    },
);
onScopeDispose(() => {
    mounted = false;
    unregister?.();
    unregister = null;
});
</script>
