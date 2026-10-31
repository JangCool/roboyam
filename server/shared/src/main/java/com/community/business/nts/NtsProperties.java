package com.community.business.nts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.business.nts.* — 국세청 사업자 상태조회(business-contract 「심사 규칙」).
 *
 * @param provider   구현 — {@code odcloud}(공공데이터포털) · {@code fake}(local·시험 전용, <b>dev·prod 프로필에서는 기동을 멈춘다</b> — {@link NtsConfig}).
 *                   환경 변수 {@code ROBOYAM_NTS_PROVIDER}. 공통 기본값 odcloud, local 은 fake
 * @param serviceKey 공공데이터포털 일반 인증키(Decoding) — 환경 변수 {@code ROBOYAM_NTS_SERVICE_KEY}. 비면 모든 조회가 「조회 불가(키 없음)」다(기동은 한다)
 * @param baseUrl    공공데이터포털 주소(기본 {@code https://api.odcloud.kr})
 */
@ConfigurationProperties(prefix = "roboyam.business.nts")
public record NtsProperties(@DefaultValue("odcloud") NtsProviderKind provider, @DefaultValue("") String serviceKey,
                            @DefaultValue("https://api.odcloud.kr") String baseUrl) {

    /** 키 값은 싣지 않는다 */
    @Override
    public String toString() {
        return "NtsProperties[provider=" + provider + ", serviceKey=" + (serviceKey == null || serviceKey.isBlank() ? "none" : "set") + ", baseUrl=" + baseUrl + "]";
    }
}
