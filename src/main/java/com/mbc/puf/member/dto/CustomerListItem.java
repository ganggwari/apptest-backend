package com.mbc.puf.member.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 고객 목록의 한 항목 (관리자 화면용). 생년월일·주소는 목록에서 필요 없어서 담지 않는다. */
public class CustomerListItem {

    private final Long id;
    private final String loginId;
    private final String name;
    private final String email;
    private final String phone;
    private final List<String> carNums;     // 등록한 차량번호 (최대 3대, 없으면 빈 목록)
    private final LocalDateTime createdAt;
    private final boolean withdrawn;

    public CustomerListItem(Long id, String loginId, String name, String email, String phone,
                            List<String> carNums, LocalDateTime createdAt, boolean withdrawn) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.carNums = carNums;
        this.createdAt = createdAt;
        this.withdrawn = withdrawn;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public List<String> getCarNums() { return carNums; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isWithdrawn() { return withdrawn; }
}
