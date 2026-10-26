package com.community.pointshop.service;

import com.community.board.dto.BoardActor;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.permission.entity.MemberPermission;
import com.community.pointshop.dto.PointShopBuyer;
import com.community.pointshop.dto.request.EquipRequest;
import com.community.pointshop.dto.request.PointShopPurchaseRequest;
import com.community.pointshop.dto.response.MyEquippedView;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.dto.response.PointShopItemView;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.exception.UserItemNotFoundException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 포인트 상점(공개 사이트 서버) — shared 포인트 상점 서비스 앞에서 회원 쪽 값만 푼다: 문자열 ID(형식이 틀리면 없는 것 — 404), 회원 권한 {@code pointshop.use}(회원 역할 —
 * {@link BoardActorResolver}, 글쓰기·쪽지와 같은 판정). 업무 규칙·잠금·트랜잭션은 shared({@link PointShopPurchaseService}·{@link PointShopRefundService}·{@link UserItemService}).
 * 멱등 키·요청 제한은 컨트롤러의 {@code MoneyRequestGuard} 가 이 메서드를 감싼다(그 업무 트랜잭션에 합류한다).
 */
@Service
public class MemberPointShopService {

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final PointShopCatalogService catalog;
    private final PointShopPurchaseService purchases;
    private final PointShopRefundService refunds;
    private final UserItemService userItems;
    private final BoardActorResolver actors;
    private final BoardCatalog boards;

    public MemberPointShopService(PointShopCatalogService catalog, PointShopPurchaseService purchases, PointShopRefundService refunds, UserItemService userItems,
                                  BoardActorResolver actors, BoardCatalog boards) {
        this.catalog = catalog;
        this.purchases = purchases;
        this.refunds = refunds;
        this.userItems = userItems;
        this.actors = actors;
        this.boards = boards;
    }

    /** 품목 상세 — 형식이 틀린 ID 는 없는 품목(404) */
    @Transactional(readOnly = true)
    public PointShopItemView item(long userId, String itemId) {
        long id = parse(itemId);
        if (id < 0) {
            throw new PointShopItemNotFoundException();
        }
        return catalog.memberItem(userId, id);
    }

    /**
     * 산다 — 자격(401 → 정지 → 이메일 인증 → {@code pointshop.use} → 포인트 동결) → 고지 동의 → 품목(형식이 틀린 ID 도 404) → … 는 shared {@link PointShopPurchaseService#purchase}.
     */
    @Transactional
    public PointShopPurchaseResultView purchase(long userId, PointShopPurchaseRequest body) {
        BoardActor actor = actors.resolve(userId, boards.snapshot());
        boolean permitted = actor.can(MemberPermission.POINTSHOP_USE.code());
        long itemId = body == null ? -1 : parse(body.itemId());
        boolean agreed = body != null && Boolean.TRUE.equals(body.agreeRefundNotice());
        return purchases.purchase(new PointShopBuyer(userId, permitted), itemId, agreed);
    }

    /** 장착 — 형식이 틀린 보유 줄 ID 는 404 */
    @Transactional
    public MyEquippedView equip(long userId, EquipSlot slot, EquipRequest body) {
        long userItemId = body == null ? -1 : parse(body.userItemId());
        if (userItemId < 0) {
            throw new UserItemNotFoundException();
        }
        return userItems.equip(userId, slot, userItemId);
    }

    /** 셀프 환불 — 형식이 틀린 보유 줄 ID 는 404. 정지 중이면 환불 신청(202 — 「1-2 정지 중 환불 신청」) */
    @Transactional
    public SelfRefundOutcome refund(long userId, String userItemId) {
        long id = parse(userItemId);
        if (id < 0) {
            throw new UserItemNotFoundException();
        }
        return refunds.refundSelf(userId, id);
    }

    /** 문자열 Snowflake → 숫자(형식이 틀리면 -1) */
    static long parse(String raw) {
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
