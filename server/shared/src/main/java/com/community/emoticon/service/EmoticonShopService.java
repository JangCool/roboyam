package com.community.emoticon.service;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.CatalogRow;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonReadStore.ShopQuery;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 공개 읽기 — 카탈로그({@code GET /api/emoticons/catalog}) · 상점 목록 · 팩 상세. 로그인하지 않아도 읽는다(로그인했으면 {@code acquired}).
 *
 * <ul>
 *   <li><b>카탈로그</b>: 상점에 나왔던 모든 팩(판매 중 · <b>판매 중단 포함</b> — 이미 올라간 본문을 그려야 한다)의 상점 판 로보티콘, 팩 ID → 차례. ETag = 내용의
 *       SHA-256 앞 16바이트(hex — 앱 서버가 둘이어도 같은 내용이면 같은 값, 서버 메모리에 따로 담아 두지 않는다). 판이 바뀌어 그림·이름표·차례가 달라지면 ETag 도 달라진다.</li>
 *   <li><b>상점 목록</b>: 판매 중(APPROVED)만. 거르기 {@code source}·{@code price}(FREE·PAID), 정렬 {@code POPULAR}(받은 수)·{@code LATEST}(처음 나온 시각), 찾기 {@code q}
 *       (이름·태그·작가 닉네임), 쪽 {@code cursor}(키셋). 모르는 거르기·정렬 값은 화면 목업처럼 「전체」·「인기」로 읽는다.</li>
 *   <li><b>팩 상세</b>: 판매 중 팩, 그리고 판매 중단 팩은 가진 회원·작가에게만(목업 가정 — 내 로보티콘에서 들어온다). 그 밖·형식이 틀린 ID 는 {@code 404 EMOTICON_PACK_NOT_FOUND}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class EmoticonShopService {

    /** 상점 한 쪽 */
    public static final int PAGE_SIZE = 24;
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final EmoticonPackStore packs;
    private final EmoticonReadStore reads;
    private final EmoticonAcquisitionStore acquisitions;
    private final EmoticonContent content;
    private final OperatorMemberAccountService operatorAccounts;

    public EmoticonShopService(EmoticonPackStore packs, EmoticonReadStore reads, EmoticonAcquisitionStore acquisitions, EmoticonContent content,
                               OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
        this.packs = packs;
        this.reads = reads;
        this.acquisitions = acquisitions;
        this.content = content;
    }

    /** 카탈로그와 그 지문(ETag 값 — 따옴표 없이) */
    public record CatalogResult(EmoticonPublicView.Catalog catalog, String etag) {
    }

    public CatalogResult catalog() {
        List<EmoticonPublicView.CatalogItem> items = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        for (CatalogRow row : reads.catalog()) {
            String url = content.url(row.storageKey(), row.staticPath());
            boolean animated = EmoticonContent.animated(row.format(), row.frames());
            items.add(new EmoticonPublicView.CatalogItem(Long.toString(row.emoticonId()), Long.toString(row.packId()), row.label(), url, animated));
            canonical.append(row.emoticonId()).append('\u001f').append(row.packId()).append('\u001f').append(row.label()).append('\u001f').append(url).append('\u001f')
                    .append(animated).append('\n');
        }
        return new CatalogResult(new EmoticonPublicView.Catalog(items), fingerprint(canonical.toString()));
    }

    /**
     * 상점 목록 한 쪽.
     *
     * @param userId 로그인한 회원(없으면 null — {@code acquired} 가 모두 거짓)
     * @throws ValidationException {@code fields.cursor} — 읽을 수 없는 쪽 기준
     */
    public EmoticonPublicView.PackList packs(Long userId, String sourceText, String priceText, String sortText, String qText, String cursor) {
        EmoticonPackSource source = "OFFICIAL".equals(sourceText) ? EmoticonPackSource.OFFICIAL : "CREATOR".equals(sourceText) ? EmoticonPackSource.CREATOR : null;
        Boolean free = "FREE".equals(priceText) ? Boolean.TRUE : "PAID".equals(priceText) ? Boolean.FALSE : null;
        boolean latest = "LATEST".equals(sortText);
        String q = qText == null || qText.isBlank() ? null : qText.trim();
        if (q != null && q.length() > 50) {
            q = q.substring(0, 50);
        }
        long[] after = cursor == null || cursor.isBlank() ? null : decode(cursor);
        List<Long> ids = reads.shopPage(new ShopQuery(source, free, latest, q, after == null ? null : after[0], after == null ? null : after[1], PAGE_SIZE + 1));
        boolean more = ids.size() > PAGE_SIZE;
        List<Long> page = more ? ids.subList(0, PAGE_SIZE) : ids;
        List<EmoticonPublicView.PackCard> cards = cards(userId, page);
        String next = null;
        if (more) {
            long last = page.get(page.size() - 1);
            next = encode(reads.sortValue(last, latest), last);
        }
        return new EmoticonPublicView.PackList(cards, next);
    }

    /** 팩 상세 */
    public EmoticonPublicView.PackDetail pack(Long userId, String idText) {
        long id = parse(idText);
        PackRow p = id < 0 ? null : packs.find(id).orElse(null);
        if (p == null || !p.status().published()) {
            throw new EmoticonPackNotFoundException();
        }
        boolean owned = userId != null && acquisitions.ownedAmong(userId, List.of(p.id())).contains(p.id());
        if (p.status() == EmoticonPackStatus.SUSPENDED && !owned && !(userId != null && userId.equals(p.creatorId()))) {
            throw new EmoticonPackNotFoundException();
        }
        // 운영자 회원 계정 — 판매 중 팩은 가지지 않아도 쓴다(가진 것으로 치지 않아 acquired 와 따로, 운영자 회원 계정 연결 2026-11-05)
        boolean operatorUsable = userId != null && p.status() == EmoticonPackStatus.APPROVED && operatorAccounts.isActiveOperatorAccount(userId);
        EmoticonContent.Loaded live = content.load(p.liveRevisionId());
        EmoticonPublicView.Creator creator = creatorOf(p, p.creatorId() == null ? Map.of() : reads.members(List.of(p.creatorId())));
        return new EmoticonPublicView.PackDetail(Long.toString(p.id()), live.revision().name(), p.source().name(), creator, content.coverUrl(live), live.items().size(),
                p.price(), p.acquireCount(), owned, p.publishedAt(), live.revision().description(), live.revision().tags(),
                p.status() == EmoticonPackStatus.SUSPENDED ? "SUSPENDED" : "ON_SALE", content.publicItems(live), operatorUsable);
    }

    /** 팩 카드들(입력 차례) */
    List<EmoticonPublicView.PackCard> cards(Long userId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PackRow> byId = packs.byIds(ids).stream().collect(Collectors.toMap(PackRow::id, p -> p));
        Map<Long, EmoticonContent.Loaded> lives = content.load(byId.values().stream().map(PackRow::liveRevisionId).toList());
        Set<Long> creators = new HashSet<>();
        byId.values().forEach(p -> {
            if (p.creatorId() != null) {
                creators.add(p.creatorId());
            }
        });
        Map<Long, MemberRef> members = reads.members(creators);
        Set<Long> owned = userId == null ? Set.of() : acquisitions.ownedAmong(userId, ids);
        List<EmoticonPublicView.PackCard> out = new ArrayList<>();
        for (Long id : ids) {
            PackRow p = byId.get(id);
            EmoticonContent.Loaded live = p == null ? null : lives.get(p.liveRevisionId());
            if (live == null) {
                continue;
            }
            out.add(new EmoticonPublicView.PackCard(Long.toString(p.id()), live.revision().name(), p.source().name(), creatorOf(p, members), content.coverUrl(live),
                    live.items().size(), p.price(), p.acquireCount(), owned.contains(p.id()), p.publishedAt()));
        }
        return out;
    }

    static EmoticonPublicView.Creator creatorOf(PackRow p, Map<Long, MemberRef> members) {
        if (p.creatorId() == null) {
            return null;
        }
        MemberRef m = members.get(p.creatorId());
        return new EmoticonPublicView.Creator(Long.toString(p.creatorId()), m == null ? null : m.nickname());
    }

    /** 문자열 ID — 형식이 틀리면 -1 */
    public static long parse(String text) {
        if (text == null || !DIGITS.matcher(text).matches()) {
            return -1;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String encode(long value, long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value + ":" + id).getBytes(StandardCharsets.US_ASCII));
    }

    private static long[] decode(String cursor) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            String[] parts = text.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (IllegalArgumentException e) {
            throw ValidationException.of("cursor", new MessageRef("emoticon.cursor.invalid"));
        }
    }

    static String fingerprint(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
