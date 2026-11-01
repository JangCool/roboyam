package com.community.hotdeal.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardFields;
import com.community.board.service.ContentRules;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.DealValues;
import com.community.hotdeal.dto.request.DealImageInput;
import com.community.hotdeal.dto.request.DealInput;
import com.community.hotdeal.dto.response.DealWarningView;
import com.community.hotdeal.entity.DealViews;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.entity.PostDealRevisionEntity;
import com.community.hotdeal.exception.DealDailyPostLimitException;
import com.community.hotdeal.exception.DealEndedException;
import com.community.hotdeal.exception.DealSameStoreTooSoonException;
import com.community.hotdeal.og.DealLinkInspector;
import com.community.hotdeal.repository.master.DealOgImageRepository;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.repository.master.PostDealRevisionRepository;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.post.entity.PostEntity;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글쓰기·고치기({@code PostWriteService})에 붙는 딜 — 검사는 글 칸 검사와 함께(트랜잭션 밖 — 새 링크의 리디렉션 체인은 여기서 가져온다), 저장은 글 저장 트랜잭션 안에서.
 * docs/features/hotdeal-contract.md 「필드·검증」·「확정 가정」·서버 단계 고정 규칙 2·3·6·7, D3·D5.
 *
 * <ul>
 *   <li><b>칸</b>: 핫딜 게시판({@code kind DEAL})이면 {@code deal} 필수, 아니면 거절({@code fields.deal}). 칸 오류는 {@code fields."deal.*"} 로 다른 글 칸 오류와 함께.</li>
 *   <li><b>링크</b>: {@code http(s)} ≤1000자. 새 링크는 리디렉션 체인의 모든 호스트를 지금 규칙으로 판정 — 차단이면 칸 오류, {@code SITE} 에서 제휴(개인 제휴)면 칸 오류
 *       {@code DEAL_MEMBER_AFFILIATE_OFF}. <b>고치기에서 링크를 그대로 두면</b> 저장한 호스트로 지금 규칙을 다시 판정하고, 차단이어도 저장 + {@code warnings[]}
 *       ({@code DEAL_LINK_BLOCKED}), {@code SITE} 여도 개인 제휴 칸 오류 없음(규칙 7).</li>
 *   <li><b>제휴</b>: {@code MEMBER} 에서 제휴 판정이면 {@code affiliate} 강제 켬, {@code SITE} 면 새 링크는 false(그대로 둔 링크는 지금 값 유지 — 방식을 되돌리면 개인 제휴 표시가 다시
 *       필요하다). 표시 종류({@code affiliateKind})는 읽을 때 지금 방식으로 계산한다.</li>
 *   <li><b>가격</b>: 딜가 필수 0 이상, 정상가 ≥ 딜가, 셋 다 ≤ {@code priceMaxWon}, 배송비 0 무료 · null 모름. 0원 딜이거나 할인율 ≥ {@code discountWarnPercent} 면 가격 기준 필수.</li>
 *   <li><b>마감</b>: 지금 이후 ~ 365일. 고치기에서 처음과 같은 마감은 다시 보지 않는다. 재고 {@code SOLD_OUT} 저장 = 작성자 종료(규칙 6).</li>
 *   <li><b>한도</b>(저장 트랜잭션 안, 작성자 잠금 아래): 하루(한국 0시) 글 수 {@code 429 DEAL_DAILY_POST_LIMIT} · 같은 판매처 간격 {@code 429 DEAL_SAME_STORE_TOO_SOON}.</li>
 *   <li><b>고치기</b>: 칸을 빼면 그대로(그래도 링크를 지금 규칙으로 다시 판정해 경고), JSON null 은 거절. 종료(ENDED)면 딜 칸을 고칠 수 없다({@code 409 DEAL_ENDED}),
 *       기간 종료(EXPIRED)는 고칠 수 있다. 딜 칸이 바뀌면 이력 한 줄(D3) + 가격 수정 셈(딜가·정상가만) + 판매처 호스트가 바뀌면 「링크 바뀜」과 운영자 알림.</li>
 * </ul>
 */
@Component
public class DealWriter {

