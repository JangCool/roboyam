package com.community.membernotification.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link MemberNotificationPreferenceEntity} 의 복합 키(회원 + 이벤트 코드) */
public class MemberNotificationPreferenceId implements Serializable {

    private Long userId;
    private String eventCode;

    protected MemberNotificationPreferenceId() {
    }

    public MemberNotificationPreferenceId(Long userId, String eventCode) {
        this.userId = userId;
        this.eventCode = eventCode;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MemberNotificationPreferenceId that && Objects.equals(userId, that.userId) && Objects.equals(eventCode, that.eventCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, eventCode);
    }
}
