package com.community.member.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 회원 상세 — admin-web 의 {@code MemberDetail} 모양이다(목록 한 줄의 칸 + 상세 전용 칸). 서버가 가진 데이터로 채우고,
 * <b>기능·표가 아직 없는 칸은 null 또는 빈 배열</b>로 내려간다(아래 표). 프론트는 빈 구획을 「준비 중」 표시로 그린다.
 *
 * <p>실제 값이 있는 칸: 목록의 칸, {@code roles}(유형·부여 방식), {@code bio}, {@code social}, {@code withdrawnAt}, {@code piiMasked}, {@code consents}(이용약관·만 14세 확인).
 * 비어 있는 칸(아직 데이터가 없다):
 * <ul>
 *   <li>{@code statusReason}·{@code activity}·{@code sanctionSummary}·{@code reports}·{@code dormancy}·{@code adultConfirmed}·{@code acceptDm} — null</li>
 *   <li>{@code gradePoint}·{@code settings}·{@code contact}·{@code profile}·{@code groupBuy} — null (등급 이력·포인트, 알림·표시 설정, 연락처, 서명·관리자 메모, 공동구매 주문이 아직 없다.
 *       모양은 admin-web {@code MemberGradePoint}·{@code MemberSettings}·{@code MemberContact}·{@code MemberProfile}·{@code MemberGroupBuy} — 만들 때 이 필드 타입을 그 모양의 레코드로 바꾼다)</li>
 *   <li>{@code sanctions} — 빈 배열, {@code nicknameHistory} — 빈 배열({@code member.pii} 가 없으면 null)</li>
 * </ul>
 * 탈퇴한 회원은 축약해 내려간다 — 역할·소셜·동의·소개가 비고 {@code withdrawnAt} 만 채워진다.
 *
 * @param statusReason   지금 상태가 된 사유 — 사유를 저장하는 곳이 아직 없다(null)
 * @param activity       활동 요약(글·댓글·추천 …) — 글·댓글 표가 아직 없다(null). 모양은 admin-web {@code MemberActivity}
 * @param sanctionSummary 제재 요약 — null. 모양은 {@code MemberSanctionSummary}
 * @param reports        신고 요약 — null. 모양은 {@code MemberReportSummary}
 * @param dormancy       휴면 예정 안내 — null. 모양은 {@code MemberDormancy}
 * @param adultConfirmed 성인 확인 여부 — 출생연도 입력이 없다(null). 가입 때의 「만 14세 이상 확인」과는 다르다
 * @param acceptDm       쪽지 수신 여부 — null
 * @param nicknameHistory 닉네임 변경 이력 — {@code member.pii} 가 없으면 null
 * @param piiMasked      휴대폰·전화·주소·배송지가 가려져 왔는가({@code member.pii} 없음) — 그 칸들이 아직 없어 이메일 가림과 같은 값이다
 */
public record MemberDetailView(
        String memberNo, String email, boolean emailMasked, String nickname, String grade, String memberType, String status, String authMethod,
        Instant emailVerifiedAt, Instant joinedAt, Instant lastLoginAt, long point, List<MemberRoleDetail> roles, Instant dormantAt,
        Instant statusChangedAt, String boardRole, long sanctionCount,
        String statusReason, Object activity, List<MemberSanction> sanctions, Object sanctionSummary, Object reports,
        String bio, List<MemberSocialLink> social, Boolean adultConfirmed, Boolean acceptDm, Object dormancy, Instant withdrawnAt,
        List<MemberNicknameChange> nicknameHistory, boolean piiMasked,
        Object gradePoint, Object settings, Object contact, Object profile, MemberConsents consents, Object groupBuy) {
}