    public static final String WARN_LINK_BLOCKED = "DEAL_LINK_BLOCKED";
    public static final int STORE_NAME_MAX = 40;
    public static final int NOTE_MAX = 100;
    public static final int ENDS_MAX_DAYS = 365;
    public static final Set<String> PRICE_BASIS = Set.of("LISTED", "COUPON", "CARD", "MEMBER", "OVERSEAS");
    public static final Set<String> STOCKS = Set.of("UNKNOWN", "IN_STOCK", "LOW", "SOLD_OUT");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DealSettingsReader settings;
    private final DealRuleBook rules;
    private final DealLinkInspector inspector;
    private final PostDealRepository deals;
    private final PostDealRevisionRepository revisions;
    private final DealOgImageRepository ogImages;
    private final FileRepositoryQDSL files;
    private final ImageStorage storage;
    private final DealQueries queries;
    private final SnowflakeIdGenerator ids;
    private final NotificationPublisher publisher;
    private final Messages messages;
    private final ObjectProvider<Clock> clocks;
    private final DealEndService ends;

    public DealWriter(DealSettingsReader settings, DealRuleBook rules, DealLinkInspector inspector, PostDealRepository deals, PostDealRevisionRepository revisions,
                      DealOgImageRepository ogImages, FileRepositoryQDSL files, ImageStorage storage, DealQueries queries, SnowflakeIdGenerator ids,
                      NotificationPublisher publisher, Messages messages, ObjectProvider<Clock> clocks, DealEndService ends) {
        this.ends = ends;
        this.settings = settings;
        this.rules = rules;
        this.inspector = inspector;
        this.deals = deals;
        this.revisions = revisions;
        this.ogImages = ogImages;
        this.files = files;
        this.storage = storage;
        this.queries = queries;
        this.ids = ids;
        this.publisher = publisher;
        this.messages = messages;
        this.clocks = clocks;
    }

    /** 검사를 마친 딜 — 저장할 값 · 링크 판정 · 경고 */
    public record Checked(DealValues values, PostDealEntity.Link link, ChainVerdict verdict, List<DealWarningView> warnings) {
    }

    /** 고치기의 딜 바꾸기 — {@code checked} 는 {@link Kind#SET} 일 때만 */
    public record Change(Kind kind, Checked checked) {

        public static final Change NONE = new Change(Kind.NONE, null);
        public static final Change KEEP = new Change(Kind.KEEP, null);
    }

    public enum Kind {
        /** 핫딜 글이 아니다 */
        NONE,
        /** 칸이 빠졌다 — 그대로(링크만 다시 판정) */
        KEEP,
        /** 값 — 고친다 */
        SET
    }

