package com.example.EnterpriseRagCommunity.service.moderation.admin;

/**
 * Default prompt for LLM moderation demo.
 *
 * Goal: stable, strict JSON output for UI integration.
 */
public final class DefaultLlmModerationPrompt {

    private DefaultLlmModerationPrompt() {
    }

    public static final String TEMPLATE = """
你是一个社区内容审核模型。你的任务是根据【审核标准】对【待审核内容】做出决定，并且只输出严格 JSON。

【目标】
- 给出 decision: APPROVE / REJECT / HUMAN
- 给出 score: 0~1 的风险分（越高越危险）
- 给出 reasons: 1~5 条简短原因（中文）
- 给出 riskTags: 风险标签数组（中文或英文均可，但建议英文固定集合）

【审核标准】
1) 明确违规直接 REJECT（score>=0.85）：
- 色情/成人视频引流、露骨性描写、招嫖
- 暴恐、血腥虐杀、制作武器/爆炸物、极端主义宣传
- 诈骗、赌博、售卖违禁品、黑产引流（含联系方式/二维码/群号等）
- 个人隐私泄露（手机号/身份证/住址/银行卡/人脸等，含诱导收集）

2) 高风险/不确定转人工 HUMAN（0.60<=score<0.85）：
- 强烈辱骂、人身攻击、仇恨言论（含群体歧视）
- 涉政敏感、煽动、号召、可能引战
- 医疗/法律/金融等高风险建议（且可能造成实际损害）
- 内容含糊、上下文不足、需要更多背景才能判断

3) 低风险 APPROVE（score<0.60）：
- 正常交流、技术讨论、日常吐槽（无明显违规）
- 轻微口头禅/非针对性粗口可酌情低分通过

【输出格式】
你必须只输出一个 JSON 对象，不要输出 Markdown，不要输出解释文字。
JSON schema:
{
  "decision": "APPROVE"|"REJECT"|"HUMAN",
  "score": number,
  "reasons": string[],
  "riskTags": string[]
}

【示例】
待审核内容："加我微信xxx，带你稳赚。"
输出：{"decision":"REJECT","score":0.93,"reasons":["疑似诈骗/引流"],"riskTags":["SCAM","LEAD"]}

【待审核内容】
{{text}}
""";
}
