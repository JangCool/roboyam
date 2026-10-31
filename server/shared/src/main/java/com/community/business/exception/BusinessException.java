package com.community.business.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 사업자 신원의 업무 오류(business-contract). 문구는 번들 {@code error.<코드>}. 화면은 {@code BUSINESS_STATE_CHANGED} 면 「이미 바뀌었어요」 + 다시 읽기.
 */
public class BusinessException extends CommonException {

    public static final String NOT_FOUND = "BUSINESS_NOT_FOUND";
    public static final String STATE_CHANGED = "BUSINESS_STATE_CHANGED";
    public static final String DUPLICATE = "BUSINESS_DUPLICATE";
    public static final String ALREADY_APPLIED = "BUSINESS_ALREADY_APPLIED";
    public static final String NOT_EDITABLE = "BUSINESS_NOT_EDITABLE";
    public static final String REAPPLY_LIMIT = "BUSINESS_REAPPLY_LIMIT";
    public static final String NTS_NOT_ACTIVE = "BUSINESS_NTS_NOT_ACTIVE";
    public static final String NTS_NOT_CHECKED = "BUSINESS_NTS_NOT_CHECKED";
    public static final String FILE_NOT_FOUND = "BUSINESS_FILE_NOT_FOUND";
    public static final String CERT_NOT_FOUND = "BUSINESS_CERT_NOT_FOUND";
    public static final String UPLOAD_LIMIT = "BUSINESS_UPLOAD_LIMIT";
    public static final String NOT_APPROVED = "BUSINESS_NOT_APPROVED";
    public static final String RE_REVIEW = "BUSINESS_RE_REVIEW";

    private BusinessException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    public static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** 처리하려던 사이 상태가 바뀌었다(다른 운영자가 먼저 처리) */
    public static BusinessException stateChanged() {
        return new BusinessException(HttpStatus.CONFLICT, STATE_CHANGED);
    }

    /** 살아 있는 줄(심사 중·승인·정지) 중에 같은 등록번호가 있다 */
    public static BusinessException duplicate() {
        return new BusinessException(HttpStatus.CONFLICT, DUPLICATE);
    }

    /** 이미 신청해 심사 중·승인·정지 — 새로 신청할 수 없다(수정은 PUT) */
    public static BusinessException alreadyApplied() {
        return new BusinessException(HttpStatus.CONFLICT, ALREADY_APPLIED);
    }

    /** 고칠 수 없는 상태(신청 전·처음 심사 중·반려·정지) */
    public static BusinessException notEditable() {
        return new BusinessException(HttpStatus.CONFLICT, NOT_EDITABLE);
    }

    /** 반려 뒤 재신청은 하루 3회까지 */
    public static BusinessException reapplyLimit() {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, REAPPLY_LIMIT);
    }

    /** 국세청 조회가 휴업·폐업·없는 번호 — 승인할 수 없다(B1) */
    public static BusinessException ntsNotActive() {
        return new BusinessException(HttpStatus.CONFLICT, NTS_NOT_ACTIVE);
    }

    /** 국세청 조회를 한 번도 하지 않았다 — [다시 조회] 부터(B1) */
    public static BusinessException ntsNotChecked() {
        return new BusinessException(HttpStatus.CONFLICT, NTS_NOT_CHECKED);
    }

    /** 올린 파일이 없거나 남의 것·이미 다른 신청에 붙은 것 */
    public static BusinessException fileNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, FILE_NOT_FOUND);
    }

    /** 사업자등록증 사본이 없다(파기됨) */
    public static BusinessException certificateNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, CERT_NOT_FOUND);
    }

    /** 사본은 하루 20장까지 올릴 수 있다 */
    public static BusinessException uploadLimit() {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, UPLOAD_LIMIT);
    }

    /** 승인된 사업자가 아니다(그 서비스 이용 권한 없음) — 광고·핫딜 신청 건이 쓴다 */
    public static BusinessException notApproved() {
        return new BusinessException(HttpStatus.CONFLICT, NOT_APPROVED);
    }

    /** 상호·번호를 고쳐 재심사 중 — 새 신청만 막힌다(F7) */
    public static BusinessException reReview() {
        return new BusinessException(HttpStatus.CONFLICT, RE_REVIEW);
    }
}
