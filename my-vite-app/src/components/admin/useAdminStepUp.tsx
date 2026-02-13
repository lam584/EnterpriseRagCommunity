import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Modal from '../common/Modal';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { sendAccountEmailVerificationCode } from '../../services/emailVerificationService';
import { getAdminStepUpStatus, verifyAdminStepUp } from '../../services/adminStepUpService';

type EnsureResult = { ensured: true } | { ensured: false };

export function useAdminStepUp() {
  const [open, setOpen] = useState(false);
  const [methods, setMethods] = useState<string[]>([]);
  const [method, setMethod] = useState<'email' | 'totp'>('totp');
  const [code, setCode] = useState('');
  const [sending, setSending] = useState(false);
  const [emailCountdown, setEmailCountdown] = useState(0);
  const [emailCodeTtlSeconds, setEmailCodeTtlSeconds] = useState<number | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const resolveRef = useRef<((r: EnsureResult) => void) | null>(null);

  const available = useMemo(() => new Set(methods.map((m) => String(m).toLowerCase())), [methods]);

  useEffect(() => {
    if (emailCountdown <= 0) return;
    const t = window.setInterval(() => setEmailCountdown((v) => Math.max(0, v - 1)), 1000);
    return () => window.clearInterval(t);
  }, [emailCountdown]);

  const close = useCallback((result: EnsureResult) => {
    setOpen(false);
    setError(null);
    setCode('');
    const r = resolveRef.current;
    resolveRef.current = null;
    if (r) r(result);
  }, []);

  const ensure = useCallback(async (): Promise<EnsureResult> => {
    const status = await getAdminStepUpStatus();
    if (status.ok) return { ensured: true };

    const nextMethods = Array.isArray(status.methods) ? status.methods : [];
    setMethods(nextMethods);
    const hasTotp = nextMethods.map((m) => String(m).toLowerCase()).includes('totp');
    const hasEmail = nextMethods.map((m) => String(m).toLowerCase()).includes('email');
    setMethod(hasTotp ? 'totp' : hasEmail ? 'email' : 'totp');

    setOpen(true);
    setError(null);
    setCode('');
    return new Promise<EnsureResult>((resolve) => {
      resolveRef.current = resolve;
    });
  }, []);

  const sendEmail = useCallback(async () => {
    if (emailCountdown > 0) return;
    setSending(true);
    setError(null);
    setEmailCodeTtlSeconds(null);
    try {
      const resp = await sendAccountEmailVerificationCode('ADMIN_STEP_UP');
      const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
      setEmailCountdown(wait);
      const ttl = Number.isFinite(Number(resp?.codeTtlSeconds)) ? Number(resp.codeTtlSeconds) : null;
      setEmailCodeTtlSeconds(ttl && ttl > 0 ? ttl : null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '发送验证码失败';
      setError(msg);
    } finally {
      setSending(false);
    }
  }, [emailCountdown]);

  const verify = useCallback(async () => {
    setVerifying(true);
    setError(null);
    try {
      const c = code.trim();
      if (!c) {
        setError('请输入验证码');
        return;
      }
      await verifyAdminStepUp({ method, code: c });
      close({ ensured: true });
    } catch (e) {
      const msg = e instanceof Error ? e.message : '验证失败';
      setError(msg);
    } finally {
      setVerifying(false);
    }
  }, [close, code, method]);

  const modal = (
    <Modal
      isOpen={open}
      onClose={() => close({ ensured: false })}
      title="高权限操作需要二次验证"
      showFooterClose={false}
    >
      <div className="space-y-3">
        {available.size === 0 ? (
          <div className="text-sm text-red-600">
            当前账号没有可用的二次验证方式（TOTP 未启用且邮箱验证码被禁止）。请先在账号安全里启用 TOTP，或联系管理员调整策略。
          </div>
        ) : null}

        <div className="space-y-2">
          <div className="text-sm text-gray-600">选择验证方式</div>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant={method === 'totp' ? 'default' : 'outline'}
              disabled={!available.has('totp')}
              onClick={() => setMethod('totp')}
            >
              TOTP
            </Button>
            <Button
              type="button"
              variant={method === 'email' ? 'default' : 'outline'}
              disabled={!available.has('email')}
              onClick={() => setMethod('email')}
            >
              邮箱验证码
            </Button>
          </div>
        </div>

        {method === 'email' ? (
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Button
                type="button"
                size="sm"
                variant="secondary"
                className="min-w-28"
                disabled={sending || verifying || emailCountdown > 0}
                onClick={sendEmail}
              >
                {sending ? '发送中...' : emailCountdown > 0 ? `重发（${emailCountdown}s）` : '发送验证码'}
              </Button>
              <div className="text-xs text-gray-500">
                {emailCountdown > 0 ? `请在 ${emailCountdown}s 后重发` : '验证码将发送到当前登录邮箱'}
              </div>
            </div>
            {emailCodeTtlSeconds ? (
              <div className="text-xs text-gray-500">验证码有效期约 {Math.max(1, Math.ceil(emailCodeTtlSeconds / 60))} 分钟</div>
            ) : null}
          </div>
        ) : null}

        <div className="space-y-1">
          <div className="text-sm text-gray-600">验证码</div>
          <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="输入验证码" />
        </div>

        {error ? <div className="text-sm text-red-600">{error}</div> : null}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" disabled={verifying} onClick={() => close({ ensured: false })}>
            取消
          </Button>
          <Button type="button" disabled={verifying || available.size === 0} onClick={verify}>
            确认
          </Button>
        </div>
      </div>
    </Modal>
  );

  return { ensureAdminStepUp: ensure, adminStepUpModal: modal };
}
