package com.community.address.exception;

import java.util.Arrays;
import java.util.List;

/**
 * 주소 DB 적재가 멈춘 까닭 — HTTP 응답이 아니라 적재 줄({@code address_db_loads})의 실패 문구(키 + 인자)로 남는다. 적재는 요청이 끝난 뒤 백그라운드에서 돌기 때문이다.
 * 인자는 사용자가 입력한 값이 아니라 파일 이름·줄 번호·건수다.
 */
public class AddressImportFailure extends RuntimeException {

    private final String messageKey;
    private final List<String> args;

    public AddressImportFailure(String messageKey, Object... args) {
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.args = Arrays.stream(args).map(String::valueOf).toList();
    }

    public String messageKey() {
        return messageKey;
    }

    public List<String> args() {
        return args;
    }
}
