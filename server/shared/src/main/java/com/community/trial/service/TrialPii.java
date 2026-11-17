package com.community.trial.service;

import com.community.zconfiguration.crypto.DataPurpose;

/**
 * 체험단 개인정보 암호 용도(V241 — {@code DataCipher}, 새 칸은 {@code DataKeyColumns} 에 등록). 신청 배송지 사본은 회원 주소록(member.address.*)·공구 참여(groupbuy.address.*)와
 * 용도를 따로 둔다(AAD — 다른 표의 암호문을 옮겨 붙여도 풀리지 않게). 구매 인증 사진은 바이트 암호문.
 */
public final class TrialPii {

    public static final DataPurpose RECIPIENT = DataPurpose.of("trial.address.recipient");
    public static final DataPurpose PHONE = DataPurpose.of("trial.address.phone");
    public static final DataPurpose ADDRESS1 = DataPurpose.of("trial.address.line1");
    public static final DataPurpose ADDRESS2 = DataPurpose.of("trial.address.line2");
    public static final DataPurpose MEMO = DataPurpose.of("trial.address.memo");
    public static final DataPurpose PROOF = DataPurpose.of("trial.purchase-proof");

    private TrialPii() {
    }
}
