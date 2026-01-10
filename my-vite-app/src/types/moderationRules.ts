export type ModerationRuleType = 'KEYWORD' | 'REGEX' | 'URL' | 'PATTERN';

export type ModerationRuleSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

// UI 层使用的“规则类别”：对应你的敏感词 / 黑名单 / URL / 广告模式
export type ModerationRuleCategory = 'SENSITIVE' | 'BLACKLIST' | 'URL' | 'AD';

export type ModerationRuleDTO = {
  id: number;
  name: string;
  type: ModerationRuleType;
  pattern: string;
  severity: ModerationRuleSeverity;
  enabled: boolean;
  metadata?: Record<string, unknown> | null;
  createdAt?: string;
  updatedAt?: string;
};

export type ModerationRuleCreatePayload = Omit<ModerationRuleDTO, 'id' | 'createdAt' | 'updatedAt'>;
export type ModerationRuleUpdatePayload = Partial<ModerationRuleCreatePayload>;

export type ModerationRuleListQuery = {
  q?: string;
  type?: ModerationRuleType | '';
  severity?: ModerationRuleSeverity | '';
  enabled?: boolean | '';
  // UI 分类筛选（前端通过 metadata.category 推断）
  category?: ModerationRuleCategory | '';
};
