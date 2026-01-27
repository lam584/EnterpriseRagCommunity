import React, { useEffect, useMemo, useState } from 'react';
import { Button } from '../../../../components/ui/button';
import { Input } from '../../../../components/ui/input';
import { Checkbox } from '../../../../components/ui/checkbox';
import { Label } from '../../../../components/ui/label';
import {
  getEmailAdminSettings,
  getEmailInboxAdminSettings,
  listEmailInboxMessages,
  listEmailSentMessages,
  sendEmailAdminTest,
  type EmailAdminSettingsDTO,
  type EmailInboxMessageDTO,
  type EmailInboxSettingsDTO,
  updateEmailInboxAdminSettings,
  updateEmailAdminSettings,
} from '../../../../services/emailAdminService';

const ENCRYPTIONS = ['NONE', 'SSL', 'STARTTLS'] as const;
const DEFAULT_SEND_PROTOCOL = 'SMTP';
const DEFAULT_POP3_HOST = 'pop.qiye.aliyun.com';
const DEFAULT_IMAP_HOST = 'imap.qiye.aliyun.com';
const DEFAULT_SMTP_HOST = 'smtp.qiye.aliyun.com';
const MAILBOX_MAX_LIMIT = 50;
const MAILBOX_PAGE_SIZE_OPTIONS = [10, 20, 50] as const;

function normalizeBool(v: boolean | 'indeterminate'): boolean {
  return v === true;
}

function toInt(v: string, fallback: number) {
  const n = Number(v);
  if (!Number.isFinite(n)) return fallback;
  return Math.trunc(n);
}

function formatMailboxDate(v?: number | null): { short: string; full: string } {
  if (v == null || !Number.isFinite(v)) return { short: '', full: '' };
  const d = new Date(v);
  const t = d.getTime();
  if (!Number.isFinite(t)) return { short: '', full: '' };
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const HH = String(d.getHours()).padStart(2, '0');
  const MM = String(d.getMinutes()).padStart(2, '0');
  const SS = String(d.getSeconds()).padStart(2, '0');
  const yyyy = String(d.getFullYear());
  return { short: `${mm}-${dd} ${HH}:${MM}:${SS}`, full: `${yyyy}-${mm}-${dd} ${HH}:${MM}:${SS}` };
}

const EmailConfigForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [editing, setEditing] = useState(false);
  const [settingsSnapshot, setSettingsSnapshot] = useState<EmailAdminSettingsDTO | null>(null);
  const [settings, setSettings] = useState<EmailAdminSettingsDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [testTo, setTestTo] = useState('');
  const [testMsg, setTestMsg] = useState<string | null>(null);
  const [inboxLoading, setInboxLoading] = useState(false);
  const [inboxSaving, setInboxSaving] = useState(false);
  const [inboxListing, setInboxListing] = useState(false);
  const [inboxEditing, setInboxEditing] = useState(false);
  const [inboxConfigSnapshot, setInboxConfigSnapshot] = useState<EmailInboxSettingsDTO | null>(null);
  const [inboxConfig, setInboxConfig] = useState<EmailInboxSettingsDTO | null>(null);
  const [inboxMessages, setInboxMessages] = useState<EmailInboxMessageDTO[]>([]);
  const [inboxSelectedId, setInboxSelectedId] = useState<string | null>(null);
  const [inboxPageNum, setInboxPageNum] = useState(1);
  const [inboxPageSize, setInboxPageSize] = useState<(typeof MAILBOX_PAGE_SIZE_OPTIONS)[number]>(10);
  const [inboxReachedEnd, setInboxReachedEnd] = useState(false);
  const [sentListing, setSentListing] = useState(false);
  const [sentMessages, setSentMessages] = useState<EmailInboxMessageDTO[]>([]);
  const [sentSelectedId, setSentSelectedId] = useState<string | null>(null);
  const [sentPageNum, setSentPageNum] = useState(1);
  const [sentPageSize, setSentPageSize] = useState<(typeof MAILBOX_PAGE_SIZE_OPTIONS)[number]>(10);
  const [sentReachedEnd, setSentReachedEnd] = useState(false);
  const [activeBox, setActiveBox] = useState<'inbox' | 'sent' | null>(null);
  const [inboxError, setInboxError] = useState<string | null>(null);
  const [inboxExpanded, setInboxExpanded] = useState(false);

  const effectivePorts = useMemo(() => {
    const p = String(settings?.protocol ?? DEFAULT_SEND_PROTOCOL).toUpperCase();
    if (p === 'POP3') return { plain: 110, enc: 995 };
    if (p === 'IMAP') return { plain: 143, enc: 993 };
    return { plain: 25, enc: 465 };
  }, [settings?.protocol]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const s = await getEmailAdminSettings();
      const p = String(s.protocol ?? DEFAULT_SEND_PROTOCOL).toUpperCase();
      const defaults =
        p === 'POP3' ? { plain: 110, enc: 995, host: DEFAULT_POP3_HOST } :
        p === 'IMAP' ? { plain: 143, enc: 993, host: DEFAULT_IMAP_HOST } :
        { plain: 25, enc: 465, host: DEFAULT_SMTP_HOST };
      setSettings({
        enabled: s.enabled ?? true,
        protocol: p,
        host: (s.host ?? '').trim() || defaults.host,
        portPlain: Number.isFinite(Number(s.portPlain)) ? Number(s.portPlain) : defaults.plain,
        portEncrypted: Number.isFinite(Number(s.portEncrypted)) ? Number(s.portEncrypted) : defaults.enc,
        encryption: String(s.encryption ?? 'SSL').toUpperCase(),
        connectTimeoutMs: Number.isFinite(Number(s.connectTimeoutMs)) ? Number(s.connectTimeoutMs) : 10000,
        timeoutMs: Number.isFinite(Number(s.timeoutMs)) ? Number(s.timeoutMs) : 10000,
        writeTimeoutMs: Number.isFinite(Number(s.writeTimeoutMs)) ? Number(s.writeTimeoutMs) : 10000,
        debug: Boolean(s.debug),
        sslTrust: s.sslTrust ?? '',
        subjectPrefix: s.subjectPrefix ?? '',
      });
      setEditing(false);
      setSettingsSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const save = async () => {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await updateEmailAdminSettings(settings);
      setSettings(saved);
      setEditing(false);
      setSettingsSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存失败';
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  const runTest = async () => {
    const to = testTo.trim();
    if (!to) {
      setTestMsg('请输入测试收件人邮箱');
      return;
    }
    setTesting(true);
    setTestMsg(null);
    try {
      await sendEmailAdminTest(to);
      setTestMsg('测试邮件已发送，请检查收件箱');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '发送失败';
      setTestMsg(msg);
    } finally {
      setTesting(false);
    }
  };

  const loadInboxConfig = async () => {
    setInboxLoading(true);
    setInboxError(null);
    try {
      const s = await getEmailInboxAdminSettings();
      setInboxConfig({
        protocol: String(s.protocol ?? 'IMAP').toUpperCase(),
        host: (s.host ?? '').trim() || DEFAULT_IMAP_HOST,
        portPlain: Number.isFinite(Number(s.portPlain)) ? Number(s.portPlain) : 143,
        portEncrypted: Number.isFinite(Number(s.portEncrypted)) ? Number(s.portEncrypted) : 993,
        encryption: String(s.encryption ?? 'SSL').toUpperCase(),
        connectTimeoutMs: Number.isFinite(Number(s.connectTimeoutMs)) ? Number(s.connectTimeoutMs) : 10000,
        timeoutMs: Number.isFinite(Number(s.timeoutMs)) ? Number(s.timeoutMs) : 10000,
        writeTimeoutMs: Number.isFinite(Number(s.writeTimeoutMs)) ? Number(s.writeTimeoutMs) : 10000,
        debug: Boolean(s.debug),
        sslTrust: s.sslTrust ?? '',
        folder: (s.folder ?? '').trim() || 'INBOX',
        sentFolder: (s.sentFolder ?? '').trim() || 'Sent',
      });
      setInboxEditing(false);
      setInboxConfigSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载收件配置失败';
      setInboxError(msg);
    } finally {
      setInboxLoading(false);
    }
  };

  const saveInboxConfig = async () => {
    if (!inboxConfig) return;
    setInboxSaving(true);
    setInboxError(null);
    try {
      const saved = await updateEmailInboxAdminSettings(inboxConfig);
      setInboxConfig(saved);
      setInboxEditing(false);
      setInboxConfigSnapshot(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存收件配置失败';
      setInboxError(msg);
    } finally {
      setInboxSaving(false);
    }
  };

  const loadInboxMessages = async (limit: number): Promise<EmailInboxMessageDTO[]> => {
    setInboxListing(true);
    setInboxError(null);
    try {
      const list = await listEmailInboxMessages(limit);
      const arr = Array.isArray(list) ? list : [];
      setInboxMessages(arr);
      setInboxReachedEnd(arr.length < limit);
      setInboxSelectedId(null);
      setSentSelectedId(null);
      setActiveBox(null);
      return arr;
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载收件箱失败';
      setInboxError(msg);
      return [];
    } finally {
      setInboxListing(false);
    }
  };

  const loadSentMessages = async (limit: number): Promise<EmailInboxMessageDTO[]> => {
    setSentListing(true);
    setInboxError(null);
    try {
      const list = await listEmailSentMessages(limit);
      const arr = Array.isArray(list) ? list : [];
      setSentMessages(arr);
      setSentReachedEnd(arr.length < limit);
      setSentSelectedId(null);
      setInboxSelectedId(null);
      setActiveBox(null);
      return arr;
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载发件箱失败';
      setInboxError(msg);
      return [];
    } finally {
      setSentListing(false);
    }
  };

  const refreshMailboxMessages = () => {
    setSentReachedEnd(false);
    setInboxReachedEnd(false);
    void loadSentMessages(Math.min(MAILBOX_MAX_LIMIT, sentPageNum * sentPageSize));
    void loadInboxMessages(Math.min(MAILBOX_MAX_LIMIT, inboxPageNum * inboxPageSize));
  };

  useEffect(() => {
    void load();
    void loadInboxConfig();
    void loadInboxMessages(Math.min(MAILBOX_MAX_LIMIT, inboxPageSize));
    void loadSentMessages(Math.min(MAILBOX_MAX_LIMIT, sentPageSize));
  }, []);

  const inboxPageStart = (inboxPageNum - 1) * inboxPageSize;
  const inboxPageItems = inboxMessages.slice(inboxPageStart, inboxPageStart + inboxPageSize);
  const inboxLastLoadedPageNum = Math.max(1, Math.ceil(inboxMessages.length / inboxPageSize));
  const inboxAtLoadedEnd = inboxReachedEnd && inboxPageNum >= inboxLastLoadedPageNum;
  const sentPageStart = (sentPageNum - 1) * sentPageSize;
  const sentPageItems = sentMessages.slice(sentPageStart, sentPageStart + sentPageSize);
  const sentLastLoadedPageNum = Math.max(1, Math.ceil(sentMessages.length / sentPageSize));
  const sentAtLoadedEnd = sentReachedEnd && sentPageNum >= sentLastLoadedPageNum;
  const selectedMsg = useMemo(() => {
    if (activeBox === 'sent') return sentMessages.find(m => m.id === sentSelectedId) ?? null;
    if (activeBox === 'inbox') return inboxMessages.find(m => m.id === inboxSelectedId) ?? null;
    return null;
  }, [activeBox, inboxMessages, inboxSelectedId, sentMessages, sentSelectedId]);

  return (
    <div className="bg-white rounded-lg shadow p-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-base font-semibold">邮箱服务器配置（用于验证码）</h3>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={load} disabled={loading || saving}>
            刷新
          </Button>
          {editing ? (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  if (settingsSnapshot) setSettings(settingsSnapshot);
                  setEditing(false);
                  setSettingsSnapshot(null);
                  setError(null);
                  setTestMsg(null);
                }}
                disabled={loading || saving}
              >
                取消
              </Button>
              <Button size="sm" onClick={save} disabled={loading || saving || !settings}>
                {saving ? '保存中...' : '保存'}
              </Button>
            </>
          ) : (
            <Button
              size="sm"
              onClick={() => {
                if (!settings) return;
                setSettingsSnapshot(settings);
                setEditing(true);
                setError(null);
                setTestMsg(null);
              }}
              disabled={loading || saving || !settings}
            >
              编辑
            </Button>
          )}
        </div>
      </div>

      {error ? (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-1 text-sm text-red-700">{error}</div>
      ) : null}

      {!settings ? (
        <div className="text-sm text-gray-500">{loading ? '加载中...' : '暂无配置'}</div>
      ) : (
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Checkbox
              id="email-enabled"
              checked={Boolean(settings.enabled)}
              disabled={!editing}
              onCheckedChange={(v) => setSettings(s => (s ? { ...s, enabled: normalizeBool(v) } : s))}
            />
            <label htmlFor="email-enabled" className="text-sm cursor-pointer select-none">
              启用邮箱验证码（注册/改密/TOTP 启用时需要）
            </label>
          </div>

          <div className="grid grid-cols-2 gap-2 md:grid-cols-12">
            <div className="space-y-0.5 md:col-span-1">
              <Label className="text-xs">协议</Label>
              <Input className="h-9 text-sm" value="SMTP" disabled />
            </div>

            <div className="col-span-2 space-y-0.5 md:col-span-5">
              <Label className="text-xs">服务器地址</Label>
              <Input
                className="h-9 text-sm"
                value={settings.host ?? ''}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, host: e.target.value } : s))}
                placeholder="例如：smtp.qiye.aliyun.com"
              />
            </div>

            <div className="space-y-0.5 md:col-span-1">
              <Label className="text-xs">加密方式</Label>
              <select
                className="border border-black rounded px-2 py-0 bg-white w-full text-sm h-9"
                value={String(settings.encryption ?? 'SSL').toUpperCase()}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, encryption: e.target.value.toUpperCase() } : s))}
              >
                {ENCRYPTIONS.map(v => (
                  <option key={v} value={v}>
                    {v}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-0.5 md:col-span-1">
              <Label className="text-xs">端口(常规)</Label>
              <Input
                className="h-9 text-sm"
                inputMode="numeric"
                value={String(settings.portPlain ?? effectivePorts.plain)}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, portPlain: toInt(e.target.value, effectivePorts.plain) } : s))}
              />
            </div>
            <div className="space-y-0.5 md:col-span-1">
              <Label className="text-xs">端口(加密)</Label>
              <Input
                className="h-9 text-sm"
                inputMode="numeric"
                value={String(settings.portEncrypted ?? effectivePorts.enc)}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, portEncrypted: toInt(e.target.value, effectivePorts.enc) } : s))}
              />
            </div>
            <div className="col-span-2 space-y-0.5 md:col-span-3">
              <Label className="text-xs">SSL Trust</Label>
              <Input
                className="h-9 text-sm"
                value={settings.sslTrust ?? ''}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, sslTrust: e.target.value } : s))}
                placeholder="例如：smtp.qiye.aliyun.com 或 *"
              />
            </div>

            <div className="col-span-2 space-y-0.5 md:col-span-4">
              <Label className="text-xs">主题前缀</Label>
              <Input
                className="h-9 text-sm"
                value={settings.subjectPrefix ?? ''}
                disabled={!editing}
                onChange={(e) => setSettings(s => (s ? { ...s, subjectPrefix: e.target.value } : s))}
                placeholder="例如：[EnterpriseRagCommunity]"
              />
            </div>

            <div className="col-span-2 md:col-span-4 grid grid-cols-3 gap-2">
              <div className="space-y-0.5">
                <Label className="text-xs" title="连接超时(ms)">连接超时</Label>
                <Input
                  className="h-9 text-sm"
                  inputMode="numeric"
                  value={String(settings.connectTimeoutMs ?? 10000)}
                  disabled={!editing}
                  onChange={(e) => setSettings(s => (s ? { ...s, connectTimeoutMs: toInt(e.target.value, 10000) } : s))}
                />
              </div>
              <div className="space-y-0.5">
                <Label className="text-xs" title="读取超时(ms)">读取超时</Label>
                <Input
                  className="h-9 text-sm"
                  inputMode="numeric"
                  value={String(settings.timeoutMs ?? 10000)}
                  disabled={!editing}
                  onChange={(e) => setSettings(s => (s ? { ...s, timeoutMs: toInt(e.target.value, 10000) } : s))}
                />
              </div>
              <div className="space-y-0.5">
                <Label className="text-xs" title="写入超时(ms)">写入超时</Label>
                <Input
                  className="h-9 text-sm"
                  inputMode="numeric"
                  value={String(settings.writeTimeoutMs ?? 10000)}
                  disabled={!editing}
                  onChange={(e) => setSettings(s => (s ? { ...s, writeTimeoutMs: toInt(e.target.value, 10000) } : s))}
                />
              </div>
            </div>

            <div className="col-span-2 md:col-span-4 space-y-0.5">
              <Label className="text-xs">测试收件人</Label>
              <div className="flex gap-2">
                <Input
                  className="h-9 text-sm flex-1 min-w-0"
                  value={testTo}
                  onChange={(e) => setTestTo(e.target.value)}
                  placeholder="name@example.com"
                />
                <Button size="sm" onClick={runTest} disabled={testing || loading || saving} className="px-3 shrink-0">
                  {testing ? '...' : '发送'}
                </Button>
              </div>
            </div>
          </div>
          {testMsg ? <div className="text-xs text-gray-500 mt-1">{testMsg}</div> : null}

          <div className="rounded border p-2 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="font-medium text-sm">邮件收件箱</div>
              <div className="flex gap-2">
                
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={refreshMailboxMessages}
                  disabled={inboxListing || sentListing || inboxLoading || inboxSaving || !inboxConfig}
                >
                  刷新列表
                </Button>
                
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    if (inboxExpanded && inboxEditing) {
                      if (inboxConfigSnapshot) setInboxConfig(inboxConfigSnapshot);
                      setInboxEditing(false);
                      setInboxConfigSnapshot(null);
                      setInboxError(null);
                    }
                    setInboxExpanded(v => !v);
                  }}
                  disabled={inboxSaving}
                >
                  {inboxExpanded ? '收起配置' : '展开配置'}
                </Button>
                {inboxEditing ? (
                  <>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        if (inboxConfigSnapshot) setInboxConfig(inboxConfigSnapshot);
                        setInboxEditing(false);
                        setInboxConfigSnapshot(null);
                        setInboxError(null);
                      }}
                      disabled={inboxLoading || inboxSaving}
                    >
                      取消
                    </Button>
                    <Button size="sm" onClick={saveInboxConfig} disabled={inboxLoading || inboxSaving || !inboxConfig}>
                      {inboxSaving ? '保存中...' : '保存配置'}
                    </Button>
                  </>
                ) : (
                  <Button
                    size="sm"
                    onClick={() => {
                      if (!inboxConfig) return;
                      setInboxConfigSnapshot(inboxConfig);
                      setInboxEditing(true);
                      setInboxExpanded(true);
                      setInboxError(null);
                    }}
                    disabled={inboxLoading || inboxSaving || !inboxConfig}
                  >
                    编辑配置
                  </Button>
                )}
              </div>
            </div>

            {inboxError ? (
              <div className="rounded border border-red-200 bg-red-50 px-3 py-1 text-sm text-red-700">{inboxError}</div>
            ) : null}

            {!inboxConfig ? (
              <div className="text-sm text-gray-500">{inboxLoading ? '加载中...' : '暂无收件配置'}</div>
            ) : (
              <div className="space-y-2">
                {inboxExpanded ? (
                  <div className="space-y-2 border-b pb-2">
                    <div className="grid grid-cols-2 gap-2 md:grid-cols-12">
                      <div className="space-y-0.5 md:col-span-1">
                        <Label className="text-xs">协议</Label>
                        <Input className="h-8 text-sm" value="IMAP" disabled />
                      </div>
                      <div className="col-span-2 space-y-0.5 md:col-span-5">
                        <Label className="text-xs">服务器地址</Label>
                        <Input
                          className="h-8 text-sm"
                          value={inboxConfig.host ?? ''}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, host: e.target.value } : s))}
                          placeholder="例如：imap.qiye.aliyun.com"
                        />
                      </div>
                      <div className="space-y-0.5 md:col-span-1">
                        <Label className="text-xs">加密方式</Label>
                        <select
                          className="border border-black rounded px-2 py-0 bg-white w-full text-sm h-8 disabled:opacity-50"
                          value={String(inboxConfig.encryption ?? 'SSL').toUpperCase()}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, encryption: e.target.value.toUpperCase() } : s))}
                        >
                          {ENCRYPTIONS.map(v => (
                            <option key={v} value={v}>
                              {v}
                            </option>
                          ))}
                        </select>
                      </div>

                      <div className="space-y-0.5 md:col-span-1">
                        <Label className="text-xs">端口(常规)</Label>
                        <Input
                          className="h-8 text-sm"
                          inputMode="numeric"
                          value={String(inboxConfig.portPlain ?? 143)}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, portPlain: toInt(e.target.value, 143) } : s))}
                        />
                      </div>
                      <div className="space-y-0.5 md:col-span-1">
                        <Label className="text-xs">端口(加密)</Label>
                        <Input
                          className="h-8 text-sm"
                          inputMode="numeric"
                          value={String(inboxConfig.portEncrypted ?? 993)}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, portEncrypted: toInt(e.target.value, 993) } : s))}
                        />
                      </div>
                      <div className="col-span-2 space-y-0.5 md:col-span-3">
                        <Label className="text-xs">SSL Trust</Label>
                        <Input
                          className="h-8 text-sm"
                          value={inboxConfig.sslTrust ?? ''}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, sslTrust: e.target.value } : s))}
                          placeholder="例如：imap.qiye.aliyun.com 或 *"
                        />
                      </div>

                      <div className="col-span-2 space-y-0.5 md:col-span-3">
                        <Label className="text-xs">收件箱文件夹</Label>
                        <Input
                          className="h-8 text-sm"
                          value={inboxConfig.folder ?? 'INBOX'}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, folder: e.target.value } : s))}
                          placeholder="例如：INBOX"
                        />
                      </div>

                      <div className="col-span-2 space-y-0.5 md:col-span-3">
                        <Label className="text-xs">发件箱文件夹</Label>
                        <Input
                          className="h-8 text-sm"
                          value={inboxConfig.sentFolder ?? 'Sent'}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, sentFolder: e.target.value } : s))}
                          placeholder="例如：Sent"
                        />
                      </div>

                      <div className="space-y-0.5 md:col-span-2">
                        <Label className="text-xs" title="连接超时(ms)">连接超时</Label>
                        <Input
                          className="h-8 text-sm"
                          inputMode="numeric"
                          value={String(inboxConfig.connectTimeoutMs ?? 10000)}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, connectTimeoutMs: toInt(e.target.value, 10000) } : s))}
                        />
                      </div>
                      <div className="space-y-0.5 md:col-span-2">
                        <Label className="text-xs" title="读取超时(ms)">读取超时</Label>
                        <Input
                          className="h-8 text-sm"
                          inputMode="numeric"
                          value={String(inboxConfig.timeoutMs ?? 10000)}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, timeoutMs: toInt(e.target.value, 10000) } : s))}
                        />
                      </div>
                      <div className="space-y-0.5 md:col-span-2">
                        <Label className="text-xs" title="写入超时(ms)">写入超时</Label>
                        <Input
                          className="h-8 text-sm"
                          inputMode="numeric"
                          value={String(inboxConfig.writeTimeoutMs ?? 10000)}
                          disabled={!inboxEditing}
                          onChange={(e) => setInboxConfig(s => (s ? { ...s, writeTimeoutMs: toInt(e.target.value, 10000) } : s))}
                        />
                      </div>
                    </div>
                  </div>
                ) : null}
              </div>
            )}
          </div>

          {inboxConfig ? (
            <div className="grid grid-cols-1 gap-2 md:grid-cols-7">
              <div className="md:col-span-2 rounded border bg-white">
                <div className="px-3 py-1.5 border-b text-sm text-gray-600 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="shrink-0">已发送</span>
                    {sentPageNum > 1 ? (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        disabled={sentListing}
                        onClick={() => {
                          setSentPageNum(p => Math.max(1, p - 1));
                          setSentSelectedId(null);
                          setActiveBox(null);
                        }}
                      >
                        上一页
                      </Button>
                    ) : null}
                    {sentPageNum * sentPageSize >= MAILBOX_MAX_LIMIT || sentAtLoadedEnd ? null : (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        disabled={sentListing}
                        onClick={async () => {
                          const next = sentPageNum + 1;
                          const limit = Math.min(MAILBOX_MAX_LIMIT, next * sentPageSize);
                          const list = await loadSentMessages(limit);
                          if (list.length > (next - 1) * sentPageSize) {
                            setSentReachedEnd(false);
                            setSentPageNum(next);
                          } else {
                            setSentReachedEnd(true);
                          }
                        }}
                      >
                        下一页
                      </Button>
                    )}
                    <select
                      className="border border-gray-300 rounded px-2 py-0 bg-white text-xs h-7"
                      value={String(sentPageSize)}
                      disabled={sentListing}
                      onChange={(e) => {
                        const nextSize = Number(e.target.value) as (typeof MAILBOX_PAGE_SIZE_OPTIONS)[number];
                        setSentPageSize(nextSize);
                        setSentPageNum(1);
                        setSentReachedEnd(false);
                        setSentSelectedId(null);
                        setActiveBox(null);
                        void loadSentMessages(Math.min(MAILBOX_MAX_LIMIT, nextSize));
                      }}
                    >
                      {MAILBOX_PAGE_SIZE_OPTIONS.map(v => (
                        <option key={v} value={String(v)}>
                          {v}/页
                        </option>
                      ))}
                    </select>
                  </div>
                  <span className="text-xs text-gray-400">{sentListing ? '刷新中...' : `${sentMessages.length} 封`}</span>
                </div>
                <div className="max-h-60 overflow-auto">
                  {sentPageItems.length === 0 ? (
                    <div className="px-3 py-2 text-sm text-gray-500">{sentListing ? '加载中...' : '暂无邮件'}</div>
                  ) : (
                    <ul className="divide-y">
                      {sentPageItems.map((m) => {
                        const active = activeBox === 'sent' && sentSelectedId === m.id;
                        const sentAt = formatMailboxDate(m.sentAt);
                        return (
                          <li
                            key={m.id}
                            className={`px-3 py-1 text-sm cursor-pointer ${active ? 'bg-gray-50' : 'bg-white'}`}
                            onClick={() => {
                              setActiveBox('sent');
                              setSentSelectedId(m.id);
                              setInboxSelectedId(null);
                            }}
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="font-medium text-gray-900 truncate text-sm flex-1 min-w-0">
                                {m.subject || '(无主题)'}
                              </div>
                              <div className="text-xs text-gray-400 shrink-0" title={sentAt.full}>
                                {sentAt.short}
                              </div>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </div>

              <div className="md:col-span-2 rounded border bg-white">
                <div className="px-3 py-1.5 border-b text-sm text-gray-600 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="shrink-0">收件箱</span>
                    {inboxPageNum > 1 ? (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        disabled={inboxListing}
                        onClick={() => {
                          setInboxPageNum(p => Math.max(1, p - 1));
                          setInboxSelectedId(null);
                          setActiveBox(null);
                        }}
                      >
                        上一页
                      </Button>
                    ) : null}
                    {inboxPageNum * inboxPageSize >= MAILBOX_MAX_LIMIT || inboxAtLoadedEnd ? null : (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        disabled={inboxListing}
                        onClick={async () => {
                          const next = inboxPageNum + 1;
                          const limit = Math.min(MAILBOX_MAX_LIMIT, next * inboxPageSize);
                          const list = await loadInboxMessages(limit);
                          if (list.length > (next - 1) * inboxPageSize) {
                            setInboxReachedEnd(false);
                            setInboxPageNum(next);
                          } else {
                            setInboxReachedEnd(true);
                          }
                        }}
                      >
                        下一页
                      </Button>
                    )}
                    <select
                      className="border border-gray-300 rounded px-2 py-0 bg-white text-xs h-7"
                      value={String(inboxPageSize)}
                      disabled={inboxListing}
                      onChange={(e) => {
                        const nextSize = Number(e.target.value) as (typeof MAILBOX_PAGE_SIZE_OPTIONS)[number];
                        setInboxPageSize(nextSize);
                        setInboxPageNum(1);
                        setInboxReachedEnd(false);
                        setInboxSelectedId(null);
                        setActiveBox(null);
                        void loadInboxMessages(Math.min(MAILBOX_MAX_LIMIT, nextSize));
                      }}
                    >
                      {MAILBOX_PAGE_SIZE_OPTIONS.map(v => (
                        <option key={v} value={String(v)}>
                          {v}/页
                        </option>
                      ))}
                    </select>
                  </div>
                  <span className="text-xs text-gray-400">{inboxListing ? '刷新中...' : `${inboxMessages.length} 封`}</span>
                </div>
                <div className="max-h-60 overflow-auto">
                  {inboxPageItems.length === 0 ? (
                    <div className="px-3 py-2 text-sm text-gray-500">{inboxListing ? '加载中...' : '暂无邮件'}</div>
                  ) : (
                    <ul className="divide-y">
                      {inboxPageItems.map((m) => {
                        const active = activeBox === 'inbox' && inboxSelectedId === m.id;
                        const sentAt = formatMailboxDate(m.sentAt);
                        return (
                          <li
                            key={m.id}
                            className={`px-3 py-1 text-sm cursor-pointer ${active ? 'bg-gray-50' : 'bg-white'}`}
                            onClick={() => {
                              setActiveBox('inbox');
                              setInboxSelectedId(m.id);
                              setSentSelectedId(null);
                            }}
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="font-medium text-gray-900 truncate text-sm flex-1 min-w-0">
                                {m.subject || '(无主题)'}
                              </div>
                              <div className="text-xs text-gray-400 shrink-0" title={sentAt.full}>
                                {sentAt.short}
                              </div>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </div>

              <div className="md:col-span-3 rounded border bg-white">
                <div className="px-3 py-1.5 border-b text-sm text-gray-600 flex items-center justify-between gap-2">
                  <span className="shrink-0">邮件内容</span>
                  {selectedMsg ? (
                    <div className="flex-1 min-w-0 flex items-center justify-end gap-3 text-xs text-gray-500">
                      <span className="max-w-64 truncate" title={selectedMsg.from ?? ''}>
                        发件人：{(selectedMsg.from ?? '').trim() || '-'}
                      </span>
                      <span className="max-w-64 truncate" title={selectedMsg.to ?? ''}>
                        收件人：{(selectedMsg.to ?? '').trim() || '-'}
                      </span>
                    </div>
                  ) : null}
                </div>
                <div className="p-2 max-h-60 overflow-auto">
                  {!selectedMsg ? (
                    <div className="text-sm text-gray-500">请选择一封邮件查看内容</div>
                  ) : (
                    <pre className="whitespace-pre-wrap break-words text-sm text-gray-800">
                      {(selectedMsg.content ?? '').trim() || '(无正文内容)'}
                    </pre>
                  )}
                </div>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
};

export default EmailConfigForm;
