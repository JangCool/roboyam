<template>
    <!--
        OTP 등록 QR — 서버가 준 otpauth 주소를 `uqr` 의 `renderSVG` 로 SVG 글자로 만들어 그린다(다른 곳에서 QR 을 만들지 않는다).
        ⚠ `v-html` 은 이 라이브러리 출력에만 쓴다 — 입력은 서버가 준 otpauth 주소이고, 출력은 사각형 경로뿐인 SVG 다(주소 글자가 SVG 에 그대로 들어가지 않는다).
        다크 모드에서도 흰 바탕·검은 점을 둔다(앱이 읽으려면 대비가 필요하다). 화면 낭독기에는 그림 이름만 읽히고, 같은 내용은 옆의 비밀키 글자로 준다.
    -->
    <div class="shrink-0 [&_svg]:block [&_svg]:size-full overflow-hidden rounded-lg border border-(--line) bg-white p-2" :style="{ width: `${size}px`, height: `${size}px` }" role="img" :aria-label="label" v-html="svg" />
</template>

<script setup lang="ts">
import { renderSVG } from 'uqr';

interface Props {
    /** `otpauth://totp/…` — 서버가 준 값 그대로 */
    uri: string;
    /** 그림 이름(화면 낭독기) */
    label: string;
    /** 한 변(px), 바깥 테두리·여백 포함 */
    size?: number;
}

const props = withDefaults(defineProps<Props>(), { size: 180 });

/** otpauth 주소가 아니면 그리지 않는다(엉뚱한 값을 QR 로 만들지 않게) */
const svg = computed(() => (props.uri.startsWith('otpauth://') ? renderSVG(props.uri, { ecc: 'M', border: 0 }) : ''));
</script>

