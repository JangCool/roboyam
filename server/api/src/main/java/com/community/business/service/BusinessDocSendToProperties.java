package com.community.business.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.biz.doc-send-to.* — 사업자등록증을 우편·팩스로 보낼 곳(biz-doc-intake-contract). 기본은 비어 있고(배포 설정 값), 주소와 팩스 번호가 둘 다 비면 「우편·팩스로 보내기」를
 * 받지 않는다(신청 400 {@code fields.certificateDelivery}). 관리자 설정 화면은 후속.
 *
 * @param postcode  우편번호
 * @param address   우편 주소
 * @param recipient 받는 사람(부서)
 * @param fax       팩스 번호
 */
@ConfigurationProperties(prefix = "roboyam.biz.doc-send-to")
public record BusinessDocSendToProperties(@DefaultValue("") String postcode, @DefaultValue("") String address, @DefaultValue("") String recipient,
                                          @DefaultValue("") String fax) {

    /** 보낼 곳이 하나라도 있는가(우편 주소 또는 팩스 번호) */
    public boolean available() {
        return !blank(address) || !blank(fax);
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
