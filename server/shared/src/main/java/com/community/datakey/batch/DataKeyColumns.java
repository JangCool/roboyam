package com.community.datakey.batch;

import com.community.ad.service.AdAdvertiserService;
import com.community.addressbook.service.AddressBookService;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.business.service.BusinessPii;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.groupbuy.service.BankTransferService;
import com.community.groupbuy.service.GroupBuyJoinService;
import com.community.identity.service.IdentityVerificationService;
import com.community.withdrawal.service.WithdrawnContactStore;
import com.community.zconfiguration.crypto.DataPurpose;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code DataCipher}(IROBIT_DATA_KEY)로 암호화한 칸 목록 — <b>새 개인정보 칸을 {@code DataCipher} 로 저장하기 시작하면 여기에 한 줄 더한다</b>(배송지·환불 계좌·현금영수증 번호 등 —
 * 커머스 1~3단계). 빠뜨리면 키 교체 배치가 그 칸을 옮기지 않아, 예전 키를 비운 뒤 그 칸이 503 {@code DATA_KEY_MISSING} 이 된다.
 * 줄의 차례가 배치 단계의 차례다. 단계 이름·대상 종류는 번들 두 곳에 이름을 둔다(docs/features/commerce-foundation-contract.md 「0-4」).
 * 용도는 쓰는 쪽의 상수를 그대로 가리킨다 — 용도가 다르면 풀리지 않는다(AAD). 마스킹·파기로 비운 칸(NULL)은 고르지 않는다.
 *
 * <p>스키마에 {@code *_enc}·{@code *_cipher} 칸이나 {@code bytea} 칸이 새로 생기면 {@code DataKeyRotationJobTest} 가 여기에도, 다른 키·평문 칸 목록에도 없는 칸을 잡는다.
 * 파일 바이트 칸({@code DataCipher#encryptBytes})은 {@link EncryptedColumn#ofBytes} 로 올린다.
 */
public final class DataKeyColumns {

    /** 탈퇴 회원 연락용 이메일(분리 보관 — commerce.md 17-2) */
    public static final EncryptedColumn WITHDRAWN_CONTACT_EMAIL = new EncryptedColumn("withdrawn-contact-email", "withdrawn_member_contacts", "user_id", "email_cipher",
            WithdrawnContactStore.EMAIL, "WITHDRAWN_CONTACT");

    /** 본인인증 이름(V143 — 로보티콘 작가 정산 계좌의 예금주 대조, emoticon-contract S2). 이 칸 앞에 인증한 회원은 NULL */
    public static final EncryptedColumn IDENTITY_NAME = new EncryptedColumn("identity-name", "identity_verifications", "user_id", "name_enc",
            IdentityVerificationService.NAME, "IDENTITY_VERIFICATION");

    /** 로보티콘 작가 정산 정보(V143 — emoticon-contract S2). 사업자 번호는 사업자 작가만(그 밖 NULL) */
    public static final List<EncryptedColumn> EMOTICON_PAYOUT = List.of(
            new EncryptedColumn("emoticon-payout-account", "emoticon_payout_accounts", "user_id", "account_number_enc", EmoticonPayoutService.ACCOUNT,
                    "EMOTICON_PAYOUT_ACCOUNT"),
            new EncryptedColumn("emoticon-payout-holder", "emoticon_payout_accounts", "user_id", "holder_name_enc", EmoticonPayoutService.HOLDER,
                    "EMOTICON_PAYOUT_ACCOUNT"),
            new EncryptedColumn("emoticon-payout-business-number", "emoticon_payout_accounts", "user_id", "business_number_enc", EmoticonPayoutService.BUSINESS_NUMBER,
                    "EMOTICON_PAYOUT_ACCOUNT"));

    /** 공구 주문 배송지(주문마다 저장한 사본 — commerce.md 10-9) */
    public static final List<EncryptedColumn> GROUP_BUY_ADDRESS = List.of(
            participation("groupbuy-address-recipient", "recipient_enc", GroupBuyJoinService.RECIPIENT),
            participation("groupbuy-address-phone", "phone_enc", GroupBuyJoinService.PHONE),
            participation("groupbuy-address-line1", "address1_enc", GroupBuyJoinService.ADDRESS1),
            participation("groupbuy-address-line2", "address2_enc", GroupBuyJoinService.ADDRESS2),
            participation("groupbuy-address-memo", "memo_enc", GroupBuyJoinService.MEMO));

    /** 회원 주소록(docs/features/address-book-contract.md) */
    public static final List<EncryptedColumn> MEMBER_ADDRESS = List.of(
            memberAddress("member-address-recipient", "recipient_enc", AddressBookService.RECIPIENT),
            memberAddress("member-address-phone", "phone_enc", AddressBookService.PHONE),
            memberAddress("member-address-line1", "address1_enc", AddressBookService.ADDRESS1),
            memberAddress("member-address-line2", "address2_enc", AddressBookService.ADDRESS2),
            memberAddress("member-address-memo", "memo_enc", AddressBookService.MEMO));

    /** 계좌 환불·보상 입금 계좌 */
    public static final List<EncryptedColumn> BANK_TRANSFER = List.of(
            bankTransfer("bank-transfer-bank", "bank_enc", BankTransferService.BANK),
            bankTransfer("bank-transfer-account", "account_no_enc", BankTransferService.ACCOUNT),
            bankTransfer("bank-transfer-holder", "holder_enc", BankTransferService.HOLDER));

    /** 사업자 신원(docs/features/business-contract.md) */
    public static final List<EncryptedColumn> BUSINESS_PROFILE = List.of(
            business("business-biz-no", "biz_no_enc", BusinessPii.BIZ_NO),
            business("business-owner", "owner_name_enc", BusinessPii.OWNER),
            business("business-phone", "phone_enc", BusinessPii.PHONE),
            business("business-email", "email_enc", BusinessPii.EMAIL),
            business("business-address1", "address1_enc", BusinessPii.ADDRESS1),
            business("business-address2", "address2_enc", BusinessPii.ADDRESS2));

    /** 계정 없는 광고주 연락처(docs/features/ad-contract.md) */
    public static final List<EncryptedColumn> AD_ADVERTISER = List.of(
            advertiser("ad-advertiser-contact-name", "contact_name_enc", AdAdvertiserService.CONTACT_NAME),
            advertiser("ad-advertiser-phone", "contact_phone_enc", AdAdvertiserService.CONTACT_PHONE),
            advertiser("ad-advertiser-email", "email_enc", AdAdvertiserService.EMAIL));

    /** 파일 사본(DB 안의 바이트 암호문 — 한 건 최대 10MB). 파기한 등록증 사본은 NULL */
    public static final List<EncryptedColumn> FILES = List.of(
            EncryptedColumn.ofBytes("business-certificate-file", "business_documents", "id", "content", BusinessPii.CERTIFICATE, "BUSINESS_DOCUMENT"),
            EncryptedColumn.ofBytes("biz-contract-file", "biz_contract_files", "id", "content", BizApplicationReviewService.CONTRACT, "BIZ_CONTRACT_FILE"));

    public static final List<EncryptedColumn> ALL = all();

    private DataKeyColumns() {
    }

    private static List<EncryptedColumn> all() {
        List<EncryptedColumn> all = new ArrayList<>();
        all.add(WITHDRAWN_CONTACT_EMAIL);
        all.addAll(GROUP_BUY_ADDRESS);
        all.addAll(MEMBER_ADDRESS);
        all.addAll(BANK_TRANSFER);
        all.addAll(BUSINESS_PROFILE);
        all.addAll(AD_ADVERTISER);
        all.addAll(FILES);
        all.add(IDENTITY_NAME);
        all.addAll(EMOTICON_PAYOUT);
        return List.copyOf(all);
    }

    private static EncryptedColumn participation(String step, String column, DataPurpose purpose) {
        return new EncryptedColumn(step, "group_buy_participations", "id", column, purpose, "GROUP_BUY_PARTICIPATION");
    }

    private static EncryptedColumn memberAddress(String step, String column, DataPurpose purpose) {
        return new EncryptedColumn(step, "member_addresses", "id", column, purpose, "MEMBER_ADDRESS");
    }

    private static EncryptedColumn bankTransfer(String step, String column, DataPurpose purpose) {
        return new EncryptedColumn(step, "commerce_bank_transfers", "id", column, purpose, "BANK_TRANSFER");
    }

    private static EncryptedColumn business(String step, String column, DataPurpose purpose) {
        return new EncryptedColumn(step, "business_profiles", "id", column, purpose, "BUSINESS_PROFILE");
    }

    private static EncryptedColumn advertiser(String step, String column, DataPurpose purpose) {
        return new EncryptedColumn(step, "ad_advertisers", "id", column, purpose, "AD_ADVERTISER");
    }
}
