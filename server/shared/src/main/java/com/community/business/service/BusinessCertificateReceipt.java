package com.community.business.service;

import com.community.business.entity.BusinessProfileEntity;
import com.community.business.repository.master.BusinessStore;
import com.community.docintake.repository.master.DocIntakeStore;
import org.springframework.stereotype.Service;

/**
 * 사업자등록증을 받았는가(biz-doc-intake-contract) — 지금 등록증 사본(파기되지 않음)이 있거나, 이번 신청(재신청·재심사 포함 — {@code submitted_at}) 뒤에 기록한 잘못 기록이 아닌
 * 등록증 종이 접수가 있으면 받은 것이다. 「우편·팩스로 보냄」 신청의 승인 잠금(409 {@code BUSINESS_CERT_NOT_RECEIVED})과 회원 화면 「받았어요」가 같은 판정을 쓴다.
 */
@Service
public class BusinessCertificateReceipt {

    private final BusinessStore store;
    private final DocIntakeStore intakes;

    public BusinessCertificateReceipt(BusinessStore store, DocIntakeStore intakes) {
        this.store = store;
        this.intakes = intakes;
    }

    public boolean received(BusinessProfileEntity p) {
        if (p.getCertificateId() != null && store.document(p.getCertificateId()).filter(d -> d.erasedAt() == null).isPresent()) {
            return true;
        }
        return p.getSubmittedAt() != null && intakes.hasCertificateSince(p.getId(), p.getSubmittedAt());
    }
}
