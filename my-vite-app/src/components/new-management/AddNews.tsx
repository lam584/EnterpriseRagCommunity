// 文件路径：src/components/new-management/AddNews.tsx
/**
 * AddNews 组件
 * 功能：
 *  1. 加载新闻主题列表（调用后端 API）
 *  2. 前端表单校验（必填、格式校验等)
 *  3. 调用后端 createNews 接口发布新闻
 *  4. 显示操作结果提示信息（成功 / 失败）
 */

import React, { useState, useEffect } from 'react'
// 后端 API 封装将来需要修改
import { fetchCategories, TopicDTO } from '../../services/TopicService'
// 导入新闻服务
import { createNews } from '../../services/NewsService'
// 新增导入富文本编辑器
import { Textarea } from '../ui/textarea'

// UI 组件库：Card、Input、Select、Button、Alert 等
import { Card, CardHeader, CardContent, CardFooter, CardTitle } from '../ui/card'
import { Label } from '../ui/label'
import { Input } from '../ui/input'
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '../ui/select'
import { Button } from '../ui/button'
import { Alert, AlertTitle, AlertDescription } from '../ui/alert'
// 图标
import { FaNewspaper, FaCheckCircle, FaTimesCircle } from 'react-icons/fa'
import { AiOutlineClear } from 'react-icons/ai'

