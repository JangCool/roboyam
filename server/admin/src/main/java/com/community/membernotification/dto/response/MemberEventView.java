package com.community.membernotification.dto.response;

/**
 * 회원 이벤트 관리 화면의 한 줄(admin-web {@code MemberEventView}). 정의(영역·받는 사람·잠금·준비 중)는 코드({@code MemberEvent})에 고정이고 운영자가 바꾸는 것은
 * 사용 여부 · 앱 알림 · 이메일 · 회원이 끌 수 있음뿐이다.
 *
 * @param area             영역 id({@code content} · {@code member} · {@code safety} — 운영자 이벤트와 같은 값)
 * @param name             요청 언어로 푼 이름
 * @param recipientText    누가 받는지 한 줄(요청 언어)
 * @param featureReady     실제로 발행되는가(false 면 「준비 중」 — 바꿀 수 없다)
 * @param email            이메일로도 보낸다 — 커밋 뒤 회원 알림 메일({@code MemberNotificationEmailSender}, 이메일 인증된 회원만 · 조용한 시간 제외)
 * @param memberCanTurnOff 회원이 개인 설정에서 끌 수 있다 — {@code lockReason} 이 있으면 늘 false
 * @param lockReason       끌 수 있는지를 코드가 잠근 까닭({@code ACCOUNT} · {@code ACTION}) — null 이면 운영자가 정한다
 * @param boardSettingKind 예전 게시판 기본 설정 「알림」의 같은 종류(V58 이 옮긴 옛 칸) — 없으면 null
 * @param sentLast30Days   최근 30일 동안 만든 회원 알림 수(알림함 줄 수)
 */
public record MemberEventView(String code, String area, String name, String description, String recipientText, boolean featureReady, boolean enabled,
                              boolean inApp, boolean email, boolean memberCanTurnOff, String lockReason, String boardSettingKind, long sentLast30Days) {
}
