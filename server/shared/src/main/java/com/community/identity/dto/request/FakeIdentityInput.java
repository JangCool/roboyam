package com.community.identity.dto.request;

/**
 * 가짜 본인인증(개발용) 입력 — 같은 {@code personKey} = 같은 사람(1인 1계정 시험용). 검사는 서비스가 한다(칸 경로 {@code fake.birthDate} · {@code fake.personKey}).
 *
 * @param birthDate 생년월일 {@code YYYY-MM-DD}
 * @param personKey 사람 번호(1~40자)
 * @param name      인증 이름(선택 — 비우면 {@code 홍길동}). 로보티콘 작가 정산 계좌의 예금주 대조(emoticon-contract S2)에 쓴다
 */
public record FakeIdentityInput(String birthDate, String personKey, String name) {

    public FakeIdentityInput(String birthDate, String personKey) {
        this(birthDate, personKey, null);
    }

    @Override
    public String toString() {
        return "FakeIdentityInput[birthDate=(감춤), personKey=(감춤), name=(감춤)]";
    }
}
