package com.community.zconfiguration.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 오류 코드. 화면이 분기에 쓰는 안정적인 값은 {@link #name()} 이다 — 문구·언어를 바꿔도 유지된다.
 * 응답 본문의 {@code code} 가 이 값이다(화면의 {@code useApiClient} 가 {@code code} 를 읽는다).
 *
 * <p>기본 문구는 코드에 두지 않고 메시지 번들의 {@code error.<코드>} 키다({@link #getMessageKey()}) — 요청 언어로 풀려 응답의 {@code message} 가 된다.
 *
 * <p>업무 고유의 코드는 여기에 모으지 않는다. 각 업무의 {@code exception} 패키지에서
 * {@link CommonException} 을 상속해 정의한다.
 */
public enum ErrorCode {

    VALIDATION(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    CRYPTO_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE),
    /** 개인정보 칸 암호화 키({@code ROBOYAM_DATA_KEY})가 없거나 암호문 버전의 키가 빠졌다 — {@code DataCipher} */
    DATA_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE),
    /** 중복 확인 해시 키({@code ROBOYAM_INDEX_KEY})가 없다 — {@code BlindIndex} */
    INDEX_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 사용자에게 노출해도 되는 기본 문구의 메시지 키({@code error.<코드>}) */
    public String getMessageKey() {
        return "error." + name();
    }
}
