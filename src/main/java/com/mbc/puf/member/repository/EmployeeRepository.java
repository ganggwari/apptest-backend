package com.mbc.puf.member.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.member.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // 이름 + 생년월일 + 전화번호로 직원 찾기. DB의 전화번호에 하이픈/공백이 섞여 있어도 숫자만 비교한다
    @Query("select e from Employee e where e.name = :name and e.birthDate = :birthDate"
            + " and replace(replace(e.phone, '-', ''), ' ', '') = :phone order by e.id")
    List<Employee> findByIdentity(@Param("name") String name, @Param("birthDate") LocalDate birthDate,
                                  @Param("phone") String phone);
}
