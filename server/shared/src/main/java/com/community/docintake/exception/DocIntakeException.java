package com.community.docintake.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 사업자 서류 접수 기록의 업무 오류(biz-doc-intake-contract). 문구는 번들 {@code error.<코드>}. 화면은 409 면 알리고 다시 읽는다 */
public class DocIntakeException extends CommonException {

    public static final String NOT_FOUND = "DOC_INTAKE_NOT_FOUND";
    public static final String STATE_CHANGED = "DOC_INTAKE_STATE_CHANGED";

    private DocIntakeException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 그 대상에 없는 접수 기록 */
    public static DocIntakeException notFound() {
        return new DocIntakeException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** 이미 바뀐 줄(잘못 기록·폐기함)이거나 대상의 상태가 그 처리를 허용하지 않는다 */
    public static DocIntakeException stateChanged() {
        return new DocIntakeException(HttpStatus.CONFLICT, STATE_CHANGED);
    }
}
