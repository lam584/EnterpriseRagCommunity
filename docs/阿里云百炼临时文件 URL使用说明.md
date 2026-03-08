# 阿里云百炼临时文件 URL（oss://）使用说明（整理版）

在调用多模态、图像、视频或音频模型时，通常需要传入文件的 URL。阿里云百炼提供免费临时存储空间：可将本地文件上传后获得以 `oss://` 为前缀的临时 URL（有效期 48 小时）。

---

## 使用限制

- **文件与模型绑定**：上传时必须指定模型名称，且后续调用模型必须与上传时一致；不同模型之间无法共享文件。不同模型对文件大小限制不同，超限会上传失败。
- **文件与主账号绑定**：上传与模型调用使用的 API Key 必须属于同一个阿里云主账号；上传文件仅供该主账号及对应模型使用，无法跨主账号/跨模型共享。
- **文件有效期限制**：上传后有效期 **48 小时**；超时会自动清理，请在有效期内完成模型调用。
- **文件使用限制**：文件上传后**不可查询、修改或下载**，仅能在模型调用时通过 URL 参数使用。
- **文件上传限流**：文件上传凭证接口按“**阿里云主账号 + 模型**”维度限流 **100 QPS**，超出将失败。

---

## 重要提示 

- 临时 URL 有效期 48 小时，过期后无法使用，**请勿用于生产环境**。
- 文件上传凭证接口限流 100 QPS 且不支持扩容，**请勿用于生产环境、高并发及压测**。
- 生产环境建议使用 **阿里云 OSS** 等稳定存储，确保文件长期可用并规避限流问题。

---

## 使用方式总览

1. **获取文件 URL**：先执行“步骤一”上传文件（图片/视频/音频），得到 `oss://...` 临时 URL。
2. **调用模型**：再执行“步骤二”使用该临时 URL 调用模型。**不能跳过**，否则会报错。

---

## 步骤一：获取临时 URL

### 方式一：通过代码上传文件

本文提供 Python/Java 示例，指定模型与本地文件即可获取临时 URL。

#### 前提条件

- 获取 API Key，并将 API Key 配置到环境变量。

#### Python 示例

**环境要求**

- 推荐 Python 3.8+
- 安装依赖：

```bash
pip install -U requests
```

**输入参数**

- `api_key`：阿里云百炼 API Key
- `model_name`：文件将用于哪个模型（如 `qwen-vl-plus`）
- `file_path`：待上传的本地文件路径（图片、视频等）

**示例代码**

```python
import os
import requests
from pathlib import Path
from datetime import datetime, timedelta

def get_upload_policy(api_key, model_name):
    """获取文件上传凭证"""
    url = "https://dashscope.aliyuncs.com/api/v1/uploads"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    params = {
        "action": "getPolicy",
        "model": model_name
    }

    response = requests.get(url, headers=headers, params=params)
    if response.status_code != 200:
        raise Exception(f"Failed to get upload policy: {response.text}")

    return response.json()['data']

def upload_file_to_oss(policy_data, file_path):
    """将文件上传到临时存储OSS"""
    file_name = Path(file_path).name
    key = f"{policy_data['upload_dir']}/{file_name}"

    with open(file_path, 'rb') as file:
        files = {
            'OSSAccessKeyId': (None, policy_data['oss_access_key_id']),
            'Signature': (None, policy_data['signature']),
            'policy': (None, policy_data['policy']),
            'x-oss-object-acl': (None, policy_data['x_oss_object_acl']),
            'x-oss-forbid-overwrite': (None, policy_data['x_oss_forbid_overwrite']),
            'key': (None, key),
            'success_action_status': (None, '200'),
            'file': (file_name, file)
        }

        response = requests.post(policy_data['upload_host'], files=files)
        if response.status_code != 200:
            raise Exception(f"Failed to upload file: {response.text}")

    return f"oss://{key}"

def upload_file_and_get_url(api_key, model_name, file_path):
    """上传文件并获取URL"""
    # 1. 获取上传凭证（该接口有限流）
    policy_data = get_upload_policy(api_key, model_name)
    # 2. 上传文件到OSS
    oss_url = upload_file_to_oss(policy_data, file_path)
    return oss_url

if __name__ == "__main__":
    api_key = os.getenv("DASHSCOPE_API_KEY")
    if not api_key:
        raise Exception("请设置DASHSCOPE_API_KEY环境变量")

    model_name="qwen-vl-plus"
    file_path = "/tmp/cat.png"  # 替换为实际文件路径

    try:
        public_url = upload_file_and_get_url(api_key, model_name, file_path)
        expire_time = datetime.now() + timedelta(hours=48)
        print(f"文件上传成功，有效期为48小时，过期时间: {expire_time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"临时URL: {public_url}")
        print("注意：使用oss://形式的临时URL时，必须在HTTP请求头（Header）中显式添加参数：X-DashScope-OssResourceResolve: enable")
    except Exception as e:
        print(f"Error: {str(e)}")
```

