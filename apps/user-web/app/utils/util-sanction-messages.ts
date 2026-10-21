import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';
import type { DisplayTime } from '#layers/display-time/utils/util-time';

/** 전역 배너에 쓸 문구 — 굵은 첫 문장 · 이어지는 말 · 해제 예정(영구 정지에는 없다) */
export interface SanctionBannerCopy {
    title: string;
    text: string;
    release: string | null;
}

/**
 * 제재 화면 문구 중 전역 배너(`RestrictionBanner`)가 쓰는 것만 남긴 단순 버전 — 원본(community)은 제재 안내·이의제기·알림 문구 전체를 담은 큰 파일이다.
 * **문구 본문은 `i18n/locales/{ko,en}/sanction.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다(상수 문구인 "자세히 보기"는 컴포넌트가 `t('sanction.bannerLink')` 를 바로 부른다).
 * 해제 예정일은 표시 값(`useDisplayTime`)의 시간대·날짜 형식으로 그린다(`time.dateDay`) — 날짜 글자는 그대로 두고 문장만 키로 감싼다.
 */
export default class UtilSanctionMessages {
    /**
     * 전역 배너의 문구 — 정지 중이면 "이용이 정지된 계정이에요. 읽기만 할 수 있어요. 해제 예정 2026.10.02(금)", 영구 정지면 "계정이 영구 정지됐어요. 읽기만 할 수 있어요."(해제 예정 없음). 그 밖에는 null(배너를 안 보인다)
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @param {DisplayTime} time - 표시 시간대·형식(`useDisplayTime()`).
     * @returns {SanctionBannerCopy | null} 배너 문구. 보이지 않을 때는 null.
     */
    static banner(t: Translate, restriction: Restriction | null | undefined, time: DisplayTime): SanctionBannerCopy | null {
        if (restriction?.status === 'BANNED') return { title: t('sanction.bannedTitle'), text: t('sanction.readOnly'), release: null };
        if (restriction?.status === 'SUSPENDED') {
            const day = time.dateDay(restriction.until);
            return { title: t('sanction.suspendedTitle'), text: t('sanction.readOnly'), release: day ? t('sanction.release', { date: day }) : null };
        }
        return null;
    }
}
