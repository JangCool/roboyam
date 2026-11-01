/**
 * 핫딜 설정(`deal_settings` 한 행) — 03-hotdeal 1-1 「설정」 중 회원 핫딜 1차 칸만. **서버에 아직 없다(목업)**.
 * 범위·처음 값은 `UtilHotdeal.SETTINGS`(서버와 같은 규칙). 감사 `HOTDEAL_SETTINGS_UPDATE`.
 * 종료 요청 보상 포인트는 이미 포인트 정책(`point_settings.dealEndRequestPoints` — 포인트 › 포인트 정책)에 있어 여기서는 읽기만 한다(두 곳에서 고치지 않게 — 목업 가정).
 */
export interface HotdealSettingsValues {
    /** 의심으로 바꾸는 서로 다른 회원의 종료 요청 수 1~20(기본 3) */
    endRequestThreshold: number;
    /** 의심 뒤 자동 종료까지 유예 시간 0~168(기본 12, 0 = 자동 종료 안 함) */
    endGraceHours: number;
    /** 회원 한 명의 하루 종료 요청 한도 1~100(기본 20) */
    endRequestDailyLimit: number;
    /** 위장 홍보 후보 — 같은 판매처 글 수 0~50(기본 3, 0 = 끔) */
    suspicionMinPosts: number;
    /** 위장 홍보 후보 — 세는 기간 1~90일(기본 7) */
    suspicionWindowDays: number;
}

export type HotdealSettingsField = keyof HotdealSettingsValues;

/** `GET /api/admin/hotdeal-settings` · `PUT` 응답 */
export interface HotdealSettingsView extends HotdealSettingsValues {
    /** 요청자 보상 포인트 — 포인트 정책의 값(읽기 전용) */
    rewardPoints: number;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/hotdeal-settings` — 400 `fields.<칸>`(범위) */
export type HotdealSettingsRequest = HotdealSettingsValues;