    public Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }

    /** 사업자 핫딜 글이면 작성자 고치기·지우기를 막는다(403 {@code DEAL_BUSINESS_POST_LOCKED} — biz-hotdeal-contract 규칙 11) */
    public void requireNotBusiness(long postId) {
        if (deals.findById(postId).map(PostDealEntity::isBusiness).orElse(false)) {
            throw new com.community.hotdeal.exception.DealBusinessPostLockedException();
        }
    }

    /** 핫딜 게시판인가 */
    public static boolean dealBoard(EffectiveBoard b) {
        return BoardFields.KIND_DEAL.equals(b.kind());
    }

    /**
     * 핫딜 게시판 쓰기 자격 — 게시판 판정({@link BoardAccessPolicy#writeBlocked}) 다음에 더 본다: 비회원 401 · 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED} ·
     * 첫 등급(새싹) {@code 403 GRADE_TOO_LOW}(게시판 매니저·스텝은 면제, 오류 본문 {@code minGrade}·{@code minGradeLabel}). 게시판 {@code writeMin} 이 비회원·첫 등급으로
     * 잘못 열려 있어도 막는다(계약 「쓰기 자격」). 3단계부터 같은 자격이 {@link BoardAccessPolicy#writeBlocked} 안에도 있어(게시판 응답·쓸 수 있는 게시판 목록이 미리 막는다) 여기는 겹쳐 지키는 자리다.
     */
    public static void requireWriter(EffectiveBoard b, BoardActor actor, BoardCatalogSnapshot s) {
        if (!dealBoard(b)) {
            return;
        }
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        if (!actor.emailVerified()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.EMAIL_NOT_VERIFIED);
        }
        if (!actor.staffOf(b.id()) && actor.gradeKey() != null && actor.gradeKey().equals(s.firstGradeKey())) {
            PostWriteService.requireWritable(BoardAccessPolicy.GRADE_TOO_LOW, b, s);
        }
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────────

    /**
     * 쓰기의 딜 검사(트랜잭션 밖).
     *
     * @return 저장할 딜(핫딜 게시판이 아니거나 칸 오류면 null — 칸 오류는 {@code fields} 에)
     */
    public Checked checkForCreate(DealInput input, EffectiveBoard b, Long authorId, Map<String, MessageRef> fields) {
        if (!dealBoard(b)) {
            if (input != null) {
                fields.put("deal", new MessageRef("hotdeal.deal.notAllowed"));
            }
            return null;
        }
        if (input == null) {
            fields.put("deal", new MessageRef("hotdeal.deal.required"));
            return null;
        }
        return validate(input, null, authorId, null, now(), fields);
    }

    /** 새 글의 딜을 넣는다 — 글 저장 트랜잭션 안, 작성자 잠금 아래(하루 한도·같은 판매처 간격을 여기서 본다) */
    public void create(long postId, long boardId, long authorId, Checked c, Instant now) {
        checkRate(authorId, storeHost(c.link()), now);
        deals.persist(PostDealEntity.create(postId, boardId, authorId, c.values(), c.link(), now));
        attachImage(postId, c.values(), now);
    }

    /**
     * 하루 글 수(한국 0시부터) → 같은 판매처 간격. 0 이면 그 한도는 없다.
     *
     * @param storeHost 판매처(최종 호스트, 없으면 호스트)
     */
    public void checkRate(long authorId, String storeHost, Instant now) {
        DealSettings s = settings.current();
        if (s.memberDailyPostLimit() > 0) {
            Instant dayStart = ServiceTime.dateOf(now).atStartOfDay(ServiceTime.ZONE).toInstant();
            if (queries.countByAuthorSince(authorId, dayStart) >= s.memberDailyPostLimit()) {
                throw new DealDailyPostLimitException(s.memberDailyPostLimit());
            }
        }
        int interval = s.sameStoreIntervalMinutes();
        if (interval > 0 && storeHost != null) {
            Duration gap = Duration.ofMinutes(interval);
            Optional<Instant> last = queries.lastAtSameStore(authorId, storeHost, now.minus(gap));
            if (last.isPresent()) {
                long leftSeconds = Math.max(1, Duration.between(now, last.get().plus(gap)).toMillis() / 1000 + 1);
                long leftMinutes = Math.max(1, (leftSeconds + 59) / 60);
                throw new DealSameStoreTooSoonException(leftMinutes, interval, leftSeconds);
            }
        }
    }

    // ── 고치기 ────────────────────────────────────────────────────────────────

    /**
     * 고치기의 딜 검사(잠그지 않고 지금 딜을 한 번 읽는다 — 저장할 때 {@link #apply} 가 잠그고 다시 본다). 칸 구분(빠짐·null·값)은 투표와 같이 {@link JsonNode} 로 받는다.
     */
    public Change checkForUpdate(JsonNode raw, PostEntity post, EffectiveBoard b, Map<String, MessageRef> fields) {
        if (!dealBoard(b)) {
            if (raw != null && !raw.isNull()) {
                fields.put("deal", new MessageRef("hotdeal.deal.notAllowed"));
            }
            return Change.NONE;
        }
        if (raw == null) {
            return Change.KEEP;
        }
        if (raw.isNull()) {
            fields.put("deal", new MessageRef("hotdeal.deal.required"));
            return Change.KEEP;
        }
        DealInput input;
        try {
            input = raw.isObject() ? MAPPER.treeToValue(raw, DealInput.class) : null;
        } catch (JacksonException e) {
            input = null;
        }
        if (input == null) {
            fields.put("deal", new MessageRef("hotdeal.deal.invalid"));
            return Change.KEEP;
        }
        PostDealEntity current = deals.findById(post.getId()).orElse(null);
        if (current != null && current.isEnded()) {
            throw new DealEndedException();
        }
        Checked checked = validate(input, current, post.getAuthorId(), post.getId(), now(), fields);
        return checked == null ? Change.KEEP : new Change(Kind.SET, checked);
    }

    /**
     * 고친 글의 딜을 맞춘다 — 글 고치기 트랜잭션 안(글 이미지 맞추기 다음). 종료된 딜을 고치려 하면 409 {@code DEAL_ENDED}(글도 고치지 않는다).
     *
     * @return 저장은 됐지만 알릴 것(예 {@code DEAL_LINK_BLOCKED})
     */
    public List<DealWarningView> apply(PostEntity post, Change change, Instant now) {
        if (change.kind() == Kind.NONE) {
            return List.of();
        }
        long postId = post.getId();
        PostDealEntity current = deals.findWithLockByPostId(postId).orElse(null);
        if (change.kind() == Kind.KEEP) {
            if (current == null) {
                return List.of();
            }
            reattach(postId, current, now); // 글 이미지 맞추기가 본문에 없는 딜 이미지를 떼었을 수 있다
            ChainVerdict verdict = rules.judge(hostsOf(current));
            return verdict.blocked() ? List.of(blockedWarning()) : List.of();
        }
        Checked c = change.checked();
        if (current == null) {
            deals.persist(PostDealEntity.create(postId, post.getBoardId(), post.getAuthorId(), c.values(), c.link(), now)); // 딜 줄이 없던 옛 글
            attachImage(postId, c.values(), now);
            return c.warnings();
        }
        if (current.isEnded()) {
            throw new DealEndedException();
        }
        List<Map<String, String>> changes = diff(current, c.values());
        if (!changes.isEmpty()) {
            boolean priceEdit = !Objects.equals(current.getDealPrice(), c.values().dealPrice()) || !Objects.equals(current.getNormalPrice(), c.values().normalPrice());
            boolean hostChanged = !Objects.equals(storeHost(current.getProductHost(), current.getFinalHost()), storeHost(c.link()));
            revisions.persist(PostDealRevisionEntity.create(ids.nextId(), postId, PostDealRevisionEntity.BY_AUTHOR, current.getAuthorId(), json(changes), hostChanged,
                    priceEdit, now));
            current.edit(c.values(), c.link(), priceEdit, hostChanged, now);
            deals.flush();
            if (current.isEnded()) {
                ends.rewardRequesters(current); // 재고를 품절로 저장 = 작성자 종료(규칙 6) — 의심이었으면 의심 구간 요청자 보상(규칙 5)
            }
            if (hostChanged) {
                publisher.publish(AdminNotificationEvent.HOTDEAL_LINK_HOST_CHANGED,
                        NotificationContext.empty().dedupe(Long.toString(postId)).link("postId", Long.toString(postId)));
            }
        }
        attachImage(postId, c.values(), now);
        return c.warnings();
    }

    // ── 검사 ──────────────────────────────────────────────────────────────────

    /**
     * @param current 고치기면 지금 딜(쓰기는 null)
     * @return 저장할 딜 — 칸 오류가 하나라도 있으면 null
     */
    Checked validate(DealInput in, PostDealEntity current, Long authorId, Long postId, Instant now, Map<String, MessageRef> fields) {
        DealSettings s = settings.current();
        int before = fields.size();
        List<DealWarningView> warnings = new ArrayList<>();

        String storeName = text(in.storeName());
        if (storeName == null || ContentRules.length(storeName) > STORE_NAME_MAX) {
            fields.put("deal.storeName", new MessageRef("hotdeal.storeName.length", STORE_NAME_MAX));
        }

        String url = in.productUrl() == null ? null : in.productUrl().strip();
        ChainVerdict verdict = null;
        boolean linkKept = current != null && url != null && url.equals(current.getProductUrl());
        if (url == null || url.isEmpty() || url.length() > DealHosts.URL_MAX || !DealHosts.isHttpUrl(url)) {
            fields.put("deal.productUrl", new MessageRef("hotdeal.productUrl.invalid", DealHosts.URL_MAX));
        } else {
            List<String> hosts = linkKept ? hostsOf(current) : inspector.inspect(url, authorId, false).hosts();
            verdict = rules.judge(hosts.isEmpty() ? List.of(DealHosts.hostOf(url)) : hosts);
            if (verdict.blocked()) {
                if (linkKept) {
                    warnings.add(blockedWarning()); // 그대로 둔 링크 — 지금 규칙이 차단이어도 저장한다(규칙 7)
                } else {
                    fields.put("deal.productUrl", new MessageRef(verdict.shortener() ? "hotdeal.productUrl.shortener" : "hotdeal.productUrl.blocked"));
                }
            } else if (verdict.affiliate() && s.siteMode() && !linkKept) {
                fields.put("deal.productUrl", new MessageRef("error.DEAL_MEMBER_AFFILIATE_OFF"));
            }
        }

        Long dealPrice = in.dealPrice();
        long max = s.priceMaxWon();
        if (dealPrice == null || dealPrice < 0) {
            fields.put("deal.dealPrice", new MessageRef("hotdeal.price.dealRequired"));
        } else if (dealPrice > max) {
            fields.put("deal.dealPrice", new MessageRef("hotdeal.price.max", String.format("%,d", max)));
        }
        Long normalPrice = in.normalPrice();
        if (normalPrice != null) {
            if (normalPrice < 0) {
                fields.put("deal.normalPrice", new MessageRef("hotdeal.price.invalid"));
            } else if (normalPrice > max) {
                fields.put("deal.normalPrice", new MessageRef("hotdeal.price.max", String.format("%,d", max)));
            } else if (dealPrice != null && dealPrice >= 0 && normalPrice < dealPrice) {
                fields.put("deal.normalPrice", new MessageRef("hotdeal.price.normalBelowDeal"));
            }
        }
        Long shippingFee = in.shippingFee();
        if (shippingFee != null) {
            if (shippingFee < 0) {
                fields.put("deal.shippingFee", new MessageRef("hotdeal.price.invalid"));
            } else if (shippingFee > max) {
                fields.put("deal.shippingFee", new MessageRef("hotdeal.price.max", String.format("%,d", max)));
            }
        }
        String couponNote = text(in.couponNote());
        if (couponNote != null && ContentRules.length(couponNote) > NOTE_MAX) {
            fields.put("deal.couponNote", new MessageRef("hotdeal.note.length", NOTE_MAX));
        }
        String basisKind = in.priceBasisKind() == null || in.priceBasisKind().isBlank() ? null : in.priceBasisKind().strip();
        if (basisKind != null && !PRICE_BASIS.contains(basisKind)) {
            fields.put("deal.priceBasisKind", new MessageRef("hotdeal.priceBasis.unknown"));
        } else if (basisKind == null && dealPrice != null && dealPrice == 0) {
            fields.put("deal.priceBasisKind", new MessageRef("hotdeal.priceBasis.requiredFree"));
        } else if (basisKind == null && !fields.containsKey("deal.normalPrice")) {
            Integer discount = DealViews.discountRate(normalPrice, dealPrice);
            if (discount != null && discount >= s.discountWarnPercent()) {
                fields.put("deal.priceBasisKind", new MessageRef("hotdeal.priceBasis.requiredDiscount", s.discountWarnPercent()));
            }
        }
        boolean dutyIncluded = "OVERSEAS".equals(basisKind) && Boolean.TRUE.equals(in.dutyIncluded());
        String priceBasis = text(in.priceBasis());
        if (priceBasis != null && ContentRules.length(priceBasis) > NOTE_MAX) {
            fields.put("deal.priceBasis", new MessageRef("hotdeal.note.length", NOTE_MAX));
        }

        Instant endsAt = null;
        if (in.endsAt() != null && !in.endsAt().isBlank()) {
            endsAt = parseInstant(in.endsAt().strip());
            boolean sameAsBefore = current != null && endsAt != null && endsAt.equals(current.getEndsAt());
            if (endsAt == null) {
                fields.put("deal.endsAt", new MessageRef("hotdeal.endsAt.range", ENDS_MAX_DAYS));
            } else if (!sameAsBefore && (!endsAt.isAfter(now) || endsAt.isAfter(now.plus(Duration.ofDays(ENDS_MAX_DAYS))))) {
                fields.put("deal.endsAt", new MessageRef("hotdeal.endsAt.range", ENDS_MAX_DAYS));
            }
        }
        String stock = in.stockState() == null || in.stockState().isBlank() ? PostDealEntity.STOCK_UNKNOWN : in.stockState().strip();
        if (!STOCKS.contains(stock)) {
            fields.put("deal.stockState", new MessageRef("hotdeal.stock.unknown"));
        }

        ImageChoice image = image(in.image(), current, authorId, postId);
        if (image == null) {
            fields.put("deal.image", new MessageRef("hotdeal.image.invalid"));
        }

        if (fields.size() > before || verdict == null) {
            return null;
        }
        boolean affiliate;
        if (s.siteMode()) {
            affiliate = linkKept && current.isAffiliate(); // 사이트 제휴 — 새 링크는 개인 제휴를 받지 않는다(D5)
        } else {
            affiliate = verdict.affiliate() || Boolean.TRUE.equals(in.affiliate()); // 제휴 판정이면 강제로 켠다
        }
        DealValues values = new DealValues(storeName, url, normalPrice, dealPrice, shippingFee, couponNote, basisKind, dutyIncluded, priceBasis, endsAt, stock,
                affiliate, image.source(), image.ogImageId(), image.imageId(), image.url());
        PostDealEntity.Link link = new PostDealEntity.Link(verdict.host(), verdict.finalHost(), DealUrlKey.of(url, verdict.keyParams()));
        return new Checked(values, link, verdict, warnings);
    }

    /** 고른 이미지 — 없음은 모든 칸이 null */
    private record ImageChoice(String source, Long ogImageId, Long imageId, String url) {

        static final ImageChoice NONE = new ImageChoice(null, null, null, null);
    }

    /**
     * 딜 이미지 확인 — OG 는 우리 OG 사본 표의 주소(지우지 않은 것), UPLOAD 는 글쓴이가 올린 아직 쓰이지 않은 글 이미지이거나 이 글이 쓰던 이미지. 지금과 같으면 그대로.
     *
     * @return 고른 이미지, 쓸 수 없으면 null
     */
    private ImageChoice image(DealImageInput in, PostDealEntity current, Long authorId, Long postId) {
        if (in == null) {
            return ImageChoice.NONE;
        }
        if (PostDealEntity.IMAGE_OG.equals(in.source())) {
            if (in.url() == null || in.url().isBlank()) {
                return null;
            }
            String url = in.url().strip();
            if (current != null && PostDealEntity.IMAGE_OG.equals(current.getImageSource()) && url.equals(current.getImageUrl())) {
                return new ImageChoice(PostDealEntity.IMAGE_OG, current.getOgImageId(), null, current.getImageUrl());
            }
            return ogImages.findByPublicUrl(url).filter(og -> !og.isRemoved())
                    .map(og -> new ImageChoice(PostDealEntity.IMAGE_OG, og.getId(), null, og.getPublicUrl())).orElse(null);
        }
        if (PostDealEntity.IMAGE_UPLOAD.equals(in.source())) {
            Long id = PostQueryService.parseId(in.imageId());
            if (id == null) {
                return null;
            }
            if (current != null && PostDealEntity.IMAGE_UPLOAD.equals(current.getImageSource()) && id.equals(current.getImageId())) {
                return new ImageChoice(PostDealEntity.IMAGE_UPLOAD, null, id, current.getImageUrl());
            }
            FileEntity file = files.postImages(List.of(id)).stream().findFirst().orElse(null);
            if (file == null || !usable(file, authorId, postId)) {
                return null;
            }
            return new ImageChoice(PostDealEntity.IMAGE_UPLOAD, null, id, storage.publicUrl(file.getStorageKey()));
        }
        return null;
    }

    private static boolean usable(FileEntity file, Long authorId, Long postId) {
        if (postId != null && postId.equals(file.getPostId()) && file.getStatus() == FileStatus.IN_USE) {
            return true;
        }
        return file.getStatus() == FileStatus.PENDING && authorId != null && authorId.equals(file.getUploadedByUser());
    }

    /** 고른 이미지를 글에 붙인다(OG 사본은 처음 붙은 글을 남기고, 올린 이미지는 글 이미지로 쓰는 중) */
    private void attachImage(long postId, DealValues v, Instant now) {
        if (PostDealEntity.IMAGE_OG.equals(v.imageSource()) && v.ogImageId() != null) {
            ogImages.findById(v.ogImageId()).ifPresent(og -> og.attach(postId, now));
        } else if (PostDealEntity.IMAGE_UPLOAD.equals(v.imageSource()) && v.imageId() != null) {
            files.lockPostImages(List.of(v.imageId())).forEach(f -> f.attachToPost(postId, now));
        }
    }

    private void reattach(long postId, PostDealEntity current, Instant now) {
        if (PostDealEntity.IMAGE_UPLOAD.equals(current.getImageSource()) && current.getImageId() != null) {
            files.lockPostImages(List.of(current.getImageId())).forEach(f -> f.attachToPost(postId, now));
        }
    }

    private DealWarningView blockedWarning() {
        return new DealWarningView(WARN_LINK_BLOCKED, messages.get(new MessageRef("hotdeal.warning.DEAL_LINK_BLOCKED")));
    }

    /** 저장한 딜의 판정 호스트(호스트 · 최종 호스트) */
    static List<String> hostsOf(PostDealEntity d) {
        List<String> hosts = new ArrayList<>();
        hosts.add(d.getProductHost());
        if (d.getFinalHost() != null) {
            hosts.add(d.getFinalHost());
        }
        return hosts;
    }

    /** 판매처 = 최종 호스트, 없으면 호스트 */
    static String storeHost(PostDealEntity.Link link) {
        return storeHost(link.productHost(), link.finalHost());
    }

    static String storeHost(String productHost, String finalHost) {
        return finalHost != null ? finalHost : productHost;
    }

    /** 바뀐 딜 칸 [{field, before, after}](값은 글자, 이미지는 주소) */
    static List<Map<String, String>> diff(PostDealEntity d, DealValues v) {
        List<Map<String, String>> out = new ArrayList<>();
        add(out, "storeName", d.getStoreName(), v.storeName());
        add(out, "productUrl", d.getProductUrl(), v.productUrl());
        add(out, "normalPrice", d.getNormalPrice(), v.normalPrice());
        add(out, "dealPrice", d.getDealPrice(), v.dealPrice());
        add(out, "shippingFee", d.getShippingFee(), v.shippingFee());
        add(out, "couponNote", d.getCouponNote(), v.couponNote());
        add(out, "priceBasisKind", d.getPriceBasisKind(), v.priceBasisKind());
        add(out, "dutyIncluded", d.isDutyIncluded(), v.dutyIncluded());
        add(out, "priceBasis", d.getPriceBasis(), v.priceBasis());
        add(out, "endsAt", d.getEndsAt(), v.endsAt());
        add(out, "stockState", d.getStockState(), v.stockState());
        add(out, "affiliate", d.isAffiliate(), v.affiliate());
        add(out, "image", d.getImageUrl(), v.imageUrl());
        return out;
    }

    private static void add(List<Map<String, String>> out, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, String> change = new LinkedHashMap<>();
            change.put("field", field);
            change.put("before", before == null ? null : before.toString());
            change.put("after", after == null ? null : after.toString());
            out.add(change);
        }
    }

    private static String json(List<Map<String, String>> changes) {
        try {
            return MAPPER.writeValueAsString(changes);
        } catch (JacksonException e) {
            throw new IllegalStateException("이력 JSON", e);
        }
    }

    /** 앞뒤 공백·줄바꿈을 정리한 글자(비면 null) */
    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String t = ContentRules.normalize(value).replace('\n', ' ').strip();
        return t.isEmpty() ? null : t;
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
