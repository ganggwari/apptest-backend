package com.mbc.puf.member.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.car.entity.Car;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.query.MatchScore;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.util.LikePattern;
import com.mbc.puf.common.util.SearchKeyword;
import com.mbc.puf.member.dto.CustomerListItem;
import com.mbc.puf.member.entity.Customer;
import com.mbc.puf.member.repository.CustomerRepository;

import jakarta.persistence.EntityManager;

@Service
public class CustomerSearchServiceImpl implements CustomerSearchService {

    private final CustomerRepository customerRepository;
    private final CarRepository carRepository;
    private final EntityManager em;

    public CustomerSearchServiceImpl(CustomerRepository customerRepository, CarRepository carRepository,
                                     EntityManager em) {
        this.customerRepository = customerRepository;
        this.carRepository = carRepository;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerListItem> search(String keyword, String loginId, String name, String email,
                                                 String phone, String carNum, boolean includeWithdrawn,
                                                 int page, int size) {
        PageParams pageParams = PageParams.of(page, size);
        QueryConditions c = new QueryConditions();

        // 기본은 탈퇴 회원 제외, includeWithdrawn=true 면 포함
        c.addIf(!includeWithdrawn, "c.withdrawn = false");

        // 항목별 조건: 비어 있으면 빠지고, 걸면 서로 AND 로 좁혀진다
        c.containsIfPresent(loginId, "c.loginId")
         .containsIfPresent(name, "c.name")
         .containsIfPresent(email, "c.email");
        LikePattern phonePattern = LikePattern.ofPhone(phone);
        if (phonePattern != null) {
            c.add("replace(c.phone, '-', '') like ? escape '\\'", phonePattern.getContains());
        }
        LikePattern carPattern = LikePattern.ofCar(carNum);
        if (carPattern != null) {
            c.add("exists (select 1 from Car k where k.customerId = c.id"
                    + " and k.deleted = false and lower(replace(k.carNum, ' ', '')) like ? escape '\\')", carPattern.getContains().toLowerCase());
        }

        // 통합 검색어: 아이디·이름·이메일·전화번호·차량번호 일치 점수(정확 3/앞부분 2/포함 1) 합산, 점수 높은 순
        SearchKeyword searchKeyword = SearchKeyword.of(keyword);
        String orderBy = " order by c.createdAt desc, c.id desc";
        if (searchKeyword != null) {
            MatchScore score = new MatchScore(c)
                    .text(searchKeyword.getText(), "c.loginId")
                    .text(searchKeyword.getText(), "c.name")
                    .text(searchKeyword.getText(), "c.email")
                    .phone(searchKeyword.getPhone(), "c.phone");
            // 차량번호는 그 고객의 차량 중 가장 잘 맞는 하나의 점수를 쓴다
            LikePattern car = searchKeyword.getCar();
            score.raw("coalesce((select max(" + score.caseExpr("lower(replace(k.carNum, ' ', ''))",
                    car.getExact().toLowerCase(), car.getPrefix().toLowerCase(), car.getContains().toLowerCase())
                    + ") from Car k where k.customerId = c.id and k.deleted = false), 0)");
            c.add(score.expression() + " > 0");
            orderBy = " order by " + score.expression() + " desc, c.createdAt desc, c.id desc";
        }

        PageResponse<Customer> customers = PagedQuery.fetch(em, "select c from Customer c",
                "select count(c) from Customer c", c, orderBy, Customer.class, x -> x, pageParams);

        // 이번 페이지 고객들의 차량번호를 한 번에 가져와서 붙인다 (등록순)
        List<Long> ids = customers.getItems().stream().map(Customer::getId).toList();
        Map<Long, List<String>> carNums = ids.isEmpty() ? Map.of()
                : carRepository.findByCustomerIdInAndDeletedFalseOrderByIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(Car::getCustomerId,
                                Collectors.mapping(Car::getCarNum, Collectors.toList())));

        List<CustomerListItem> items = customers.getItems().stream()
                .map(x -> new CustomerListItem(x.getId(), x.getLoginId(), x.getName(), x.getEmail(), x.getPhone(),
                        carNums.getOrDefault(x.getId(), List.of()), x.getCreatedAt(), x.isWithdrawn()))
                .toList();
        return new PageResponse<>(items, customers.getPage(), customers.getSize(), customers.getTotalItems());
    }
}