package com.community.nicknameimage.dto.response;

/**
 * 신청 조건 한 줄 — user-web {@code RequirementItem}. 켜진 조건만 이 차례로 온다: {@code EMAIL_VERIFIED} → {@code GRADE} → {@code DAYS} → {@code ACTIVITY} → {@code POINTS}.
 * 종류마다 칸이 달라 종류별 record 로 둔다({@code kind} 로 가른다).
 */
public sealed interface NicknameImageRequirementItemView {

    String kind();

    boolean met();

    /** {@code {kind:'EMAIL_VERIFIED', met}} — 설정이 이메일 인증을 요구할 때만 */
    record Email(String kind, boolean met) implements NicknameImageRequirementItemView {

        public static Email of(boolean met) {
            return new Email("EMAIL_VERIFIED", met);
        }
    }

    /** {@code {kind:'GRADE', required, current, met}} — {@code required} 가 null 이면 고른 등급이 지워졌다(채울 수 없다) */
    record Grade(String kind, NicknameImageGradeView required, NicknameImageGradeView current, boolean met) implements NicknameImageRequirementItemView {

        public static Grade of(NicknameImageGradeView required, NicknameImageGradeView current, boolean met) {
            return new Grade("GRADE", required, current, met);
        }
    }

    /**
     * {@code {kind:'DAYS'|'ACTIVITY'|'POINTS', required, current, met}} — 가입 후 일수 · 보이는 글+댓글 수 · 포인트 잔액.
     * {@code ACTIVITY} 의 {@code current} 는 필요한 수에서 멈춘다(그 이상은 세지 않는다).
     */
    record Count(String kind, long required, long current, boolean met) implements NicknameImageRequirementItemView {

        public static Count of(String kind, long required, long current) {
            return new Count(kind, required, current, current >= required);
        }
    }
}
