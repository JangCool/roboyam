package com.community.zconfiguration.message;

/**
 * 사람이 읽는 문구를 <b>문구 대신 키 + 인자</b>로 들고 다니는 값. 문구는 응답을 만드는 자리({@link Messages})가 요청 언어로 푼다.
 * 키는 {@code messages_ko.properties}·{@code messages_en.properties} 에 있다({@code 업무.무엇}, 예: {@code user.nickname.length}).
 *
 * <p>인자는 문구의 {@code {0}}·{@code {1}} 자리에 들어간다. 1000 이상의 수는 천 단위 쉼표가 붙으니 문자열로 넘긴다.
 * 입력한 값(비밀번호·이메일 …)은 인자로 싣지 않는다 — 한도 같은 규칙 값만 싣는다.
 */
public record MessageRef(String key, Object... args) {

    public MessageRef {
        args = args == null ? new Object[0] : args;
    }
}
