package com.community.mail.dto.request;

/**
 * 시험 발송 — {@code POST /api/admin/mail-settings/test}. 받는 주소는 서비스가 검사한다({@code fields.to}).
 *
 * @param to 시험 메일을 받을 주소
 */
public record MailTestRequest(String to) {

    /** 받는 주소를 로그에 남기지 않는다 */
    @Override
    public String toString() {
        return "MailTestRequest[to=(감춤)]";
    }
}
