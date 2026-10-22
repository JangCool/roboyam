package com.community.notification.dto.request;

/**
 * 알림 도구 만들기·고치기. {@code type} 은 만들 때 정하고 고칠 때는 같은 값이어야 한다(바꿀 수 없다). 사용 여부를 안 보내면 만들 때는 사용, 고칠 때는 그대로다.
 * 검사는 서비스가 한다(칸마다 요청 언어 문구가 나간다). 비밀({@code secrets})은 로그·오류에 남지 않게 {@code toString} 을 재정의한다.
 */
public record NotificationChannelSaveRequest(String type, String name, String description, Boolean enabled, NotificationChannelConfigInput config,
                                             NotificationChannelSecretsInput secrets) {

    @Override
    public String toString() {
        return "NotificationChannelSaveRequest[type=" + type + ", secrets=(감춤)]";
    }
}
