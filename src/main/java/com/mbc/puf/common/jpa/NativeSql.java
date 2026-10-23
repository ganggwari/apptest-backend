package com.mbc.puf.common.jpa;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.util.NumberUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

/**
 * 네이티브 SQL 조립 + 결과를 DTO에 채워주는 헬퍼 (대시보드 통계처럼 JPQL로 표현이 안 되는 집계용).
 *
 *  - SQL 조립: 값이 있을 때만 조건을 붙인다 (MyBatis 의 &lt;if&gt; 역할)
 *      NativeSql sql = new NativeSql("SELECT ... FROM underpass u WHERE u.is_deleted = FALSE")
 *              .andIf(regionId != null, " AND u.region_id = :regionId", "regionId", regionId);
 *  - 결과 매핑: 컬럼 별칭(snake_case)을 DTO 프로퍼티(camelCase)에 맞춰 setter 로 채운다
 *      (MyBatis map-underscore-to-camel-case 와 같은 규칙, 대소문자·밑줄 무시하고 비교)
 *      DB enum 라벨('위험' 등)은 DbEnums 로, 숫자·날짜 타입은 DTO 필드 타입에 맞춰 변환한다.
 */
public class NativeSql {

    private final StringBuilder sql;
    private final Map<String, Object> params = new LinkedHashMap<>();

    public NativeSql(String base) {
        this.sql = new StringBuilder(base);
    }

    public NativeSql append(String text) {
        sql.append(text);
        return this;
    }

    // 조건이 true 일 때만 clause 를 붙이고 파라미터도 등록한다
    public NativeSql andIf(boolean condition, String clause, String name, Object value) {
        if (condition) {
            sql.append(clause);
            params.put(name, value);
        }
        return this;
    }

    public NativeSql param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public String text() {
        return sql.toString();
    }

    // 결과 여러 행 → DTO 목록
    public <T> List<T> list(EntityManager em, Class<T> type) {
        Query query = em.createNativeQuery(sql.toString(), Tuple.class);
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return rows.stream().map(row -> toBean(row, type)).toList();
    }

    // 결과 한 행 → DTO (집계 쿼리처럼 항상 한 행이 나오는 경우)
    public <T> T single(EntityManager em, Class<T> type) {
        List<T> rows = list(em, type);
        return rows.isEmpty() ? BeanUtils.instantiateClass(type) : rows.get(0);
    }

    // 결과 여러 행 → Object[] 목록 (DTO 가 생성자 방식이라 직접 만들어야 할 때)
    @SuppressWarnings("unchecked")
    public List<Object[]> rows(EntityManager em) {
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    // ---- 매핑 ----

    private static final Map<Class<?>, Map<String, PropertyDescriptor>> PROPS = new HashMap<>();

    private static <T> T toBean(Tuple row, Class<T> type) {
        T bean = BeanUtils.instantiateClass(type);
        BeanWrapper wrapper = new BeanWrapperImpl(bean);
        Map<String, PropertyDescriptor> props = propertiesOf(type);
        for (TupleElement<?> element : row.getElements()) {
            PropertyDescriptor pd = props.get(normalize(element.getAlias()));
            if (pd == null || pd.getWriteMethod() == null) {
                continue;   // DTO 에 없는 컬럼은 무시
            }
            Object value = convert(row.get(element), pd.getPropertyType());
            if (value == null && pd.getPropertyType().isPrimitive()) {
                continue;   // long 같은 기본형에 null 은 못 넣으므로 기본값(0) 유지
            }
            wrapper.setPropertyValue(pd.getName(), value);
        }
        return bean;
    }

    private static synchronized Map<String, PropertyDescriptor> propertiesOf(Class<?> type) {
        return PROPS.computeIfAbsent(type, t -> {
            Map<String, PropertyDescriptor> map = new HashMap<>();
            for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(t)) {
                map.put(normalize(pd.getName()), pd);
            }
            return map;
        });
    }

    // "avg_risk_score" / "avgRiskScore" / "AVGRISKSCORE" → "avgriskscore"
    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object convert(Object value, Class<?> target) {
        if (value == null) {
            return null;
        }
        Class<?> boxed = target.isPrimitive() ? primitiveWrapper(target) : target;
        if (boxed.isInstance(value)) {
            return value;
        }
        if (Enum.class.isAssignableFrom(boxed)) {
            return DbEnums.fromDbValue((Class) boxed, value.toString());
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(boxed)) {
            return NumberUtils.convertNumberToTargetClass(number, (Class<? extends Number>) boxed);
        }
        if (boxed == LocalDateTime.class) {
            if (value instanceof Timestamp ts) return ts.toLocalDateTime();
            if (value instanceof OffsetDateTime odt) return odt.toLocalDateTime();
        }
        if (boxed == LocalDate.class) {
            if (value instanceof java.sql.Date d) return d.toLocalDate();
            if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
            if (value instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        }
        if (boxed == BigDecimal.class && value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return value;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        if (primitive == long.class) return Long.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == double.class) return Double.class;
        return primitive;
    }
}
