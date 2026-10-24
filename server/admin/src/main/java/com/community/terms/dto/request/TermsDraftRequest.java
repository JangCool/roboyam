package com.community.terms.dto.request;

/** 초안 저장 — 본문 HTML(서버가 정화한다). 검사는 서비스가 한다({@code fields.body}) */
public record TermsDraftRequest(String body) {

    @Override
    public String toString() {
        return "TermsDraftRequest[body=" + (body == null ? "null" : body.length() + "자") + "]";
    }
}
