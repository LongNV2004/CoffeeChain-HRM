package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.DeclineReason;
import com.example.coffee_hrm.common.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lời mời hỗ trợ ca gấp: Quản lý mời một nhân viên nhận ca đang cần người thay.
 * Mỗi lời mời có trạng thái rõ ràng (Đồng ý / Từ chối / Hết hạn / Đã hủy) làm căn cứ cộng/trừ điểm chuyên cần.
 */
@Entity
@Table(name = "ShiftCoverInvitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"request", "employee", "invitedBy"})
public class ShiftCoverInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "InvitationId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RequestId", nullable = false)
    private ShiftChangeRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "InvitedBy")
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private InvitationStatus status;

    /** Ca bắt đầu trong vòng 24h tại thời điểm gửi lời mời. */
    @Column(name = "Urgent", nullable = false)
    private boolean urgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "DeclineReason", length = 20)
    private DeclineReason declineReason;

    @Column(name = "DeclineNote", length = 255)
    private String declineNote;

    @Column(name = "SentAt", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "RespondedAt")
    private LocalDateTime respondedAt;
}
