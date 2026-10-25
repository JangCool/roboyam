package com.community.member.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 회원 상세 — admin-web 의 {@code MemberDetail} 모양이다(목록 한 줄의 칸 + 상세 전용 칸). 서버가 가진 데이터로 채우고,
 * <b>기능·표가 아직 없는 칸은 null 또는 빈 배열</b>로 내려간다(아래 표). 프론트는 빈 구획을 「준비 중」 표시로 그린다.
 *
 * <p>실제 값이 있는 칸: 목록의 칸, {@code roles}(유형·부여 방식), {@code bio}, {@code social}, {@code withdrawnAt}, {@code piiMasked},
 * {@code consents}(이용약관·개인정보처리방침 — 동의 버전 {@code T{n}.P{m}} 을 나눠서, 만 14세 확인),
 * {@code settings}(표시·보기·알림 설정, 차단·차단 단어·메모 수, 알림함 건수 — {@link MemberSettingsView}), {@code acceptDm}(쪽지 받기),
 * {@code nicknameChange}(닉네임 변경 횟수·무료 한도), {@code nicknameImage}(적용 중인 그림·최근 신청),
 * {@code activity}(글·댓글 수·받은 추천 합 — 인기글·접속 일수는 0), {@code reports}(받은 신고·조치된 신고·한 신고 수),
 * {@code point}(잔액), {@code gradePoint}(승급 상태·다음 등급 달성도·등급 변경 이력·최근 포인트 내역 — 닉네임 변경권은 교환 기능이 없어 0).
 * 비어 있는 칸(아직 데이터가 없다):
 * <ul>
 *   <li>{@code statusReason}·{@code sanctionSummary}·{@code dormancy}·{@code adultConfirmed} — null</li>
 *   <li>{@code contact}·{@code profile}·{@code groupBuy} — null (연락처, 서명·관리자 메모, 공동구매 주문이 아직 없다.
 *       모양은 admin-web {@code MemberContact}·{@code MemberProfile}·{@code MemberGroupBuy} — 만들 때 이 필드 타입을 그 모양의 레코드로 바꾼다)</li>
 *   <li>{@code settings.pushSubscribed} — null(웹 푸시가 없다)</li>
 *   <li>{@code sanctions} — 빈 배열</li>
 * </ul>
 * 탈퇴한 회원은 축약해 내려간다 — 역할·소셜·동의·소개·활동 요약·신고 요약·등급 구획·개인 설정·쪽지 받기·닉네임 변경 횟수·닉네임 이미지가 비고 {@code withdrawnAt} 만 채워진다.
 *
 * @param statusReason   지금 상태가 된 사유 — 사유를 저장하는 곳이 아직 없다(null)
 * @param activity       활동 요약(글·댓글·받은 추천 …) — 탈퇴한 회원은 null. 모양은 admin-web {@code MemberActivity}
 * @param gradePoint     등급·포인트 구획 — 탈퇴한 회원은 null. 모양은 admin-web {@code MemberGradePoint}
 * @param sanctionSummary 제재 요약 — null. 모양은 {@code MemberSanctionSummary}
 * @param reports        신고 요약 — 탈퇴한 회원은 null. 모양은 admin-web {@code MemberReportSummary}
 * @param dormancy       휴면 예정 안내 — null. 모양은 {@code MemberDormancy}
 * @param adultConfirmed 성인 확인 여부 — 출생연도 입력이 없다(null). 가입 때의 「만 14세 이상 확인」과는 다르다
 * @param acceptDm       쪽지 수신 여부({@code dm_user_states.accept_dm}, 행이 없으면 true) — 탈퇴한 회원은 null
 * @param nicknameHistory 닉네임 변경 이력(최신이 위, 최근 20줄) — {@code member.pii} 가 없으면 null, 탈퇴한 회원은 빈 배열
 * @param piiMasked      휴대폰·전화·주소·배송지가 가려져 왔는가({@code member.pii} 없음) — 그 칸들이 아직 없어 이메일 가림과 같은 값이다
 * @param settings       개인 설정·알림함 구획 — 탈퇴한 회원은 null
 * @param nicknameChange 닉네임 변경 횟수·무료 한도 — 탈퇴한 회원은 null
 * @param nicknameImage  닉네임 이미지(적용 중·최근 대기/반려 신청) — 탈퇴한 회원은 null
 */
public record MemberDetailView(
        String memberNo, String email, boolean emailMasked, String nickname, String grade, String memberType, String status, String authMethod,
        Instant emailVerifiedAt, Instant joinedAt, Instant lastLoginAt, long point, List<MemberRoleDetail> roles, Instant dormantAt,
        Instant statusChangedAt, String boardRole, long sanctionCount,
        String statusReason, MemberActivity activity, List<MemberSanction> sanctions, Object sanctionSummary, MemberReportSummary reports,
        String bio, List<MemberSocialLink> social, Boolean adultConfirmed, Boolean acceptDm, Object dormancy, Instant withdrawnAt,
        List<MemberNicknameChange> nicknameHistory, boolean piiMasked,
        MemberGradePoint gradePoint, MemberSettingsView settings, Object contact, Object profile, MemberConsents consents, Object groupBuy,
        MemberNicknameChangeUsage nicknameChange, MemberNicknameImageView nicknameImage) {
}
