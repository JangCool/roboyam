package com.community.terms.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 약관 종류 — 본문·DB 값은 {@code TERMS}·{@code PRIVACY}, 주소의 {@code {kind}} 는 {@code terms}·{@code privacy}. 목록은 이 선언 차례로 낸다.
 */
public enum TermsKind {

    /** 이용약관 — 동의 버전 문자열의 {@code T} */
    TERMS("terms", "T"),
    /** 개인정보처리방침 — 동의 버전 문자열의 {@code P} */
    PRIVACY("privacy", "P");

    private final String path;
    private final String agreementPrefix;

    TermsKind(String path, String agreementPrefix) {
        this.path = path;
        this.agreementPrefix = agreementPrefix;
    }

    /** 주소 조각({@code terms}·{@code privacy}) */
    public String path() {
        return path;
    }

    /** 동의 버전 문자열({@code T3.P2})의 머리 글자 */
    public String agreementPrefix() {
        return agreementPrefix;
    }

    /** 주소 조각으로 찾는다. 모르는 값이면 비어 있다(대소문자는 가리지 않는다) */
    public static Optional<TermsKind> ofPath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(kind -> kind.path.equalsIgnoreCase(path)).findFirst();
    }
}
