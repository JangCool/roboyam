/**
 * 대시보드 가젯 숫자·목록 — `GET /api/admin/dashboard/gadgets?types=A,B,C&limit.A=5&days.B=30` 의 종류별 응답 모양(서버 `dto/response/gadget/Dashboard<Kind>View`).
 * 처리할 일(`TODO`)·커머스(`COMMERCE_TODO`)는 이미 있는 API(신청 건·사업자·핫딜 목록의 `counts`, `/api/admin/commerce/dashboard`)를 쓰고, 메모·바로가기는 서버를 부르지 않아 여기 없다.
 *
 * 서버 부담 원칙: 숫자형 = 상태별 COUNT 한 번(인덱스로) · 목록형 = 최대 `limit`(5|10)행 · 추이형 = 배치가 만든 **일별 집계**만 읽음(원본 GROUP BY 없음).
 * 권한이 없는 종류는 묶음에서 `null`, 한 종류만 부를 때는 `403` 이다. 날짜는 서비스 날짜(`YYYY-MM-DD`, 한국 시간), 시각은 ISO-8601.
 */
import type { DashboardGadgetType } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

/** 신고 — 대기·검토 중 건수 + 오래된 대기 */
export interface DashboardReports {
    pending: number;
    reviewing: number;
    oldest: {
        id: string;
        /** 사유 코드(`board.reason.*` — SPAM_AD · ABUSE · OBSCENE · ILLEGAL_FILMING · RIGHTS · OTHER) */
        reason: string;
        /** 대상 종류(`report.targetType.*` — POST · COMMENT · GROUP) */
        targetType: string;
        reportedAt: string;
        /** 자동 숨김된 대상인가 */
        autoHidden: boolean;
    }[];
}

/** 하루 숫자 하나 */
export interface DashboardDayCount {
    date: string;
    count: number;
}

/** 글·댓글 활동 — 오늘·어제 + 7일 일별(오래된 차례, 오늘 포함) */
export interface DashboardPostActivity {
    postsToday: number;
    postsYesterday: number;
    commentsToday: number;
    commentsYesterday: number;
    days: { date: string; posts: number; comments: number }[];
}

/** 지금 인기글 */
export interface DashboardPopularNow {
    /** 게시판 이름은 `boardName`·`boardNameEn`(영어 이름, 없으면 null) — 화면은 `localizedName` */
    items: { postId: string; boardName: string; boardNameEn: string | null; title: string; likes: number }[];
}

/** 소모임 개설 신청 */
export interface DashboardGroupRequests {
    pending: number;
    recent: { id: string; name: string; requesterNickname: string; requestedAt: string }[];
}

/** 회원 숫자 — 휴면 기능이 없어 휴면 예정 칸은 없다(사용자 결정 2026-11-03) */
export interface DashboardMemberStats {
    joinedToday: number;
    withdrawnToday: number;
    /** 이용 제한(정지) 중 */
    suspended: number;
    /** 탈퇴 대기 — 탈퇴를 신청해 남은 일(배송·환불 등)이 끝나기를 기다리는 예약 */
    withdrawing: number;
}

/** 가입 추이(오래된 차례, 오늘 포함) */
export interface DashboardSignupTrend {
    days: DashboardDayCount[];
}

/** 닉네임 이미지 심사 대기 */
export interface DashboardNicknameImages {
    pending: number;
    /** 오래된 차례 몇 장(썸네일) */
    recent: { id: string; nickname: string; imageUrl: string }[];
}

/** 핫딜 현황 */
export interface DashboardHotdealStatus {
    running: number;
    registeredToday: number;
    endingToday: number;
    /** 확인 필요(`FLAGGED`) */
    flagged: number;
}

/** 노출·클릭 */
export interface DashboardAdCounts {
    impressions: number;
    clicks: number;
}

/** 광고 성과 요약 — 어제·7일 합계 + 7일 일별(오래된 차례, 오늘 제외) */
export interface DashboardAdPerformance {
    yesterday: DashboardAdCounts;
    week: DashboardAdCounts;
    days: (DashboardAdCounts & { date: string })[];
}

