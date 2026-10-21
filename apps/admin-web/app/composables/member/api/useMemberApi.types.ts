import type { DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 회원 조회 API 가 주고받는 모양 — 서버(`MemberController`·`member/dto`)와 맞춘 계약이다(상세의 보강 칸 중 서버에 데이터가 없는 것은 null/빈 값 — 아래 `MemberDetail`). 회원은 공개 사이트 이용자다(운영자 계정은 여기 나오지 않는다 — `docs/domain/operator-permissions.md`).
 * 칸의 근거는 community 의 `UserEntity`(계정·상태·이메일 인증·마지막 로그인)·`ProfileEntity`(닉네임·등급)다.
 * 시각은 UTC ISO(`Instant`)이고 화면은 `useDisplayTime()` 으로 그린다. ⚠ ID 는 문자열이다(Snowflake 64비트).
 *
 * 상세(`MemberDetail`)의 보강 칸 모양은 **후보 계약**이고, 서버가 지금 채우는 것은 일부뿐이다(나머지는 null/빈 값 — 기능·표가 생기면 채워진다). 모양의 근거는 세 가지다.
 *  · community 근거 — 한 줄 소개·소셜 연동·성인 확인·쪽지 수신·휴면 예정·활동(받은 추천·인기글)·닉네임 변경 이력·등급(자동 승급·진행도·변경 이력)·포인트(잔액·변경권·내역)·제재 요약·신고 요약·
 *    표시 형식·알림 설정·앱 푸시·차단/메모 건수·가입 동의(약관·개인정보처리방침·만 14세)·공동구매 주문(상태·배송지 스냅샷). 파일: community 관리자 `pages/members/[id].vue`, `types/admin-member.ts`·`admin-grade.ts`·`admin-point.ts`,
 *    문서 `docs/api-account.md`·`api-profile.md`·`api-display.md`·`api-notification.md`·`api-push.md`·`api-block-scrap.md`·`api-dm.md`·`api-dormancy.md`·`api-auth.md`·`api-commerce.md`·`api-commerce-admin.md`.
 *  · 근거: 그누보드7 회원 상세(사용자 제시) — 연락처·위치(홈페이지·휴대폰·전화·국가·주소), 서명, 관리자 메모, 마케팅·광고성 이메일·제3자 제공·정보 공개 동의와 변경·철회 이력, 게시글 완료/답변 알림, 알림함 건수.
 *    community 와 roboyam 서버에 대응이 없다. 쇼핑 데이터는 roboyam 이 쇼핑몰이 아니라 공동구매라서 찜·장바구니·상품문의·배송지 목록 대신 공동구매 주문 요약으로 대응했다.
 *  · 준비 중(칸을 만들지 않았다) — 「본인 인증」 탭에 나중에 채울 후보: 본인확인 방법(휴대폰·아이핀)·본인확인 여부·성인인증(본인 인증 기준)·인증 이력(일시·방법·결과).
 *
 * ⚠ 미확정: PII 가림 규칙(휴대폰·전화·주소·공동구매 배송지·닉네임 이력)은 `docs/domain/operator-permissions.md` 에 이메일 외에는 정해진 것이 없어 목업 가정이다(`member.pii` 없으면 서버가 가려서 준다).
 *  공동구매 배송지 열람 시 사유 입력(community 는 5~200자)과 열람 기록을 남길지, 관리자 메모 편집 권한(회원 수정 권한이 권한 표에 없다), 동의 변경 이력의 경로 값, 휴면 예정 안내의 기준.
 */

/** 계정 상태 — 정상 · 기간 정지 · 영구 정지 · 탈퇴 · 휴면 */
export type MemberStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED' | 'WITHDRAWN' | 'DORMANT';

/** 등급 키 — 등급은 등급 설정이 정하는 순서 있는 목록이라(4단계 고정이 아니다) 서버가 주는 불변 문자열이다. 이름·뱃지는 등급 목록(`useGradeAppearance`)에서 읽는다 */
export type MemberGrade = string;

/** 회원 유형 — 일반 회원 · 사업자 */
export type MemberType = 'GENERAL' | 'BUSINESS';

/** 가입 방식 */
export type MemberAuthMethod = 'EMAIL' | 'KAKAO' | 'NAVER' | 'GOOGLE';

/** 게시판 매니저·스텝 여부 — 한 게시판이라도 맡았으면 `MANAGER`(매니저가 우선)·`STAFF`, 아니면 `NONE` */
export type MemberBoardRole = 'MANAGER' | 'STAFF' | 'NONE';

/** 검색 대상 — 전체 · 이메일 · 닉네임 · 회원번호 */
export type MemberSearchField = 'ALL' | 'EMAIL' | 'NICKNAME' | 'MEMBER_NO';

/** 상태 변경일의 기준 — 정지(기간·영구) · 탈퇴 · 휴면 */
export type MemberStatusChangeKind = 'SUSPENDED' | 'WITHDRAWN' | 'DORMANT';

/** 목록의 정렬 가능한 칸 */
export type MemberSortField = 'memberNo' | 'email' | 'nickname' | 'grade' | 'status' | 'joinedAt' | 'lastLoginAt' | 'point' | 'dormantAt';

/** 회원 역할 한 개 — 회원 쪽 역할(별도 작업에서 정의 중)의 이름표 */
export interface MemberRoleRef {
    roleId: string;
    roleName: string;
}

export interface MemberRow {
    /** 회원번호 */
    memberNo: string;
    /** 이메일. 소셜 가입자가 안 줬거나 탈퇴한 회원은 null. `member.pii` 가 없으면 서버가 마스킹해서 준다(`ab***@gmail.com`) — `emailMasked` */
    email: string | null;
    emailMasked: boolean;
    nickname: string;
    grade: MemberGrade;
    memberType: MemberType;
    status: MemberStatus;
    authMethod: MemberAuthMethod;
    /** 이메일 인증 시각 — 미인증은 null */
    emailVerifiedAt: string | null;
    joinedAt: string;
    /** 마지막 로그인 — 로그인한 적 없으면 null */
    lastLoginAt: string | null;
    point: number;
    roles: MemberRoleRef[];
    /** 휴면으로 바뀐 시각 — 휴면 이력이 없으면 null */
    dormantAt: string | null;
    /** 가장 최근 상태 변경(정지·탈퇴·휴면) 시각 */
    statusChangedAt: string | null;
    boardRole: MemberBoardRole;
    /** 제재 이력 수 */
    sanctionCount: number;
}

/** 역할 고르개 한 항목 */
export interface MemberRoleOption {
    roleId: string;
    roleName: string;
}

export interface MemberPage {
    items: MemberRow[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

/**
 * 조회 조건 — 빈 값은 보내지 않는다(서버가 "거르지 않음"으로 받는다).
 * 기간(`*From`·`*To`)은 **UTC ISO 순간**이다: 화면은 날짜만 받고, 사이트 시간대의 시작 `00:00:00.000` ~ 끝 `23:59:59.999` 를 순간으로 바꿔 보낸다.
 */
export interface MemberQuery {
    searchField?: MemberSearchField;
    keyword?: string;
    statuses?: MemberStatus[];
    roleIds?: string[];
    joinedFrom?: string;
    joinedTo?: string;
    lastLoginFrom?: string;
    lastLoginTo?: string;
    /** true 면 로그인 이력이 없는 회원만 — 최근 로그인 기간은 보내지 않는다 */
    noLoginHistory?: boolean;
    grades?: MemberGrade[];
    memberType?: MemberType;
    /** true 인증 완료 · false 미완료 */
    emailVerified?: boolean;
    authMethods?: MemberAuthMethod[];
    dormantFrom?: string;
    dormantTo?: string;
    statusChangedKind?: MemberStatusChangeKind;
    statusChangedFrom?: string;
    statusChangedTo?: string;
    pointMin?: number;
    pointMax?: number;
    boardRole?: MemberBoardRole;
    /** true 제재 이력 있음 · false 없음 */
    hasSanction?: boolean;
    /** `칸,asc|desc` — 기본 `joinedAt,desc` */
    sort?: string;
    /** 0 부터 */
    page?: number;
    size?: number;
}

// ── 상세(`GET /api/admin/members/{memberId}`) ─

/** 역할을 받는 방식 — 자동(등급·회원 유형) · 지정(게시판 매니저·스텝) · 수동(사용자 정의) */
export type MemberRoleGrantMode = 'AUTO' | 'APPOINTED' | 'MANUAL';

/** 회원 역할 유형 id — 역할 관리의 회원 역할 유형과 같다(등급 · 회원 유형 · 게시판 단위 · 사용자 정의) */
export type MemberRoleTypeId = 'memberGrade' | 'memberKind' | 'memberBoard' | 'memberCustom';

/** 보유 회원 역할 한 개 — 이름·아이디는 역할 관리의 회원 역할과 같다(`ROLE_…`) */
export interface MemberRoleDetail extends MemberRoleRef {
    typeId: MemberRoleTypeId;
    grantMode: MemberRoleGrantMode;
    /** 게시판 단위 역할이 맡은 게시판 이름들. 그 밖의 역할은 빈 배열 */
    boardNames: string[];
}

/** 활동 요약 — 숫자 몇 개(목업 값) */
export interface MemberActivity {
    postCount: number;
    commentCount: number;
    /** 받은 추천 수 — 근거: community `MemberActivity.likesReceived` */
    likesReceived: number;
    /** 인기글 수 — 근거: community `MemberActivity.popular` */
    popularCount: number;
    /** 최근 7일 */
    recentPostCount: number;
    recentCommentCount: number;
    /** 최근 7일 중 접속한 날 수(0~7) */
    recentLoginDays: number;
}

/** 제재 종류 — 경고 · 기간 정지 · 영구 정지 */
export type MemberSanctionKind = 'WARNING' | 'SUSPENSION' | 'BAN';
/** 제재 범위 — 사이트 전체 · 게시판 한정 */
export type MemberSanctionScope = 'SITE' | 'BOARD';
/** 제재 상태 — 진행 · 종료(기간 만료) · 해제(운영자가 풂) */
export type MemberSanctionStatus = 'ACTIVE' | 'ENDED' | 'RELEASED';

export interface MemberSanction {
    sanctionId: string;
    kind: MemberSanctionKind;
    scope: MemberSanctionScope;
    /** 게시판 한정이면 게시판 이름 */
    boardName: string | null;
    occurredAt: string;
    /** 끝나는 시각 — 영구·경고는 null */
    endsAt: string | null;
    status: MemberSanctionStatus;
    reason: string;
}

/** 제재 요약 — 경고 횟수와 다음 단계는 서버가 계산한다(근거: community `SanctionPreview`) */
export interface MemberSanctionSummary {
    /** 유효한 경고 횟수(0~2) */
    warningCount: number;
    /** 다음에 줄 단계 — 이미 영구 정지이거나 탈퇴한 회원은 null */
    next: MemberSanctionKind | null;
}

/** 신고 요약 — 이 회원이 대상인 사건 수 · 그중 조치한 수 · 이 회원이 한 신고 수(근거: community `MemberReports`) */
export interface MemberReportSummary {
    receivedCount: number;
    actionedCount: number;
    filedCount: number;
}

// ── 상세 보강 — 기본 정보 ─

/** 소셜 연동 공급자 */
export type MemberSocialProvider = 'KAKAO' | 'NAVER' | 'GOOGLE';

export interface MemberSocialLink {
    provider: MemberSocialProvider;
    linked: boolean;
}

/** 휴면 예정 안내 — 안내 메일을 보냈고 아직 처리·취소되지 않았다. 해당 없으면 상세의 `dormancy` 가 null */
export interface MemberDormancy {
    noticedAt: string;
    /** 휴면 처리 예정일 */
    eraseOn: string;
}

/** 닉네임 변경 이력 한 줄 — 최근 것이 먼저 */
export interface MemberNicknameChange {
    oldNickname: string;
    newNickname: string;
    changedAt: string;
}

// ── 상세 보강 — 등급·포인트 ─

/** 자동 승급 상태 — 정상 · 운영자가 등급을 바꿔 중지 · 제재로 중지 */
export type MemberAutoPromotion = 'ACTIVE' | 'PAUSED_ADMIN' | 'PAUSED_SANCTION';

/** 승급 조건 종류 — 가입 일수 · 글·댓글 수(roboyam 등급 설정의 두 조건) */
export type MemberGradeRequirementKind = 'DAYS' | 'ACTIVITY';

/** 다음 등급 조건 한 줄 — `current` 는 상한 없는 실제 값 */
export interface MemberGradeRequirement {
    kind: MemberGradeRequirementKind;
    current: number;
    required: number;
}

/** 다음 등급과 조건 — 마지막 등급이면 `next` 가 null */
export interface MemberGradeNext {
    gradeKey: MemberGrade;
    requirements: MemberGradeRequirement[];
}

/** 등급 변경 종류 — 자동 승급 · 운영자 변경 · 자동 승급 재개 */
export type MemberGradeChangeKind = 'AUTO' | 'ADMIN' | 'RESUME';

export interface MemberGradeChange {
    changeId: string;
    /** 이전 등급 키 — 처음 부여면 null */
    fromGrade: MemberGrade | null;
    toGrade: MemberGrade;
    kind: MemberGradeChangeKind;
    reason: string | null;
    /** 처리한 운영자 닉네임 — 자동이면 null */
    actorNickname: string | null;
    changedAt: string;
}

/** 포인트 내역 종류 — 받음 · 사용 · 회수 */
export type MemberPointKind = 'EARN' | 'USE' | 'REVOKE';

export interface MemberPointEntry {
    entryId: string;
    kind: MemberPointKind;
    /** 실제 반영된 증감(−는 차감) */
    delta: number;
    /** 서버가 한국어로 주는 사유(출석체크·글 작성 등) */
    reason: string;
    createdAt: string;
}

/** 등급·포인트 탭 — 현재 등급은 상세의 `grade`, 잔액은 `point` 를 쓴다(중복해서 내려 주지 않는다) */
export interface MemberGradePoint {
    autoPromotion: MemberAutoPromotion;
    next: MemberGradeNext | null;
    /** 최근 것이 먼저 */
    gradeHistory: MemberGradeChange[];
    nicknameTicketCount: number;
    /** 최근 N건(목업 8건), 최근 것이 먼저 */
    pointEntries: MemberPointEntry[];
}

// ── 상세 보강 — 개인 설정 ─

/**
 * 알림 종류 — `COMMENT`(댓글)·`REPLY`(답글)·`POPULAR_REWARD`(인기글 보상)·`DM`(쪽지)은 community `api-notification.md`, `POST_DONE`(게시글 완료)·`POST_ANSWER`(게시글 답변)는 그누보드7 항목이다.
 * 그누보드7 의 「댓글 알림」「답변 알림」은 community 의 `COMMENT`·`REPLY` 와 같은 종류라 한 줄로 합쳤다.
 */
export type MemberNotificationKind = 'COMMENT' | 'REPLY' | 'POPULAR_REWARD' | 'DM' | 'POST_DONE' | 'POST_ANSWER';

export interface MemberNotificationSetting {
    kind: MemberNotificationKind;
    /** 켜짐 여부. roboyam 에 대응 기능이 아직 없는 종류(`POST_DONE`·`POST_ANSWER`)는 null — 화면은 「준비 중」으로 보인다 */
    enabled: boolean | null;
}

export interface MemberInbox {
    totalCount: number;
    unreadCount: number;
}

/** 개인 설정 탭 — 회원이 직접 정한 값을 운영자가 읽기만 한다. 차단·메모는 건수만(내용은 내려오지 않는다), 알림함도 건수만 */
export interface MemberSettings {
    /** 이 회원에게 적용되는 표시 형식(언어·시간대·시간 형식·날짜 형식) — 연락처·위치 탭의 언어·시간대도 이 값이다 */
    display: DisplayValues;
    /** true 면 회원이 직접 고른 값, false 면 사이트 기본값 */
    displayOwn: boolean;
    notifications: MemberNotificationSetting[];
    pushSubscribed: boolean;
    blockedUserCount: number;
    blockedWordCount: number;
    memoCount: number;
    inbox: MemberInbox;
}

// ── 상세 보강 — 연락처·위치, 프로필·메모 (그누보드7) ─

/** 연락처·위치 탭 — 휴대폰·전화·주소는 PII 라 `member.pii` 가 없으면 서버가 가려서 준다(`piiMasked`). 언어·시간대는 `settings.display` 를 쓴다 */
export interface MemberContact {
    homepage: string | null;
    mobile: string | null;
    phone: string | null;
    /** ISO 3166-1 alpha-2 */
    country: string | null;
    address: string | null;
}

/** 프로필·메모 탭 — 한 줄 소개(`bio`)는 상세 최상위 칸이다. 관리자 메모는 읽기 전용(편집 권한이 아직 정의되지 않았다) */
export interface MemberProfile {
    signature: string | null;
    adminMemo: string | null;
    adminMemoUpdatedAt: string | null;
    adminMemoUpdatedBy: string | null;
}

// ── 상세 보강 — 동의 이력 ─

export interface MemberConsentRecord {
    agreedAt: string;
    version: string;
}

/** 선택 동의 — 마케팅 · 광고성 이메일(마케팅의 하위) · 제3자 제공 · 정보 공개(그누보드7) */
export type MemberOptionalConsentKey = 'MARKETING' | 'EMAIL_AD' | 'THIRD_PARTY' | 'PROFILE_PUBLIC';

export interface MemberOptionalConsent {
    key: MemberOptionalConsentKey;
    agreed: boolean;
    agreedAt: string | null;
}

/** 동의 항목 — 가입 때 필수 3개 + 선택 동의 */
export type MemberConsentItem = 'TERMS' | 'PRIVACY' | 'AGE' | MemberOptionalConsentKey;
export type MemberConsentAction = 'AGREE' | 'WITHDRAW';
/** 변경 경로 — 회원이 마이페이지에서 · 운영자가 관리자에서 */
export type MemberConsentChannel = 'MYPAGE' | 'ADMIN';

/** 동의 변경·철회 이력 한 줄 — community 에는 이력 표가 없어 목업 후보다 */
export interface MemberConsentChange {
    changeId: string;
    item: MemberConsentItem;
    action: MemberConsentAction;
    channel: MemberConsentChannel;
    changedAt: string;
}

export interface MemberConsents {
    terms: MemberConsentRecord;
    /** 약관과 따로 저장하지 않아 서버가 아직 null 로 준다 */
    privacy: MemberConsentRecord | null;
    /** 만 14세 이상 확인 시각 */
    ageConfirmedAt: string;
    optional: MemberOptionalConsent[];
    /** 최근 것이 먼저 */
    history: MemberConsentChange[];
}

// ── 상세 보강 — 공동구매 (그누보드7 쇼핑 데이터의 대응 후보) ─

/** 공동구매 주문 상태 — community `api-commerce.md` 의 주문 상태: 결제 전 · 결제완료 · 준비중 · 발송 · 배송완료 · 취소(발송 전) · 환불 */
export type MemberOrderStatus = 'PENDING' | 'PAID' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'REFUNDED';

/** 주문 단위 배송지 스냅샷 — community 는 배송지를 주문에 저장한다(기본 배송지·주소록 개념 없음). 주소·전화는 `member.pii` 가 없으면 가려서 온다 */
export interface MemberOrderShipping {
    recipient: string;
    address: string;
    phone: string;
}

export interface MemberGroupBuyOrder {
    orderNo: string;
    dealTitle: string;
    quantity: number;
    /** 결제 금액(원) */
    totalAmount: number;
    status: MemberOrderStatus;
    orderedAt: string;
    /** 배송 완료 6개월 경과·탈퇴로 지워졌으면 null */
    shipping: MemberOrderShipping | null;
}

/** 공동구매 탭 — 건수는 상태별로 센다: 진행 중(`PENDING`·`PAID`·`PREPARING`) · 배송 중(`SHIPPED`) · 완료(`DELIVERED`) · 취소·환불(`CANCELED`·`REFUNDED`) */
export interface MemberGroupBuy {
    dealCount: number;
    orderCount: number;
    inProgressCount: number;
    shippingCount: number;
    completedCount: number;
    canceledCount: number;
    /** 최근 5건, 최근 것이 먼저 */
    recentOrders: MemberGroupBuyOrder[];
}

/**
 * 회원 상세 — 목록 한 줄의 칸 + 상세 전용 칸. 역할은 이름뿐 아니라 유형·게시판까지 준다.
 * **서버가 비워 주는 칸**(null/빈 값 — 화면은 해당 구획을 안내로 보인다): `statusReason`·`activity`·`sanctionSummary`·`reports`·`adultConfirmed`·`acceptDm`·`dormancy`·`gradePoint`·`settings`·`contact`·`profile`·`groupBuy`·`consents.privacy` 는 null,
 * `sanctions`·`consents.optional`·`consents.history`·`roles[].boardNames` 는 빈 배열, `nicknameHistory` 는 빈 배열(`member.pii` 가 없으면 null), `point`·`sanctionCount` 는 늘 0, `statusChangedAt` 은 탈퇴 시각만.
 * 실제 값이 있는 칸: 목록 한 줄의 칸, `roles`, `bio`, `social`, `withdrawnAt`, `consents.terms`/`ageConfirmedAt`, `piiMasked`.
 * 탈퇴한 회원은 `bio`·`consents` 가 null 이고 `social` 은 빈 배열, `withdrawnAt` 이 채워진다(줄어든 표시).
 */
export interface MemberDetail extends Omit<MemberRow, 'roles'> {
    roles: MemberRoleDetail[];
    /** 지금 상태가 된 사유(정지 사유·탈퇴 방식 등). 없으면 null — 서버는 사유를 저장하는 곳이 아직 없어 늘 null */
    statusReason: string | null;
    /** 활동 요약 — 글·댓글 표가 아직 없어 서버가 null */
    activity: MemberActivity | null;
    /** 제재 이력 — 최근 것이 먼저. 제재 표가 아직 없어 서버가 늘 빈 배열 */
    sanctions: MemberSanction[];
    /** 제재 요약 — 서버가 아직 null */
    sanctionSummary: MemberSanctionSummary | null;
    /** 신고 요약 — 서버가 아직 null */
    reports: MemberReportSummary | null;
    /** 한 줄 소개 — 프로필·메모 탭에서 보인다 */
    bio: string | null;
    social: MemberSocialLink[];
    /** 성인 확인 여부(출생연도 자기 입력 — community `api-post-kinds.md`). 출생연도는 PII 라 내려오지 않는다. 본인 인증과는 별개다 */
    adultConfirmed: boolean | null;
    /** 쪽지 수신 여부 */
    acceptDm: boolean | null;
    dormancy: MemberDormancy | null;
    /** 탈퇴 시각 — 탈퇴한 회원만 */
    withdrawnAt: string | null;
    /** 닉네임 변경 이력. `member.pii` 가 없으면 서버가 주지 않는다(null → 화면은 잠금 문구). 탈퇴한 회원은 빈 배열 */
    nicknameHistory: MemberNicknameChange[] | null;
    /** 휴대폰·전화·주소·배송지가 가려져 왔는가(`member.pii` 없음) — 이메일의 `emailMasked` 와 같은 방식 */
    piiMasked: boolean;
    gradePoint: MemberGradePoint | null;
    settings: MemberSettings | null;
    contact: MemberContact | null;
    profile: MemberProfile | null;
    consents: MemberConsents | null;
    groupBuy: MemberGroupBuy | null;
}
