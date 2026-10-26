package com.community.identity.dto.request;

/**
 * 가짜 본인인증(개발용) 입력 — 같은 {@code personKey} = 같은 사람(1인 1계정 시험용). 검사는 서비스가 한다(칸 경로 {@code fake.birthDate} · {@code fake.personKey}).
 *
 * @param birthDate 생년월일 {@code YYYY-MM-DD}
 * @param personKey 사람 번호(1~40자)
 */
public record FakeIdentityInput(String birthDate, String personKey) {

    @Override
    public String toString() {
        return "FakeIdentityInput[birthDate=(감춤), personKey=(감춤)]";
    }
}
