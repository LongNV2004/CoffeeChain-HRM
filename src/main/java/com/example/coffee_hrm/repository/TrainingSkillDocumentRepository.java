package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.TrainingSkillDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TrainingSkillDocumentRepository extends JpaRepository<TrainingSkillDocument, Integer> {

    List<TrainingSkillDocument> findBySkill_IdOrderByIdAsc(Integer skillId);

    List<TrainingSkillDocument> findBySkill_IdInOrderByIdAsc(Collection<Integer> skillIds);

    long countBySkill_Id(Integer skillId);
}
