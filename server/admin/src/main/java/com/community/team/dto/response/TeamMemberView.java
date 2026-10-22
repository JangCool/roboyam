package com.community.team.dto.response;

/**
 * 팀원 한 사람. {@code active} 는 지금 로그인할 수 있는 운영자인가 — false 면(사용 중지·잠금·기간 만료 등) 팀에는 남아 있지만 알림을 받지 않는다.
 * 후보 목록({@code candidates})에서는 늘 true 다.
 */
public record TeamMemberView(String operatorId, String operatorName, String email, boolean active) {

    /** 이메일이 로그에 남지 않게 한다 */
    @Override
    public String toString() {
        return "TeamMemberView[operatorId=" + operatorId + ", active=" + active + "]";
    }
}
