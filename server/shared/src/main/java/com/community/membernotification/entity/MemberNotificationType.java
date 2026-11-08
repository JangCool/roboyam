package com.community.membernotification.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 회원 알림 종류 — <b>실제로 발행되는 것만</b> 둔다(코드가 원천, V50 의 CHECK 와 같은 값). 이름은 한 번 정하면 바꾸지 않는다(알림 행이 이 값으로 가리킨다).
 * {@code COMMENT}·{@code REPLY} 는 관리자 회원 상세의 알림 설정 종류(admin-web {@code MemberNotificationKind})·community {@code api-notification.md} 와 같은 이름이다.
 * 같은 이름의 회원 이벤트({@link MemberEvent})가 사용 여부·앱 알림·잠금을 정한다(발행기는 그 설정을 본다).
 *
 * <ul>
 *   <li>{@link #category()}: 목록 거르기 — {@code comment}(댓글·답글) · {@code dm}(쪽지) · {@code system}(그 밖).</li>
 * </ul>
 */
public enum MemberNotificationType {

    /** 내 글에 새 댓글 — 같은 글의 새 댓글은 묶음 시간 안에서 한 줄로 묶는다 */
    COMMENT("comment"),
    /** 내 댓글에 답글 */
    REPLY("comment"),
    /** 내 포인트가 회수됐다(운영 조정 차감) */
    POINTS_REVOKED("system"),
    /** 신고가 쌓여(또는 불법촬영물·권리침해 신고로) 내 글이 자동으로 숨겨졌다 */
    POST_HIDDEN("system"),
    /** 신고로 내 댓글이 자동으로 숨겨졌다 */
    COMMENT_HIDDEN("system"),
    /**
     * 쪽지를 받았다 — 같은 대화의 안 읽은 쪽지 알림이 있으면 새로 만들지 않는다(대화를 읽으면 다음 쪽지부터 다시 알린다). 묶음 {@code dm} 은 목록 거르기 {@code all}·{@code dm} 에 나온다
     * (community {@code api-notification.md} 처럼 댓글·시스템 거르기에는 안 나온다)
     */
    DM("dm"),
    /** 내 닉네임 그림이 승인·반려·해제됐다(운영자 처리 — 사유와 함께) */
    NICKNAME_IMAGE_REVIEWED("system"),
    /** 내 아바타 올리기가 승인·반려·해제됐다(운영자 처리 — 사유와 함께, V157 commerce-motion-avatar-contract) */
    AVATAR_UPLOAD_REVIEWED("system"),
    /** 내가 확인할 의견 고정 요청이 왔다(글쓴이·게시판 매니저 — 게시판 정책 pinRequestReviewers, V78) — 일으킨 사람 = 요청한 회원, 글·댓글을 가리킨다 */
    OPINION_PIN_REQUESTED("comment"),
    /** 내 의견 고정 요청이 승인·거절됐다(거절 사유와 함께, V78) */
    OPINION_PIN_REVIEWED("system"),
    /** 운영자가 내 글을 지웠다(관리자 게시글 관리·신고 관리 — 글 제목·사유, V80). 지운 글은 열 수 없어 링크가 없다 */
    POST_REMOVED("system"),
    /** 운영자가 내 댓글을 지웠다(관리자 신고 관리 — 댓글 앞부분·사유, V82). 링크는 그 글 */
    COMMENT_REMOVED("system"),
    /** 내가 한 신고가 처리됐다(관리자 신고 관리 — 조치됨 · 위반 아님, V82). 게시판 신고 정책 notifyReporterOnResult 가 켜졌을 때만 */
    REPORT_RESULT("system"),
    /** 내 계정에 기능 제한(포인트 동결·공구 참여 제한·충전 제한)이 걸렸다 — 종류·끝·공개 사유(운영 메모는 싣지 않는다, commerce.md 12-3) */
    FEATURE_RESTRICTED("system"),
    /**
     * 내 무상 포인트가 곧 소멸된다(소멸 예정 알림 배치 {@code point-expiry-notice}, commerce.md 14) — 알림 기간({@code point_settings.expiry_notice_days}) 안 소멸 예정 합 · 가장 가까운
     * 소멸일(한국 날짜) · 일수. 같은 묶음으로 두 번 알리지 않는다({@code point_lots.expiry_noticed_at}). 누르면 내 포인트({@code /me/points})
     */
    POINTS_EXPIRING("system"),
    /** 정지 중에 낸 포인트 상점 환불 신청을 운영자가 수락해 환불했다(V87) — 품목 이름 · 돌려준 포인트. 누르면 내 아이템({@code /me/items}) */
    POINT_SHOP_REFUND_APPROVED("system"),
    /** 정지 중에 낸 포인트 상점 환불 신청을 운영자가 거절했다(V87) — 품목 이름 · 사유. 누르면 내 아이템 */
    POINT_SHOP_REFUND_REJECTED("system"),
    /**
     * 공동구매(커머스 2b-4) — 덧붙임 {@code kind}(주문 확인·관심 알림·수량 풀림·연장·차수 시작·환불 완료·자동 취소·발송 지연·공급 불가 선택·요청 처리 등 —
     * {@code GroupBuyNotifications.Kind}) · {@code groupBuyId} · {@code title}(알릴 때 공구 제목) · {@code amount} · {@code note}(주문 번호·날짜) · {@code path}(누르면 갈 곳).
     * 끌 수 없는 거래 알림(관심 알림은 회원이 신청한 것). 꼭 가야 하는 메일(계약 통지·환불 완료·지연 통지)은 커머스 메일 대기열이 따로 보낸다.
     */
    GROUP_BUY("system"),
    /**
     * 사업자 신원(V111 — docs/features/business-contract.md) — 덧붙임 {@code kind}(SUBMITTED 접수 · APPROVED 승인 · REJECTED 반려 · SUSPENDED 정지 · UNSUSPENDED 해제 ·
     * RE_REVIEW 재심사 대기) · {@code companyName}(알릴 때 상호) · {@code note}(반려·정지 사유). 끌 수 없는 계정 알림. 누르면 사업자 센터({@code /business})
     */
    BUSINESS("system"),
    /**
     * 사업자 신청 건(광고·핫딜 — V112, docs/features/biz-application-contract.md) — 덧붙임 {@code kind}(접수·승인·반려·수동 전환·결제 완료·예약 확정·기한 지남·결제 기한 임박·
     * 시작 임박·종료 임박·취소·중단·되돌림·환불 요청·환불 승인·환불 거절·환불 완료·자동 취소 — {@code BizApplicationNotices.Kind}) · {@code applicationId} · {@code title}(알릴 때 제목) ·
     * {@code amount} · {@code note}(기한·사유) · {@code path}. 끌 수 없는 거래 알림. 누르면 신청 건 상세({@code /business/applications/{id}})
     */
    BIZ_APPLICATION("system"),

    // ── 소모임(V90 — docs/features/group-contract.md 1단계, community api-groups.md 7절). 묶음 system(user-web 거르기에 소모임 묶음이 없어 계정·처리 알림과 같이). 덧붙임 groupId·groupName(+reason·applicantId) ──
    /** 내 개설 신청이 승인돼 소모임이 열렸다 — 누르면 그 소모임 */
    GROUP_APPROVED("system"),
    /** 내 개설 신청이 반려됐다(사유와 함께) — 누르면 내 소모임 */
    GROUP_REJECTED("system"),
    /** 내가 처리할 가입 신청이 왔다(모임장 — 3단계부터 APPLICATIONS 매니저도) — 일으킨 사람 = 신청한 회원. 누르면 가입 신청 관리 */
    GROUP_JOIN_REQUEST("system"),
    /** 내 가입 신청이 승인됐다 — 누르면 그 소모임 */
    GROUP_JOIN_APPROVED("system"),
    /** 내 가입 신청이 거절됐다(처리자가 사유를 적었을 때만 사유) — 누르면 내 소모임 */
    GROUP_JOIN_REJECTED("system"),
    /** 소모임에서 내보내졌다 — 누르면 소모임 목록 */
    GROUP_KICKED("system"),
    /** 내가 멤버인 소모임이 해산됐다 — 누르면 소모임 목록 */
    GROUP_DISSOLVED("system"),
    /** 모임장 자리를 넘겨받았다 — 누르면 그 소모임 */
    GROUP_LEADER_TRANSFERRED("system"),
    /** 내가 모임장인 소모임이 운영 조치로 비활성이 됐다(사유와 함께) — 누르면 그 소모임 */
    GROUP_DEACTIVATED("system"),
    /** 내가 모임장인 소모임이 다시 열렸다(운영 메모는 싣지 않는다) — 누르면 그 소모임 */
    GROUP_REACTIVATED("system"),
    /**
     * 모임장(또는 되돌리기를 맡은 매니저 — 3단계)이 내 공개 소모임 글을 비공개로 되돌렸다(V91, community api-groups.md 8절) — 덧붙임 groupId·groupName·title·actorRole(LEADER · MANAGER).
     * 글·일으킨 사람 칸은 그 글·처리자. 누르면 그 글. 작성자 본인이 되돌리면 알림 없음
     */
    GROUP_POST_UNPUBLISHED("system"),

    // ── 소모임 매니저(V92 — group-contract 3단계, community api-groups.md 5-1·7절 「매니저 알림 5종」). 모두 끌 수 없다(내 자리·할 일이 바뀐 알림) ──
    /** 매니저로 임명됐다 — 덧붙임 groupId·groupName·permissions(맡은 일 키, 고정 순서). 일으킨 사람 = 모임장. 누르면 관리 메인 */
    GROUP_MANAGER_APPOINTED("system"),
    /** 내 맡은 일이 바뀌었다(바뀐 것이 없으면 안 간다) — 덧붙임 permissions = 바뀐 뒤 지금 맡은 일 전체. 누르면 관리 메인 */
    GROUP_MANAGER_PERMISSIONS_CHANGED("system"),
    /** 매니저에서 해임됐다 — 누르면 그 소모임 */
    GROUP_MANAGER_REMOVED("system"),
    /** 매니저가 스스로 물러났다(모임장에게) — 일으킨 사람 = 물러난 매니저(덧붙임 actorId). 누르면 멤버 관리 */
    GROUP_MANAGER_RESIGNED("system"),
    /** 매니저가 멤버를 강퇴했다(모임장에게) — 일으킨 사람 = 강퇴한 매니저, 덧붙임 targetId(내보낸 멤버). 누르면 멤버 관리 */
    GROUP_KICKED_BY_MANAGER("system"),

    // ── 소모임 일정(V93 — group-contract 4단계, community api-groups.md 9절 「알림 GROUP_EVENT」). 소모임 알림 중 유일하게 회원이 끌 수 있다 ──
    /**
     * 내가 멤버인 소모임의 일정이 등록·수정·취소됐다 — 덧붙임 groupId·groupName·eventId·title(일정 제목)·eventAction(CREATED · UPDATED · CANCELED)·reason(취소 사유, 적었을 때만).
     * 일으킨 사람 = 한 사람(모임장 · 일정 관리를 맡은 매니저). 누르면 그 일정({@code /groups/{groupId}/events/{eventId}})
     */
    GROUP_EVENT("system"),

    // ── 소모임 공개 범위·공개 요청(V94 — group-contract 5단계, community api-groups.md 11-8 「알림 7종」). 모두 끌 수 없다 ──
    // 덧붙임 공통 groupId·groupName, 글 알림은 title(글 제목 — 알릴 때)·actorRole(LEADER · MANAGER — 요청 알림 제외)·reason(거절 사유 — 적었을 때만). 글·일으킨 사람 칸은 그 글·한 사람
    /** 처리할 공개 요청이 왔다(모임장 + 「글 공개 처리·되돌리기」 매니저) — 일으킨 사람 = 요청한 작성자. 누르면 공개 요청 처리({@code /groups/{id}/manage/publish-requests}) */
    GROUP_PUBLISH_REQUESTED("system"),
    /** 내 공개 요청이 승인됐다(직접 공개가 대기 요청을 닫은 경우 포함) — 누르면 그 글 */
    GROUP_PUBLISH_APPROVED("system"),
    /** 내 공개 요청이 거절됐다(사유가 있으면 reason) — 누르면 그 글 */
    GROUP_PUBLISH_REJECTED("system"),
    /** 모임장·매니저가 요청 없이 내 글을 모두에게 공개했다 — 누르면 그 글 */
    GROUP_POST_PUBLISHED("system"),
    /** 내가 멤버인 소모임의 공개 범위가 바뀌었다(바꾼 모임장 제외) — 덧붙임 visibility(바뀐 값 PUBLIC · PRIVATE). 누르면 그 소모임 */
    GROUP_VISIBILITY_CHANGED("system"),
    /** 공개 소모임에서 모임장·매니저가 내 글을 멤버 전용으로 돌렸다 — 누르면 그 글 */
    GROUP_POST_MEMBERS_ONLY("system"),
    /** 공개 소모임에서 멤버 전용으로 돌려 둔 내 글을 모임장·매니저가 다시 공개했다 — 누르면 그 글 */
    GROUP_POST_RELEASED("system"),

    // ── 회원 핫딜(V122 — docs/features/hotdeal-contract.md 서버 단계 고정 규칙 9, 메모 1-7). 받는 사람 = 그 딜의 작성자. 덧붙임 title(알릴 때 글 제목)·note(운영진 종료 사유 — 적었을 때만).
    //    글 칸 = 그 글, 누르면 그 글({@code /posts/{postId}}). 모두 끌 수 없는 처리 알림 ──
    /** 내 딜에 종료 요청이 문턱만큼 모여 의심(FLAGGED)이 됐다 — 「아직 진행 중」이나 종료를 고르게 */
    DEAL_FLAGGED("system"),
    /** 의심 유예가 지나 내 딜이 자동으로 종료됐다(배치 hotdeal-auto-end) */
    DEAL_AUTO_ENDED("system"),
    /** 운영진이 내 딜을 종료했다(사유가 있으면 note) */
    DEAL_MANAGER_ENDED("system"),
    /** 마감 없는 내 딜이 기간 종료됐다 — 한 번만(배치 hotdeal-expiry-notice, {@code post_deals.expiry_notified_at}) */
    DEAL_EXPIRED("system"),
    /** 내 딜이 다시 진행 중이 됐는데 기간이 이미 지났다 — 「마감을 고쳐 주세요」 */
    DEAL_REOPENED_FIX_ENDS("system"),

    // ── 사이트 제재(V136 — docs/features/member-sanction-contract.md 정책 8). 끌 수 없는 계정 알림, 누르면 /sanction ──
    /** 제재를 받았다(경고·정지·영구 정지) — 덧붙임 sanctionId·kind·warningNo(경고 몇 번째)·until(기간 정지의 끝)·reason(운영자가 적은 사유). 메일도(인증된 이메일) */
    SANCTION_ISSUED("system"),
    /** 운영자가 내 정지·영구 정지를 풀었다 — 덧붙임 sanctionId·kind(해제 사유는 싣지 않는다) */
    SANCTION_LIFTED("system"),
    /** 내 이의제기에 답이 왔다(인용·기각) — 덧붙임 sanctionId·appealId·kind·warningNo·result(ACCEPTED · REJECTED)·answer. 메일도(인증된 이메일) */
    APPEAL_ANSWERED("system"),

    // ── 게시판 제재(V137 — docs/features/board-sanction-contract.md 결정 6). 끌 수 없는 계정 알림, 누르면 /sanction, 메일 없음 ──
    /** 게시판 매니저·스텝에게 게시판 제재를 받았다 — 덧붙임 boardSanctionId·boardId·boardName·kind·until(쓰기 제한의 끝)·reasonCode·reasonText. 처리자는 싣지 않는다 */
    BOARD_SANCTION_ISSUED("system"),

    // ── 로보티콘(V142 — docs/features/emoticon-contract.md S1). 받는 사람 = 그 팩의 작가. 끌 수 없는 처리 알림, 누르면 스튜디오의 그 팩 ──
    /**
     * 내 로보티콘 팩이 처리됐다 — 덧붙임 {@code kind}(APPROVED 승인 · REJECTED 반려 · UPDATE_APPROVED 새 판 승인 · UPDATE_REJECTED 새 판 반려 · SUSPENDED 판매 중단 ·
     * RESUMED 다시 판매) · {@code packId} · {@code packName}(알릴 때 이름) · {@code note}(반려·판매 중단 사유)
     */
    EMOTICON_PACK("system"),
    /**
     * 로보티콘 유료 팩 구매(V146 — emoticon-contract 「후속 결정 반영(2026-11-05)」 2, 받는 사람 = 산 회원). 끌 수 없는 거래 알림. 덧붙임 {@code kind}(PAID 구매 완료 ·
     * AUTO_CANCELED 결제를 받아들이지 않아 자동 취소 · REFUNDED_SELF 셀프 환불 접수 · REFUNDED_EXCEPTION 운영자 예외 환불) · {@code purchaseId} · {@code packId} ·
     * {@code packName}(산 때 이름) · {@code amount}(원) · {@code path}(누르면 갈 곳 — 내 로보티콘 또는 그 팩)
     */
    EMOTICON_PURCHASE("system"),
    /** 보상 이벤트 보상을 받았다(V163 — event-admin-contract) — 덧붙임 {@code eventId}·{@code eventName}(알릴 때 이름)·{@code points}·{@code items}·{@code manual}(직접 전달 수). 누르면 내 포인트 */
    REWARD_EVENT_GRANTED("system"),
    /** 보상 이벤트 보상이 회수됐다 — 덧붙임 {@code eventId}·{@code eventName}·{@code reason}(운영자가 적은 사유). 누르면 내 포인트 */
    REWARD_EVENT_REVOKED("system"),
    /**
     * 내 글의 처리 상태가 바뀌었다(V166 — 운영 게시판 「처리 상태」, 바꾼 사람 = 그 게시판 매니저·스텝·운영자 회원 계정·관리자 앱 운영자, 본인이 바꾸면 보내지 않는다).
     * 덧붙임 {@code boardName}(알릴 때 게시판 이름)·{@code status}({@code PostProcessStatus} 이름)·{@code title}(알릴 때 글 제목). 누르면 그 글
     */
    POST_STATUS_CHANGED("system");

    private final String category;

    MemberNotificationType(String category) {
        this.category = category;
    }

    public String category() {
        return category;
    }

    public static Optional<MemberNotificationType> of(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
