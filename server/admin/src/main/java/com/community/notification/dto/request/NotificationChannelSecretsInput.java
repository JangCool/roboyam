package com.community.notification.dto.request;

/**
 * 입력한 비밀 — 봇 토큰(Slack · Mattermost 봇 방식), 웹훅 주소(Mattermost 웹훅 방식). <b>고칠 때 보내지 않거나 비워 두면 저장된 값을 그대로 쓴다.</b>
 * 로그·오류에 값이 남지 않게 {@code toString} 에 값을 싣지 않는다.
 */
public record NotificationChannelSecretsInput(String botToken, String webhookUrl) {

    @Override
    public String toString() {
        return "NotificationChannelSecretsInput[botToken=(감춤), webhookUrl=(감춤)]";
    }
}
