package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.AdStatsView;
import com.community.bizapplication.dto.BizAdStatsView;
import com.community.bizapplication.service.BizHotdealPublisher;
import com.community.bizapplication.service.MyBizAdStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 광고 성과 — 진행 중 사업자 핫딜 글 건의 노출 자리 멈춤(ad-performance-contract 「확인 뒤 고침」 1). 신청 건이 예약·기간 안이어도 딜이 끝났거나 글이 숨겨졌으면
 * 공개 노출 판정(biz-hotdeal-contract 규칙 7)과 같게 {@code exposureStop} 을 준다 — 사업자 센터 요약 줄 · 한 건 성과.
 */
class BizAdStatsExposureStopTest extends BizHotdealTestBase {

    @Autowired
    private MyBizAdStatsService stats;

    private BizAdStatsView.Item item(Booked b) {
        return stats.summary(b.user()).items().stream().filter(i -> i.id().equals(Long.toString(b.id()))).findFirst().orElseThrow();
    }

    @Test
    void 노출_중이면_exposureStop_없음_사업자_종료_뒤에는_DEAL_ENDED_요약에서_빠지지_않는다() {
        Booked b = postedBooking("HOME_TOP");
        assertThat(item(b).exposureStop()).isNull();
        assertThat(stats.application(b.user(), b.id(), null, null).exposureStop()).isNull();

        view.endDeal(b.user(), b.id());

        BizAdStatsView.Item after = item(b);
        assertThat(after.status()).isEqualTo("SCHEDULED");
        assertThat(after.exposureStop()).isEqualTo("DEAL_ENDED");
        AdStatsView.Application one = stats.application(b.user(), b.id(), null, null);
        assertThat(one.exposureStop()).isEqualTo("DEAL_ENDED");
    }

    @Test
    void 글이_운영_숨김이면_POST_HIDDEN() {
        Booked b = postedBooking("HOME_TOP");
        long postId = postOf(b.id());
        boardFixture.staffHide(postId);
        assertThat(item(b).exposureStop()).isEqualTo("POST_HIDDEN");
    }

    @Test
    void 시작_뒤_아직_게시되지_않았으면_NOT_POSTED_게시하면_풀린다() {
        Booked b = booked("HOME_TOP");
        into(b, 1);
        assertThat(item(b).exposureStop()).isEqualTo("NOT_POSTED");
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        assertThat(item(b).exposureStop()).isNull();
    }
}
