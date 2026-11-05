package com.community.emoticon.dto.request;

/**
 * 작가 정산 정보 저장 본문 — user-web {@code EmoticonPayoutAccountRequest}. 검사는 서비스가 칸별로 모은다({@code fields.type}·{@code businessNumber}·{@code bankCode}·
 * {@code accountNumber}·{@code holderName}). {@code toString} 은 값을 싣지 않는다(계좌 원문이 로그에 남지 않게).
 *
 * @param type           {@code INDIVIDUAL}(원천징수 3.3%) · {@code BUSINESS}(세금계산서)
 * @param businessNumber 사업자일 때만 — 숫자 10자리(하이픈 허용)
 * @param bankCode       은행 코드(user-web {@code UtilEmoticon.banks()} 와 같은 11가지)
 * @param accountNumber  계좌번호 — 숫자 10~14자리(하이픈·띄어쓰기 허용)
 * @param holderName     예금주 — 본인인증 이름과 같아야 한다
 */
public record EmoticonPayoutAccountRequest(String type, String businessNumber, String bankCode, String accountNumber, String holderName) {

    @Override
    public String toString() {
        return "EmoticonPayoutAccountRequest[type=" + type + ", bankCode=" + bankCode + ", (나머지 감춤)]";
    }
}