**输出示例**

```text
文件上传成功，有效期为48小时，过期时间: 2024-07-18 17:36:15
临时URL: oss://dashscope-instant/xxx/2024-07-18/xxx/cat.png
注意：使用oss://形式的临时URL时，必须在HTTP请求头（Header）中显式添加参数：X-DashScope-OssResourceResolve: enable
```

> 重要：获取临时 URL 后，若通过 HTTP 方式调用模型，必须在 Header 中显式添加：`X-DashScope-OssResourceResolve: enable`。

---

### 方式二：通过命令行工具上传文件

- 原文包含“命令行工具上传”入口说明；核心流程与接口一致：获取凭证 → 表单上传 → 拼接 `oss://` URL。

---

## 步骤二：使用临时 URL 调用模型

### 使用限制（调用侧）

- **文件格式**：临时 URL 必须通过上述方式生成，且以 `oss://` 为前缀。
- **文件未过期**：必须在上传后 48 小时有效期内。
- **模型一致**：调用模型必须与上传时指定模型完全一致。
- **账号一致**：模型调用 API Key 必须与上传 API Key 同属一个阿里云主账号。

---

### 方式一：通过 HTTP 调用（curl/Postman/任意 HTTP 客户端）

**必须遵循的规则**

- 必须在请求 Header 中添加：`X-DashScope-OssResourceResolve: enable`
- 若缺失该 Header，服务端无法解析 `oss://` 链接，请求会失败（错误码见下文）。

**请求示例：qwen-vl-plus 图片识别**

> 将 `oss://...` 替换为真实临时 URL，否则会失败。

```bash
curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-H "X-DashScope-OssResourceResolve: enable" \
-d '{
  "model": "qwen-vl-plus",
  "messages": [{
    "role": "user",
    "content": [
      {"type": "text", "text": "这是什么"},
      {"type": "image_url", "image_url": {"url": "oss://dashscope-instant/xxx/2024-07-18/xxxx/cat.png"}}
    ]
  }]
}'
```

---

### 方式二：通过 DashScope SDK 调用

- **直接传入 URL**：在 SDK 调用参数中直接传入 `oss://...` 字符串作为文件参数。
- **无需关心 Header**：SDK 会自动添加必要请求头。
- 注意：并非所有模型都支持 SDK 调用；以模型 API 文档为准。
- 不支持 OpenAI SDK。

**Python 示例（qwen-vl / omni 系列适用）**

```python
import os
import dashscope

messages = [
    {
        "role": "system",
        "content": [{"text": "You are a helpful assistant."}]
    },
    {
        "role": "user",
        "content": [
            {"image": "oss://dashscope-instant/xxx/2024-07-18/xxxx/cat.png"},
            {"text": "这是什么"}
        ]
    }
]

api_key = os.getenv('DASHSCOPE_API_KEY')

response = dashscope.MultiModalConversation.call(
    api_key=api_key,
    model='qwen-vl-plus',
    messages=messages
)

print(response)
```

---

## 附：接口说明（上传流程拆解）

> 代码调用与命令行工具上传方式都集成了以下三步：  
> 1) 获取上传凭证 → 2) 上传文件 → 3) 生成文件 URL（`oss://` + key）

---

### 步骤 1：获取文件上传凭证

**请求接口**

- `GET https://dashscope.aliyuncs.com/api/v1/uploads`

**重要说明**

- 凭证接口限流：按“主账号 + 模型”维度 **100 QPS**；临时存储不可扩容。生产/高并发请改用 OSS 等自有存储。

**请求参数**

| 传参位置 | 字段 | 类型 | 必选 | 描述 | 示例 |
|---|---|---|---|---|---|
| Header | Content-Type | string | 是 | `application/json` | application/json |
| Header | Authorization | string | 是 | `Bearer {API_KEY}` | Bearer sk-xxx |
| Params | action | string | 是 | 操作类型：`getPolicy` | getPolicy |
| Params | model | string | 是 | 需要调用的模型名称 | qwen-vl-plus |

**响应参数**

| 字段 | 类型 | 描述 | 示例 |
|---|---|---|---|
| request_id | string | 本次请求系统唯一码 | 7574ee8f-... |
| data | object | 上传凭证数据 | - |
| data.policy | string | 上传凭证 | eyJl... |
| data.signature | string | 凭证签名 | g5K... |
| data.upload_dir | string | 上传目录 | dashscope-instant/... |
| data.upload_host | string | 上传 host | https://dashscope-file-xxx... |
| data.expire_in_seconds | string | 凭证有效期（秒） | 300 |
| data.max_file_size_mb | string | 单次最大文件（MB） | 100 |
| data.capacity_limit_mb | string | 每日容量限制（MB） | 999999999 |
| data.oss_access_key_id | string | 上传用 AK | LTAxxx |
| data.x_oss_object_acl | string | 权限（private） | private |
| data.x_oss_forbid_overwrite | string | 禁止覆盖（true） | true |

