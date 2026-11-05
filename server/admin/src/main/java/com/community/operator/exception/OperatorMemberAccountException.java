package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 운영자 회원 계정 연결·해제(docs/features/operator-member-account-contract.md 「API > 관리자」)의 거절 — 코드마다 정적 팩터리 하나. 모두 409.
 */
public class OperatorMemberAccountException extends CommonException {

    public static final String NOT_CANDIDATE = "OPERATOR_MEMBER_NOT_CANDIDATE";
    public static final String NOT_ELIGIBLE = "OPERATOR_MEMBER_NOT_ELIGIBLE";
    public static final String ALREADY_LINKED = "OPERATOR_MEMBER_ALREADY_LINKED";
    public static final String NOT_LINKED = "OPERATOR_MEMBER_NOT_LINKED";
    public static final String ROOT_PROTECTED = "OPERATOR_ROOT_PROTECTED";

    private OperatorMemberAccountException(String code, MessageRef message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    private static OperatorMemberAccountException of(String code) {
        return new OperatorMemberAccountException(code, new MessageRef("error." + code));
    }

    /** 고른 회원이 지금 후보(운영자와 같은 이메일로 가입한 회원)가 아니다 */
    public static OperatorMemberAccountException notCandidate() {
        return of(NOT_CANDIDATE);
    }

    /** 후보지만 자격 미달(인증 안 됨 · 정지·탈퇴 진행 · 사업자 · 다른 운영자에 이어짐) */
    public static OperatorMemberAccountException notEligible() {
        return of(NOT_ELIGIBLE);
    }

    /** 이 운영자는 이미 회원 계정이 이어져 있다 */
    public static OperatorMemberAccountException alreadyLinked() {
        return of(ALREADY_LINKED);
    }

    /** 이어진 회원 계정이 없다(해제) */
    public static OperatorMemberAccountException notLinked() {
        return of(NOT_LINKED);
    }

    /** 최고 관리자의 연결은 최고 관리자 본인만 바꾼다 — 코드는 운영자 관리와 같은 {@code OPERATOR_ROOT_PROTECTED}, 문구만 연결용 */
    public static OperatorMemberAccountException rootProtected() {
        return new OperatorMemberAccountException(ROOT_PROTECTED, new MessageRef("operatorMember.rootProtected"));
    }
}
