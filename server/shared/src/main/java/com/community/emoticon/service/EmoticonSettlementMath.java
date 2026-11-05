package com.community.emoticon.service;

/**
 * 로보티콘 유료 판매의 작가 몫 계산 — <b>한 곳</b>(계약 「목업 3차」 계산식, user-web {@code UtilEmoticonSettlement.compute} · admin-web {@code UtilEmoticon.creatorShare}·{@code withholding}
 * 와 같은 정수 식). 사이트가 판매자라 부가세는 사이트가 낸다: 정산 기준 = ⌊(판매 − 환불) × 10 ÷ 11⌋, 부가세 = (판매 − 환불) − 정산 기준, 작가 몫 = ⌊정산 기준 × (100 − 수수료율) ÷ 100⌋,
 * 수수료 = 정산 기준 − 작가 몫.
 *
 * <p>월 정산(S4 — {@link #month}): 합 = 작가 몫 + 앞 달에서 넘어온 금액. 합이 최소 지급액 이상(이고 0 보다 크면) 지급 — 개인 작가는 원천징수 ⌊합 × 3.3% ÷ 10⌋ × 10(사업소득 3%
 * + 지방소득세 0.3%, 10원 미만 버림), 사업자는 0(세금계산서), 지급액 = 합 − 원천징수. 아니면 지급액·원천징수 0 이고 합 전부를 다음 달로 넘긴다(이월 — 음수면 다음 달에서 뺀다).
 * 확정 뒤 환불이 그 달 판매보다 커 순판매가 음수인 달은 같은 식을 부호만 바꿔 쓴다(⌊|x|⌋ 에 음수 부호 — 이미 지급한 몫을 되돌리는 금액). V143 CHECK 가 같은 식을 본다.
 */
public final class EmoticonSettlementMath {

    /** 원천징수율(만분율) — 3.3% */
    public static final int WITHHOLDING_BP = 330;

    private EmoticonSettlementMath() {
    }

    /** 나눈 값(원) — {@code share} 는 원천징수 전 작가 몫 */
    public record Breakdown(long net, long vat, long base, long fee, long share) {
    }

    /**
     * 한 달 정산(원) — 판매 · 환불 · 나눈 값 · 넘어온 금액 · 원천징수 · 지급액 · 넘기는 금액.
     * {@code payout > 0} 이면 {@code carriedOut == 0}, 아니면 {@code withholding == 0} 이고 {@code carriedOut == share + carriedIn}.
     */
    public record Month(long salesAmount, long refundAmount, long net, long vat, long base, long fee, long share, long carriedIn, long withholding, long payout,
                        long carriedOut) {

        public boolean carriedOver() {
            return carriedOut != 0;
        }
    }

    /**
     * 판매 화면·스튜디오 요약의 나눈 값 — 순판매가 0 아래면 0 으로 본다(S3 그대로).
     *
     * @param sales          판매액(부가세 포함 결제 금액 합)
     * @param refund         환불액
     * @param feeRatePercent 수수료율(%, 0~90 — 설정)
     */
    public static Breakdown of(long sales, long refund, int feeRatePercent) {
        long net = Math.max(0, sales - refund);
        long base = net * 10 / 11;
        long share = base * (100 - feeRatePercent) / 100;
        return new Breakdown(net, net - base, base, base - share, share);
    }

    /** 순판매가 음수일 수 있는 나눈 값(월 정산 — 확정 뒤 환불을 환불한 달에서 뺀다). 양수면 {@link #of} 와 같다 */
    public static Breakdown signed(long sales, long refund, int feeRatePercent) {
        long net = sales - refund;
        long base = net < 0 ? -((-net) * 10 / 11) : net * 10 / 11;
        long share = base < 0 ? -((-base) * (100 - feeRatePercent) / 100) : base * (100 - feeRatePercent) / 100;
        return new Breakdown(net, net - base, base, base - share, share);
    }

    /** 개인 작가 원천징수 — ⌊금액 × 3.3% ÷ 10⌋ × 10(0 이하면 0) */
    public static long withholding(long amount) {
        return amount <= 0 ? 0 : amount * WITHHOLDING_BP / 100_000 * 10;
    }

    /**
     * 한 달 정산.
     *
     * @param carriedIn  앞 달에서 넘어온 금액(음수면 앞 달의 확정 뒤 환불이 남긴 빚)
     * @param individual 개인 작가(원천징수) — 사업자면 false
     * @param minimum    최소 지급액(설정 — 0~1,000,000)
     */
    public static Month month(long sales, long refund, int feeRatePercent, long carriedIn, boolean individual, long minimum) {
        Breakdown b = signed(sales, refund, feeRatePercent);
        long total = b.share() + carriedIn;
        if (total > 0 && total >= minimum) {
            long withholding = individual ? withholding(total) : 0;
            return new Month(sales, refund, b.net(), b.vat(), b.base(), b.fee(), b.share(), carriedIn, withholding, total - withholding, 0);
        }
        return new Month(sales, refund, b.net(), b.vat(), b.base(), b.fee(), b.share(), carriedIn, 0, 0, total);
    }
}
