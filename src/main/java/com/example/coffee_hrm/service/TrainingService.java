package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.TrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingSkillResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface TrainingService {

    List<TrainingSkillResponse> listSkills();

    List<TrainingSkillResponse> listActiveSkills();

    TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor);

    TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request, AuthenticatedUser actor);

    TrainingSkillResponse deactivateSkill(Integer skillId, AuthenticatedUser actor);

    TrainingSkillResponse activateSkill(Integer skillId, AuthenticatedUser actor);

    boolean managerHasAssignedStore(AuthenticatedUser manager);

    Page<TrainingClassResponse> listClassesForManager(AuthenticatedUser manager,
                                                      Integer skillId,
                                                      LocalDate date,
                                                      String keyword,
                                                      String sortDir,
                                                      int page);

    TrainingClassResponse getApprovedClassDetailForManager(Integer classId, AuthenticatedUser manager);

    void deleteClassForManager(Integer classId, AuthenticatedUser manager);

    TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser manager);

    List<TrainingClassResponse> listPendingClasses();

    List<TrainingClassResponse> listAllClassesForAdmin();

    TrainingClassResponse approveClass(Integer classId, AuthenticatedUser admin);

    TrainingClassResponse rejectClass(Integer classId, AuthenticatedUser admin);

    long countPendingClasses();
}
