import React, { useEffect, useMemo, useState } from 'react';
import { Button } from '../../../../components/ui/button';
import { Input } from '../../../../components/ui/input';
import { Checkbox } from '../../../../components/ui/checkbox';
import { Label } from '../../../../components/ui/label';
import EmailConfigForm from './email-config';
import type { UserQueryDTO } from '../../../../types/userAccess';
import { getEmailAdminSettings } from '../../../../services/emailAdminService';
import { listRoleSummaries, type RoleSummaryDTO } from '../../../../services/rolePermissionsService';
import {
  getSecurity2faPolicySettings,
  type Security2faPolicySettingsDTO,
  updateSecurity2faPolicySettings,
} from '../../../../services/security2faPolicyAdminService';
import {
  type AdminUserTotpStatusDTO,
  getTotpAdminSettings,
  queryAdminUserTotpStatus,
  resetAdminUserTotp,
  type TotpAdminSettingsDTO,
  updateTotpAdminSettings,
} from '../../../../services/totpAdminService';

const ALG_OPTIONS = ['SHA1', 'SHA256', 'SHA512'] as const;
const DIGIT_OPTIONS = [6, 8] as const;
const PERIOD_OPTIONS = [30, 60] as const;

const ENABLE_POLICY_OPTIONS = [
  { value: 'FORBID_ALL', label: '禁止所有人启用' },
  { value: 'ALLOW_ALL', label: '全部用户可选启用' },
  { value: 'ALLOW_ROLES', label: '指定角色可选启用' },
  { value: 'ALLOW_USERS', label: '指定用户可选启用' },
  { value: 'REQUIRE_ALL', label: '全部用户强制启用' },
  { value: 'REQUIRE_ROLES', label: '指定角色强制启用' },
  { value: 'REQUIRE_USERS', label: '指定用户强制启用' },
] as const;

const LOGIN_2FA_MODE_OPTIONS = [
  { value: 'DISABLED', label: '禁用' },
  { value: 'EMAIL_ONLY', label: '仅验证邮箱' },
  { value: 'TOTP_ONLY', label: '仅验证 TOTP' },
  { value: 'EMAIL_OR_TOTP', label: '邮箱和 TOTP 二选一' },
] as const;

const LOGIN_2FA_SCOPE_OPTIONS = [
  { value: 'FORBID_ALL', label: '禁用（不要求二次验证）' },
  { value: 'REQUIRE_ALL', label: '全部用户强制二次验证' },
  { value: 'REQUIRE_ROLES', label: '指定角色强制二次验证' },
  { value: 'REQUIRE_USERS', label: '指定用户强制二次验证' },
  { value: 'ALLOW_ALL', label: '全部用户可选二次验证' },
  { value: 'ALLOW_ROLES', label: '指定角色可选二次验证' },
  { value: 'ALLOW_USERS', label: '指定用户可选二次验证' },
] as const;

function uniq<T>(arr: T[]): T[] {
  return Array.from(new Set(arr));
}

function toggleValue<T>(list: T[], value: T, nextChecked: boolean): T[] {
  const has = list.includes(value);
  if (nextChecked && !has) return [...list, value];
  if (!nextChecked && has) return list.filter(v => v !== value);
  return list;
}

function normalizeChecked(v: boolean | 'indeterminate'): boolean {
  return v === true;
}

