import type { VisitKind } from '#layers/analytics/utils/util-visit-signal';

/** 앱이 넘기는 것 — 레이어는 앱을 모르므로 켤지·뺄지·화면 종류는 앱이 답한다 */
export interface VisitTrackingOptions {
    /**
     * 지금 신호를 보낼까 — 앱이 사이트 정보의 수집 스위치(`UtilVisitSignal.isCollecting`)로 답한다. 아직 받는 중이면 받을 때까지 기다려도 된다(신호 차례는 지킨다).
     * 화면이 바뀔 때마다 묻는다 — 앱이 캐시한 값을 돌려주면 된다.
     */
    collecting: () => boolean | Promise<boolean>;
    /** 이 창은 보내지 않는다(관리자 미리보기 등) — 화면이 바뀔 때마다 묻는다 */
    excluded?: () => boolean;
    /**
     * 화면 종류 — 앱이 자기 라우트 표로 정한다.
     * @param path - 쿼리·해시를 뺀 라우트 경로(인코딩된 그대로).
     * @param name - 라우트 이름(없으면 null).
     */
    classify: (path: string, name: string | null) => VisitKind;
}
