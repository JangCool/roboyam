import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { CacheAdminLinkView, CacheGadgetPlace, CacheKind, CacheMemberMode, CacheRefreshLogView, CacheServerRole, CacheSettingChangeView, CacheUsageKind, CacheUsageView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilAdminMenu from '~/utils/util-admin-menu';

/**
 * 캐시 관리 화면의 계산이 낀 문구 — 코드 값 → 문구 키, TTL 글자. `t` 를 첫 인자로 받는다(i18n.md 3절).
 */
export default class UtilCacheMessages {
    static kindLabel(t: Translate, kind: CacheKind): string {
        if (kind === 'PUBLIC_VIEW') return t('cache.kindPublicView');
        if (kind === 'GADGET') return t('cache.kindGadget');
        if (kind === 'SETTINGS_COPY') return t('cache.kindSettingsCopy');
        return t('cache.kindInternalRule');
    }

    static serverLabel(t: Translate, role: CacheServerRole): string {
        return role === 'API' ? t('cache.serverApi') : t('cache.serverAdmin');
    }

    static memberModeLabel(t: Translate, mode: CacheMemberMode): string {
        return mode === 'SHARED' ? t('cache.memberShared') : t('cache.memberOverlay');
    }

    static memberModeHelp(t: Translate, mode: CacheMemberMode): string {
        return mode === 'SHARED' ? t('cache.memberSharedHelp') : t('cache.memberOverlayHelp');
    }

    /** TTL 글자 — `20초` · `1분 30초` · `5분` · `1시간` */
    static ttlText(t: Translate, seconds: number): string {
        if (seconds >= 3600 && seconds % 3600 === 0) return t('cache.ttlHours', { n: seconds / 3600 });
        if (seconds >= 60 && seconds % 60 === 0) return t('cache.ttlMinutes', { n: seconds / 60 });
        if (seconds >= 60) return t('cache.ttlMinSec', { m: Math.floor(seconds / 60), s: seconds % 60 });
        return t('cache.ttlSeconds', { n: seconds });
    }

    /** 허용 범위 안내 — `5초 ~ 1시간` */
    static rangeText(t: Translate, min: number, max: number): string {
        return t('cache.ttlRange', { min: UtilCacheMessages.ttlText(t, min), max: UtilCacheMessages.ttlText(t, max) });
    }

    /** 갱신 기록의 계기 — 수동 · 수동(전체 갱신) · 자동(저장) */
    static triggerLabel(t: Translate, log: Pick<CacheRefreshLogView, 'trigger' | 'all'>): string {
        if (log.trigger === 'AUTO_SAVE') return t('cache.triggerAutoSave');
        return log.all ? t('cache.triggerManualAll') : t('cache.triggerManual');
    }

    /** 가젯을 놓는 곳 — 홈 · 가젯 페이지 · 게시판 레이아웃 */
    static placeLabel(t: Translate, place: CacheGadgetPlace): string {
        if (place === 'HOME') return t('cache.placeHome');
        if (place === 'GADGET_PAGE') return t('cache.placeGadgetPage');
        return t('cache.placeBoardLayout');
    }

    /** 미리보기에서 찾은 영역 수 안내 — 아직 모름 · 없음 · N곳 */
    static areaCount(t: Translate, count: number | null): string {
        if (count == null) return t('cache.previewCounting');
        if (count === 0) return t('cache.previewAreaMissing');
        return t('cache.previewAreaFound', { n: count });
    }

    /** 쓰이는 곳의 종류 — 화면 영역 · 동작 · 서버 내부 */
    static usageKindLabel(t: Translate, kind: CacheUsageKind): string {
        if (kind === 'AREA') return t('cache.usageArea');
        if (kind === 'ACTION') return t('cache.usageAction');
        return t('cache.usageServer');
    }

    /**
     * 목록 줄의 「쓰이는 곳」 한 줄 — 화면 영역·동작 이름 둘 + 「외 N」, 화면이 없으면 서버 내부 이름. 없으면 빈 글자.
     * @returns `쓰이는 곳: 내 포인트 · 알림 화면 외 1` 꼴.
     */
    static usageSummary(t: Translate, usages: readonly CacheUsageView[]): string {
        const screen = usages.filter((u) => u.kind !== 'SERVER');
        const list = screen.length ? screen : usages;
        if (!list.length) return '';
        const names = list.slice(0, 2).map((u) => u.label).join(' · ');
        const more = list.length - 2;
        const text = more > 0 ? t('cache.usageMore', { names, n: more }) : names;
        return screen.length ? t('cache.usageSummary', { text }) : t('cache.usageSummaryServer', { text });
    }

    /** 관련 관리 화면 이름 — 관리자 메뉴에 같은 주소가 있으면 메뉴 이름(지금 언어), 없으면 서버 글 */
    static adminLinkLabel(t: Translate, link: CacheAdminLinkView): string {
        const item = UtilAdminMenu.allItems().find((i) => i.to === link.path);
        return item?.labelKey ? t(item.labelKey) : link.label;
    }

    /** 설정 변경 이력 한 줄 — `20초 → 30초` · `이 캐시 끔` · `전체 캐시 켬` */
    static settingChangeText(t: Translate, change: CacheSettingChangeView): string {
        if (change.kind === 'TTL') return `${UtilCacheMessages.ttlText(t, change.fromSeconds ?? 0)} → ${UtilCacheMessages.ttlText(t, change.toSeconds ?? 0)}`;
        if (change.kind === 'GLOBAL_ENABLED') return change.enabled ? t('cache.changeGlobalOn') : t('cache.changeGlobalOff');
        return change.enabled ? t('cache.changeItemOn') : t('cache.changeItemOff');
    }

    /** 대기 중인 서버 — `api-2 대기` */
    static pendingNodes(t: Translate, nodeIds: readonly string[]): string {
        return t('cache.pendingNodes', { nodes: nodeIds.join(', ') });
    }
}
