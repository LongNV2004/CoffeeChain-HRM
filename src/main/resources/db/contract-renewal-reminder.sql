-- Một thông báo nhắc gia hạn cho mỗi hợp đồng. Không xóa dữ liệu cũ.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_Notifications_ContractRenewalDue'
      AND object_id = OBJECT_ID(N'dbo.Notifications')
)
    CREATE UNIQUE INDEX UX_Notifications_ContractRenewalDue
        ON dbo.Notifications (ReferenceId)
        WHERE NotificationType = 'CONTRACT_RENEWAL_DUE'
          AND ReferenceType = 'EMPLOYMENT_CONTRACT'
          AND ReferenceId IS NOT NULL;
GO
