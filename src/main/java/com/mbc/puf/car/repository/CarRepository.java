package com.mbc.puf.car.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.car.entity.Car;

/** 차량. 삭제는 전부 소프트 삭제(is_deleted)라 조회 메서드는 삭제된 차량을 항상 뺀다 */
public interface CarRepository extends JpaRepository<Car, Long> {

    // 그 고객이 지금 등록해둔 차량 수 (최대 3대 제한 체크용)
    long countByCustomerIdAndDeletedFalse(Long customerId);

    // 같은 고객이 같은 번호를 이미 등록했는지 (DB 부분 유니크 uq_car_customer_num_active 와 같은 조건)
    boolean existsByCustomerIdAndCarNumAndDeletedFalse(Long customerId, String carNum);

    // 그 고객이 등록한 차량 목록 (등록일순)
    List<Car> findByCustomerIdAndDeletedFalseOrderByCreatedAtAsc(Long customerId);

    // 여러 고객의 차량을 한 번에 (관리자 고객 목록에서 차량번호 붙일 때)
    List<Car> findByCustomerIdInAndDeletedFalseOrderByIdAsc(Collection<Long> customerIds);

    // 본인 소유 차량 단건
    Optional<Car> findByIdAndCustomerIdAndDeletedFalse(Long id, Long customerId);

    // 번호판 인식 결과와 매칭: 같은 번호를 등록한 차량 전부(가족 공유 차량은 여러 고객).
    // 삭제한 차량과 탈퇴한 고객의 차량은 빠진다
    @Query("select c from Car c join Customer cu on cu.id = c.customerId"
            + " where c.carNum = :carNum and c.deleted = false and cu.withdrawn = false order by c.id")
    List<Car> findActiveByCarNum(@Param("carNum") String carNum);

    // 고객 탈퇴: 그 고객 차량 전부 소프트 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Car c set c.deleted = true where c.customerId = :customerId and c.deleted = false")
    int softDeleteAllByCustomerId(@Param("customerId") Long customerId);
}
