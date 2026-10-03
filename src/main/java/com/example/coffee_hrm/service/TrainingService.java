package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AddTrainingClassStudentsRequest;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.EmployeeTrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingClassStudentResponse;
import com.example.coffee_hrm.dto.response.TrainingEmployeeOption;
import com.example.coffee_hrm.dto.response.TrainingSkillResponse;
import com.example.coffee_hrm.dto.response.TrainingTrainerOption;
import com.example.coffee_hrm.entity.Store;
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

    List<TrainingClassStudentResponse> listEmployeesForNewStoreClass(AuthenticatedUser manager);

    List<Store> listActiveStores(AuthenticatedUser admin);

    List<TrainingTrainerOption> listTrainerCandidates(AuthenticatedUser admin);

    List<TrainingEmployeeOption> listActiveEmployees(AuthenticatedUser admin);

    Page<TrainingClassResponse> listClassesForManager(AuthenticatedUser manager,
                                                      Integer skillId,
                                                      LocalDate date,
                                                      String keyword,
                                                      String sortDir,
                                                      int page);

    List<TrainingClassResponse> listSubmittedClassesForManager(AuthenticatedUser manager);

    List<TrainingClassResponse> listEndedClassesForManager(AuthenticatedUser manager);

    TrainingClassResponse getApprovedClassDetailForManager(Integer classId, AuthenticatedUser manager);

    List<TrainingClassStudentResponse> listAvailableEmployeesForClass(Integer classId, AuthenticatedUser manager);

    int addStudentsToClass(Integer classId, AddTrainingClassStudentsRequest request, AuthenticatedUser manager);

    void evaluateStudent(Integer classId,
                         Integer employeeId,
                         EvaluateTrainingStudentRequest request,
                         AuthenticatedUser manager);

    void deleteClassForManager(Integer classId, AuthenticatedUser manager);

    List<EmployeeTrainingClassResponse> listClassesForEmployee(AuthenticatedUser employee);

    EmployeeTrainingClassResponse getClassForEmployee(Integer classId, AuthenticatedUser employee);

    long countOngoingClassesForEmployee(AuthenticatedUser employee);

    TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser manager);

    TrainingClassResponse createCentralizedClass(CreateCentralizedTrainingRequest request, AuthenticatedUser admin);

    List<TrainingClassResponse> listPendingClasses();

    List<TrainingClassResponse> listAllClassesForAdmin();

    TrainingClassResponse approveClass(Integer classId, AuthenticatedUser admin);

    TrainingClassResponse rejectClass(Integer classId, AuthenticatedUser admin);

    long countPendingClasses();
}