function parseIdCsv(v: string): number[] {
  const parts = String(v ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const ids: number[] = [];
  for (const p of parts) {
    const n = Number(p);
    if (Number.isFinite(n) && n > 0) ids.push(Math.trunc(n));
  }
  return uniq(ids).sort((a, b) => a - b);
}

const TwoFAForm: React.FC = () => {
  const [policyLoading, setPolicyLoading] = useState(false);
  const [policySaving, setPolicySaving] = useState(false);
  const [policyEditing, setPolicyEditing] = useState(false);
  const [policy, setPolicy] = useState<Security2faPolicySettingsDTO | null>(null);
  const [policySnapshot, setPolicySnapshot] = useState<Security2faPolicySettingsDTO | null>(null);
  const [policyError, setPolicyError] = useState<string | null>(null);
  const [roleSummaries, setRoleSummaries] = useState<RoleSummaryDTO[]>([]);
  const [roleSummariesError, setRoleSummariesError] = useState<string | null>(null);
  const [emailSvcEnabled, setEmailSvcEnabled] = useState<boolean | null>(null);

  const [settingsLoading, setSettingsLoading] = useState(false);
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [settingsEditing, setSettingsEditing] = useState(false);
  const [settings, setSettings] = useState<TotpAdminSettingsDTO | null>(null);
  const [settingsSnapshot, setSettingsSnapshot] = useState<TotpAdminSettingsDTO | null>(null);
  const [settingsError, setSettingsError] = useState<string | null>(null);

  const [usersLoading, setUsersLoading] = useState(false);
  const [userRows, setUserRows] = useState<AdminUserTotpStatusDTO[]>([]);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [pageNum, setPageNum] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [emailKeyword, setEmailKeyword] = useState('');
  const [usernameKeyword, setUsernameKeyword] = useState('');

  const queryPayload = useMemo((): UserQueryDTO => {
    const email = emailKeyword.trim();
    const username = usernameKeyword.trim();
    return {
      pageNum,
      pageSize: 10,
      email: email ? email : undefined,
      username: username ? username : undefined,
      includeDeleted: false,
    };
  }, [pageNum, emailKeyword, usernameKeyword]);

  const loadSettings = async () => {
    setSettingsLoading(true);
    setSettingsError(null);
    try {
      const s = await getTotpAdminSettings();
      setSettings({
        ...s,
        allowedAlgorithms: uniq((s.allowedAlgorithms ?? []).map(x => String(x).trim().toUpperCase()).filter(Boolean)),
        allowedDigits: uniq((s.allowedDigits ?? []).map(x => Number(x)).filter(x => Number.isFinite(x))),
        allowedPeriodSeconds: uniq((s.allowedPeriodSeconds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x))),
      });
      setSettingsEditing(false);
      setSettingsSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setSettingsError(msg);
    } finally {
      setSettingsLoading(false);
    }
  };

  const loadPolicy = async () => {
    setPolicyLoading(true);
    setPolicyError(null);
    setRoleSummariesError(null);
    try {
      const [p, emailCfg] = await Promise.all([
        getSecurity2faPolicySettings(),
        getEmailAdminSettings().catch(() => null),
      ]);

      setEmailSvcEnabled(emailCfg ? Boolean(emailCfg.enabled) : null);
      setPolicy({
        totpPolicy: String(p.totpPolicy ?? 'ALLOW_ALL').trim().toUpperCase(),
        totpRoleIds: uniq((p.totpRoleIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
        totpUserIds: uniq((p.totpUserIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
        emailOtpPolicy: String(p.emailOtpPolicy ?? 'ALLOW_ALL').trim().toUpperCase(),
        emailOtpRoleIds: uniq((p.emailOtpRoleIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
        emailOtpUserIds: uniq((p.emailOtpUserIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
        login2faMode: String(p.login2faMode ?? 'EMAIL_OR_TOTP').trim().toUpperCase(),
        login2faScopePolicy: String(p.login2faScopePolicy ?? 'ALLOW_ALL').trim().toUpperCase(),
        login2faRoleIds: uniq((p.login2faRoleIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
        login2faUserIds: uniq((p.login2faUserIds ?? []).map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)),
      });

      try {
        const roles = await listRoleSummaries();
        setRoleSummaries((roles ?? []).filter(r => Number.isFinite(Number(r.roleId))));
      } catch (e) {
        const msg = e instanceof Error ? e.message : '加载角色列表失败';
        setRoleSummariesError(msg);
        setRoleSummaries([]);
      }

      setPolicyEditing(false);
      setPolicySnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setPolicyError(msg);
    } finally {
      setPolicyLoading(false);
    }
  };

  const savePolicy = async () => {
    if (!policy) return;
    setPolicySaving(true);
    setPolicyError(null);
    try {
      const saved = await updateSecurity2faPolicySettings(policy);
      setPolicy(saved);
      setPolicyEditing(false);
      setPolicySnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存失败';
      setPolicyError(msg);
    } finally {
      setPolicySaving(false);
    }
  };

  const saveSettings = async () => {
    if (!settings) return;
    setSettingsSaving(true);
    setSettingsError(null);
    try {
      const saved = await updateTotpAdminSettings(settings);
      setSettings(saved);
      setSettingsEditing(false);
      setSettingsSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存失败';
      setSettingsError(msg);
    } finally {
      setSettingsSaving(false);
    }
  };

  const loadUsers = async () => {
    setUsersLoading(true);
    setUsersError(null);
    try {
      const page = await queryAdminUserTotpStatus(queryPayload);
      setUserRows(page.content ?? []);
      setTotalPages(Number(page.totalPages) || 0);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setUsersError(msg);
    } finally {
      setUsersLoading(false);
    }
  };

  useEffect(() => {
    void loadSettings();
    void loadPolicy();
  }, []);

  useEffect(() => {
    void loadUsers();
  }, [queryPayload]);

  const canEditSettings = settingsEditing && !settingsLoading && !settingsSaving;
  const canEditPolicy = policyEditing && !policyLoading && !policySaving;

  const policyNeedsRoles = (v?: string | null) => String(v ?? '').toUpperCase().includes('_ROLES');
  const policyNeedsUsers = (v?: string | null) => String(v ?? '').toUpperCase().includes('_USERS');
  const toggleRoleId = (list: number[] | undefined, roleId: number, nextChecked: boolean): number[] => {
    const src = Array.isArray(list) ? list : [];
    const next = toggleValue(src, roleId, nextChecked);
    return uniq(next.map(x => Number(x)).filter(x => Number.isFinite(x) && x > 0)).sort((a, b) => a - b);
  };
  const policyLabel = (v?: string | null) => ENABLE_POLICY_OPTIONS.find(x => x.value === String(v ?? '').toUpperCase())?.label ?? String(v ?? '');

  return (
    <div className="space-y-4">
      <EmailConfigForm />
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">2FA 启用策略（TOTP / 邮箱验证码）</h3>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={loadPolicy} disabled={policyLoading || policySaving || policyEditing}>
              刷新
            </Button>
            {policyEditing ? (
              <>
                <Button
                  variant="secondary"
                  onClick={() => {
                    if (policySnapshot) setPolicy(policySnapshot);
                    setPolicyEditing(false);
                    setPolicySnapshot(null);
                    setPolicyError(null);
                  }}
                  disabled={policyLoading || policySaving}
                >
                  取消
                </Button>
                <Button onClick={savePolicy} disabled={policyLoading || policySaving || !policy}>
                  {policySaving ? '保存中...' : '保存'}
                </Button>
              </>
            ) : (
              <Button
                onClick={() => {
                  if (!policy) return;
                  setPolicySnapshot(policy);
                  setPolicyEditing(true);
                  setPolicyError(null);
                }}
                disabled={policyLoading || policySaving || !policy}
              >
                编辑
              </Button>
            )}
          </div>
        </div>

        {policyError ? (
          <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{policyError}</div>
        ) : null}

        {roleSummariesError ? (
          <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            {roleSummariesError}
          </div>
        ) : null}

        {!policy ? (
          <div className="text-sm text-gray-500">{policyLoading ? '加载中...' : '暂无配置'}</div>
        ) : (
          <div className="space-y-3">
            {String(policy.emailOtpPolicy ?? '').toUpperCase().startsWith('REQUIRE') && emailSvcEnabled === false ? (
              <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                邮箱服务当前处于关闭状态；邮箱验证码的“强制启用”策略将无法生效（用户侧会按不可用处理）。
              </div>
            ) : null}

            <div className="rounded border p-3 space-y-2">
              <div className="font-medium text-sm">登录二次验证</div>
              <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                <div className="space-y-1">
                  <Label className="text-xs text-gray-600">登录是否要求二次验证</Label>
                  <select
                    className="border border-gray-300 rounded px-2 py-0 bg-white text-sm w-full h-9"
                    value={String(policy.login2faMode ?? 'EMAIL_OR_TOTP').toUpperCase()}
                    disabled={!canEditPolicy}
                    onChange={(e) => setPolicy(p => (p ? { ...p, login2faMode: e.target.value } : p))}
                  >
                    {LOGIN_2FA_MODE_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs text-gray-600">作用范围</Label>
                  <select
                    className="border border-gray-300 rounded px-2 py-0 bg-white text-sm w-full h-9"
                    value={String(policy.login2faScopePolicy ?? 'ALLOW_ALL').toUpperCase()}
                    disabled={!canEditPolicy}
                    onChange={(e) => setPolicy(p => (p ? { ...p, login2faScopePolicy: e.target.value } : p))}
                  >
                    {LOGIN_2FA_SCOPE_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              {policyNeedsUsers(policy.login2faScopePolicy) ? (
                <div className="space-y-1">
                  <div className="text-xs text-gray-600">指定用户 userId（逗号分隔）</div>
                  <Input
                    className="h-9 text-sm"
                    value={(policy.login2faUserIds ?? []).join(',')}
                    disabled={!canEditPolicy}
                    onChange={(e) => setPolicy(p => (p ? { ...p, login2faUserIds: parseIdCsv(e.target.value) } : p))}
                    placeholder="例如：1,2,3"
                  />
                </div>
              ) : null}

              {policyNeedsRoles(policy.login2faScopePolicy) ? (
                <div className="space-y-1">
                  <div className="text-xs text-gray-600">指定角色（命中任意一个 roleId 即生效）</div>
                  {roleSummaries.length === 0 ? (
                    <div className="text-xs text-gray-500">暂无角色数据</div>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      {roleSummaries.map(r => (
                        <div key={r.roleId} className="flex items-center gap-1.5">
                          <Checkbox
                            id={`login2fa-role-${r.roleId}`}
                            className="h-3.5 w-3.5"
                            checked={(policy.login2faRoleIds ?? []).includes(r.roleId)}
                            disabled={!canEditPolicy}
                            onCheckedChange={(v) => {
                              const next = toggleRoleId(policy.login2faRoleIds ?? [], r.roleId, normalizeChecked(v));
                              setPolicy(p => (p ? { ...p, login2faRoleIds: next } : p));
                            }}
                          />
                          <label htmlFor={`login2fa-role-${r.roleId}`} className="text-xs cursor-pointer select-none">
                            {(r.roleName ? r.roleName : `roleId=${r.roleId}`) + ` (#${r.roleId})`}
                          </label>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : null}
            </div>

            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <div className="rounded border p-3 space-y-2">
                <div className="font-medium text-sm">TOTP 启用策略</div>
                <select
                  className="border border-gray-300 rounded px-2 py-0 bg-white text-sm w-full h-9"
                  value={String(policy.totpPolicy ?? 'ALLOW_ALL').toUpperCase()}
                  disabled={!canEditPolicy}
                  onChange={(e) => setPolicy(p => (p ? { ...p, totpPolicy: e.target.value } : p))}
                >
                  {ENABLE_POLICY_OPTIONS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>

                {policyNeedsRoles(policy.totpPolicy) ? (
                  <div className="space-y-1">
                    <div className="text-xs text-gray-600">指定角色（命中任意一个 roleId 即生效）</div>
                    {roleSummaries.length === 0 ? (
                      <div className="text-xs text-gray-500">暂无角色数据</div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {roleSummaries.map(r => (
                          <div key={r.roleId} className="flex items-center gap-1.5">
                            <Checkbox
                              id={`totp-role-${r.roleId}`}
                              className="h-3.5 w-3.5"
                              checked={(policy.totpRoleIds ?? []).includes(r.roleId)}
                              disabled={!canEditPolicy}
                              onCheckedChange={(v) => {
                                const next = toggleRoleId(policy.totpRoleIds ?? [], r.roleId, normalizeChecked(v));
                                setPolicy(p => (p ? { ...p, totpRoleIds: next } : p));
                              }}
                            />
                            <label htmlFor={`totp-role-${r.roleId}`} className="text-xs cursor-pointer select-none">
                              {(r.roleName ? r.roleName : `roleId=${r.roleId}`) + ` (#${r.roleId})`}
                            </label>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ) : null}

                {policyNeedsUsers(policy.totpPolicy) ? (
                  <div className="space-y-1">
                    <div className="text-xs text-gray-600">指定用户 userId（逗号分隔）</div>
                    <Input
                      className="h-9 text-sm"
                      value={(policy.totpUserIds ?? []).join(',')}
                      disabled={!canEditPolicy}
                      onChange={(e) => setPolicy(p => (p ? { ...p, totpUserIds: parseIdCsv(e.target.value) } : p))}
                      placeholder="例如：1,2,3"
                    />
                  </div>
                ) : null}
              </div>

              <div className="rounded border p-3 space-y-2">
                <div className="font-medium text-sm">邮箱验证码策略</div>
                <select
                  className="border border-gray-300 rounded px-2 py-0 bg-white text-sm w-full h-9"
                  value={String(policy.emailOtpPolicy ?? 'ALLOW_ALL').toUpperCase()}
                  disabled={!canEditPolicy}
                  onChange={(e) => setPolicy(p => (p ? { ...p, emailOtpPolicy: e.target.value } : p))}
                >
                  {ENABLE_POLICY_OPTIONS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
                <div className="text-[10px] text-gray-500">
                  邮箱验证码是否可用还取决于 SMTP 配置是否开启。
                </div>

                {policyNeedsRoles(policy.emailOtpPolicy) ? (
                  <div className="space-y-1">
                    <div className="text-xs text-gray-600">指定角色（命中任意一个 roleId 即生效）</div>
                    {roleSummaries.length === 0 ? (
                      <div className="text-xs text-gray-500">暂无角色数据</div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {roleSummaries.map(r => (
                          <div key={r.roleId} className="flex items-center gap-1.5">
                            <Checkbox
                              id={`email-role-${r.roleId}`}
                              className="h-3.5 w-3.5"
                              checked={(policy.emailOtpRoleIds ?? []).includes(r.roleId)}
                              disabled={!canEditPolicy}
                              onCheckedChange={(v) => {
                                const next = toggleRoleId(policy.emailOtpRoleIds ?? [], r.roleId, normalizeChecked(v));
                                setPolicy(p => (p ? { ...p, emailOtpRoleIds: next } : p));
                              }}
                            />
                            <label htmlFor={`email-role-${r.roleId}`} className="text-xs cursor-pointer select-none">
                              {(r.roleName ? r.roleName : `roleId=${r.roleId}`) + ` (#${r.roleId})`}
                            </label>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ) : null}

                {policyNeedsUsers(policy.emailOtpPolicy) ? (
                  <div className="space-y-1">
                    <div className="text-xs text-gray-600">指定用户 userId（逗号分隔）</div>
                    <Input
                      className="h-9 text-sm"
                      value={(policy.emailOtpUserIds ?? []).join(',')}
                      disabled={!canEditPolicy}
                      onChange={(e) => setPolicy(p => (p ? { ...p, emailOtpUserIds: parseIdCsv(e.target.value) } : p))}
                      placeholder="例如：1,2,3"
                    />
                  </div>
                ) : null}
              </div>
            </div>

            {!canEditPolicy ? (
              <div className="text-xs text-gray-500">
                当前策略：TOTP「{policyLabel(policy.totpPolicy)}」；邮箱验证码「{policyLabel(policy.emailOtpPolicy)}」。
              </div>
            ) : null}
          </div>
        )}
      </div>
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">TOTP 策略配置（允许用户选择的范围）</h3>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={loadSettings} disabled={settingsLoading || settingsSaving || settingsEditing}>
              刷新
            </Button>
            {settingsEditing ? (
              <>
                <Button
                  variant="secondary"
                  onClick={() => {
                    if (settingsSnapshot) setSettings(settingsSnapshot);
                    setSettingsEditing(false);
                    setSettingsSnapshot(null);
                    setSettingsError(null);
                  }}
                  disabled={settingsLoading || settingsSaving}
                >
                  取消
                </Button>
                <Button onClick={saveSettings} disabled={settingsLoading || settingsSaving || !settings}>
                  {settingsSaving ? '保存中...' : '保存'}
                </Button>
              </>
            ) : (
              <Button
                onClick={() => {
                  if (!settings) return;
                  setSettingsSnapshot(settings);
                  setSettingsEditing(true);
                  setSettingsError(null);
                }}
                disabled={settingsLoading || settingsSaving || !settings}
              >
                编辑
              </Button>
            )}
          </div>
        </div>

        {settingsError ? (
          <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{settingsError}</div>
        ) : null}

        {!settings ? (
          <div className="text-sm text-gray-500">{settingsLoading ? '加载中...' : '暂无配置'}</div>
        ) : (
          <div className="space-y-3">
            {(settings.allowedPeriodSeconds ?? []).includes(60) || (settings.maxSkew ?? 0) >= 2 ? (
              <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 space-y-0.5">
                <div className="font-medium">风险提示</div>
                {(settings.allowedPeriodSeconds ?? []).includes(60) ? (
                  <div>• 启用 60 秒步长可能导致 Google/Microsoft Auth 不兼容。</div>
                ) : null}
                {(settings.maxSkew ?? 0) >= 2 ? (
                  <div>• maxSkew=2 增加被撞库/重放风险。</div>
                ) : null}
              </div>
            ) : null}

            <div className="grid grid-cols-1 gap-3 md:flex md:items-start md:gap-4">
              <div className="space-y-1 md:w-[280px] md:flex-none">
                <Label className="text-xs text-gray-600">颁发者</Label>
                <Input
                  className="h-8 text-sm md:max-w-[340px]"
                  value={settings.issuer ?? ''}
                  disabled={!canEditSettings}
                  onChange={(e) => setSettings(s => (s ? { ...s, issuer: e.target.value } : s))}
                  placeholder="例如：EnterpriseRagCommunity"
                />
                <div className="text-[10px] text-gray-400">影响认证器应用里展示的账户归属</div>
              </div>

              <div className="border-t pt-3 md:flex-1 md:border-t-0 md:border-l md:pl-4 md:pt-0">
                <div className="flex flex-wrap items-start gap-x-6 gap-y-3">
                  <div className="space-y-1 md:w-[220px] md:flex-none">
                    <div className="font-medium text-xs text-gray-700">算法 (Algorithms)</div>
                    <div className="flex flex-wrap gap-2 min-h-[20px]">
                      {ALG_OPTIONS.map(a => (
                        <div key={a} className="flex items-center gap-1.5">
                          <Checkbox
                            id={`alg-${a}`}
                            className="h-3.5 w-3.5"
                            checked={settings.allowedAlgorithms?.includes(a)}
                            disabled={!canEditSettings}
                            onCheckedChange={(v) => {
                              const next = toggleValue(settings.allowedAlgorithms ?? [], a, normalizeChecked(v));
                              setSettings(s => (s ? { ...s, allowedAlgorithms: next } : s));
                            }}
                          />
                          <label htmlFor={`alg-${a}`} className="text-xs cursor-pointer select-none">{a}</label>
                        </div>
                      ))}
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-xs text-gray-500 whitespace-nowrap">默认:</span>
                      <select
                        className="border border-gray-300 rounded px-2 py-0 bg-white text-xs w-24 h-7"
                        value={settings.defaultAlgorithm ?? 'SHA1'}
                        disabled={!canEditSettings}
                        onChange={(e) => setSettings(s => (s ? { ...s, defaultAlgorithm: e.target.value } : s))}
                      >
                        {(settings.allowedAlgorithms?.length ? settings.allowedAlgorithms : [...ALG_OPTIONS]).map(a => (
                          <option key={a} value={a}>{a}</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="space-y-1 md:w-[220px] md:flex-none">
                    <div className="font-medium text-xs text-gray-700">位数 (Digits)</div>
                    <div className="flex flex-wrap gap-2 min-h-[20px]">
                      {DIGIT_OPTIONS.map(d => (
                        <div key={d} className="flex items-center gap-1.5">
                          <Checkbox
                            id={`dig-${d}`}
                            className="h-3.5 w-3.5"
                            checked={(settings.allowedDigits ?? []).includes(d)}
                            disabled={!canEditSettings}
                            onCheckedChange={(v) => {
                              const next = toggleValue(settings.allowedDigits ?? [], d, normalizeChecked(v));
                              setSettings(s => (s ? { ...s, allowedDigits: next } : s));
                            }}
                          />
                          <label htmlFor={`dig-${d}`} className="text-xs cursor-pointer select-none">{d} 位</label>
                        </div>
                      ))}
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-xs text-gray-500 whitespace-nowrap">默认:</span>
                      <select
                        className="border border-gray-300 rounded px-2 py-0 bg-white text-xs w-24 h-7"
                        value={String(settings.defaultDigits ?? 6)}
                        disabled={!canEditSettings}
                        onChange={(e) => setSettings(s => (s ? { ...s, defaultDigits: Number(e.target.value) } : s))}
                      >
                        {(settings.allowedDigits?.length ? settings.allowedDigits : [...DIGIT_OPTIONS]).map(d => (
                          <option key={d} value={String(d)}>{d} 位</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="space-y-1 md:w-[220px] md:flex-none">
                    <div className="font-medium text-xs text-gray-700">时间步长 (Period)</div>
                    <div className="flex flex-wrap gap-2 min-h-[20px]">
                      {PERIOD_OPTIONS.map(p => (
                        <div key={p} className="flex items-center gap-1.5">
                          <Checkbox
                            id={`per-${p}`}
                            className="h-3.5 w-3.5"
                            checked={(settings.allowedPeriodSeconds ?? []).includes(p)}
                            disabled={!canEditSettings}
                            onCheckedChange={(v) => {
                              const next = toggleValue(settings.allowedPeriodSeconds ?? [], p, normalizeChecked(v));
                              setSettings(s => (s ? { ...s, allowedPeriodSeconds: next } : s));
                            }}
                          />
                          <label htmlFor={`per-${p}`} className="text-xs cursor-pointer select-none">{p} 秒</label>
                        </div>
                      ))}
                    </div>
                    {(settings.allowedPeriodSeconds ?? []).includes(60) ? (
                      <div className="text-[10px] text-amber-700 leading-tight">注：60s 可能不兼容部分 App。</div>
                    ) : null}
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-xs text-gray-500 whitespace-nowrap">默认:</span>
                      <select
                        className="border border-gray-300 rounded px-2 py-0 bg-white text-xs w-24 h-7"
                        value={String(settings.defaultPeriodSeconds ?? 30)}
                        disabled={!canEditSettings}
                        onChange={(e) => setSettings(s => (s ? { ...s, defaultPeriodSeconds: Number(e.target.value) } : s))}
                      >
                        {(settings.allowedPeriodSeconds?.length ? settings.allowedPeriodSeconds : [...PERIOD_OPTIONS]).map(p => (
                          <option key={p} value={String(p)}>{p} 秒</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="space-y-1 md:w-[160px] md:flex-none">
                    <Label className="text-xs text-gray-600">最大偏移(MAX Skew)</Label>
                    <select
                      className="border border-gray-300 rounded px-2 py-0 bg-white w-24 text-sm h-8"
                      value={String(settings.maxSkew ?? 0)}
                      disabled={!canEditSettings}
                      onChange={(e) => setSettings(s => (s ? { ...s, maxSkew: Number(e.target.value) } : s))}
                    >
                      {[0, 1, 2].map(v => (
                        <option key={v} value={String(v)}>
                          {v}
                        </option>
                      ))}
                    </select>
                    {(settings.maxSkew ?? 0) >= 2 ? (
                      <div className="text-[10px] text-amber-700 leading-tight">风险：增加撞库风险。</div>
                    ) : null}
                  </div>

                  <div className="space-y-1 md:w-[160px] md:flex-none">
                    <Label className="text-xs text-gray-600">默认偏移值(Default Skew)</Label>
                    <select
                      className="border border-gray-300 rounded px-2 py-0 bg-white w-24 text-sm h-8"
                      value={String(settings.defaultSkew ?? 1)}
                      disabled={!canEditSettings}
                      onChange={(e) => setSettings(s => (s ? { ...s, defaultSkew: Number(e.target.value) } : s))}
                    >
                      {Array.from({ length: (settings.maxSkew ?? 0) + 1 }).map((_, i) => (
                        <option key={i} value={String(i)}>
                          {i}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">用户 TOTP 状态</h3>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setPageNum(1)} disabled={usersLoading}>
              刷新
            </Button>
          </div>
        </div>

        {usersError ? (
          <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{usersError}</div>
        ) : null}

        <div className="grid grid-cols-1 gap-3 md:grid-cols-3 md:items-end">
          <div className="space-y-1">
            <Label>邮箱</Label>
            <Input value={emailKeyword} onChange={(e) => setEmailKeyword(e.target.value)} placeholder="模糊匹配" />
          </div>
          <div className="space-y-1">
            <Label>用户名</Label>
            <Input value={usernameKeyword} onChange={(e) => setUsernameKeyword(e.target.value)} placeholder="模糊匹配" />
          </div>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              onClick={() => setPageNum(1)}
              disabled={usersLoading}
              className="whitespace-nowrap"
            >
              搜索
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setEmailKeyword('');
                setUsernameKeyword('');
                setPageNum(1);
              }}
              disabled={usersLoading}
              className="whitespace-nowrap"
            >
              重置
            </Button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">用户Id</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">邮箱</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">用户名</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">启用</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">验证时间</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"> 创建时间</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">算法/位数/步长</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {usersLoading ? (
                <tr>
                  <td colSpan={8} className="px-4 py-4 text-center text-sm text-gray-500">
                    加载中...
                  </td>
                </tr>
              ) : userRows.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-4 text-center text-sm text-gray-500">
                    暂无数据
                  </td>
                </tr>
              ) : (
                userRows.map((u) => (
                  <tr key={u.userId}>
                    <td className="px-4 py-2 text-sm text-gray-900">{u.userId}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">{u.email ?? '-'}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">{u.username ?? '-'}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">{u.enabled ? '是' : '否'}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">{u.verifiedAt ?? '-'}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">{u.createdAt ?? '-'}</td>
                    <td className="px-4 py-2 text-sm text-gray-700">
                      {(u.algorithm ?? '-') + ' / ' + String(u.digits ?? '-') + ' / ' + String(u.periodSeconds ?? '-') + ' / skew=' + String(u.skew ?? '-')}
                    </td>
                    <td className="px-4 py-2 text-sm">
                      <Button
                        size="sm"
                        variant="secondary"
                        className="bg-red-100 text-red-700 hover:bg-red-200"
                        onClick={async () => {
                          if (!window.confirm(`确定要重置用户 userId=${u.userId} 的 TOTP 吗？\n\n这会禁用该用户所有 TOTP 记录。`)) return;
                          try {
                            await resetAdminUserTotp(u.userId);
                            await loadUsers();
                          } catch (e) {
                            const msg = e instanceof Error ? e.message : '重置失败';
                            alert(msg);
                          }
                        }}
                      >
                        重置
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between">
          <div className="text-sm text-gray-500">
            第 {pageNum} 页 / 共 {totalPages || 0} 页
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              disabled={usersLoading || pageNum <= 1}
              onClick={() => setPageNum((p) => Math.max(1, p - 1))}
            >
              上一页
            </Button>
            <Button
              variant="outline"
              disabled={usersLoading || (totalPages > 0 && pageNum >= totalPages)}
              onClick={() => setPageNum((p) => p + 1)}
            >
              下一页
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TwoFAForm;
