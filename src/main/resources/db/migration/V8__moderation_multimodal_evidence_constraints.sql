UPDATE prompts
SET user_prompt_template = CONCAT(
  user_prompt_template,
  '\n- 若同一违规文本已经能在 [TEXT] 中通过 before_context/after_context 或 text 原样定位，不得重复输出仅含 image_id 的图片证据。',
  '\n- 只有当风险证据来自图片独有内容，且无法仅凭 [TEXT] 完整落点时，才允许输出 image_id。'
)
WHERE prompt_code = 'MODERATION_MULTIMODAL'
  AND user_prompt_template NOT LIKE '%不得重复输出仅含 image_id 的图片证据%';