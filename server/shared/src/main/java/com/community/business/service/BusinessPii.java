package com.community.business.service;

import com.community.business.entity.BusinessProfileEntity;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import org.springframework.stereotype.Component;

/**
 * 사업자 신원의 개인정보 칸 잠그기·풀기 — 용도 이름({@link DataPurpose})은 한 번 정하면 바꾸지 않는다(바꾸면 저장한 암호문을 읽을 수 없다).
 * 등록번호 비교는 {@link BlindIndex}(HMAC) — 정규화(숫자 10자리)는 {@link BusinessFields#digits} 가 한다. 평문은 로그·감사 detail·예외 문구에 싣지 않는다.
 */
@Component
public class BusinessPii {

    public static final DataPurpose BIZ_NO = DataPurpose.of("business.biz-no");
    public static final DataPurpose OWNER = DataPurpose.of("business.owner");
    public static final DataPurpose PHONE = DataPurpose.of("business.phone");
    public static final DataPurpose EMAIL = DataPurpose.of("business.email");
    public static final DataPurpose ADDRESS1 = DataPurpose.of("business.address1");
    public static final DataPurpose ADDRESS2 = DataPurpose.of("business.address2");
    public static final DataPurpose CERTIFICATE = DataPurpose.of("business.certificate");

    /** 푼 신원 칸 — 화면에 그대로 내지 않는다(가린 값·열람 기록 뒤 평문) */
    public record Plain(String bizNo, String ownerName, String phone, String email, String postcode, String address1, String address2) {
    }

    private final DataCipher cipher;
    private final BlindIndex index;

    public BusinessPii(DataCipher cipher, BlindIndex index) {
        this.cipher = cipher;
        this.index = index;
    }

    /** 키가 둘 다 있는가 — 없으면 503 {@code DATA_KEY_MISSING}·{@code INDEX_KEY_MISSING} */
    public void requireConfigured() {
        cipher.requireConfigured();
        index.requireConfigured();
    }

    /** 데이터 키가 있는가(없으면 상세는 가린 값을 비워 보인다) */
    public boolean isReady() {
        return cipher.isConfigured();
    }

    /** 해시 키가 있는가(없으면 목록 검색이 등록번호로 찾지 않는다) */
    public boolean isIndexReady() {
        return index.isConfigured();
    }

    public String encrypt(DataPurpose purpose, String plain) {
        return plain == null || plain.isEmpty() ? null : cipher.encrypt(purpose, plain);
    }

    public String decrypt(DataPurpose purpose, String cipherText) {
        return cipherText == null ? null : cipher.decrypt(purpose, cipherText);
    }

    /** 등록번호 해시(숫자 10자리로 정규화한 값) */
    public String bizNoHash(String digits) {
        return index.hash(BIZ_NO, digits);
    }

    public byte[] encryptFile(byte[] content) {
        return cipher.encryptBytes(CERTIFICATE, content);
    }

    public byte[] decryptFile(byte[] content) {
        return cipher.decryptBytes(CERTIFICATE, content);
    }

    /** 신원 칸을 모두 푼다 — 파기된 줄이면 null */
    public Plain plain(BusinessProfileEntity p) {
        if (p.getErasedAt() != null || p.getBizNoEnc() == null) {
            return null;
        }
        return new Plain(decrypt(BIZ_NO, p.getBizNoEnc()), decrypt(OWNER, p.getOwnerNameEnc()), decrypt(PHONE, p.getPhoneEnc()), decrypt(EMAIL, p.getEmailEnc()),
                p.getPostcode(), decrypt(ADDRESS1, p.getAddress1Enc()), decrypt(ADDRESS2, p.getAddress2Enc()));
    }
}
