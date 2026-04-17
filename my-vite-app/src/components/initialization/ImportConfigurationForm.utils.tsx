/* eslint-disable react-refresh/only-export-components */
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
  APP_SITE_COPYRIGHT: '站点版权文案，例如：©2026 某某公司 版权所有。不填写则使用系统默认文案。',
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
  'spring.kafka.bootstrap-servers': 'Kafka Broker 地址，多个地址用逗号分隔。默认本地预设为 127.0.0.1:9092（单机开发）；云预设为 your-kafka-broker:9092（托管 Kafka）。',
  'app.logging.access.kafka-topic': 'Access 日志写入的 Kafka Topic，默认 access-logs-v1。通常无需修改，除非你有多环境或多业务隔离需求。',
  'app.logging.access.sink-mode': '日志写入模式：MYSQL（仅数据库）、KAFKA（主写 Kafka）、DUAL（Kafka+MySQL 双写，适合灰度与一致性校验）。默认 KAFKA。',
  'app.logging.access.es-sink.enabled': '是否启用 Access 日志 Kafka -> ES 落库链路。关闭后不会消费 Kafka 写入 ES。',
  'app.logging.access.es-sink.consumer-enabled': '是否自动启动 ES sink 消费者。仅在启用 ES sink 时生效。',
  'app.logging.access.es-sink.index': 'ES 写入索引名，默认 access-logs-v1。',
  'app.logging.access.es-sink.consumer-group': 'Kafka 消费组名称，建议环境内唯一，避免多实例抢占异常。',
  'app.logging.access.es-sink.dual-verify-enabled': 'DUAL 校验开关。开启后消费端会回查 MySQL 是否存在同一事件并记录校验日志。',
  'app.logging.access.es-sink.dual-verify-log-on-success': '是否输出 dual 校验成功日志。排障期可开启，稳定后建议关闭以减少日志噪声。',
  APP_KAFKA_AUTH_ENABLED: '是否启用 Kafka SASL 鉴权。默认开启；若本地 Kafka 未启用 SASL，可暂时留空 APP_KAFKA_API_KEY 和 APP_KAFKA_API_SECRET，后续再补齐。',
  APP_KAFKA_SECURITY_PROTOCOL: 'Kafka 安全协议，常见值：SASL_SSL、SASL_PLAINTEXT。',
  APP_KAFKA_SASL_MECHANISM: 'Kafka SASL 机制，常见值：PLAIN。',
  APP_KAFKA_API_KEY: 'Kafka API Key（或用户名），用于 SASL 鉴权。',
  APP_KAFKA_API_SECRET: 'Kafka API Secret（或密码），用于 SASL 鉴权。',
};

export const CONFIG_LABELS: Record<string, string> = {
  APP_AI_API_KEY: 'AI API 密钥',
  APP_TOTP_MASTER_KEY: 'TOTP 主密钥',
  APP_ES_API_KEY: 'Elasticsearch API 密钥',
  APP_AI_TOKENIZER_API_KEY: 'Tokenizer API 密钥',
  APP_SITE_COPYRIGHT: '版权所有文案',
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
  'spring.kafka.bootstrap-servers': 'Kafka Broker 地址',
  'app.logging.access.kafka-topic': 'Kafka Topic',
  'app.logging.access.sink-mode': '日志写入模式',
  'app.logging.access.es-sink.enabled': 'ES Sink 总开关',
  'app.logging.access.es-sink.consumer-enabled': 'ES Sink 消费者开关',
  'app.logging.access.es-sink.index': 'ES 索引名',
  'app.logging.access.es-sink.consumer-group': 'ES Sink 消费组',
  'app.logging.access.es-sink.dual-verify-enabled': 'DUAL 校验开关',
  'app.logging.access.es-sink.dual-verify-log-on-success': 'DUAL 成功日志开关',
  APP_KAFKA_AUTH_ENABLED: 'Kafka 鉴权开关',
  APP_KAFKA_SECURITY_PROTOCOL: 'Kafka 安全协议',
  APP_KAFKA_SASL_MECHANISM: 'Kafka SASL 机制',
  APP_KAFKA_API_KEY: 'Kafka API Key',
  APP_KAFKA_API_SECRET: 'Kafka API Secret',
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
