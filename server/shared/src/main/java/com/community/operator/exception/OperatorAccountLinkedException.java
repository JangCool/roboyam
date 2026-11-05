package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code OPERATOR_ACCOUNT_LINKED} — 운영자 회원 계정으로 이어진 동안은 탈퇴할 수 없다(docs/features/operator-member-account-contract.md 정책 12).
 * 회원 본인 탈퇴와 관리자 강제 탈퇴가 같은 코드이고 문구만 다르다(회원 「관리자에게 요청」 · 관리자 「먼저 연결 해제」).
 */
public class OperatorAccountLinkedException extends CommonException {

    public static final String CODE = "OPERATOR_ACCOUNT_LINKED";

    private OperatorAccountLinkedException(MessageRef message) {
        super(HttpStatus.CONFLICT, CODE, message);
    }

    /** 회원 본인 탈퇴 */
    public static OperatorAccountLinkedException byMember() {
        return new OperatorAccountLinkedException(new MessageRef("error." + CODE));
    }

    /** 관리자 강제 탈퇴 */
    public static OperatorAccountLinkedException byOperator() {
        return new OperatorAccountLinkedException(new MessageRef("operatorMember.withdraw.linked"));
    }
}
