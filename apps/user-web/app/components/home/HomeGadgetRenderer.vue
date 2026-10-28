<template>
    <!--
        가젯 하나를 레지스트리(`UtilHomeGadgetRegistry`)에서 찾아 그린다 — 뿌리 요소를 따로 만들지 않는다(격자 칸 `HomeGadgetGrid` 가 가젯 요소를 바로 자식으로 보고 칸 높이로 늘리고, 그리지 않으면 칸을 비운다). 모르는 종류는 그리지 않는다.
        잠긴 가젯(`locked`)은 종류 컴포넌트 대신 잠긴 모습만. 한 가젯이 그리다 터져도 홈 전체가 죽지 않게 그 가젯만 빼고 콘솔에 남긴다.
    -->
    <HomeGadgetLocked v-if="gadget.locked && comp && !failed" :gadget="gadget" />
    <component :is="comp" v-else-if="comp && !gadget.locked && !failed && !UtilHomeGadget.hiddenWhenEmpty(gadget)" :gadget="gadget" />
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilHomeGadgetRegistry from '~/utils/util-home-gadget-registry';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const comp = computed(() => UtilHomeGadgetRegistry.componentOf(props.gadget.type));
const failed = ref(false);

watch(
    () => props.gadget.id,
    () => {
        failed.value = false;
    },
);

onErrorCaptured((error) => {
    failed.value = true;
    console.error(`[home-gadget] ${props.gadget.type}(${props.gadget.id}) 를 그리지 못했어요`, error);
    return false;
});
</script>
