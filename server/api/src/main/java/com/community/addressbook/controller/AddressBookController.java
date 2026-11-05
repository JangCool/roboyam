package com.community.addressbook.controller;

import com.community.addressbook.dto.request.AddressBookRequest;
import com.community.addressbook.dto.response.AddressBookView;
import com.community.addressbook.exception.AddressNotFoundException;
import com.community.addressbook.service.AddressBookService;
import com.community.auth.dto.AuthUser;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 배송지 목록(주소록 — user-web {@code useAddressBookApi}, docs/features/address-book-contract.md). 로그인 필요(401, {@code SecurityConfig} 기본 규칙),
 * 회원 본인 것만 — 남의·없는 배송지는 404 {@code ADDRESS_NOT_FOUND}. 값은 개인정보라 응답은 {@code Cache-Control: no-store}. 키가 없으면 503 {@code DATA_KEY_MISSING}.
 */
@RestController
@RequestMapping("/api/me/addresses")
public class AddressBookController {

    private final AddressBookService service;

    public AddressBookController(AddressBookService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AddressBookView.ListPage>> list(Authentication authentication) {
        AddressBookView.ListPage page = new AddressBookView.ListPage(service.list(user(authentication)).stream().map(AddressBookView.Item::of).toList(),
                AddressBookService.MAX);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(page));
    }

    /** 201 — 400 {@code VALIDATION}, 409 {@code ADDRESS_BOOK_FULL} */
    @PostMapping
    public ResponseEntity<ApiResponse<AddressBookView.Item>> create(@RequestBody(required = false) AddressBookRequest body, Authentication authentication) {
        AddressBookView.Item item = AddressBookView.Item.of(service.create(user(authentication), orEmpty(body).toInput()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressBookView.Item>> update(@PathVariable String id, @RequestBody(required = false) AddressBookRequest body,
                                                                    Authentication authentication) {
        AddressBookView.Item item = AddressBookView.Item.of(service.update(user(authentication), id(id), orEmpty(body).toInput()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
        service.delete(user(authentication), id(id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<Void> makeDefault(@PathVariable String id, Authentication authentication) {
        service.makeDefault(user(authentication), id(id));
        return ResponseEntity.noContent().build();
    }

    private static AddressBookRequest orEmpty(AddressBookRequest body) {
        return body == null ? new AddressBookRequest(null, null, null, null, null, null, null, null) : body;
    }

    /** 경로의 글자 ID — 숫자가 아니면 없는 배송지(404) */
    private static long id(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new AddressNotFoundException();
        }
    }

    private static long user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
