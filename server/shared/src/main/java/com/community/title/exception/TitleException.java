package com.community.title.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 회원 칭호(member-title-contract) 업무 오류 — 코드마다 정적 팩터리. 문구 키는 {@code error.<코드>} */
public class TitleException extends CommonException {

    private TitleException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 404 — 없는 칭호(키) */
    public static TitleException notFound() {
        return new TitleException(HttpStatus.NOT_FOUND, "TITLE_NOT_FOUND");
    }

    /** 404 — 없는·탈퇴한 회원 */
    public static TitleException memberNotFound() {
        return new TitleException(HttpStatus.NOT_FOUND, "TITLE_MEMBER_NOT_FOUND");
    }

    /** 404 — 없는 받은 줄(회수) */
    public static TitleException grantNotFound() {
        return new TitleException(HttpStatus.NOT_FOUND, "TITLE_GRANT_NOT_FOUND");
    }

    /** 404 — 없는 신청 */
    public static TitleException applicationNotFound() {
        return new TitleException(HttpStatus.NOT_FOUND, "TITLE_APPLICATION_NOT_FOUND");
    }

    /** 404 — 없는 글(이달의 작가 후보) */
    public static TitleException postNotFound() {
        return new TitleException(HttpStatus.NOT_FOUND, "TITLE_POST_NOT_FOUND");
    }

    /** 409 — 다른 칭호가 같은 이름을 쓴다(공백·대소문자 무시) */
    public static TitleException nameTaken() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_NAME_TAKEN");
    }

    /** 409 — 다른 칭호가 같은 키를 쓴다(만들 때 고른 키) */
    public static TitleException keyTaken() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_KEY_TAKEN");
    }

    /** 409 — 기본 칭호의 받는 방법은 바꿀 수 없다 */
    public static TitleException builtinLocked() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_BUILTIN_LOCKED");
    }

    /** 409 — AI 회원·운영팀 계정은 칭호를 받지 않는다(자동·신청·수여 모두) */
    public static TitleException memberExcluded() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_MEMBER_EXCLUDED");
    }

    /** 409 — 같은 칭호(같은 기간)를 이미 가졌다 */
    public static TitleException alreadyGranted() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_ALREADY_GRANTED");
    }

    /** 409 — 이달의 작가 그 달 인원이 찼다 */
    public static TitleException monthlyFull() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_MONTHLY_FULL");
    }

    /** 409 — 올해의 작가는 그해 이달의 작가 중에서만 */
    public static TitleException yearlyNotEligible() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_YEARLY_NOT_ELIGIBLE");
    }

    /** 409 — 이미 회수된 줄 */
    public static TitleException grantNotActive() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_GRANT_NOT_ACTIVE");
    }

    /** 409 — 신청할 수 없는 칭호(신청형이 아니거나 꺼짐) */
    public static TitleException notApplicable() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_NOT_APPLICABLE");
    }

    /** 409 — 조건을 다 채우지 않았다 */
    public static TitleException conditionsUnmet() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_CONDITIONS_UNMET");
    }

    /** 409 — 이미 심사 대기 중인 신청이 있다 */
    public static TitleException applicationPending() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_APPLICATION_PENDING");
    }

    /** 409 — 거절 뒤 다시 신청할 수 있는 날 전이다 */
    public static TitleException reapplyTooSoon() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_REAPPLY_TOO_SOON");
    }

    /** 409 — 이미 처리된 신청 */
    public static TitleException applicationClosed() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_APPLICATION_CLOSED");
    }

    /** 403 — 그 게시판의 매니저가 아니거나 그 게시판에 연결된 칭호가 아니다 */
    public static TitleException reviewForbidden() {
        return new TitleException(HttpStatus.FORBIDDEN, "TITLE_REVIEW_FORBIDDEN");
    }

    /** 403 — 자기 신청은 심사할 수 없다 */
    public static TitleException reviewSelf() {
        return new TitleException(HttpStatus.FORBIDDEN, "TITLE_REVIEW_SELF");
    }

    /** 409 — 가지지 않은 칭호를 대표로 고를 수 없다 */
    public static TitleException notOwned() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_NOT_OWNED");
    }

    /** 409 — 이달의 작가 후보로 넣을 수 없는 글(비회원 글·AI·운영팀 계정 글) */
    public static TitleException candidateInvalid() {
        return new TitleException(HttpStatus.CONFLICT, "TITLE_CANDIDATE_INVALID");
    }
}
