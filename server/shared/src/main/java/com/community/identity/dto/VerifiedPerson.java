package com.community.identity.dto;

import java.time.LocalDate;

/**
 * 업체가 확인해 준 사람 — 확정 처리 동안만 메모리에 있다. CI·DI 원문은 해시({@code BlindIndex})로만 저장하고 버린다. {@code toString} 은 값을 싣지 않는다.
 *
 * @param ci           연계정보(없으면 null — 업체·PG 에 따라)
 * @param di           중복가입확인정보(없으면 null — KG이니시스는 주지 않는다, commerce.md 12-1)
 * @param birthDate    생년월일
 * @param providerTxId 업체의 인증 건 번호(유일 — 같은 결과로 두 번 묶지 못한다)
 * @param name         인증 이름(없으면 null) — 암호문으로만 저장한다(로보티콘 작가 정산 계좌의 예금주 대조, emoticon-contract S2 · V143)
 */
public record VerifiedPerson(String ci, String di, LocalDate birthDate, String providerTxId, String name) {

    public VerifiedPerson(String ci, String di, LocalDate birthDate, String providerTxId) {
        this(ci, di, birthDate, providerTxId, null);
    }

    @Override
    public String toString() {
        return "VerifiedPerson[ci=(감춤), di=(감춤), birthDate=(감춤), name=(감춤), providerTxId=" + providerTxId + "]";
    }
}
