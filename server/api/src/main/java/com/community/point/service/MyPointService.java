package com.community.point.service;

import com.community.point.dto.PointRules;
import com.community.point.dto.response.LikeBonusRuleView;
import com.community.point.dto.response.PointCountView;
import com.community.point.dto.response.PointRulesView;
import com.community.point.dto.response.PointSummaryView;
import com.community.point.dto.response.PointTodayView;
import com.community.point.dto.response.PopularRewardRuleView;
import com.community.point.entity.PointBalanceEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointBalanceRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 포인트 요약 — 로그인한 회원 본인 것만(이메일 인증 전·정지 중이어도 읽는다). 하루의 기준은 한국 시간 0시. 규칙은 정책 사본({@link PointPolicy}, 15초)에서 읽는다.
 * 출석·교환 기능이 아직 없어 {@code attended=false}·{@code ticketCount=0}·{@code attendanceAvailable=false} 다.
 */
@Service
public class MyPointService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PointBalanceRepository balances;
    private final PointService points;
    private final PointPolicy policy;

    public MyPointService(PointBalanceRepository balances, PointService points, PointPolicy policy) {
        this.balances = balances;
        this.points = points;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public PointSummaryView summary(long userId) {
        PointRules rules = policy.current();
        long balance = balances.findById(userId).map(PointBalanceEntity::getBalance).orElse(0L);
        PointTodayView today = new PointTodayView(LocalDate.now(KST).toString(), false,
                new PointCountView(points.countToday(userId, PointReason.POST), rules.postDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.COMMENT), rules.commentDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.LIKE_BONUS), rules.likeBonusDailyLimit()));
        PointRulesView view = new PointRulesView(rules.attendancePoints(), rules.postPoints(), rules.commentPoints(),
                new PopularRewardRuleView(rules.popularRewardTopN(), rules.popularRewardPoints()), rules.nicknameTicketPrice(),
                new LikeBonusRuleView(rules.likeBonusThreshold(), rules.likeBonusPoints(), rules.likeBonusDailyLimit()));
        return new PointSummaryView(balance, 0, false, today, view);
    }
}
