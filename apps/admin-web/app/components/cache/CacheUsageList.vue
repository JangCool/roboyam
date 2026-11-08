<template>
    <!--
        캐시가 쓰이는 곳 + 관련 관리 화면 — 상세 서랍의 「쓰이는 곳」 구획과 미리보기 대화상자(서버 내부만인 캐시의 설명)가 같은 모양을 쓴다.
        줄마다: 종류 태그(화면 영역 · 동작 · 서버 내부) · 이름 · 공개 사이트 경로 · 「로그인 필요」 · 한 줄 설명 · (화면 영역·동작이고 경로나 예시 경로 규칙이 있으면) 「미리보기에서 보기」 → `preview(usage)` — 예시 줄은 틀(`/posts/{id}`)을 보이고 번호는 미리보기가 고른다.
        아래에 관련 관리 화면 링크(관리자 메뉴에 같은 주소가 있으면 메뉴 이름). 서버 호출은 없다.
    -->
    <div class="flex flex-col gap-3">
        <ul v-if="usages.length" class="m-0 flex list-none flex-col gap-2 p-0">
            <li v-for="(u, i) in usages" :key="`${u.kind}-${i}`" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <Tag :value="UtilCacheMessages.usageKindLabel(t, u.kind)" :icon="ICON[u.kind]" :severity="SEVERITY[u.kind]" class="cache-usage-tag" />
                    <span class="text-[13px] font-medium">{{ u.label }}</span>
                    <code v-if="u.path" class="text-[11px] text-muted-color">{{ u.path }}</code>
                    <code v-else-if="u.sample" v-tooltip.top="t('cache.sampleTip')" class="text-[11px] text-muted-color">{{ u.sample.template }} · {{ t('cache.sampleWord') }}</code>
                    <span v-if="u.loginRequired" class="inline-flex items-center gap-1 text-[11px] text-muted-color"><i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ t('cache.loginRequired') }}</span>
                    <Button
                        v-if="previewable && UtilCache.openable(u)"
                        :label="t('cache.viewInPreview')"
                        :aria-label="t('cache.viewInPreviewLabel', { name: u.label })"
                        icon="pi pi-eye"
                        size="small"
                        severity="secondary"
                        variant="text"
                        class="ml-auto"
                        @click="emit('preview', u)"
                    />
                </div>
                <p class="m-0 text-[12px] leading-relaxed text-muted-color">{{ u.description }}</p>
            </li>
        </ul>
        <p v-else class="m-0 text-[12px] text-muted-color">{{ t('cache.usagesNone') }}</p>

        <div v-if="adminLinks.length" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px]">
            <span class="text-muted-color">{{ t('cache.adminLinks') }}</span>
            <NuxtLink v-for="link in adminLinks" :key="link.path" :to="link.path" class="inline-flex items-center gap-1 text-primary hover:underline">
                <i class="pi pi-external-link text-[10px]" aria-hidden="true" />
                {{ UtilCacheMessages.adminLinkLabel(t, link) }}
            </NuxtLink>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { CacheAdminLinkView, CacheUsageKind, CacheUsageView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

interface Props {
    usages?: CacheUsageView[];
    adminLinks?: CacheAdminLinkView[];
    /** 화면 영역·동작 줄에 「미리보기에서 보기」를 단다 */
    previewable?: boolean;
}

withDefaults(defineProps<Props>(), { usages: () => [], adminLinks: () => [], previewable: false });
const emit = defineEmits<{ preview: [usage: CacheUsageView] }>();

const { t } = useI18n();

const ICON: Record<CacheUsageKind, string> = { AREA: 'pi pi-eye', ACTION: 'pi pi-bolt', SERVER: 'pi pi-server' };
const SEVERITY: Record<CacheUsageKind, 'info' | 'contrast' | 'secondary'> = { AREA: 'info', ACTION: 'contrast', SERVER: 'secondary' };
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(components.md 2-4) */
.cache-usage-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
