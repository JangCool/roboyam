package com.community.terms.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 약관 종류 — 본문·DB 값은 {@code TERMS}·{@code PRIVACY}·{@code ROBOTIKON_CONTRACT}·{@code OPERATION_POLICY}, 주소의 {@code {kind}} 는 {@code terms}·{@code privacy}·
 * {@code robotikon_contract}·{@code operation_policy}.
 * 목록은 이 선언 차례로 낸다. 가입 동의 버전 문자열·공개 약관 요약(가입·로그인 화면의 「보기」 링크·변경 안내)에는 {@link #signup()} 인 종류만 들어간다.
 * 불리한 변경으로 게시하면 가입 회원 전체에게 메일로 알리는 종류는 {@link #memberNotice()} 다(terms-operator-contract).
 */
public enum TermsKind {

    /** 이용약관 — 동의 버전 문자열의 {@code T} */
    TERMS("terms", "T", true, true, "/terms"),
    /** 개인정보처리방침 — 동의 버전 문자열의 {@code P} */
    PRIVACY("privacy", "P", true, true, "/privacy"),
    /**
     * 로보티콘 상품화 계약(docs/features/emoticon-contract.md S2, V143) — 유료 팩 작가가 동의하는 판 있는 문서. 동의 기록은 로보티콘 쪽 표({@code emoticon_contract_consents}).
     * 가입 동의·가입·로그인 링크에는 넣지 않는다.
     */
    ROBOTIKON_CONTRACT("robotikon_contract", "R", false, false, "/robotikon/contract"),
    /**
     * 운영정책(docs/features/terms-operator-contract.md, V158) — 공개 화면 {@code /policy} 와 버전 기록만 있다. 가입 동의 버전 문자열·가입·로그인 링크·변경 안내 띠에는 넣지 않는다.
     */
    OPERATION_POLICY("operation_policy", "O", false, true, "/policy");

    private final String path;
    private final String agreementPrefix;
    private final boolean signup;
    private final boolean memberNotice;
    private final String publicPath;

    TermsKind(String path, String agreementPrefix, boolean signup, boolean memberNotice, String publicPath) {
        this.path = path;
        this.agreementPrefix = agreementPrefix;
        this.signup = signup;
        this.memberNotice = memberNotice;
        this.publicPath = publicPath;
    }

    /** 회원 전체가 따르는 문서인가 — 불리한 변경으로 게시하면 가입 회원 전체에게 메일로 알린다(로보티콘 상품화 계약은 작가만 따르므로 아니다) */
    public boolean memberNotice() {
        return memberNotice;
    }

    /** 공개 사이트에서 이 문서를 보는 화면 경로(메일 링크) */
    public String publicPath() {
        return publicPath;
    }

    /** 가입 때 동의하는 약관인가 — 가입 동의 버전 문자열 · 공개 요약(가입·로그인 링크·변경 안내)에 들어간다 */
    public boolean signup() {
        return signup;
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
