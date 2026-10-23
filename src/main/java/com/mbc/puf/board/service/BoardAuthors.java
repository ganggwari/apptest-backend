package com.mbc.puf.board.service;

import jakarta.persistence.EntityManager;

/** 게시판 공용: 관리자 id → 이름(employee.name) */
final class BoardAuthors {

    private BoardAuthors() {
    }

    static String managerName(EntityManager em, Long managerId) {
        if (managerId == null) {
            return null;
        }
        return em.createQuery("select e.name from Manager m join Employee e on e.id = m.employeeId where m.id = :id",
                        String.class)
                .setParameter("id", managerId)
                .getResultStream().findFirst().orElse(null);
    }
}
