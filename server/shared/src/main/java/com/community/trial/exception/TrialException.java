package com.community.trial.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 체험단·구매 인증·협찬 숨김(trial-group-contract) 업무 오류 — 코드마다 정적 팩터리. 문구 키는 {@code error.<코드>} */
public class TrialException extends CommonException {

    private TrialException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 404 — 없는 모집(광고주 화면에서 남의 모집도 같다) */
    public static TrialException notFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "TRIAL_NOT_FOUND");
    }

    /** 404 — 없는 신청 */
    public static TrialException applicationNotFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "TRIAL_APPLICATION_NOT_FOUND");
    }

    /** 404 — 없는 뽑힌 줄(남의 줄도 같다) */
    public static TrialException participantNotFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "TRIAL_PARTICIPANT_NOT_FOUND");
    }

    /** 404 — 없는 파손 신고 */
    public static TrialException damageNotFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "TRIAL_DAMAGE_NOT_FOUND");
    }

    /** 403 — 광고주 화면: 사업자 신원에 이어진 광고주가 없다 */
    public static TrialException notAdvertiser() {
        return new TrialException(HttpStatus.FORBIDDEN, "TRIAL_NOT_ADVERTISER");
    }

    /** 409 — 지금 상태에서 할 수 없는 일(예: 뽑기 뒤 고치기, 모집 중이 아닌데 뽑기) */
    public static TrialException stateInvalid() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_STATE_INVALID");
    }

    /** 409 — 신청을 받는 때가 아니다(모집 중이 아니거나 마감) */
    public static TrialException notRecruiting() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_NOT_RECRUITING");
    }

    /** 409 — 아직 신청이 열리지 않았다(리뷰어 먼저 신청 창 — 금 48·은 24시간 전부터) */
    public static TrialException applyNotOpen() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_APPLY_NOT_OPEN");
    }

    /** 409 — 이미 신청했다 */
    public static TrialException alreadyApplied() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_ALREADY_APPLIED");
    }

    /** 409 — AI 회원·운영팀 계정은 신청할 수 없다 */
    public static TrialException applicantExcluded() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_APPLICANT_EXCLUDED");
    }

    /** 409 — 뽑기가 끝나 신청을 취소할 수 없다 */
    public static TrialException applicationClosed() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_APPLICATION_CLOSED");
    }

    /** 409 — 아직 보내지 않았다(받음·반납·파손 신고 전) */
    public static TrialException notShipped() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_NOT_SHIPPED");
    }

    /** 409 — 이미 받음으로 처리됐다(송장을 바꿀 수 없다·받음을 다시 누를 수 없다) */
    public static TrialException alreadyReceived() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_ALREADY_RECEIVED");
    }

    /** 409 — 대여형이 아니다(반납·파손은 대여형만) */
    public static TrialException notLend() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_NOT_LEND");
    }

    /** 409 — 이미 반납 받음 */
    public static TrialException alreadyReturned() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_ALREADY_RETURNED");
    }

    /** 409 — 보내는 쪽이 아니다(광고주가 보내는 모집이면 운영자 입고·발송이 없고, 운영자가 보내는 모집이면 광고주가 송장을 넣지 않는다) */
    public static TrialException wrongShipper() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_WRONG_SHIPPER");
    }

    /** 409 — 유료 체험단의 광고 결제가 끝나기(예약 SCHEDULED) 전에는 입고·발송을 할 수 없다(trial-group-contract 「유료 결제 연결」 — 0원이면 막지 않는다) */
    public static TrialException paymentPending() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_PAYMENT_PENDING");
    }

    /** 409 — 취소한 뽑힌 줄이다(발송·받음·사용기 연결을 할 수 없다) */
    public static TrialException participantCancelled() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_PARTICIPANT_CANCELLED");
    }

    /** 409 — 이미 보냈거나(송장·받음·사용기 연결) 진행이 시작돼 취소할 수 없다 — 모집 취소·뽑힌 줄 취소는 발송 전에만 */
    public static TrialException shippingStarted() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_SHIPPING_STARTED");
    }

    /** 409 — 입고 전에는 운영자 발송을 할 수 없다 */
    public static TrialException noStock() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_NO_STOCK");
    }

    /** 409 — 이의 제기 기한이 지났거나 이미 이의했다 */
    public static TrialException objectionClosed() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_OBJECTION_CLOSED");
    }

    /** 409 — 회원 이의 기한(7일)이 지나기 전·이의 전에는 판정할 수 없다 */
    public static TrialException objectionOpen() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_DAMAGE_OBJECTION_OPEN");
    }

    /** 409 — 이미 판정한 파손 신고 */
    public static TrialException damageDecided() {
        return new TrialException(HttpStatus.CONFLICT, "TRIAL_DAMAGE_DECIDED");
    }

    /** 404 — 없는 구매 인증 */
    public static TrialException proofNotFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "PURCHASE_PROOF_NOT_FOUND");
    }

    /** 409 — 구매 인증을 받을 수 없는 글(구입 경로를 받지 않는 게시판·「내 돈으로 샀어요」가 아닌 글·남의 글) */
    public static TrialException proofNotAllowed() {
        return new TrialException(HttpStatus.CONFLICT, "PURCHASE_PROOF_NOT_ALLOWED");
    }

    /** 409 — 확인 대기 중이거나 이미 승인된 구매 인증이 있다 */
    public static TrialException proofExists() {
        return new TrialException(HttpStatus.CONFLICT, "PURCHASE_PROOF_EXISTS");
    }

    /** 409 — 이미 확인한 구매 인증 */
    public static TrialException proofClosed() {
        return new TrialException(HttpStatus.CONFLICT, "PURCHASE_PROOF_CLOSED");
    }

    /** 410 — 사진이 파기됐다 */
    public static TrialException proofPurged() {
        return new TrialException(HttpStatus.GONE, "PURCHASE_PROOF_PURGED");
    }

    /** 403 — 그 게시판의 매니저가 아니다(구매 인증 확인) */
    public static TrialException proofForbidden() {
        return new TrialException(HttpStatus.FORBIDDEN, "PURCHASE_PROOF_FORBIDDEN");
    }

    /** 403 — 자기 글의 구매 인증은 확인할 수 없다 */
    public static TrialException proofSelf() {
        return new TrialException(HttpStatus.FORBIDDEN, "PURCHASE_PROOF_SELF");
    }

    /** 404 — 없는 협찬 숨김 확인 */
    public static TrialException sponsorReviewNotFound() {
        return new TrialException(HttpStatus.NOT_FOUND, "SPONSOR_REVIEW_NOT_FOUND");
    }

    /** 409 — 이미 처리한 협찬 숨김 확인 */
    public static TrialException sponsorReviewClosed() {
        return new TrialException(HttpStatus.CONFLICT, "SPONSOR_REVIEW_CLOSED");
    }
}
