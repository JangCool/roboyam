package com.community.pointgame.service;

import com.community.board.dto.BoardActor;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.pointgame.dto.request.LottoDrawRequest;
import com.community.pointgame.dto.response.LottoCheckLinkView;
import com.community.pointgame.dto.response.LottoDrawResultView;
import com.community.pointgame.dto.response.LottoDrawView;
import com.community.pointgame.dto.response.LottoStatusView;
import com.community.pointgame.dto.response.RouletteSpinResultView;
import com.community.pointgame.dto.response.RouletteStatusView;
import com.community.pointgame.entity.LottoDrawEntity;
import com.community.pointgame.entity.RouletteSpinEntity;
import com.community.pointgame.exception.LottoDrawNotFoundException;
import com.community.pointgame.exception.LottoStorageFullException;
import com.community.pointgame.exception.PointGameBlockedException;
import com.community.pointgame.exception.PointGameDailyLimitException;
import com.community.pointgame.repository.master.LottoDrawRepository;
import com.community.pointgame.repository.master.PointGameQueries;
import com.community.pointgame.repository.master.RouletteSpinRepository;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 포인트 게임(공개 사이트 — docs/features/point-games-contract.md). 포인트는 모두 <b>적립(무상) 포인트</b>다 — 쓰기는 {@link PointService#useFree}(무상 묶음에서만 —
 * 충전 포인트는 절대 빠지지 않는다), 룰렛 당첨은 {@link PointService#gameReward}(새 무상 묶음 · 활동 한도·랭킹 밖).
 *
 * <ul>
 *   <li><b>자격</b>(돌리기·뽑기): 로그인(없거나 탈퇴 401) → 정지·영구 정지·탈퇴 진행 중 {@code 403 ACCOUNT_RESTRICTED} → 사업자 회원 {@code 403 BUSINESS_ACCOUNT_RESTRICTED}
 *       → 포인트 동결 {@code 403 MEMBER_RESTRICTED}(기능 제한 {@code POINT_USE} — 포인트 상점 구매와 같다). 읽기는 401 → 사업자 403 만 본다(정지 회원도 자기 기록은 본다).</li>
 *   <li><b>룰렛</b>({@link #spin}): 차감·추첨·지급이 한 트랜잭션. 결과는 서버 난수({@link SecureRandom}). 하루 {@value RouletteTable#DAILY_LIMIT}회(한국 시간 0시 초기화)는
 *       회원 잔액 행 잠금 아래에서 오늘 돌린 기록을 세어 막는다(같은 회원의 동시 요청이 줄을 선다 — {@code 409 POINT_GAME_DAILY_LIMIT}). 횟수 검사가 잔액 검사보다 먼저다.</li>
 *   <li><b>로또</b>({@link #draw}): 검사({@link LottoRules#validate} — 400) → 차감·저장이 한 트랜잭션. 보관 {@value LottoRules#STORAGE_LIMIT}세트는 잔액 행 잠금 아래에서 세어
 *       막는다({@code 409 LOTTO_STORAGE_FULL} — 잔액 검사보다 먼저). 하루 횟수 제한 없음. 지우기({@link #removeDraw})는 내 줄만(이미 없으면 그대로 성공, 남의 줄 404), 포인트는 돌려주지 않는다.</li>
 * </ul>
 */
@Service
public class MemberPointGameService {

    private static final Logger log = LoggerFactory.getLogger(MemberPointGameService.class);
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final PointService points;
    private final PointGameQueries queries;
    private final RouletteSpinRepository spins;
    private final LottoDrawRepository draws;
    private final PointGameSettingsService settings;
    private final BoardActorResolver actors;
    private final BoardCatalog boards;
    private final MemberRestrictionService restrictions;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    public MemberPointGameService(PointService points, PointGameQueries queries, RouletteSpinRepository spins, LottoDrawRepository draws,
                                  PointGameSettingsService settings, BoardActorResolver actors, BoardCatalog boards, MemberRestrictionService restrictions,
                                  SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.points = points;
        this.queries = queries;
        this.spins = spins;
        this.draws = draws;
        this.settings = settings;
        this.actors = actors;
        this.boards = boards;
        this.restrictions = restrictions;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 룰렛 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RouletteStatusView roulette(long userId) {
        assertCanView(userId);
        Instant now = now();
        return new RouletteStatusView(RouletteTable.COST, RouletteTable.DAILY_LIMIT, queries.spinsSince(userId, todayStart(now)), RouletteTable.views(),
                queries.freeBalance(userId), nextReset(now));
    }

    /** 한 번 돌린다 — 차감 → 추첨 → 지급 → 기록(한 트랜잭션) */
    @Transactional
    public RouletteSpinResultView spin(long userId) {
        assertCanPlay(userId);
        Instant now = now();
        Instant since = todayStart(now);
        long spinId = ids.nextId();
        int[] before = new int[1];
        points.useFree(userId, RouletteTable.COST, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, spinId, () -> {
            before[0] = queries.spinsSince(userId, since);
            if (before[0] >= RouletteTable.DAILY_LIMIT) {
                throw new PointGameDailyLimitException();
            }
        });
        RouletteTable.Prize prize = RouletteTable.pick(random);
        if (prize.points() > 0) {
            points.gameReward(userId, spinId, prize.points());
        }
        spins.persist(RouletteSpinEntity.create(spinId, userId, prize.key(), RouletteTable.COST, prize.points(), now));
        log.info("룰렛 userId={} spinId={} prize={} won={}", userId, spinId, prize.key(), prize.points());
        return new RouletteSpinResultView(Long.toString(spinId), prize.key(), prize.points(), queries.freeBalance(userId), before[0] + 1);
    }

    // ── 로또 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LottoStatusView lotto(long userId) {
        assertCanView(userId);
        LottoCheckLinkView link = settings.checkLink().map(l -> new LottoCheckLinkView(l.providerName(), l.url())).orElse(null);
        return new LottoStatusView(LottoRules.SET_COST, LottoRules.BUNDLE_SIZE, LottoRules.BUNDLE_COST, queries.freeBalance(userId), LottoRules.STORAGE_LIMIT,
                queries.draws(userId, LottoRules.STORAGE_LIMIT).stream().map(MemberPointGameService::view).toList(), link);
    }

    /** 번호를 뽑는다(1세트 또는 묶음) — 자격 → 입력 400 → 보관 한도 409 → 잔액 409 → 차감·저장(한 트랜잭션) */
    @Transactional
    public LottoDrawResultView draw(long userId, LottoDrawRequest body) {
        assertCanPlay(userId);
        LottoRules.Pick pick = LottoRules.validate(body);
        Instant now = now();
        long batchId = ids.nextId();
        points.useFree(userId, pick.cost(), PointReason.LOTTO_DRAW, PointService.REF_LOTTO_DRAW, batchId, () -> {
            if (queries.drawCount(userId) + pick.sets() > LottoRules.STORAGE_LIMIT) {
                throw new LottoStorageFullException(LottoRules.STORAGE_LIMIT);
            }
        });
        List<LottoDrawEntity> made = new ArrayList<>();
        for (List<Integer> numbers : LottoRules.draw(pick, random)) {
            LottoDrawEntity draw = LottoDrawEntity.create(ids.nextId(), userId, batchId, pick.mode(), numbers, now);
            draws.persist(draw);
            made.add(draw);
        }
        log.info("로또 번호 뽑기 userId={} batchId={} sets={} mode={}", userId, batchId, pick.sets(), pick.mode());
        List<LottoDrawView> views = made.stream().sorted(Comparator.comparing(LottoDrawEntity::getId).reversed()).map(MemberPointGameService::view).toList();
        return new LottoDrawResultView(views, queries.freeBalance(userId));
    }

    /** 내 번호 한 줄 지우기 — 이미 없으면 그대로(성공), 남의 줄·모양이 틀린 ID 는 404 */
    @Transactional
    public void removeDraw(long userId, String drawId) {
        long id = parse(drawId);
        if (id < 0) {
            throw new LottoDrawNotFoundException();
        }
        Long owner = queries.drawOwner(id).orElse(null);
        if (owner == null) {
            return;
        }
        if (owner != userId) {
            throw new LottoDrawNotFoundException();
        }
        queries.deleteDraw(id, userId);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 읽기 자격 — 로그인 · 사업자 아님 */
    private void assertCanView(long userId) {
        BoardActor actor = actors.resolve(userId, boards.snapshot());
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.business()) {
            throw new PointGameBlockedException(PointGameBlockedException.BUSINESS_ACCOUNT_RESTRICTED);
        }
    }

    /** 쓰기 자격 — 로그인 → 정지 → 사업자 → 포인트 동결 */
    private void assertCanPlay(long userId) {
        BoardActor actor = actors.resolve(userId, boards.snapshot());
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new PointGameBlockedException(PointGameBlockedException.ACCOUNT_RESTRICTED);
        }
        if (actor.business()) {
            throw new PointGameBlockedException(PointGameBlockedException.BUSINESS_ACCOUNT_RESTRICTED);
        }
        restrictions.assertAllowed(userId, RestrictedFeature.POINT_USE);
    }

    private static LottoDrawView view(LottoDrawEntity d) {
        return new LottoDrawView(Long.toString(d.getId()), d.getNumbers(), d.getMode().name(), d.getCreatedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 그 순간의 한국 시간 오늘 0시 */
    private static Instant todayStart(Instant now) {
        return ServiceTime.dateOf(now).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 다음 한국 자정 */
    private static Instant nextReset(Instant now) {
        return ServiceTime.dateOf(now).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 문자열 Snowflake → 숫자(형식이 틀리면 -1) */
    private static long parse(String raw) {
        if (raw == null || !DIGITS.matcher(raw.strip()).matches()) {
            return -1;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
