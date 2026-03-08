import React from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '../ui/button';

export const HELP_FALLBACK_TEXT = '暂无该配置项的详细说明，请参考系统部署文档。';

export const HELP_CONTENT: Record<string, string> = {
  APP_AI_API_KEY:
    'AI 服务的 API 密钥。如果是 阿里云，获取方式详情见：https://bailian.console.aliyun.com/cn-beijing/?tab=api#/api ；如果是其他服务商，请参考对应文档。',
  APP_TOTP_MASTER_KEY:
    '用于生成两步验证码的主密钥。点击“生成”按钮可以自动生成一个安全的随机密钥。如果你有保存之前的主密钥，这里可以输入。可以避免已绑定TOTP的用户需要重新绑定的问题。',
  APP_ES_API_KEY:
    'Elasticsearch 的 API Key。您可以在 Kibana 或使用 Elasticsearch API 生成。获取方式详情见：https://www.elastic.co/docs/deploy-manage/api-keys/elasticsearch-api-keys',
  APP_AI_TOKENIZER_API_KEY:
    '用于计算 Token 数量的 阿里云 API Key,获取方式详情见：https://help.aliyun.com/zh/open-search/search-platform/user-guide/api-keys-management',
  APP_SITE_BEIAN: '站点备案号（ICP 备案号），例如：桂ICP备2026*******号。不填写则登录页底部不展示备案信息。',
  'spring.elasticsearch.uris': 'Elasticsearch 的连接地址，例如 http://localhost:9200。默认端口号为9200',
  APP_MAIL_USERNAME: '认证用户名，通常是您的完整邮箱地址。用于登录，建议和 APP_MAIL_FROM_ADDRESS 保持一致。',
  APP_MAIL_PASSWORD: '邮件服务器的密码或应用专用密码。',
  APP_MAIL_FROM_ADDRESS: '用于发送邮件的邮箱账号。用于展示，建议和 APP_MAIL_USERNAME 保持一致。',
  APP_MAIL_HOST: 'SMTP 发件服务器地址，例如 smtp.qiye.aliyun.com。',
  APP_MAIL_PORT: 'SMTP 发件服务器端口，通常 SSL 为 465（加密）。',
  APP_MAIL_INBOX_HOST: 'IMAP/POP3 收件服务器地址，例如 imap.qiye.aliyun.com。',
  APP_MAIL_INBOX_PORT: 'IMAP/POP3 收件服务器端口，通常 IMAP SSL 为 993（加密）。',
  IMAGE_STORAGE_MODE: '图片存储模式。LOCAL: 使用本地URL（需公网IP）；DASHSCOPE_TEMP: 百炼临时存储（48h有效）；ALIYUN_OSS: 阿里云OSS对象存储。',
  IMAGE_STORAGE_OSS_ENDPOINT: '阿里云 OSS Endpoint，例如 oss-cn-beijing.aliyuncs.com。',
  IMAGE_STORAGE_OSS_BUCKET: '阿里云 OSS Bucket 名称。',
  IMAGE_STORAGE_OSS_ACCESS_KEY_ID: '阿里云 OSS AccessKey ID，用于身份验证。',
  IMAGE_STORAGE_OSS_ACCESS_KEY_SECRET: '阿里云 OSS AccessKey Secret，用于身份验证。请妥善保管。',
  IMAGE_STORAGE_OSS_REGION: '阿里云 OSS Region，例如 cn-beijing。',
};

export const CONFIG_LABELS: Record<string, string> = {
  APP_AI_API_KEY: 'AI API 密钥',
  APP_TOTP_MASTER_KEY: 'TOTP 主密钥',
  APP_ES_API_KEY: 'Elasticsearch API 密钥',
  APP_AI_TOKENIZER_API_KEY: 'Tokenizer API 密钥',
  APP_SITE_BEIAN: '备案号（ICP）',
  'spring.elasticsearch.uris': 'Elasticsearch 连接地址',
  APP_MAIL_USERNAME: '邮箱用户名（建议与邮箱账号一致）',
  APP_MAIL_PASSWORD: '邮箱密码',
  APP_MAIL_FROM_ADDRESS: '邮箱账号',
  APP_MAIL_HOST: '发件服务器地址',
  APP_MAIL_PORT: '发件端口',
  APP_MAIL_INBOX_HOST: '收件服务器地址',
  APP_MAIL_INBOX_PORT: '收件端口',
  IMAGE_STORAGE_MODE: '图片存储模式',
  IMAGE_STORAGE_OSS_ENDPOINT: 'OSS Endpoint',
  IMAGE_STORAGE_OSS_BUCKET: 'OSS Bucket',
  IMAGE_STORAGE_OSS_ACCESS_KEY_ID: 'OSS AccessKey ID',
  IMAGE_STORAGE_OSS_ACCESS_KEY_SECRET: 'OSS AccessKey Secret',
  IMAGE_STORAGE_OSS_REGION: 'OSS Region',
};

export const isSensitiveConfigKey = (key: string) => key.includes('KEY') || key.includes('PASSWORD') || key.includes('SECRET');

export const getConfigLabel = (key: string) => CONFIG_LABELS[key] || key;

export const getHelpText = (key: string) => HELP_CONTENT[key] || HELP_FALLBACK_TEXT;

export const getConfigInputType = (key: string, visible?: boolean) =>
  isSensitiveConfigKey(key) && !visible ? 'password' : 'text';

export const getConfigInputValue = (
  key: string,
  visible: boolean | undefined,
  encryptedValues: Record<string, string>,
  configs: Record<string, string>
) => (visible ? encryptedValues[key] || configs[key] : configs[key]);

export const getReadOnlyInputClass = (visible?: boolean) => (visible ? 'bg-gray-50 text-gray-500' : undefined);

export const VisibilityToggleButton: React.FC<{
  fieldKey: string;
  visible: boolean;
  onToggle: () => void;
  className?: string;
}> = ({ fieldKey, visible, onToggle, className }) => {
  if (!isSensitiveConfigKey(fieldKey)) return null;

  return (
    <Button type="button" variant="ghost" size="icon" onClick={onToggle} className={className}>
      {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
    </Button>
  );
};

