export function normTaskType(value: string | null | undefined): string {
  return String(value || '').trim().toUpperCase();
}
