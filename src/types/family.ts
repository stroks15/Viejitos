export type FriendlyApp = { packageName: string; originalLabel: string; alias: string; color: string; icon: string };
export type ScamAssessment = { risky: boolean; reasons: string[]; safeText: string };
export type SmsMessage = { id: string; sender: string; text: string; timestamp: number };
