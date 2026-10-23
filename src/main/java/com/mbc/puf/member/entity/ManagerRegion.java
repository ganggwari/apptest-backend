package com.mbc.puf.member.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 관리자 담당 지역 (테이블: manager_region). 관리자 1명이 여러 지역을 담당할 수 있다. PK = (manager_id, region_id) */
@Entity
@Table(name = "manager_region")
@IdClass(ManagerRegion.Key.class)
public class ManagerRegion {

    @Id
    @Column(name = "manager_id")
    private Long managerId;

    @Id
    @Column(name = "region_id")
    private Long regionId;

    protected ManagerRegion() {
    }

    public ManagerRegion(Long managerId, Long regionId) {
        this.managerId = managerId;
        this.regionId = regionId;
    }

    public Long getManagerId() {
        return managerId;
    }

    public Long getRegionId() {
        return regionId;
    }

    /** 복합키 클래스 (필드 이름·타입이 엔티티의 @Id 필드와 같아야 한다) */
    public static class Key implements Serializable {
        private Long managerId;
        private Long regionId;

        public Key() {
        }

        public Key(Long managerId, Long regionId) {
            this.managerId = managerId;
            this.regionId = regionId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(managerId, k.managerId) && Objects.equals(regionId, k.regionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(managerId, regionId);
        }
    }
}
