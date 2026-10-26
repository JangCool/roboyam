package com.community.datakey.batch;

import com.community.withdrawal.service.WithdrawnContactStore;
import java.util.List;

/**
 * {@code DataCipher}(ROBOYAM_DATA_KEY)로 암호화한 칸 목록 — <b>새 개인정보 칸을 {@code DataCipher} 로 저장하기 시작하면 여기에 한 줄 더한다</b>(배송지·환불 계좌·현금영수증 번호 등 —
 * 커머스 1~3단계). 빠뜨리면 키 교체 배치가 그 칸을 옮기지 않아, 예전 키를 비운 뒤 그 칸이 503 {@code DATA_KEY_MISSING} 이 된다.
 * 줄의 차례가 배치 단계의 차례다. 단계 이름·대상 종류는 번들 두 곳에 이름을 둔다(docs/features/commerce-foundation-contract.md 「0-4」).
 */
public final class DataKeyColumns {

    /** 탈퇴 회원 연락용 이메일(분리 보관 — commerce.md 17-2) */
    public static final EncryptedColumn WITHDRAWN_CONTACT_EMAIL = new EncryptedColumn("withdrawn-contact-email", "withdrawn_member_contacts", "user_id", "email_cipher",
            WithdrawnContactStore.EMAIL, "WITHDRAWN_CONTACT");

    public static final List<EncryptedColumn> ALL = List.of(WITHDRAWN_CONTACT_EMAIL);

    private DataKeyColumns() {
    }
}
