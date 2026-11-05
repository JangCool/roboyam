package com.community.datakey.batch;

import com.community.emoticon.service.EmoticonPayoutService;
import com.community.identity.service.IdentityVerificationService;
import com.community.withdrawal.service.WithdrawnContactStore;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code DataCipher}(ROBOYAM_DATA_KEY)로 암호화한 칸 목록 — <b>새 개인정보 칸을 {@code DataCipher} 로 저장하기 시작하면 여기에 한 줄 더한다</b>(배송지·환불 계좌·현금영수증 번호 등 —
 * 커머스 1~3단계). 빠뜨리면 키 교체 배치가 그 칸을 옮기지 않아, 예전 키를 비운 뒤 그 칸이 503 {@code DATA_KEY_MISSING} 이 된다.
 * 줄의 차례가 배치 단계의 차례다. 단계 이름·대상 종류는 번들 두 곳에 이름을 둔다(docs/features/commerce-foundation-contract.md 「0-4」).
 * 용도는 쓰는 쪽의 상수를 그대로 가리킨다 — 용도가 다르면 풀리지 않는다(AAD). 비운 칸(NULL)은 고르지 않는다.
 */
public final class DataKeyColumns {

    /** 탈퇴 회원 연락용 이메일(분리 보관 — commerce.md 17-2) */
    public static final EncryptedColumn WITHDRAWN_CONTACT_EMAIL = new EncryptedColumn("withdrawn-contact-email", "withdrawn_member_contacts", "user_id", "email_cipher",
            WithdrawnContactStore.EMAIL, "WITHDRAWN_CONTACT");

    /** 본인인증 이름(V141 — 로보티콘 작가 정산 계좌의 예금주 대조, emoticon-contract S2). 이 칸 앞에 인증한 회원은 NULL */
    public static final EncryptedColumn IDENTITY_NAME = new EncryptedColumn("identity-name", "identity_verifications", "user_id", "name_enc",
            IdentityVerificationService.NAME, "IDENTITY_VERIFICATION");

    /** 로보티콘 작가 정산 정보(V141 — emoticon-contract S2). 사업자 번호는 사업자 작가만(그 밖 NULL) */
    public static final List<EncryptedColumn> EMOTICON_PAYOUT = List.of(
            new EncryptedColumn("emoticon-payout-account", "emoticon_payout_accounts", "user_id", "account_number_enc", EmoticonPayoutService.ACCOUNT,
                    "EMOTICON_PAYOUT_ACCOUNT"),
            new EncryptedColumn("emoticon-payout-holder", "emoticon_payout_accounts", "user_id", "holder_name_enc", EmoticonPayoutService.HOLDER,
                    "EMOTICON_PAYOUT_ACCOUNT"),
            new EncryptedColumn("emoticon-payout-business-number", "emoticon_payout_accounts", "user_id", "business_number_enc", EmoticonPayoutService.BUSINESS_NUMBER,
                    "EMOTICON_PAYOUT_ACCOUNT"));

    public static final List<EncryptedColumn> ALL = all();

    private DataKeyColumns() {
    }

    private static List<EncryptedColumn> all() {
        List<EncryptedColumn> all = new ArrayList<>();
        all.add(WITHDRAWN_CONTACT_EMAIL);
        all.add(IDENTITY_NAME);
        all.addAll(EMOTICON_PAYOUT);
        return List.copyOf(all);
    }
}
