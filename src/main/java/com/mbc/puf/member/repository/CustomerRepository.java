package com.mbc.puf.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.member.entity.Customer;

/**
 * 고객. 아이디·전화번호·이메일 중복은 '탈퇴하지 않은 계정끼리만' 막는다(DB 부분 유니크 인덱스와 같은 조건).
 * 그래서 탈퇴한 사람의 아이디·번호로 새로 가입할 수 있고, 로그인도 탈퇴하지 않은 계정에서만 찾는다.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByLoginIdAndWithdrawnFalse(String loginId);

    boolean existsByLoginIdAndWithdrawnFalse(String loginId);

    boolean existsByPhoneAndWithdrawnFalse(String phone);

    boolean existsByEmailAndWithdrawnFalse(String email);

    // 내 정보 수정 시: 나 말고 같은 번호/이메일을 쓰는 활성 계정이 있는지
    boolean existsByPhoneAndWithdrawnFalseAndIdNot(String phone, Long id);

    boolean existsByEmailAndWithdrawnFalseAndIdNot(String email, Long id);
}
