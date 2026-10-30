package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AddTrainingClassStudentsRequest;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateManagementTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
import com.example.coffee_hrm.common.enums.CertificateKind;
import com.example.coffee_hrm.dto.request.SkillFilePayload;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.SkillDocumentDownload;
import com.example.coffee_hrm.dto.response.ManagementTrainingView;
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

    List<TrainingSkillResponse> listSkills(com.example.coffee_hrm.common.enums.CertificateKind certificateKind);

    List<TrainingSkillResponse> listActiveSkills();

    TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor);

    TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor, List<SkillFilePayload> files);

    TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request, AuthenticatedUser actor);

    TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request, AuthenticatedUser actor, List<SkillFilePayload> files);

    CertificateKind removeSkillDocument(Integer documentId, AuthenticatedUser actor);

    Integer skillIdOfDocument(Integer documentId);

    SkillDocumentDownload downloadSkillDocument(Integer documentId, AuthenticatedUser actor);

    SkillDocumentDownload downloadSkillDocumentForManager(Integer documentId, AuthenticatedUser manager);

    SkillDocumentDownload downloadClassMaterialForManager(Integer classId, Integer documentId, AuthenticatedUser manager);

    SkillDocumentDownload downloadClassMaterialForAdmin(Integer classId, Integer documentId, AuthenticatedUser admin);

    TrainingSkillResponse deactivateSkill(Integer skillId, AuthenticatedUser actor);

    TrainingSkillResponse activateSkill(Integer skillId, AuthenticatedUser actor);

    boolean managerHasAssignedStore(AuthenticatedUser manager);

    List<TrainingClassStudentResponse> listEmployeesForNewStoreClass(AuthenticatedUser manager);

    List<Store> listActiveStores(AuthenticatedUser admin);

    List<TrainingTrainerOption> listTrainerCandidates(AuthenticatedUser admin);

    List<TrainingEmployeeOption> listActiveEmployees(AuthenticatedUser admin);

    List<TrainingEmployeeOption> listClassCandidates(AuthenticatedUser admin);

    Page<TrainingClassResponse> listClassesForManager(AuthenticatedUser manager,
                                                      Integer skillId,
                                                      LocalDate date,
                                                      String keyword,
                                                      String sortDir,
                                                      int page);

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

    List<EmployeeTrainingClassResponse> listOwnClasses(AuthenticatedUser actor);

    EmployeeTrainingClassResponse getOwnClass(Integer classId, AuthenticatedUser actor);

    long countOngoingClassesForEmployee(AuthenticatedUser employee);

    /**
     * Một dòng Chứng chỉ nhân viên của đúng hồ sơ. Không còn chứng chỉ theo từng kỹ năng.
     */
    List<EmployeeSkillStatusLine> listCurrentSkillStatuses(Integer employeeId);

    TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser manager);

    TrainingClassResponse createCentralizedClass(CreateCentralizedTrainingRequest request, AuthenticatedUser admin);

    ManagementTrainingView createManagementClass(CreateManagementTrainingRequest request, AuthenticatedUser admin);

    List<ManagementTrainingView> listManagementClasses(AuthenticatedUser admin);

    List<ManagementTrainingView.Candidate> listManagementCandidates(AuthenticatedUser admin);

    void enrollManagerInManagementClass(Integer classId, Integer employeeId, AuthenticatedUser admin);

    void enrollSelfInManagementClass(Integer classId, AuthenticatedUser manager);

    List<ManagementTrainingView> listManagementClassesForManager(AuthenticatedUser manager);

    void evaluateManagementStudent(Integer classId,
                                   Integer employeeId,
                                   EvaluateTrainingStudentRequest request,
                                   AuthenticatedUser admin);

    Page<TrainingClassResponse> listActiveClassesForAdmin(AuthenticatedUser admin,
                                                          Integer skillId,
                                                          LocalDate date,
                                                          String keyword,
                                                          String sortDir,
                                                          int page);

    TrainingClassResponse getApprovedClassDetailForAdmin(Integer classId, AuthenticatedUser admin);

    List<TrainingClassResponse> listAllClassesForAdmin();

    List<TrainingClassResponse> listEndedClassesForAdmin(AuthenticatedUser admin);

    void notifyEndedClasses();
}
