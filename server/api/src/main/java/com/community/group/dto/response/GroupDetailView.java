package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/groups/{id}} — 소모임 상세(community {@code GroupDetail}). 비활성({@code status: INACTIVE})이면 {@code id}·{@code name}·{@code status} 만 값이 있고 나머지는 null(목록 칸은 빈 값).
 *
 * @param description            소개 전문
 * @param board                  소모임 게시판(말머리·투자 고지 — 글쓰기는 2단계)
 * @param leader                 모임장
 * @param stockNotice            소모임 게시판이 투자 고지 대상
 * @param myState                NONE · MEMBER · LEADER · APPLIED · REJECTED · KICKED — 비로그인 null
 * @param myRole                 LEADER · MANAGER · MEMBER · null(비멤버·비회원)
 * @param join                   지금 가입·신청할 수 있나(요청 전에 단추 상태)
 * @param chat                   멤버에게만 채팅 링크, 비멤버·비회원 null
 * @param chatLocked             채팅을 볼 수 없다(비멤버·비회원)
 * @param permissions            관리 권한(역할·맡은 일만 — 정지·미인증은 {@code manageBlockedReason})
 * @param managerInfo            멤버에게만 {@code {count, limit, limitMax}}
 * @param newcomers              멤버에게만 최근 가입자
 * @param pendingApplicationCount 모임장·가입 신청을 맡은 매니저에게만 승인 대기 수, 그 밖 null
 * @param limits                 지금 적용되는 운영 한도(정원 범위·일정 한도)
 * @param postCount              보는 사람에게 보이는 소모임 글 수 — 멤버는 모든 보이는 글, 비멤버·비회원은 공개로 전환된 글(지운·숨긴·나가서 가린 글 제외, 2단계)
 * @param newPostCount           멤버에게만 — 내가 글 탭을 마지막으로 본 뒤(본 적이 없으면 가입 뒤) 올라온 남의 글 수, 비멤버·비회원 null(2단계)
 * @param pendingPublishRequestCount 공개 요청을 처리할 사람({@code permissions.canDecidePublishRequests})에게만 처리 대기 수(정지 중에도 값이 온다 — 지워졌거나 가려진 글의 요청은 빼고), 그 밖 null(5단계)
 * @param visibilityInfo         모임장에게만 공개 범위 변경 정보, 그 밖 null(5단계)
 * @param dissolveInfo           모임장에게만 지금 해산하면 패널티가 걸리는지(V96), 그 밖 null
 */
public record GroupDetailView(String id, String name, String description, GroupCategoryRefView category, String joinType, Integer capacity, Integer memberCount,
                              Boolean recruiting, String recruitState, Boolean offline, String visibility, Board board, String status, Instant openedAt,
                              DmPartnerView leader, Boolean stockNotice, String myState, String myRole, Join join, Chat chat, Boolean chatLocked,
                              Permissions permissions, ManagerInfo managerInfo, Newcomers newcomers, Long pendingApplicationCount, Limits limits,
                              Long postCount, Long newPostCount, Long pendingPublishRequestCount, VisibilityInfo visibilityInfo, DissolveInfo dissolveInfo) {

    /** 비활성 소모임 — 안내 화면에 쓸 칸만 */
    public static GroupDetailView inactive(String id, String name) {
        return new GroupDetailView(id, name, null, null, null, null, null, null, null, null, null, null, "INACTIVE", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /** 소모임 게시판 — 켜진 말머리만(순서대로) */
    public record Board(String slug, String name, String nameEn, String color, boolean investmentNotice, List<Prefix> prefixes) {
    }

    public record Prefix(String id, String name, boolean staffOnly, boolean common, String color) {
    }

    /**
     * @param allowed           지금 할 수 있나
     * @param method            JOIN(바로 가입) · APPLY(신청 — 신청제이거나 강퇴됐던 회원)
     * @param reason            못 할 때 LOGIN_REQUIRED · EMAIL_NOT_VERIFIED · ACCOUNT_RESTRICTED · ALREADY_MEMBER · ALREADY_APPLIED · CLOSED · FULL · REAPPLY_LATER
     * @param retryAfterSeconds REAPPLY_LATER 일 때 남은 초
     */
    public record Join(boolean allowed, String method, String reason, Long retryAfterSeconds) {
    }

    public record Chat(List<ChatLink> links) {
    }

    /** @param kind KAKAO · DISCORD */
    public record ChatLink(String kind, String url, String label) {
    }

    /**
     * @param canManage             모임장 또는 매니저(관리 화면)
     * @param canWritePost          멤버이고 정지 중이 아님(소모임 글쓰기 — 서버는 쓸 때 이메일 인증·사업자 회원·post.write 까지 다시 본다)
     * @param canEditGroup          모임장 — 정보 수정·채팅 링크·모임장 넘기기·해산
     * @param canAppointManager     모임장이고 매니저 기능이 켜짐(3단계)
     * @param manageBlockedReason   모임장·매니저가 이메일 인증 전이면 EMAIL_NOT_VERIFIED, 정지면 ACCOUNT_RESTRICTED(쓰기 단추를 끈다), 그 밖 null
     * @param canChangeVisibility   모임장(역할만 — 정지·미인증은 {@code manageBlockedReason}). 공개 범위 기능이 꺼져 있으면 공개 소모임의 모임장에게만 true(공개→비공개)(5단계)
     * @param canDecidePublishRequests 공개 범위 기능이 켜져 있고 모임장 또는 「글 공개 처리·되돌리기」 매니저(5단계)
     */
    public record Permissions(boolean canManage, boolean canWritePost, boolean canEditGroup, boolean canAppointManager, boolean canHandleApplications,
                              boolean canKickMembers, boolean canSetRecruiting, boolean canManageEvents, boolean canUnpublishPosts, String manageBlockedReason,
                              boolean canChangeVisibility, boolean canDecidePublishRequests) {
    }

    /**
     * 모임장에게만 싣는 공개 범위 정보(community {@code VisibilityInfo}).
     *
     * @param changedAt           마지막 변경 시각(개설 때 정한 값이면 null)
     * @param changedBy           변경한 모임장(없으면 null)
     * @param canChangeToPublicAt 비공개→공개 쿨다운이 끝나는 시각 — 지금 바꿀 수 있으면 null
     */
    public record VisibilityInfo(Instant changedAt, DmPartnerView changedBy, Instant canChangeToPublicAt) {
    }

    /**
     * 모임장에게만 싣는 해산 패널티 정보(V96 — 해산 확인 창). 지금 해산하면 패널티가 걸리는가 = {@code penaltyDays > 0 && !exempt}.
     *
     * @param penaltyDays 설정의 해산 패널티 일수(0 = 끔)
     * @param exempt      개설부터 1년(KST 달력) 이상 유지돼 지금 해산하면 패널티가 없다
     * @param exemptAt    면제가 시작되는 시각(개설 시각 + 1년) — 이미 면제여도 값이 온다
     */
    public record DissolveInfo(int penaltyDays, boolean exempt, Instant exemptAt) {
    }

    /** @param limit 유효 상한 = min(소모임 상한, 최대 인원) */
    public record ManagerInfo(int count, int limit, int limitMax) {
    }

    /** @param count 기간 안에 가입한 지금 멤버 수(모임장 제외) · {@code items} 는 그중 최근 5명 */
    public record Newcomers(int windowDays, long count, List<DmPartnerView> items) {
    }

    public record Limits(int capacityMin, int capacityMax, int eventMaxDaysAhead, int eventMaxLengthDays) {
    }
}
