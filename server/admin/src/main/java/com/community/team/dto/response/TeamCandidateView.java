package com.community.team.dto.response;

/** 팀에 넣을 수 있는 운영자 — 지금 활성인 운영자. {@code alreadyIn} 은 이미 이 팀에 든 사람 */
public record TeamCandidateView(String operatorId, String operatorName, String email, boolean alreadyIn) {

    /** 이메일이 로그에 남지 않게 한다 */
    @Override
    public String toString() {
        return "TeamCandidateView[operatorId=" + operatorId + ", alreadyIn=" + alreadyIn + "]";
    }
}
