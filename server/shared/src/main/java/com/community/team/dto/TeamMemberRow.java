package com.community.team.dto;

/** 팀원 한 줄(팀원 조회 결과) — {@code active} 는 지금 로그인할 수 있는 운영자인가(멈춘 계정의 팀원은 알림을 받지 않는다) */
public record TeamMemberRow(long teamId, long operatorId, String operatorName, String email, boolean active) {

    /** 이메일이 로그에 남지 않게 한다 */
    @Override
    public String toString() {
        return "TeamMemberRow[teamId=" + teamId + ", operatorId=" + operatorId + ", active=" + active + "]";
    }
}
