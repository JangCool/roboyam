package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.AddressRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배송지 보호(commerce.md 10-9, 계약 결정 20·30) — 평문은 화면·목록·로그·감사 detail 에 싣지 않는다.
 *
 * <ul>
 *   <li><b>가린 값</b>: {@code 홍*동} · {@code 010-****-5678} · 주소 앞 두 어절.</li>
 *   <li><b>회원 본인</b>: 「전체 보기」로 자기 주문의 값. 수정 기한은 주문 기준(결정 30) — 마감 후 발주 공구는 공구 발주(ORDERED) 전, 즉시 발송 공구는 주문 준비중 전.</li>
 *   <li><b>운영자 열람</b>({@code commerce.address.reveal} — 컨트롤러): 사유 5~200자 → 열람 기록·감사를 같은 트랜잭션에서 남긴 뒤에만 값, 화면은 설정 시간(기본 10분) 뒤 다시 가린다.</li>
 *   <li><b>배송 명단 내보내기</b>({@code commerce.export} + {@code commerce.address.reveal}): 발송 전 주문(CONFIRMED·PREPARING)만, 마감 후 발주 공구는 발주 뒤(결정 30),
 *       배송에 필요한 칸만, 수식 주입 방어(결정 20 — {@code = + - @} 로 시작하는 칸 앞에 {@code '}), 감사에 건수·사유만.</li>
 * </ul>
 */
@Service
public class AddressRevealService {

    /** 운영자에게 보인 배송지와 화면이 다시 가릴 시각 */
    public record Revealed(ShippingAddress address, Instant hideAt) {
    }

    /** 내보낸 명단(CSV — UTF-8, 엑셀이 한글을 읽게 BOM) */
    public record ExportFile(String fileName, String csv, int rowCount) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final FulfillmentStore store;
    private final DataCipher cipher;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AddressRevealService(ParticipationStore participations, GroupBuyStore groups, FulfillmentStore store, DataCipher cipher, GroupBuyAudit audit,
                                SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.store = store;
        this.cipher = cipher;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 가린 값 ─────────────────────────────────────────────────────────────

    /** 가린 배송지(파기됐으면 모든 칸 null — 우편번호만 평문이라 그것도 지운 뒤에는 없다) */
    public ShippingAddress masked(long participationId) {
        AddressRow row = participations.address(participationId).orElseThrow(GroupBuyException::orderNotFound);
        if (row.maskedAt() != null || row.recipientEnc() == null) {
            return new ShippingAddress(null, null, null, null, null, null);
        }
        ShippingAddress a = decrypt(row);
        return new ShippingAddress(maskName(a.recipient()), maskPhone(a.phone()), a.postcode(), maskAddress(a.address1()), null, null);
    }

    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int[] cps = name.codePoints().toArray();
        if (cps.length == 1) {
            return "*";
        }
        StringBuilder out = new StringBuilder().appendCodePoint(cps[0]);
        out.append("*".repeat(Math.max(1, cps.length - 2)));
        if (cps.length > 2) {
            out.appendCodePoint(cps[cps.length - 1]);
        }
        return out.toString();
    }

    public static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /** 주소 앞 두 어절 */
    public static String maskAddress(String address) {
        if (address == null) {
            return null;
        }
        String[] words = address.strip().split("\\s+");
        return words.length <= 2 ? String.join(" ", words) : words[0] + " " + words[1] + " …";
    }

    // ── 회원 본인 ───────────────────────────────────────────────────────────

    /** 본인 「전체 보기」 — 남의 주문은 404 */
    public ShippingAddress memberView(long userId, long participationId) {
        ParticipationRow p = participations.find(participationId).filter(r -> r.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
        AddressRow row = participations.address(p.id()).orElseThrow();
        if (row.maskedAt() != null || row.recipientEnc() == null) {
            throw GroupBuyException.addressErased();
        }
        return decrypt(row);
    }

    /**
     * 본인 배송지 수정 — 기한(결정 30): 마감 후 발주 공구는 공구 발주 전 + 주문 결제 중·참여, 즉시 발송 공구는 주문 준비중 전(CONFIRMED). 지나면 409 {@code GROUP_BUY_ADDRESS_LOCKED}.
     */
    public void memberUpdate(long userId, long participationId, ShippingAddress a) {
        validate(a);
        cipher.requireConfigured();
        tx.executeWithoutResult(status -> {
            ParticipationRow p = participations.lock(participationId).filter(r -> r.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
            if (!editable(p, g)) {
                throw GroupBuyException.addressLocked();
            }
            if (!participations.updateAddress(p.id(), cipher.encrypt(GroupBuyJoinService.RECIPIENT, a.recipient().strip()), cipher.encrypt(GroupBuyJoinService.PHONE,
                    a.phone().strip()), a.postcode().strip(), cipher.encrypt(GroupBuyJoinService.ADDRESS1, a.address1().strip()), enc(GroupBuyJoinService.ADDRESS2,
                    a.address2()), enc(GroupBuyJoinService.MEMO, a.memo()), now())) {
                throw GroupBuyException.addressErased();
            }
        });
    }

    /** 배송지를 고칠 수 있는가(결정 30 — 주문 기준) */
    public static boolean editable(ParticipationRow p, GroupBuyRow g) {
        if (g.immediate()) {
            return EnumSet.of(ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.CONFIRMED).contains(p.status());
        }
        return EnumSet.of(ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED).contains(p.status())
                && EnumSet.of(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS).contains(g.status());
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    /** 운영자 열람 — 열람 기록·감사를 같은 트랜잭션에서 남긴 뒤 값(10-9) */
    public Revealed reveal(long participationId, String reason, long operatorId, String ip) {
        String why = GroupBuyAudit.reason(reason);
        return tx.execute(status -> {
            Instant now = now();
            ParticipationRow p = participations.find(participationId).orElseThrow(GroupBuyException::orderNotFound);
            AddressRow row = participations.address(p.id()).orElseThrow();
            if (row.maskedAt() != null || row.recipientEnc() == null) {
                throw GroupBuyException.addressErased();
            }
            store.insertAddressView(ids.nextId(), "REVEAL", p.id(), p.groupBuyId(), operatorId, why, null, now);
            audit.record(operatorId, AuditActions.GROUPBUY_ADDRESS_REVEAL, GroupBuyAudit.TARGET_ORDER, p.id(), ip, Map.of("reason", why, "orderNo", p.orderNo()));
            int minutes = groups.settings().addressRevealMinutes();
            return new Revealed(decrypt(row), now.plus(Duration.ofMinutes(minutes)));
        });
    }

    /** 배송 명단 내보내기(결정 20·30) — 한 줄에 주문 하나(남은 줄 「단품 × 수량」) */
    public ExportFile export(long groupBuyId, String reason, long operatorId, String ip) {
        String why = GroupBuyAudit.reason(reason);
        return tx.execute(status -> {
            Instant now = now();
            GroupBuyRow g = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
            if (!g.immediate() && g.status() != GroupBuyStatus.ORDERED && g.status() != GroupBuyStatus.SHIPPING) {
                throw GroupBuyException.exportNotReady();
            }
            Map<Long, String> skuNames = groups.skus(groupBuyId).stream().collect(Collectors.toMap(SkuRow::id, SkuRow::name));
            StringBuilder csv = new StringBuilder("﻿");
            csv.append(String.join(",", List.of("주문번호", "받는 분", "전화", "우편번호", "주소", "상세 주소", "배송 메모", "상품"))).append("\r\n");
            int rows = 0;
            for (AddressRow row : participations.exportAddresses(groupBuyId)) {
                if (row.maskedAt() != null || row.recipientEnc() == null) {
                    continue;
                }
                ParticipationRow p = participations.find(row.participationId()).orElseThrow();
                ShippingAddress a = decrypt(row);
                String products = participations.items(p.id()).stream().filter(i -> i.heldQty() > 0).map(i -> skuNames.get(i.skuId()) + " × " + i.heldQty())
                        .collect(Collectors.joining(" / "));
                csv.append(List.of(p.orderNo(), a.recipient(), a.phone(), nz(a.postcode()), a.address1(), nz(a.address2()), nz(a.memo()), products).stream()
                        .map(AddressRevealService::csvCell).collect(Collectors.joining(","))).append("\r\n");
                rows++;
            }
            store.insertAddressView(ids.nextId(), "EXPORT", null, groupBuyId, operatorId, why, rows, now);
            audit.record(operatorId, AuditActions.GROUPBUY_SHIPPING_EXPORT, GroupBuyAudit.TARGET_GROUP_BUY, groupBuyId, ip, Map.of("rowCount", rows, "reason", why));
            String fileName = "shipping-" + groupBuyId + "-" + STAMP.format(now.atZone(ServiceTime.ZONE)) + ".csv";
            return new ExportFile(fileName, csv.toString(), rows);
        });
    }

    /** CSV 한 칸 — 수식 주입 방어(결정 20: {@code = + - @}(그리고 탭·CR)로 시작하면 앞에 {@code '}) 뒤 늘 큰따옴표로 감싼다 */
    public static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private ShippingAddress decrypt(AddressRow row) {
        return new ShippingAddress(cipher.decrypt(GroupBuyJoinService.RECIPIENT, row.recipientEnc()), cipher.decrypt(GroupBuyJoinService.PHONE, row.phoneEnc()),
                row.postcode(), cipher.decrypt(GroupBuyJoinService.ADDRESS1, row.address1Enc()), dec(GroupBuyJoinService.ADDRESS2, row.address2Enc()),
                dec(GroupBuyJoinService.MEMO, row.memoEnc()));
    }

    private String dec(com.community.zconfiguration.crypto.DataPurpose purpose, String value) {
        return value == null ? null : cipher.decrypt(purpose, value);
    }

    private String enc(com.community.zconfiguration.crypto.DataPurpose purpose, String value) {
        return value == null || value.isBlank() ? null : cipher.encrypt(purpose, value.strip());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** 주문서와 같은 배송지 칸 규칙(400 {@code address.*}) */
    private static void validate(ShippingAddress a) {
        String field = null;
        if (a == null || a.recipient() == null || a.recipient().isBlank() || a.recipient().strip().length() > 50) {
            field = "address.recipient";
        } else if (a.phone() == null || !a.phone().strip().matches("[0-9-]{9,15}")) {
            field = "address.phone";
        } else if (a.postcode() == null || !a.postcode().strip().matches("[0-9]{5,6}")) {
            field = "address.postcode";
        } else if (a.address1() == null || a.address1().isBlank() || a.address1().strip().length() > 200) {
            field = "address.address1";
        } else if ((a.address2() != null && a.address2().strip().length() > 200) || (a.memo() != null && a.memo().strip().length() > 200)) {
            field = "address.address2";
        }
        if (field != null) {
            throw ValidationException.of(field, new MessageRef("groupbuy.order.address.invalid"));
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