/** 이번 주 시작·종료 예정 광고(시각 차례, 최대 8) */
export interface DashboardAdSchedule {
    /** `placementName` = 광고 유형 코드(`bizApplication.adType.*` — WIDE_BANNER 등) */
    items: { id: string; title: string; placementName: string; kind: 'START' | 'END'; at: string }[];
}

/** 모집 중 공구(마감 임박 차례) */
export interface DashboardGroupBuyProgress {
    items: { id: string; title: string; joined: number; goal: number; endsAt: string }[];
}

/** 답변 대기 공구 Q&A(오래된 차례) */
export interface DashboardGroupBuyQna {
    pending: number;
    oldest: { id: string; groupBuyTitle: string; question: string; askedAt: string }[];
}

/** 결제·환불 합계(원) */
export interface DashboardSalesPeriod {
    paid: number;
    paidCount: number;
    refunded: number;
    refundedCount: number;
}

/** 매출 요약 — 오늘·어제·이번 달·지난달 같은 날까지 */
export interface DashboardSalesSummary {
    today: DashboardSalesPeriod;
    yesterday: DashboardSalesPeriod;
    month: DashboardSalesPeriod;
    lastMonth: DashboardSalesPeriod;
}

/** 대사 상태 */
export interface DashboardReconcile {
    /** 최근 대사일(없으면 null) */
    lastDate: string | null;
    status: 'MATCHED' | 'MISMATCHED' | 'PENDING';
    mismatched: number;
    /** 최근 24시간 웹훅 처리 실패 */
    webhookFailures: number;
}

/** 포인트 요약 */
export interface DashboardPointSummary {
    grantedToday: number;
    usedToday: number;
    /** 7일 안에 만료될 포인트 */
    expiringSoon: number;
    /** 환불 승인 대기 — 승인 권한(`point.refund.approve`)이 없으면 null */
    refundPending: number | null;
}

/** 포인트 상점 */
export interface DashboardPointShop {
    purchasesToday: number;
    /** 남은 수량 적은 품목(적은 차례) */
    lowStock: { id: string; name: string; nameEn: string | null; stock: number }[];
}

/** 배치 상태 — 최근 24시간 실패 + 실행 중 */
export interface DashboardBatchHealth {
    failed: { id: string; name: string; failedAt: string; message: string }[];
    running: { id: string; name: string; startedAt: string }[];
}

/** 최근 운영자 활동(최근 차례) — `summary` 는 서버가 요청 언어로 만든 한 줄 */
export interface DashboardAuditRecent {
    items: { id: string; actorNickname: string; summary: string; at: string }[];
}

/** 운영자 보안 */
export interface DashboardOperatorSecurity {
    total: number;
    locked: number;
    mfaMissing: number;
}

/** 방문 현황 — 오늘 방문자·페이지뷰 · 지금 접속(최근 5분) · 어제 같은 시각까지 + 오늘 시간대별(0시부터 지금 시각까지, 시간별 집계) */
export interface DashboardVisitors {
    visitorsToday: number;
    pageViewsToday: number;
    activeNow: number;
    /** 어제 같은 시각까지의 방문자 */
    visitorsYesterdaySameTime: number;
    pageViewsYesterdaySameTime: number;
    hours: { hour: number; visitors: number }[];
    /**
     * 이번 주(월요일부터)·이번 달 순방문자(방문자, 중복 없이 — 방문 통계 3단계 결정 ④). 세기 시작 전이면 null, 옛 서버는 칸이 없다(그러면 줄을 숨긴다).
     * 서버가 준 값 그대로 쓴다(`uniques` 의 방문자 `week.current.count`·`month.current.count` 와 같은 숫자).
     */
    visitorsThisWeek?: number | null;
    visitorsThisMonth?: number | null;
}

