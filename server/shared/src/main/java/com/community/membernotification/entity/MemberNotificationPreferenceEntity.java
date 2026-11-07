package com.community.membernotification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원이 이벤트마다 고른 알림 받기(앱 알림·이메일) — shared V67 의 {@code member_notification_preferences}(키 = 회원 + 이벤트 코드). 줄이 없으면 둘 다 켬이다.
 * 저장은 api {@code MemberNotificationPreferenceQueries#upsert}(한 문장 upsert), 발행기의 읽기는 세이브포인트 안이라 JDBC({@code MemberNotificationWriter#memberChoice})다.
 */
@Entity
@Table(name = "member_notification_preferences")
@IdClass(MemberNotificationPreferenceId.class)
public class MemberNotificationPreferenceEntity {

    @Id
    private Long userId;

    @Id
    private String eventCode;

    private boolean inApp;
    private boolean email;
    private Instant updatedAt;

    protected MemberNotificationPreferenceEntity() {
    }
}
