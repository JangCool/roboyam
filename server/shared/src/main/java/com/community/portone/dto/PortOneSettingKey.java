package com.community.portone.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 포트원 설정 칸(표 {@code portone_settings} 의 {@code key}) — 코드가 원천이다. 0단계는 본인인증 칸 셋, 2a 에서 결제 칸이 같은 표에 더해진다(그래서 DB 에 키 값 CHECK 가 없다).
 * 키 문자열({@link #key()})은 화면·감사가 쓰는 이름이라 한 번 정하면 바꾸지 않는다.
 */
public enum PortOneSettingKey {

    /** 상점 ID */
    STORE_ID("storeId", Group.IDENTITY),
    /** V2 API 비밀키(API Secret) — 결제(2a)도 같은 값을 쓴다 */
    API_SECRET("apiSecret", Group.IDENTITY),
    /** 본인인증 채널 키 */
    IDENTITY_CHANNEL_KEY("identityChannelKey", Group.IDENTITY);

    /** 화면의 카드 */
    public enum Group {
        IDENTITY
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
