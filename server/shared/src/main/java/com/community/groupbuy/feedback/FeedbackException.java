package com.community.groupbuy.feedback;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 공구 Q&A·리뷰 오류(2b-6) — 화면이 분기하는 값은 {@link #getErrorCode()}, 문구는 번들 {@code error.<코드>}.
 */
public class FeedbackException extends CommonException {

    public static final String QUESTION_NOT_FOUND = "GROUP_BUY_QUESTION_NOT_FOUND";
    public static final String QUESTION_ANSWERED = "GROUP_BUY_QUESTION_ANSWERED";
    public static final String QUESTION_DELETED = "GROUP_BUY_QUESTION_DELETED";
    public static final String QUESTION_TOO_FAST = "GROUP_BUY_QUESTION_TOO_FAST";
    public static final String QNA_CLOSED = "GROUP_BUY_QNA_CLOSED";
    public static final String REVIEW_NOT_FOUND = "GROUP_BUY_REVIEW_NOT_FOUND";
    public static final String REVIEW_EXISTS = "GROUP_BUY_REVIEW_EXISTS";
    public static final String REVIEW_NOT_WRITABLE = "GROUP_BUY_REVIEW_NOT_WRITABLE";
    public static final String REVIEW_EDIT_EXPIRED = "GROUP_BUY_REVIEW_EDIT_EXPIRED";
    public static final String REVIEW_DELETED = "GROUP_BUY_REVIEW_DELETED";
    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String ALREADY_REPORTED = "ALREADY_REPORTED";
    public static final String CANNOT_REPORT_OWN = "CANNOT_REPORT_OWN";
    public static final String REPORT_TARGET_NOT_FOUND = "REPORT_TARGET_NOT_FOUND";

    private FeedbackException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 공용 코드(신고·정지)는 공구 문구로 — {@code error.<코드>.groupBuy} */
    private static FeedbackException common(HttpStatus status, String code) {
        return new FeedbackException(status, code, new MessageRef("error." + code + ".groupBuy"));
    }

    private FeedbackException(HttpStatus status, String code, MessageRef message) {
        super(status, code, message);
    }

    private FeedbackException(HttpStatus status, String code, MessageRef message, long retryAfterSeconds) {
        super(status, code, message, Map.of(), retryAfterSeconds);
    }

    public static FeedbackException questionNotFound() {
        return new FeedbackException(HttpStatus.NOT_FOUND, QUESTION_NOT_FOUND);
    }

    public static FeedbackException questionAnswered() {
        return new FeedbackException(HttpStatus.CONFLICT, QUESTION_ANSWERED);
    }

    public static FeedbackException questionDeleted() {
        return new FeedbackException(HttpStatus.CONFLICT, QUESTION_DELETED);
    }

    /** 질문 쓰기 간격 — 기다릴 초를 {@code retryAfterSeconds} 로 */
    public static FeedbackException questionTooFast(int gapSeconds, long retryAfterSeconds) {
        return new FeedbackException(HttpStatus.TOO_MANY_REQUESTS, QUESTION_TOO_FAST, new MessageRef("error." + QUESTION_TOO_FAST, gapSeconds, retryAfterSeconds),
                retryAfterSeconds);
    }

    public static FeedbackException qnaClosed() {
        return new FeedbackException(HttpStatus.CONFLICT, QNA_CLOSED);
    }

    public static FeedbackException reviewNotFound() {
        return new FeedbackException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND);
    }

    public static FeedbackException reviewExists() {
        return new FeedbackException(HttpStatus.CONFLICT, REVIEW_EXISTS);
    }

    public static FeedbackException reviewNotWritable() {
        return new FeedbackException(HttpStatus.CONFLICT, REVIEW_NOT_WRITABLE);
    }

    public static FeedbackException reviewEditExpired() {
        return new FeedbackException(HttpStatus.CONFLICT, REVIEW_EDIT_EXPIRED);
    }

    public static FeedbackException reviewDeleted() {
        return new FeedbackException(HttpStatus.CONFLICT, REVIEW_DELETED);
    }

    /** 정지·영구 정지 회원 — 글쓰기와 같다 */
    public static FeedbackException restricted() {
        return common(HttpStatus.FORBIDDEN, ACCOUNT_RESTRICTED);
    }

    public static FeedbackException alreadyReported() {
        return common(HttpStatus.CONFLICT, ALREADY_REPORTED);
    }

    public static FeedbackException cannotReportOwn() {
        return common(HttpStatus.BAD_REQUEST, CANNOT_REPORT_OWN);
    }

    public static FeedbackException reportTargetNotFound() {
        return common(HttpStatus.NOT_FOUND, REPORT_TARGET_NOT_FOUND);
    }
}
