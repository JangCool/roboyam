package com.community.portone.config;

import com.community.portone.dto.PortOneSettingKey;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.portone.seed.* — local 개발 PC 의 포트원 설정 시드(roboyam-shared-local.yml). DB 를 새로 만들어도(재설치) 포트원 설정이 등록된 상태로 뜨게 한다.
 * 값은 평문이고 {@link PortOneLocalSeeder} 가 암호화해 넣는다. 공통 설정(dev·prod)에는 없다 — 거기서는 관리자 화면에서 저장한다.
 *
 * @param enabled false 면 넣지 않는다 — Gradle 시험 작업이 끈다(앱 시험도 local 프로필로 뜨기 때문, roboyam.java-conventions)
 */
@ConfigurationProperties(prefix = "roboyam.portone.seed")
public record PortOneSeedProperties(@DefaultValue("true") boolean enabled, String storeId, String apiSecret, String identityChannelKey, String paymentChannelKey,
                                    String webhookSecret) {

    /** 넣을 칸 — 값이 있는 칸만(선언 차례), 꺼져 있으면 없음 */
    public Map<PortOneSettingKey, String> values() {
        Map<PortOneSettingKey, String> out = new EnumMap<>(PortOneSettingKey.class);
        if (!enabled) {
            return out;
        }
        put(out, PortOneSettingKey.STORE_ID, storeId);
        put(out, PortOneSettingKey.API_SECRET, apiSecret);
        put(out, PortOneSettingKey.IDENTITY_CHANNEL_KEY, identityChannelKey);
        put(out, PortOneSettingKey.PAYMENT_CHANNEL_KEY, paymentChannelKey);
        put(out, PortOneSettingKey.WEBHOOK_SECRET, webhookSecret);
        return out;
    }

    private static void put(Map<PortOneSettingKey, String> out, PortOneSettingKey key, String value) {
        if (value != null && !value.isBlank()) {
            out.put(key, value.strip());
        }
    }

    @Override
    public String toString() {
        return "PortOneSeedProperties[" + values().keySet() + "]"; // 값은 싣지 않는다
    }
}
