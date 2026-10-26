package com.community.point.dto;

/** 조정 대상 후보의 원본 — 이메일은 아직 가리지 않은 값이라 응답에 그대로 싣지 않는다. 잔액 행이 없으면 세 잔액 모두 0 */
public record PointMemberRow(Long id, String nickname, String email, Long balance, Long freeBalance, Long paidBalance) {

    /** 이메일을 로그에 남기지 않는다 */
    @Override
    public String toString() {
        return "PointMemberRow[id=" + id + "]";
    }
}
