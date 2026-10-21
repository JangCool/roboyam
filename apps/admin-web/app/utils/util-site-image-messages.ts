import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteImageDims, SiteImageKind, SiteImageProblem } from '~/utils/util-site-image';
import UtilSiteImage from '~/utils/util-site-image';

/**
 * 사이트 아이콘·링크 미리보기 이미지 줄에서 **계산이 낀 문구** — 문구 본문은 `i18n/locales/{ko,en}/site.ts` 에 있고 여기는 "어느 키를 고를지"와 숫자 조합만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`). 서버가 거절하면 서버 `message` 를 그대로 보이므로 서버 오류 문구는 여기에 없다.
 */

/** 소수 한 자리 MB 올림 — 한도를 막 넘은 파일이 "지금 2.0MB" 로 보이지 않게 한다. 로캘에 기대지 않는다 */
const megabytes = (bytes: number): string => (Math.ceil((bytes / (1024 * 1024)) * 10) / 10).toFixed(1);

export default class UtilSiteImageMessages {
    /**
     * 올리기 전에 화면이 먼저 거른 까닭을 문구로 — 서버가 같은 까닭으로 거절할 때와 같은 말이다. 파일 이름은 문구에 넣지 않고 규칙과 숫자만 말한다.
     * @param {Translate} t - 번역 함수.
     * @param {SiteImageKind} kind - 어느 줄인가(아이콘 · 링크 미리보기 이미지).
     * @param {SiteImageProblem} problem - 걸린 까닭.
     * @param {{ dims?: SiteImageDims | null; size?: number }} [now] - 지금 파일의 가로·세로·용량(알면).
     * @returns {string} 줄 아래에 보일 한 줄.
     */
    static problem(t: Translate, kind: SiteImageKind, problem: SiteImageProblem, now: { dims?: SiteImageDims | null; size?: number } = {}): string {
        const width = now.dims?.width ?? 0;
        const height = now.dims?.height ?? 0;
        switch (problem) {
            case 'EMPTY':
                return t('site.imageEmpty');
            case 'FORMAT':
                return kind === 'icon' ? t('site.iconFormat') : t('site.ogFormat');
            case 'SIZE':
                return t('site.imageSize', { max: UtilSiteImage.MAX_BYTES / (1024 * 1024), now: megabytes(now.size ?? 0) });
            case 'CORRUPT':
                return t('site.imageCorrupt');
            case 'TOO_BIG':
                return t('site.imageTooBig', { max: UtilSiteImage.MAX_SIDE, width, height });
            case 'NOT_SQUARE':
                return t('site.iconNotSquare', { width, height });
            case 'WRONG_RATIO':
                return t('site.ogWrongRatio', { width, height, targetWidth: UtilSiteImage.OG_WIDTH, targetHeight: UtilSiteImage.OG_HEIGHT });
            case 'TOO_SMALL':
                return kind === 'icon' ? t('site.iconTooSmall', { min: UtilSiteImage.ICON_MIN_SIDE, width, height }) : t('site.ogTooSmall', { min: UtilSiteImage.OG_MIN_WIDTH, width, height });
        }
    }

    /**
     * 사이트 아이콘의 규칙 한 줄.
     * @param {Translate} t - 번역 함수.
     * @returns {string} 줄 아래 힌트.
     */
    static iconHint(t: Translate): string {
        return t('site.iconHint', { min: UtilSiteImage.ICON_MIN_SIDE, max: UtilSiteImage.MAX_SIDE, mb: UtilSiteImage.MAX_BYTES / (1024 * 1024) });
    }

    /**
     * 링크 미리보기 이미지의 규칙 한 줄.
     * @param {Translate} t - 번역 함수.
     * @returns {string} 줄 아래 힌트.
     */
    static ogHint(t: Translate): string {
        return t('site.ogHint', { width: UtilSiteImage.OG_WIDTH, height: UtilSiteImage.OG_HEIGHT, min: UtilSiteImage.OG_MIN_WIDTH, max: UtilSiteImage.MAX_SIDE, mb: UtilSiteImage.MAX_BYTES / (1024 * 1024) });
    }
}
