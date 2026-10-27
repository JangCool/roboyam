package com.community.portone.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 포트원 설정 칸(표 {@code portone_settings} 의 {@code key}) — 코드가 원천이다. 0단계는 본인인증 칸 셋, 2a 에서 결제 칸 둘이 같은 표에 더해졌다(그래서 DB 에 키 값 CHECK 가 없다).
 * 키 문자열({@link #key()})은 화면·감사가 쓰는 이름이라 한 번 정하면 바꾸지 않는다. 묶음은 2a 에서 공통·본인인증·결제로 나눴다(결정 14 — 키 문자열은 그대로).
 */
public enum PortOneSettingKey {

    /** 상점 ID — 본인인증·결제가 같이 쓴다 */
    STORE_ID("storeId", Group.COMMON),
    /** V2 API 비밀키(API Secret) — 본인인증·결제가 같이 쓴다 */
    API_SECRET("apiSecret", Group.COMMON),
    /** 본인인증 채널 키 */
    IDENTITY_CHANNEL_KEY("identityChannelKey", Group.IDENTITY),
    /** 결제 채널 키(2a — 하나, 결정 15) */
    PAYMENT_CHANNEL_KEY("paymentChannelKey", Group.PAYMENT),
    /** 웹훅 비밀키(2a — Standard Webhooks {@code whsec_…}) */
    WEBHOOK_SECRET("webhookSecret", Group.PAYMENT);

    /** 화면의 카드 */
    public enum Group {
        /** 공통(상점 ID·API 비밀키) */
        COMMON,
        IDENTITY,
        PAYMENT
    }

    /** 본인인증에 필요한 칸(공통 + 본인인증) */
    public static List<PortOneSettingKey> identityKeys() {
        return List.of(STORE_ID, API_SECRET, IDENTITY_CHANNEL_KEY);
    }

    /** 결제에 필요한 칸(공통 + 결제 — {@code paymentReady} 의 4칸) */
    public static List<PortOneSettingKey> paymentKeys() {
        return List.of(STORE_ID, API_SECRET, PAYMENT_CHANNEL_KEY, WEBHOOK_SECRET);
    }

    private final String key;
    private final Group group;

    PortOneSettingKey(String key, Group group) {
        this.key = key;
        this.group = group;
    }

    public String key() {
        return key;
    }

    public Group group() {
        return group;
    }

    /** 그 카드의 칸(선언 차례) */
    public static List<PortOneSettingKey> of(Group group) {
        return Arrays.stream(values()).filter(k -> k.group == group).toList();
    }

    public static Optional<PortOneSettingKey> ofKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
