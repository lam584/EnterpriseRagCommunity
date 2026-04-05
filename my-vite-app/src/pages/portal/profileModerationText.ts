export function formatProfileModerationStatus(status: string): string {
  return status === 'PENDING'
    ? '待审核'
    : status === 'REVIEWING'
      ? '审核中'
      : status === 'HUMAN'
        ? '待人工审核'
        : status === 'REJECTED'
          ? '已驳回'
          : status === 'APPROVED'
            ? '已通过'
            : status;
}
