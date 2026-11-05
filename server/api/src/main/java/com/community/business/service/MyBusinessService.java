package com.community.business.service;

import com.community.business.dto.BusinessApplyRequest;
import com.community.business.dto.MyBusinessView;
import com.community.business.entity.BusinessCertificateDelivery;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.NtsValidity;
import com.community.business.exception.BusinessException;
import com.community.business.nts.NtsLookup;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessServiceRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.business.repository.master.BusinessStore.DocumentRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 센터의 내 신원(business-contract) — 조회·신청(반려 뒤 재신청 포함)·수정·사업자등록증 사본 올리기. 클래스 트랜잭션을 두지 않는다
 * (입력 검사·파일 읽기·국세청 조회가 DB 연결을 붙잡지 않게).
 *
 * <p>국세청 진위확인(「국세청 진위확인 · 공공데이터 설정」): 저장 <b>전에</b>(트랜잭션 밖) 번호·개업일·대표자명·상호를 보낸다. 불일치면 400 {@code BUSINESS_NTS_MISMATCH}
 * + {@code fields.companyName·ownerName·openedOn}(신청을 받지 않는다), 조회 불가면 받고 스냅샷 {@code UNAVAILABLE}, 일치면 같은 응답의 상태로 스냅샷을 채운다
 * (상태조회를 따로 부르지 않는다). 스냅샷은 신청·수정과 같은 트랜잭션에 남는다. 수정은 네 칸 중 하나라도 바뀌었을 때만 다시 부른다.
 *
 * <ul>
 *   <li>등록번호 중복: 살아 있는 줄(심사 중·승인·정지) 사이에서 409 {@code BUSINESS_DUPLICATE}(부분 유일 색인이 동시 신청도 막는다).</li>
 *   <li>재신청: 반려 뒤 하루 3회(한국 날짜) — 넘으면 429 {@code BUSINESS_REAPPLY_LIMIT}.</li>
 *   <li>진위확인 하루 한도: 회원별 하루 5회(한국 날짜, 신청·수정 합산, {@link BusinessNtsDailyLimiter}) — 다른 검사 뒤 국세청을 부르기 바로 앞에서 센다. 넘으면 429 {@code BUSINESS_NTS_DAILY_LIMIT}.</li>
 *   <li>수정(F7): 승인·재심사 중만. 상호·번호가 바뀌면 재심사 — 회원·운영자 알림. 민감 칸(번호·연락처)은 null 이면 유지. 개업일이 없던 옛 사업자도 개업일 필수.</li>
 * </ul>
 */
@Service
public class MyBusinessService {