/** 오늘·내일 일정 항목 종류 — 항목마다 그 종류 조회 권한이 있을 때만 서버가 준다 */
export type DashboardScheduleKind = 'AD_START' | 'AD_END' | 'GROUPBUY_CLOSE' | 'POINTSHOP_START' | 'POINTSHOP_END' | 'BATCH';

/** 오늘·내일 일정(시각 차례, 최대 10) — `titleEn` 은 영어 이름이 있는 항목(포인트 상점 품목)만, 아니면 null */
export interface DashboardTodaySchedule {
    items: { id: string; kind: DashboardScheduleKind; title: string; titleEn: string | null; at: string }[];
}

/** 일별 결제(오래된 차례, 오늘 포함 — 결제 일별 집계) */
export interface DashboardOrderTrend {
    days: { date: string; amount: number; count: number }[];
}

/** 최근 가입 회원(최근 차례) */
export interface DashboardRecentSignups {
    /** `method` — 가입 방법(`member.authMethod.*` — EMAIL · KAKAO · NAVER · GOOGLE) */
    items: { id: string; nickname: string; method: string; joinedAt: string }[];
}

/** 최근 글(최근 차례) */
export interface DashboardRecentPosts {
    items: { id: string; boardName: string; boardNameEn: string | null; title: string; authorNickname: string; createdAt: string; hidden: boolean; reported: boolean }[];
}

/** 오늘 활발한 게시판 TOP 5(글 + 댓글 많은 차례) */
export interface DashboardBoardRanking {
    items: { boardId: string; boardName: string; posts: number; comments: number }[];
}

/** 제재 — 진행 중 · 이의 신청 대기 + 이의 신청 오래된 차례 3건(`kind` 는 서버가 요청 언어로 만든 제재 이름) */
export interface DashboardSanctions {
    active: number;
    appealsPending: number;
    appeals: { id: string; nickname: string; kind: string; appealedAt: string }[];
}

/** 지금 인기 핫딜 TOP 5 — 가격은 원(모르면 null) */
export interface DashboardHotdealTop {
    items: { id: string; shopName: string; title: string; likes: number; price: number | null }[];
}

/** 알림 상태 — 오늘 보낸 수 · 채널별(`notification.channelType.*`) 보낸·실패 수 + 최근 실패 3건(`summary` 는 서버 문구) */
export interface DashboardNotificationHealth {
    sentToday: number;
    channels: { channel: string; sent: number; failed: number }[];
    recentFailures: { id: string; channel: string; summary: string; failedAt: string }[];
}

/** 인기 페이지의 화면 종류 — 서버가 경로로 가른다(모르는 화면은 `OTHER`) */
export type DashboardPageKind = 'HOME' | 'BOARD' | 'POST' | 'GROUP_BUY' | 'HOTDEAL' | 'GROUP' | 'POINT_SHOP' | 'SEARCH' | 'MY' | 'OTHER';

/**
 * 인기 페이지 — 오늘 많이 본 페이지 `limit`(5|10)개(조회수 차례). 방문 원본 기록(`rawEvents`)을 서버가 1분마다 몰아 센 오늘 페이지별 집계를 읽는다.
 * `title` = 글이면 글 제목, 아니면 화면 이름(게시판 이름 · 공구 제목 …) — 서버가 모르면 null(화면은 경로를 보인다). `titleEn` 은 영어 이름이 있는 것만.
 */
export interface DashboardPopularPages {
    items: { path: string; kind: DashboardPageKind; title: string | null; titleEn: string | null; views: number }[];
}

/** 유입처 분류 — 검색 · SNS · 직접(주소 입력·즐겨찾기) · 다른 사이트 · 캠페인(`utm_*` 붙은 주소) */
export type DashboardReferrerGroup = 'SEARCH' | 'SOCIAL' | 'DIRECT' | 'OTHER_SITE' | 'CAMPAIGN';

