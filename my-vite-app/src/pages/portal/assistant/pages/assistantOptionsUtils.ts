import type { AiChatOptionsDTO, AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';

export function normAssistantValue(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

export function pickAssistantProviderId(
  opt: AiChatOptionsDTO,
  providers: AiChatProviderOptionDTO[],
  preferredProviderId: unknown,
): string {
  const providerIds = new Set(providers.map((p) => normAssistantValue(p.id)).filter(Boolean));
  const storedProvider = normAssistantValue(preferredProviderId);
  if (storedProvider && providerIds.has(storedProvider)) {
    return storedProvider;
  }
  const active = normAssistantValue(opt.activeProviderId);
  if (active && providerIds.has(active)) {
    return active;
  }
  return normAssistantValue(providers[0]?.id);
}

export function pickAssistantModel(
  provider: AiChatProviderOptionDTO | null,
  preferredModel: unknown,
): string {
  const models = Array.isArray(provider?.chatModels) ? provider.chatModels.filter(Boolean) : [];
  const modelNames = new Set(models.map((m) => normAssistantValue((m as { name?: unknown }).name)).filter(Boolean));
  const storedModel = normAssistantValue(preferredModel);
  if (storedModel && modelNames.has(storedModel)) return storedModel;
  const directDefault = normAssistantValue(provider?.defaultChatModel);
  if (directDefault && modelNames.has(directDefault)) return directDefault;
  const flagged = models.find((m) => Boolean((m as { isDefault?: unknown }).isDefault));
  const flaggedName = normAssistantValue((flagged as { name?: unknown })?.name);
  if (flaggedName && modelNames.has(flaggedName)) return flaggedName;
  return normAssistantValue((models[0] as { name?: unknown })?.name);
}