    /** 반려 뒤 하루 재신청 한도 */
    static final int REAPPLY_PER_DAY = 3;
    /** 하루 사본 올리기 한도 */
    static final int UPLOADS_PER_DAY = 20;
    /** 사본 한 장 최대(10MB) */
    static final int FILE_MAX_BYTES = 10 * 1024 * 1024;

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z.\\-]{1,20}");
    private static final Pattern POSTCODE = Pattern.compile("[0-9]{5}");
    /** 개업일 하한 */
    private static final LocalDate OPENED_ON_MIN = LocalDate.of(1900, 1, 1);

    private final BusinessProfileRepository profiles;
    private final BusinessStore store;
    private final BusinessPii pii;
    private final BusinessServiceRows serviceRows;
    private final BusinessServiceRepository services;
    private final BusinessNtsService nts;
    private final BusinessNtsDailyLimiter ntsLimit;
    private final BusinessNotices notices;
    private final SnowflakeIdGenerator ids;
    private final BusinessCertificateReceipt receipt;
    private final BusinessDocSendToProperties sendTo;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MyBusinessService(BusinessProfileRepository profiles, BusinessStore store, BusinessPii pii, BusinessServiceRows serviceRows, BusinessServiceRepository services,
                             BusinessNtsService nts, BusinessNtsDailyLimiter ntsLimit, BusinessNotices notices, SnowflakeIdGenerator ids,
                             BusinessCertificateReceipt receipt, BusinessDocSendToProperties sendTo, PlatformTransactionManager transactions,
                             ObjectProvider<Clock> clocks) {
        this.receipt = receipt;
        this.sendTo = sendTo;
        this.profiles = profiles;
        this.store = store;
        this.pii = pii;
        this.serviceRows = serviceRows;
        this.services = services;
        this.nts = nts;
        this.ntsLimit = ntsLimit;
        this.notices = notices;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 조회 ────────────────────────────────────────────────────────────────

    public MyBusinessView.Profile profile(long userId) {
        return profiles.findByUserId(userId).map(this::view).orElseGet(MyBusinessView.Profile::none);
    }

    // ── 신청·재신청 ─────────────────────────────────────────────────────────

    public MyBusinessView.Profile apply(long userId, BusinessApplyRequest body) {
        Input in = validate(body, true);
        pii.requireConfigured();
        BusinessProfileEntity current = profiles.findByUserId(userId).orElse(null);
        if (current != null && current.getStatus() != BusinessStatus.REJECTED) {
            throw BusinessException.alreadyApplied(); // 국세청을 부르기 전에 — 트랜잭션 안에서 다시 본다
        }
        if (current != null && store.appliedSince(current.getId(), startOfToday()) >= REAPPLY_PER_DAY) {
            throw BusinessException.reapplyLimit();
        }
        Verified verified = verify(userId, in, current); // 트랜잭션 밖 — 불일치면 여기서 400
        long profileId = translate(() -> tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity existing = profiles.findWithLockByUserId(userId).orElse(null);
            if (existing != null && existing.getStatus() != BusinessStatus.REJECTED) {
                throw BusinessException.alreadyApplied();
            }
            if (existing != null && store.appliedSince(existing.getId(), startOfToday()) >= REAPPLY_PER_DAY) {
                throw BusinessException.reapplyLimit();
            }
            long id = existing == null ? ids.nextId() : existing.getId();
            BusinessProfileEntity.Identity identity = identity(in, existing, id, userId);
            if (!identity.bizNoHash().equals(verified.bizNoHash())) {
                throw BusinessException.stateChanged(); // 확인한 번호와 저장할 번호가 다르다(그 사이 다른 요청이 고침)
            }
            if (existing == null) {
                profiles.persist(BusinessProfileEntity.apply(id, userId, identity, now));
            } else {
                existing.reapply(identity, now);
            }
            profiles.flush(); // 아래 JDBC 줄(사본 붙이기·처리 기록)이 신원 줄을 외래키로 가리킨다 — 유일 색인 위반도 여기서 드러난다
            if (identity.certificateId() != null && !store.attachDocument(identity.certificateId(), id, now)) {
                throw BusinessException.fileNotFound();
            }
            serviceRows.sync(id, BusinessServiceStatus.PENDING, null, now);
            store.insertHistory(ids.nextId(), id, BusinessStore.APPLIED, BusinessStore.MEMBER, userId,
                    in.delivery() == BusinessCertificateDelivery.MAIL_FAX ? BusinessCertificateDelivery.MAIL_FAX.name() : null, now);
            nts.record(id, verified.lookup(), null, null);
            notices.member(userId, BusinessNotices.SUBMITTED, identity.companyName(), null);
            notices.operators(id, false);
            return id;
        }));
        return view(profiles.findById(profileId).orElseThrow(BusinessException::notFound));
    }

    // ── 수정(F7) ────────────────────────────────────────────────────────────

    public MyBusinessView.Profile update(long userId, BusinessApplyRequest body) {
        Input in = validate(body, false);
        pii.requireConfigured();
        BusinessProfileEntity current = profiles.findByUserId(userId).orElseThrow(BusinessException::notEditable);
        if (!current.editable()) {
            throw BusinessException.notEditable(); // 국세청을 부르기 전에 — 트랜잭션 안에서 다시 본다
        }
        // 번호·상호·대표자명·개업일 중 하나라도 바뀌면 다시 진위확인(트랜잭션 밖 — 불일치면 여기서 400)
        Verified verified = needsVerification(in, current) ? verify(userId, in, current) : null;
        long profileId = translate(() -> tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = profiles.findWithLockByUserId(userId).orElseThrow(BusinessException::notEditable);
            if (!p.editable()) {
                throw BusinessException.notEditable();
            }
            if (verified == null && needsVerification(in, p)) {
                throw BusinessException.stateChanged(); // 그 사이 다른 요청이 고쳐 확인하지 않은 값이 됐다
            }
            BusinessProfileEntity.Identity identity = identity(in, p, p.getId(), userId);
            if (verified != null && !identity.bizNoHash().equals(verified.bizNoHash())) {
                throw BusinessException.stateChanged();
            }
            boolean numberChanged = !identity.bizNoHash().equals(p.getBizNoHash());
            boolean identityChanged = numberChanged || !identity.companyName().equals(p.getCompanyName());
            if (!store.attachDocument(identity.certificateId(), p.getId(), now)) {
                throw BusinessException.fileNotFound();
            }
            boolean reReviewStarted = p.update(identity, identityChanged, now);
            profiles.flush(); // 살아 있는 같은 번호의 유일 색인 위반을 여기서 드러낸다(409)
            store.insertHistory(ids.nextId(), p.getId(), BusinessStore.UPDATED, BusinessStore.MEMBER, userId, identityChanged ? "IDENTITY" : null, now);
            if (verified != null) {
                nts.record(p.getId(), verified.lookup(), null, null);
            }
            if (reReviewStarted) {
                notices.member(userId, BusinessNotices.RE_REVIEW, identity.companyName(), null);
                notices.operators(p.getId(), true);
            }
            return p.getId();
        }));
        return view(profiles.findById(profileId).orElseThrow(BusinessException::notFound));
    }

    // ── 국세청 진위확인 ─────────────────────────────────────────────────────

    /** 진위확인한 결과 + 확인한 번호의 해시(트랜잭션 안에서 저장할 번호와 같은지 본다) */
    private record Verified(NtsLookup lookup, String bizNoHash) {
    }

    /**
     * 번호(비워 두면 저장된 번호)·개업일·대표자명·상호로 진위확인 — 트랜잭션 밖에서. 불일치면 400 {@code BUSINESS_NTS_MISMATCH}(신청을 받지 않는다),
     * 조회 불가면 그대로 받는다(스냅샷 {@code UNAVAILABLE} — 운영자가 사유를 적어 승인).
     */
    private Verified verify(long userId, Input in, BusinessProfileEntity current) {
        String bizNo;
        if (in.bizNo() != null) {
            bizNo = in.bizNo();
        } else if (current != null && current.getBizNoEnc() != null) {
            bizNo = pii.decrypt(BusinessPii.BIZ_NO, current.getBizNoEnc());
        } else {
            throw ValidationException.of("bizNo", new MessageRef("business.field.required"));
        }
        ntsLimit.acquire(userId); // 하루 한도(신청·수정 합산) — 다른 검사 뒤, 부르기 바로 앞에서 따로 커밋해 센다. 넘으면 429
        NtsLookup result = nts.validate(bizNo, in.openedOn(), in.ownerName(), in.companyName());
        if (result.validity() == NtsValidity.MISMATCH) {
            throw BusinessException.ntsMismatchOnApply();
        }
        return new Verified(result, pii.bizNoHash(bizNo));
    }

    /** 수정 — 번호·상호·대표자명·개업일 중 하나라도 저장된 값과 다른가(번호를 비워 두면 그대로). 개업일이 없던 옛 사업자는 늘 다시 확인한다 */
    private boolean needsVerification(Input in, BusinessProfileEntity p) {
        if (in.bizNo() != null && !pii.bizNoHash(in.bizNo()).equals(p.getBizNoHash())) {
            return true;
        }
        if (!in.companyName().equals(p.getCompanyName()) || !in.openedOn().equals(p.getOpenedOn()) || p.getOwnerNameEnc() == null) {
            return true;
        }
        return !in.ownerName().equals(pii.decrypt(BusinessPii.OWNER, p.getOwnerNameEnc()));
    }

    // ── 사본 올리기(B2) ─────────────────────────────────────────────────────

    /** PDF·JPG·PNG(내용의 첫 바이트로 판정 — 확장자·머리글을 믿지 않는다) 10MB 한 장. 신청·수정이 붙이기 전까지는 회원 것으로만 있다 */
    public MyBusinessView.File upload(long userId, MultipartFile file) {
        byte[] data;
        try {
            data = file == null || file.isEmpty() ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        if (data == null || data.length == 0) {
            throw ValidationException.of("file", new MessageRef("business.file.required"));
        }
        if (data.length > FILE_MAX_BYTES) {
            throw ValidationException.of("file", new MessageRef("business.file.tooLarge"));
        }
        String type = sniff(data);
        if (type == null) {
            throw ValidationException.of("file", new MessageRef("business.file.type"));
        }
        pii.requireConfigured();
        String name = fileName(file.getOriginalFilename(), type);
        byte[] encrypted = pii.encryptFile(data);
        String sha = sha256(data);
        int size = data.length;
        long id = tx.execute(status -> {
            Instant now = now();
            if (store.uploadsSince(userId, startOfToday()) >= UPLOADS_PER_DAY) {
                throw BusinessException.uploadLimit();
            }
            long fileId = ids.nextId();
            store.insertDocument(fileId, userId, name, type, size, encrypted, sha, now);
            return fileId;
        });
        return new MyBusinessView.File(Long.toString(id), name, size, type, null);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 정규화한 입력(숫자만 남긴 번호·연락처 — 바꾸지 않으면 null). 등록증을 우편·팩스로 보내면 {@code certificateId} 는 null */
    private record Input(String companyName, String bizNo, String ownerName, String phone, String email, String postcode, String address1, String address2,
                         Long certificateId, String termsVersion, String privacyVersion, LocalDate openedOn, BusinessCertificateDelivery delivery) {
    }

    /**
     * @param mailAllowed 신청·재신청이면 true — 「우편·팩스로 보냄」({@code MAIL_FAX})을 받는다(보낼 곳 설정이 비었으면 400 {@code fields.certificateDelivery}). 수정은 파일만(400)
     */
    private Input validate(BusinessApplyRequest b, boolean mailAllowed) {
        if (b == null) {
            throw ValidationException.of("companyName", new MessageRef("business.field.required"));
        }
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        BusinessCertificateDelivery delivery = BusinessCertificateDelivery.UPLOAD;
        String deliveryText = strip(b.certificateDelivery());
        if (deliveryText != null) {
            try {
                delivery = BusinessCertificateDelivery.valueOf(deliveryText);
            } catch (IllegalArgumentException e) {
                errors.put("certificateDelivery", new MessageRef("business.value.invalid"));
            }
        }
        if (delivery == BusinessCertificateDelivery.MAIL_FAX && !mailAllowed) {
            errors.put("certificateDelivery", new MessageRef("business.certificateDelivery.uploadOnly"));
        } else if (delivery == BusinessCertificateDelivery.MAIL_FAX && !sendTo.available()) {
            errors.put("certificateDelivery", new MessageRef("business.docSendTo.missing"));
        }
        String company = strip(b.companyName());
        if (company == null || company.length() > 60) {
            errors.put("companyName", new MessageRef(company == null ? "business.field.required" : "business.field.tooLong", 60));
        }
        String bizNo = BusinessFields.digits(strip(b.bizNo()));
        if (bizNo != null && bizNo.isEmpty()) {
            bizNo = null;
        }
        if (bizNo != null && !BusinessFields.validBizNo(bizNo)) {
            errors.put("bizNo", new MessageRef("business.bizNo.invalid"));
        }
        String owner = strip(b.ownerName());
        if (owner == null || owner.length() > 30) {
            errors.put("ownerName", new MessageRef(owner == null ? "business.field.required" : "business.field.tooLong", 30));
        }
        LocalDate openedOn = openedOn(strip(b.openedOn()), errors);
        String phone = BusinessFields.digits(strip(b.phone()));
        if (phone != null && phone.isEmpty()) {
            phone = null;
        }
        if (phone != null && (phone.length() < 9 || phone.length() > 11)) {
            errors.put("phone", new MessageRef("business.phone.invalid"));
        }
        String email = BusinessFields.normalizeEmail(strip(b.email()));
        if (email == null || email.length() > 120 || !EMAIL.matcher(email).matches()) {
            errors.put("email", new MessageRef(email == null ? "business.field.required" : "business.email.invalid"));
        }
        MyBusinessView.Address a = b.address();
        String postcode = a == null ? null : strip(a.postcode());
        String address1 = a == null ? null : strip(a.address1());
        String address2 = a == null ? null : strip(a.address2());
        if (postcode == null || !POSTCODE.matcher(postcode).matches() || address1 == null || address1.length() > 200
                || (address2 != null && address2.length() > 200)) {
            errors.put("address1", new MessageRef("business.address.invalid"));
        }
        Long certificateId = null;
        if (delivery != BusinessCertificateDelivery.MAIL_FAX) {
            try {
                certificateId = Long.parseLong(strip(b.certificateFileId()) == null ? "" : b.certificateFileId().strip());
            } catch (NumberFormatException e) {
                errors.put("certificate", new MessageRef("business.certificate.required"));
            }
        }
        String terms = strip(b.agreedTermsVersion());
        String privacy = strip(b.agreedPrivacyVersion());
        if (terms == null || !VERSION.matcher(terms).matches() || privacy == null || !VERSION.matcher(privacy).matches()) {
            errors.put("agree", new MessageRef("business.agree.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Input(company, bizNo, owner, phone, email, postcode, address1, address2, certificateId, terms, privacy, openedOn, delivery);
    }

    /** 입력 + 지금 값(바꾸지 않은 민감 칸) → 암호문·해시까지 만든 신원. 사본은 회원 것이고 파기되지 않았으며 다른 신원에 붙지 않았어야 한다 */
    private BusinessProfileEntity.Identity identity(Input in, BusinessProfileEntity current, long profileId, long userId) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String bizNoEnc;
        String bizNoHash;
        String last4;
        if (in.bizNo() != null) {
            bizNoEnc = pii.encrypt(BusinessPii.BIZ_NO, in.bizNo());
            bizNoHash = pii.bizNoHash(in.bizNo());
            last4 = BusinessFields.last4(in.bizNo());
        } else if (current != null && current.getBizNoEnc() != null) {
            bizNoEnc = current.getBizNoEnc();
            bizNoHash = current.getBizNoHash();
            last4 = current.getBizNoLast4();
        } else {
            errors.put("bizNo", new MessageRef("business.field.required"));
            bizNoEnc = bizNoHash = last4 = null;
        }
        String phoneEnc;
        if (in.phone() != null) {
            phoneEnc = pii.encrypt(BusinessPii.PHONE, in.phone());
        } else if (current != null && current.getPhoneEnc() != null) {
            phoneEnc = current.getPhoneEnc();
        } else {
            errors.put("phone", new MessageRef("business.field.required"));
            phoneEnc = null;
        }
        if (in.certificateId() != null) {
            DocumentRow doc = store.document(in.certificateId()).orElse(null);
            if (doc == null || doc.userId() != userId || doc.erasedAt() != null || (doc.profileId() != null && doc.profileId() != profileId)) {
                errors.put("certificate", new MessageRef("business.certificate.required"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (profiles.existsByBizNoHashAndStatusInAndIdNot(bizNoHash, EnumSet.of(BusinessStatus.PENDING, BusinessStatus.APPROVED, BusinessStatus.SUSPENDED), profileId)) {
            throw BusinessException.duplicate();
        }
        return new BusinessProfileEntity.Identity(in.companyName(), bizNoEnc, bizNoHash, last4, pii.encrypt(BusinessPii.OWNER, in.ownerName()), phoneEnc,
                pii.encrypt(BusinessPii.EMAIL, in.email()), in.postcode(), pii.encrypt(BusinessPii.ADDRESS1, in.address1()),
                pii.encrypt(BusinessPii.ADDRESS2, in.address2()), in.certificateId(), in.termsVersion(), in.privacyVersion(), in.openedOn(), in.delivery());
    }

    private MyBusinessView.Profile view(BusinessProfileEntity p) {
        String status = BusinessEligibility.memberStatus(p);
        if ("NONE".equals(status)) {
            return MyBusinessView.Profile.none();
        }
        BusinessPii.Plain plain = pii.plain(p);
        MyBusinessView.Certificate cert = p.getCertificateId() == null ? null : store.document(p.getCertificateId()).filter(d -> d.erasedAt() == null)
                .map(d -> new MyBusinessView.Certificate(Long.toString(d.id()), d.fileName(), d.sizeBytes())).orElse(null);
        return new MyBusinessView.Profile(status, p.getCompanyName(), BusinessFields.maskBizNo(p.getBizNoLast4()), plain == null ? null : plain.ownerName(), p.getOpenedOn(),
                plain == null ? null : BusinessFields.maskPhone(plain.phone()), plain == null ? null : plain.email(),
                plain == null ? null : new MyBusinessView.Address(plain.postcode(), plain.address1(), plain.address2() == null ? "" : plain.address2()), cert,
                p.getSubmittedAt(), p.getReviewedAt(), p.getStatus() == BusinessStatus.REJECTED && p.getRejectCode() != null ? p.getRejectCode().name() : null,
                p.getStatus() == BusinessStatus.REJECTED ? p.getRejectText() : null, p.getStatus() == BusinessStatus.SUSPENDED ? p.getSuspendText() : null,
                p.isReReview(), services.findByProfileId(p.getId()).stream()
                .sorted(java.util.Comparator.comparing(com.community.business.entity.BusinessServiceEntity::getService))
                .map(s -> new MyBusinessView.Service(s.getService().name(), s.getStatus().name(), s.getSuspendCode() == null ? null : s.getSuspendCode().name(),
                        s.getSuspendText(), s.getSuspendedAt()))
                .toList(), p.getCertificateDelivery().name(), receipt.received(p));
    }

    // ── 등록증 보낼 곳(biz-doc-intake-contract) ─────────────────────────────

    /** 우편·팩스로 보낼 곳 — 서버 설정값(비어 있는 칸은 null) */
    public MyBusinessView.DocSendTo docSendTo() {
        return new MyBusinessView.DocSendTo(strip(sendTo.postcode()), strip(sendTo.address()), strip(sendTo.recipient()), strip(sendTo.fax()));
    }

    /** 동시 신청으로 유일 색인(같은 회원 · 살아 있는 같은 번호)에 걸리면 409 */
    private static long translate(java.util.function.Supplier<Long> work) {
        try {
            Long id = work.get();
            return id == null ? 0 : id;
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.duplicate();
        }
    }

    /** 첫 바이트로 종류 판정 — PDF {@code %PDF-} · JPEG {@code FF D8 FF} · PNG {@code 89 50 4E 47 0D 0A 1A 0A} */
    static String sniff(byte[] d) {
        if (d.length >= 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-') {
            return "application/pdf";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G' && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    /** 표시용 파일 이름 — 경로·제어 문자를 빼고 120자, 없으면 종류별 기본 이름 */
    static String fileName(String original, String type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            name = "certificate" + (type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg");
        }
        return name.length() <= 120 ? name : name.substring(name.length() - 120);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 개업일 {@code YYYY-MM-DD} — 필수, 1900-01-01 ~ 오늘(한국 날짜). 틀리면 {@code fields.openedOn} 에 문구를 넣고 null */
    private LocalDate openedOn(String text, Map<String, MessageRef> errors) {
        if (text == null) {
            errors.put("openedOn", new MessageRef("business.field.required"));
            return null;
        }
        LocalDate date;
        try {
            date = text.length() == 10 ? LocalDate.parse(text) : null;
        } catch (DateTimeParseException e) {
            date = null;
        }
        if (date == null || date.isBefore(OPENED_ON_MIN)) {
            errors.put("openedOn", new MessageRef("business.openedOn.invalid"));
            return null;
        }
        if (date.isAfter(ServiceTime.today(clock))) {
            errors.put("openedOn", new MessageRef("business.openedOn.future"));
            return null;
        }
        return date;
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        return s.isEmpty() ? null : s;
    }

    private Instant startOfToday() {
        return ServiceTime.today(clock).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
