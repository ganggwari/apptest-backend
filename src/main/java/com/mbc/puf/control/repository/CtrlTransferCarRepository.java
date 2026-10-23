package com.mbc.puf.control.repository;

import com.mbc.puf.control.entity.CtrlTransferCar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CtrlTransferCarRepository extends JpaRepository<CtrlTransferCar, Long> {

    List<CtrlTransferCar> findByCtrlTransferId(Long ctrlTransferId);
}
