package com.community.pointgame.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이번에 뽑으면 내 번호 보관 한도를 넘는다(point-games-contract 「내 번호」 — 자동 삭제 없음, 지난 번호를 지운 뒤 뽑는다) */
public class LottoStorageFullException extends CommonException {

    public LottoStorageFullException(int limit) {
        super(HttpStatus.CONFLICT, "LOTTO_STORAGE_FULL", new MessageRef("error.LOTTO_STORAGE_FULL", limit));
    }
}
