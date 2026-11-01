package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 끝난 딜(409 {@code DEAL_ENDED}) — 고치기: 종료된 딜의 딜 칸은 고칠 수 없다(기간 종료 EXPIRED 는 고칠 수 있다) · {@link #action()}: 끝난 딜(종료·기간 종료)에는 종료 요청·작성자 종료를 할 수 없다
 */
public class DealEndedException extends CommonException {

    public DealEndedException() {
        this("error.DEAL_ENDED");
    }

    private DealEndedException(String key) {
        super(HttpStatus.CONFLICT, "DEAL_ENDED", new MessageRef(key));
    }

    /** 끝난 딜에 요청·종료 */
    public static DealEndedException action() {
        return new DealEndedException("error.DEAL_ENDED.action");
    }
}
