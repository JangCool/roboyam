package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 — 로보티콘 팩·심사 건의 상태가 그 일을 받지 않는다. 코드는 까닭 그대로(문구 키 {@code error.<코드>}):
 * <ul>
 *   <li>{@link #PACK_SUSPENDED} 판매 중단 팩이라 받을 수 없다 · {@link #PACK_PAID} 유료 팩은 받지 않는다(구매 — S3)</li>
 *   <li>{@link #PACK_LOCKED} 심사 중·판매 중이라 임시 저장할 수 없다 / 이미 심사 중이다</li>
 *   <li>{@link #PAYOUT_REQUIRED} 유료 판매 희망(또는 유료 팩의 새 판)인데 정산 정보가 없다 · {@link #CONTRACT_REQUIRED} 지금 판 로보티콘 상품화 계약 동의가 없다
 *       (보낸 판이 지금 판이 아니거나 게시된 계약이 없다 — S2)</li>
 *   <li>{@link #IDENTITY_NAME_MISSING} 본인인증은 했지만 인증 이름이 남아 있지 않아 예금주를 대조할 수 없다(V143 전에 인증한 회원 — S2)</li>
 *   <li>{@link #REVIEW_NOT_PENDING} 이미 처리된 심사 건 · {@link #PACK_NOT_ON_SALE} 판매 중이 아니다 · {@link #PACK_NOT_SUSPENDED} 판매 중단이 아니다 ·
 *       {@link #PACK_NOT_OFFICIAL} 운영 팩이 아니다</li>
 *   <li>S3 유료 구매: {@link #PACK_FREE} 무료 팩은 사지 않는다(받기) · {@link #PACK_OWNED} 이미 가진 팩(산 팩 · 무료일 때 받은 팩 — 뺐어도) ·
 *       {@link #PAID_SALES_CLOSED} 유료 판매가 꺼져 있어 새 구매를 받지 않는다 · {@link #ALREADY_REFUNDED} 이미 환불한 구매(회원) ·
 *       {@link #SALE_ALREADY_REFUNDED} 이미 환불된 판매(관리자 예외 환불). 셀프 환불 조건 밖은 {@link EmoticonRefundNotAllowedException}</li>
 * </ul>
 * 한 사람 심사 대기 상한은 {@link EmoticonPendingLimitException}(문구에 상한 수).
 */
public class EmoticonConflictException extends CommonException {

    public static final String PACK_SUSPENDED = "EMOTICON_PACK_SUSPENDED";
    public static final String PACK_PAID = "EMOTICON_PACK_PAID";
    public static final String PACK_LOCKED = "EMOTICON_PACK_LOCKED";
    public static final String PAYOUT_REQUIRED = "EMOTICON_PAYOUT_REQUIRED";
    public static final String CONTRACT_REQUIRED = "EMOTICON_CONTRACT_REQUIRED";
    public static final String IDENTITY_NAME_MISSING = "EMOTICON_IDENTITY_NAME_MISSING";
    public static final String REVIEW_NOT_PENDING = "EMOTICON_REVIEW_NOT_PENDING";
    public static final String PACK_NOT_ON_SALE = "EMOTICON_PACK_NOT_ON_SALE";
    public static final String PACK_NOT_SUSPENDED = "EMOTICON_PACK_NOT_SUSPENDED";
    public static final String PACK_NOT_OFFICIAL = "EMOTICON_PACK_NOT_OFFICIAL";
    public static final String PACK_FREE = "EMOTICON_PACK_FREE";
    public static final String PACK_OWNED = "EMOTICON_PACK_OWNED";
    public static final String PAID_SALES_CLOSED = "EMOTICON_PAID_SALES_CLOSED";
    public static final String ALREADY_REFUNDED = "EMOTICON_ALREADY_REFUNDED";
    public static final String SALE_ALREADY_REFUNDED = "EMOTICON_SALE_ALREADY_REFUNDED";
    // S4 작가 정산 — 목업 코드 그대로
    /** 예정·확정 정산만 보류할 수 있다 */
    public static final String SETTLEMENT_NOT_HOLDABLE = "EMOTICON_SETTLEMENT_NOT_HOLDABLE";
    /** 보류한 정산이 아니다 */
    public static final String SETTLEMENT_NOT_HELD = "EMOTICON_SETTLEMENT_NOT_HELD";
    /** 확정한 정산만 지급 완료로 기록할 수 있다 */
    public static final String SETTLEMENT_NOT_CONFIRMED = "EMOTICON_SETTLEMENT_NOT_CONFIRMED";
    /** 지급할 금액이 없다(다음 달로 넘겼다) */
    public static final String SETTLEMENT_NOTHING_TO_PAY = "EMOTICON_SETTLEMENT_NOTHING_TO_PAY";

    public EmoticonConflictException(String code) {
        super(HttpStatus.CONFLICT, code, new MessageRef("error." + code));
    }

    /**
     * {@code 409 EMOTICON_IDENTITY_NAME_MISSING} — 최상위 {@code reverify} = 이름 채우기 재인증(본인인증 다시 하기)으로 풀 수 있는가(S3 — 데이터 키가 있으면 true).
     * 화면은 true 면 본인인증 화면으로 보낸다.
     */
    public static EmoticonConflictException identityNameMissing(boolean reverify) {
        EmoticonConflictException e = new EmoticonConflictException(IDENTITY_NAME_MISSING);
        e.extra("reverify", reverify);
        return e;
    }

    /**
     * {@code 409 EMOTICON_PACK_OWNED} — 내가 만든 상점 팩이라 이미 가졌다(후속 결정 1 — 결제 준비 · 목록에서 빼기). 최상위 {@code reason: "CREATOR"}(산 팩·받은 팩의 같은 코드와 가른다).
     * 새 코드를 두지 않은 까닭: 화면이 이미 「이미 가진 팩」으로 다루는 코드이고, 작가 팩은 {@code acquired=true} 라 구매 단추가 애초에 나오지 않는다.
     */
    public static EmoticonConflictException creatorOwned(String messageKey) {
        EmoticonConflictException e = new EmoticonConflictException(PACK_OWNED, messageKey);
        e.extra("reason", "CREATOR");
        return e;
    }

    /** 같은 코드에 그 자리의 문구(예: 가격 바꾸기의 {@code EMOTICON_PACK_NOT_ON_SALE} — 「판매 중인 작가 팩만 가격을 바꿀 수 있어요」) */
    public EmoticonConflictException(String code, String messageKey) {
        super(HttpStatus.CONFLICT, code, new MessageRef(messageKey));
    }
}