const AddNews: React.FC = () => {
  // 表单模型
  const [formData, setFormData] = useState({
    title: '',
    content: '',
    summary: '',
    authorId: '', // 将 author 改为 authorId
    TopicId: 0,
    coverImage: '',
    isTop: false,
    status: '待发布',
  })
  // 主题分类下拉选项
  const [categories, setCategories] = useState<TopicDTO[]>([])
  // loading 状态：防止重复提交
  const [loading, setLoading] = useState(false)
  // 操作提示：成功 or 失败
  const [message, setMessage] =
    useState<{ type: 'success' | 'error'; text: string } | null>(null)
  // 表单校验错误信息集合
  const [errors, setErrors] = useState<Record<string, string>>({})

  // 页面加载时拉取新闻主题数据
  useEffect(() => {
    ;(async () => {
      try {
        const cats = await fetchCategories()
        setCategories(cats)
      } catch (e) {
        console.error('加载新闻主题失败', e)
        setMessage({ type: 'error', text: '加载数据失败，请刷新页面重试' })
      }
    })()
  }, [])

  /**
   * validateForm - 前端表单校验
   * 校验规则：
   *   - title: 必填
   *   - content: 必填
   *   - author: 必填
   *   - TopicId: 必选
   */
  const validateForm = () => {
    const e: Record<string, string> = {}
    if (!formData.title.trim()) e.title = '标题不能为空'
    if (!formData.content.trim()) e.content = '内容不能为空'
    if (!formData.summary.trim()) e.summary = '摘要不能为空'
    if (!formData.authorId.trim()) e.author = '作者不能为空'
    // 下拉选择必须非 0
    if (formData.TopicId === 0) e.TopicId = '请选择新闻主题'

    setErrors(e)
    return Object.keys(e).length === 0 // 返回是否校验通过
  }

  // 表单提交处理
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    // 表单校验
    if (!validateForm()) return

    try {
      setLoading(true)
      // 调用新闻发布接口
      await createNews({
        title: formData.title,
        content: formData.content,
        summary: formData.summary,
        topicId: Number(formData.TopicId),
        coverImage: formData.coverImage,
        status: formData.status,
        authorId: Number(formData.authorId) || undefined
      })

      // 成功提示
      setMessage({
        type: 'success',
        text: '新闻发布成功！'
      })
      // 重置表单
      setFormData({
        title: '',
        content: '',
        summary: '',
        authorId: '',
        TopicId: 0,
        coverImage: '',
        isTop: false,
        status: '待发布',
      })
    } catch (error) {
      console.error('发布新闻失败', error)
      setMessage({
        type: 'error',
        text: '发布失败，请稍后重试'
      })
    } finally {
      setLoading(false)
    }
  }

  // 重置表单
  const resetForm = () => {
    setFormData({
      title: '',
      content: '',
      summary: '',
      authorId: '',
      TopicId: 0,
      coverImage: '',
      isTop: false,
      status: '待发布',
    })
    setErrors({})
    setMessage(null)
  }

  // 表单字段更新处理
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: value
    }))
    // 清除对应字段的错误提示
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev }
        delete newErrors[name]
        return newErrors
      })
    }
  }

  // 下拉框选择处理
  const handleSelectChange = (name: string, value: string) => {
    setFormData(prev => ({
      ...prev,
      [name]: value
    }))
    // 清除对应字段的错误提示
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev }
        delete newErrors[name]
        return newErrors
      })
    }
  }

  return (
    <Card className="w-full max-w-4xl mx-auto  bg-white">
      <CardHeader className="bg-gradient-to-r from-blue-600 to-purple-600 text-white">
        <CardTitle className="text-2xl flex items-center gap-2">
          <FaNewspaper /> 发布新闻
        </CardTitle>
      </CardHeader>
      <CardContent className="pt-6">
        {message && (
          <Alert className={`mb-6 ${message.type === 'success' ? 'bg-green-50 text-green-800 border-green-200' : 'bg-red-50 text-red-800 border-red-200'}`}>
            <AlertTitle className="flex items-center gap-2">
              {message.type === 'success' ? <FaCheckCircle className="text-green-500" /> : <FaTimesCircle className="text-red-500" />}
              {message.type === 'success' ? '成功' : '错误'}
            </AlertTitle>
            <AlertDescription>{message.text}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 ">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* 标题 */}
            <div className="space-y-2">
              <Label htmlFor="title">新闻标题 <span className="text-red-500">*</span></Label>
              <Input
                id="title"
                name="title"
                placeholder="请输入新闻标题"
                value={formData.title}
                onChange={handleInputChange}
                className={errors.title ? 'border-red-500' : ''}
              />
              {errors.title && <p className="text-red-500 text-sm">{errors.title}</p>}
            </div>

            {/* 作者 */}
            <div className="space-y-2">
              <Label htmlFor="authorId">作者 <span className="text-red-500">*</span></Label>
              <Input
                id="authorId"
                name="authorId"
                placeholder="请输入作者名称"
                value={formData.authorId}
                onChange={handleInputChange}
                className={errors.author ? 'border-red-500' : ''}
              />
              {errors.author && <p className="text-red-500 text-sm">{errors.author}</p>}
            </div>

            {/* 新闻主题 */}
            <div className="space-y-2">
              <Label htmlFor="TopicId">新闻主题 <span className="text-red-500">*</span></Label>
              <div className="bg-white">
              <Select
                value={formData.TopicId.toString()}
                onValueChange={(value: string) => handleSelectChange('TopicId', value)}
              >
                <SelectTrigger className={errors.TopicId ? 'border-red-500' : ''}>
                  <SelectValue placeholder="选择新闻主题" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="0">请选择</SelectItem>
                  {categories.map(cat => (
                    <SelectItem key={cat.id ?? 0} value={(cat.id ?? 0).toString()}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {errors.TopicId && <p className="text-red-500 text-sm">{errors.TopicId}</p>}
              </div>
            </div>

            {/* 封面图片链接 */}
            <div className="space-y-2">
              <Label htmlFor="coverImage">封面图片链接</Label>
              <Input
                id="coverImage"
                name="coverImage"
                placeholder="请输入新闻封面图片链接"
                value={formData.coverImage}
                onChange={handleInputChange}
              />
            </div>
          </div>

          {/* 新闻摘要 */}
          <div className="space-y-2">
            <Label htmlFor="summary">新闻摘要 <span className="text-red-500">*</span></Label>
            <Textarea
              id="summary"
              name="summary"
              placeholder="请输入新闻摘要"
              value={formData.summary}
              onChange={handleInputChange}
              className={`min-h-20 ${errors.summary ? 'border-red-500' : ''}`}
            />
            {errors.summary && <p className="text-red-500 text-sm">{errors.summary}</p>}
          </div>

          {/* 新闻内容 */}
          <div className="space-y-2">
            <Label htmlFor="content">新闻内容 <span className="text-red-500">*</span></Label>
            <Textarea
              id="content"
              name="content"
              placeholder="请输入新闻内容"
              value={formData.content}
              onChange={handleInputChange}
              className={`min-h-[200px] ${errors.content ? 'border-red-500' : ''}`}
            />
            {errors.content && <p className="text-red-500 text-sm">{errors.content}</p>}
          </div>
        </form>
      </CardContent>
      <CardFooter className="flex justify-between border-t p-4">
        <Button
          type="reset"
          onClick={resetForm}
          variant="outline"
          className="gap-1"
        >
          <AiOutlineClear /> 重置表单
        </Button>
        <Button
          type="submit"
          onClick={handleSubmit}
          disabled={loading}
          className="gap-1 bg-blue-600 hover:bg-blue-700"
        >
          <FaNewspaper /> {loading ? '发布中...' : '发布新闻'}
        </Button>
      </CardFooter>
    </Card>
  )
}

export default AddNews