/** 유입 경로 — 오늘 방문(방문 시작 기준)을 유입처 분류별로 + 상위 유입 도메인 5(많은 차례, 직접·캠페인은 도메인이 없어 빠진다) */
export interface DashboardReferrers {
    /** 오늘 방문 수(분류 합) */
    total: number;
    groups: { group: DashboardReferrerGroup; visits: number }[];
    domains: { domain: string; visits: number }[];
}

/** 종류 → 응답 모양 */
export interface DashboardGadgetDataMap {
    VISITORS: DashboardVisitors;
    TODAY_SCHEDULE: DashboardTodaySchedule;
    ORDER_TREND: DashboardOrderTrend;
    RECENT_SIGNUPS: DashboardRecentSignups;
    RECENT_POSTS: DashboardRecentPosts;
    BOARD_RANKING: DashboardBoardRanking;
    SANCTIONS: DashboardSanctions;
    HOTDEAL_TOP: DashboardHotdealTop;
    NOTIFICATION_HEALTH: DashboardNotificationHealth;
    POPULAR_PAGES: DashboardPopularPages;
    REFERRERS: DashboardReferrers;
    REPORTS: DashboardReports;
    POST_ACTIVITY: DashboardPostActivity;
    POPULAR_NOW: DashboardPopularNow;
    GROUP_REQUESTS: DashboardGroupRequests;
    MEMBER_STATS: DashboardMemberStats;
    SIGNUP_TREND: DashboardSignupTrend;
    NICKNAME_IMAGES: DashboardNicknameImages;
    HOTDEAL_STATUS: DashboardHotdealStatus;
    AD_PERFORMANCE: DashboardAdPerformance;
    AD_SCHEDULE: DashboardAdSchedule;
    GROUPBUY_PROGRESS: DashboardGroupBuyProgress;
    GROUPBUY_QNA: DashboardGroupBuyQna;
    SALES_SUMMARY: DashboardSalesSummary;
    RECONCILE: DashboardReconcile;
    POINT_SUMMARY: DashboardPointSummary;
    POINTSHOP: DashboardPointShop;
    BATCH_HEALTH: DashboardBatchHealth;
    AUDIT_RECENT: DashboardAuditRecent;
    OPERATOR_SECURITY: DashboardOperatorSecurity;
}

/** 이 API 로 받는 종류 */
export type DashboardDataType = keyof DashboardGadgetDataMap & DashboardGadgetType;

/** 종류별 질의 — 해당되는 것만 */
export interface DashboardGadgetQuery {
    limit?: number;
    days?: number;
}

/** 묶음 요청의 종류 하나 */
export interface DashboardGadgetRequest {
    type: DashboardDataType;
    query: DashboardGadgetQuery;
}

/** 묶음 응답 — 종류마다 값 + 센 시각(서버 `DashboardGadgetsView`) */
export interface DashboardGadgetsResponse {
    generatedAt: string;
    /** 요청한 종류마다 값 — 권한이 없거나 서버가 아직 줄 수 없는 종류는 null */
    gadgets: { [K in DashboardDataType]?: DashboardGadgetDataMap[K] | null };
    /** 권한은 있으나 서버가 아직 데이터를 줄 수 없는 종류(원천 없음 · 다음 단계) — 화면은 「준비 중」, 나머지 null 은 「권한 없음」 */
    unavailable: DashboardDataType[];
    /**
     * 권한은 있으나 「통계 수집」 설정에서 그 수집이 꺼진 종류(값은 null) — 화면은 「수집이 꺼져 있어요」(`/settings/analytics`).
     * 옛 서버는 이 칸을 주지 않는다 — 없으면 빈 배열로 읽는다.
     */
    disabled?: DashboardDataType[];
}

/** 한 종류를 받은 결과 — `data` 가 null 이면 `disabled` 일 때 수집 꺼짐, `unavailable` 일 때 준비 중, 아니면 권한 없음 */
export interface DashboardGadgetResult<T> {
    generatedAt: string;
    data: T | null;
    unavailable?: boolean;
    disabled?: boolean;
}
