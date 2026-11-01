package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 끝나지 않은(진행 중인) 딜이다(409 {@code DEAL_NOT_ENDED}) — 「끝난 딜에서 새 딜 올리기」는 끝난(종료·기간 종료) 딜에서만 · {@link #reopen()}: 되돌리기·재개는 종료된 딜에서만
 */
public class DealNotEndedException extends CommonException {

    public DealNotEndedException() {
        this("error.DEAL_NOT_ENDED");
    }

    private DealNotEndedException(String key) {
        super(HttpStatus.CONFLICT, "DEAL_NOT_ENDED", new MessageRef(key));
    }

    /** 진행 중인 딜을 되돌리기·재개 */
    public static DealNotEndedException reopen() {
        return new DealNotEndedException("error.DEAL_NOT_ENDED.reopen");
    }
}
