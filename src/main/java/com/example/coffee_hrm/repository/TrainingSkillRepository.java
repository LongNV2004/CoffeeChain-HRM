package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.entity.TrainingSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrainingSkillRepository extends JpaRepository<TrainingSkill, Integer> {

    @Query("""
            SELECT s FROM TrainingSkill s
            LEFT JOIN FETCH s.createdBy
            ORDER BY s.skillName ASC
            """)
    List<TrainingSkill> findAllWithCreator();

    @Query("""
            SELECT s FROM TrainingSkill s
            LEFT JOIN FETCH s.createdBy
            WHERE s.status = :status
            ORDER BY s.skillName ASC
            """)
    List<TrainingSkill> findByStatusWithCreator(@Param("status") TrainingSkillStatus status);

    boolean existsBySkillNameIgnoreCase(String skillName);

    boolean existsBySkillNameIgnoreCaseAndIdNot(String skillName, Integer id);
}