**请求示例**

```bash
curl --location 'https://dashscope.aliyuncs.com/api/v1/uploads?action=getPolicy&model=qwen-vl-plus' \
--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
--header 'Content-Type: application/json'
```

**响应示例**

```json
{
  "request_id": "52f4383a-c67d-9f8c-xxxxxx",
  "data": {
    "policy": "eyJl...1ZSJ=",
    "signature": "eWy...=",
    "upload_dir": "dashscope-instant/xxx/2024-07-18/xxx",
    "upload_host": "https://dashscope-file-xxx.oss-cn-beijing.aliyuncs.com",
    "expire_in_seconds": 300,
    "max_file_size_mb": 100,
    "capacity_limit_mb": 999999999,
    "oss_access_key_id": "LTA...",
    "x_oss_object_acl": "private",
    "x_oss_forbid_overwrite": "true"
  }
}
```

---

### 步骤 2：上传文件至临时存储空间

**前提条件**

- 已获取上传凭证，且凭证在有效期内（见 `data.expire_in_seconds`）

**请求接口**

- `POST {data.upload_host}`（将 `{data.upload_host}` 替换为上一步返回值）

**表单字段（multipart/form-data）**

| 传参方式 | 字段 | 类型 | 必选 | 描述 |
|---|---|---|---|---|
| form-data | OSSAccessKeyId | text | 是 | `data.oss_access_key_id` |
| form-data | policy | text | 是 | `data.policy` |
| form-data | Signature | text | 是 | `data.signature` |
| form-data | key | text | 是 | `data.upload_dir + "/" + 文件名` |
| form-data | x-oss-object-acl | text | 是 | `data.x_oss_object_acl` |
| form-data | x-oss-forbid-overwrite | text | 是 | `data.x_oss_forbid_overwrite` |
| form-data | success_action_status | text | 否 | 通常为 `200` |
| form-data | file | file/text | 是 | 文件内容（一次仅一个文件，且必须最后一个字段） |

**请求示例**

```bash
curl --location 'https://dashscope-file-xxx.oss-cn-beijing.aliyuncs.com' \
--form 'OSSAccessKeyId="LTAm5xxx"' \
--form 'Signature="Sm/tv7DcZuTZftFVvt5yOoSETsc="' \
--form 'policy="eyJleHBpcmF0aW9 ... ... ... dHJ1ZSJ9XX0="' \
--form 'x-oss-object-acl="private"' \
--form 'x-oss-forbid-overwrite="true"' \
--form 'key="dashscope-instant/xxx/2024-07-18/xxx/cat.png"' \
--form 'success_action_status="200"' \
--form 'file=@"/tmp/cat.png"'
```

---

### 步骤 3：生成文件 URL

- 拼接逻辑：`oss://` + `key`（即步骤 2 表单中的 `key`）
- URL 有效期：48 小时

示例：

```text
oss://dashscope-instant/xxx/2024-07-18/xxxx/cat.png
```

---

## 错误码

| HTTP 状态码 | code | message | 含义/处理 |
|---|---|---|---|
| 400 | invalid_parameter_error | InternalError.Algo.InvalidParameter: The provided URL does not appear to be valid... | URL 无效；若用临时 URL，确保 Header 包含 `X-DashScope-OssResourceResolve: enable` |
| 400 | InvalidParameter.DataInspection | The media format is not supported or incorrect for the data inspection. | 可能缺少必要 Header；或图片格式不符合模型要求（以错误信息为准） |
| 403 | AccessDenied | Invalid according to Policy: Policy expired. | 上传凭证过期；重新获取凭证 |
| 429 | Throttling.RateQuota | Requests rate limit exceeded, please try again later. | 触发限流（100 QPS，主账号+模型）；降低频率或迁移到 OSS |

---

## 常见问题

### Q：使用 `oss://` 前缀 URL 调用时报错，如何处理？

- **检查 Header**：若通过 HTTP（curl/Postman）调用，必须添加 `X-DashScope-OssResourceResolve: enable`
- **检查 URL 有效性**：确认 URL 是 48 小时内生成且未过期；过期则重新上传获取新 URL

### Q：文件上传与模型调用使用的 API Key 可以不一样吗？

- 文件存储与访问权限基于**阿里云主账号**管理；API Key 仅为主账号的访问凭证  
- 因此：**同一主账号下不同 API Key 可以正常使用**；不同主账号之间无法跨账号读取文件

---

> 请确保文件上传与模型调用使用的 API Key 属于同一阿里云主账号。