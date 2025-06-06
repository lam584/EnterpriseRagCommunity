// src/components/book-management/AddBookForm.tsx

import React, { useState, useEffect } from 'react'
import { fetchCategories, CategoryDTO } from '../../services/categoryService'
import { fetchShelves, ShelfDTO } from '../../services/shelfService'
import { createBook } from '../../services/bookService'

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
import { FaBook, FaCheckCircle, FaTimesCircle } from 'react-icons/fa'
import { AiOutlineClear } from 'react-icons/ai'

const AddBookForm: React.FC = () => {
  const [formData, setFormData] = useState({
    isbn: '', title: '', author: '', publisher: '',
    edition: '1', price: '', printTimes: '1',
    categoryId: 0, shelfId: 0, status: '可借阅',
  })
  const [categories, setCategories] = useState<CategoryDTO[]>([])
  const [shelves, setShelves] = useState<ShelfDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    ;(async () => {
      try {
        const [cats, shs] = await Promise.all([fetchCategories(), fetchShelves()])
        setCategories(cats)
        setShelves(shs)
      } catch {
        setMessage({ type: 'error', text: '加载数据失败，请刷新页面重试' })
      }
    })()
  }, [])

  const validateForm = () => {
    const e: Record<string, string> = {}
    if (!formData.isbn.trim()) e.isbn = 'ISBN不能为空'
    else if (!/^\d{13}$/.test(formData.isbn)) e.isbn = 'ISBN必须为13位数字'
    if (!formData.title.trim()) e.title = '书名不能为空'
    if (!formData.author.trim()) e.author = '作者不能为空'
    if (!formData.publisher.trim()) e.publisher = '出版社不能为空'
    if (!formData.price || isNaN(+formData.price) || +formData.price <= 0)
      e.price = '价格必须大于0'
    if (formData.categoryId === 0) e.categoryId = '请选择图书分类'
    if (formData.shelfId === 0) e.shelfId = '请选择存放书架'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData(p => ({ ...p, [name]: value }))
    if (errors[name]) {
      setErrors(p => {
        const np = { ...p }
        delete np[name]
        return np
      })
    }
  }

  const clearForm = () => {
    setFormData({
      isbn: '', title: '', author: '', publisher: '',
      edition: '1', price: '', printTimes: '1',
      categoryId: 0, shelfId: 0, status: '可借阅',
    })
    setErrors({})
    setMessage(null)
  }

  const handleSubmit = async (clearAfter: boolean) => {
    if (!validateForm()) return
    setLoading(true)
    setMessage(null)
    try {
      await createBook({
        isbn: formData.isbn,
        title: formData.title,
        author: formData.author,
        publisher: formData.publisher,
        edition: formData.edition,
        price: formData.price,
        printTimes: formData.printTimes,
        status: formData.status,
        category: { id: formData.categoryId },
        shelf: { id: formData.shelfId },
      })
      setMessage({ type: 'success', text: '图书添加成功！' })
      if (clearAfter) clearForm()
    } catch (err) {
      console.error(err)
      setMessage({ type: 'error', text: '添加图书失败，请稍后重试' })
    } finally {
      setLoading(false)
    }
  }

  return (
      <section className="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-6">
        <div className="max-w-6xl mx-auto grid gap-8 lg:grid-cols-3">
          {/* 表单区 */}
          <Card className="col-span-2 bg-white dark:bg-gray-800 shadow-lg rounded-2xl overflow-hidden">
            <CardHeader className="bg-blue-600 dark:bg-blue-700 text-white">
              <CardTitle className="flex items-center gap-3 text-2xl font-bold">
                <FaBook className="text-3xl" /> 添加新图书
              </CardTitle>
            </CardHeader>

            <CardContent className="p-8 space-y-6">
              {/* 提示信息 */}
              {message && (
                  <Alert
                      variant={message.type === 'success' ? 'default' : 'destructive'}
                      className="mt-0 mb-4"
                  >
                    {message.type === 'success'
                        ? <FaCheckCircle className="absolute left-4 top-4 text-green-500" />
                        : <FaTimesCircle className="absolute left-4 top-4 text-red-500" />
                    }
                    <AlertTitle className="pl-7">
                      {message.type === 'success' ? '成功' : '错误'}
                    </AlertTitle>
                    <AlertDescription className="pl-7">
                      {message.text}
                    </AlertDescription>
                  </Alert>
              )}

              {/* 基本信息 */}
              <div className="space-y-4">
                <h3 className="border-l-4 border-blue-400 pl-2 text-lg font-semibold text-gray-700 dark:text-gray-200">
                  基本信息
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* ISBN */}
                  <div className="flex flex-col">
                    <Label htmlFor="isbn">
                      ISBN号 <span className="text-red-500">*</span>
                    </Label>
                    <Input
                        id="isbn" name="isbn"
                        value={formData.isbn}
                        onChange={handleChange}
                        placeholder="13位数字"
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                    {errors.isbn && (
                        <p className="mt-1 text-sm text-red-500">{errors.isbn}</p>
                    )}
                  </div>
                  {/* 书名 */}
                  <div className="flex flex-col">
                    <Label htmlFor="title">
                      书名 <span className="text-red-500">*</span>
                    </Label>
                    <Input
                        id="title" name="title"
                        value={formData.title}
                        onChange={handleChange}
                        placeholder="请输入书名"
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                    {errors.title && (
                        <p className="mt-1 text-sm text-red-500">{errors.title}</p>
                    )}
                  </div>
                  {/* 作者 */}
                  <div className="flex flex-col">
                    <Label htmlFor="author">
                      作者 <span className="text-red-500">*</span>
                    </Label>
                    <Input
                        id="author" name="author"
                        value={formData.author}
                        onChange={handleChange}
                        placeholder="请输入作者"
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                    {errors.author && (
                        <p className="mt-1 text-sm text-red-500">{errors.author}</p>
                    )}
                  </div>
                  {/* 出版社 */}
                  <div className="flex flex-col">
                    <Label htmlFor="publisher">
                      出版社 <span className="text-red-500">*</span>
                    </Label>
                    <Input
                        id="publisher" name="publisher"
                        value={formData.publisher}
                        onChange={handleChange}
                        placeholder="请输入出版社"
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                    {errors.publisher && (
                        <p className="mt-1 text-sm text-red-500">{errors.publisher}</p>
                    )}
                  </div>
                  {/* 定价 */}
                  <div className="flex flex-col">
                    <Label htmlFor="price">
                      定价 <span className="text-red-500">*</span>
                    </Label>
                    <Input
                        id="price" name="price"
                        value={formData.price}
                        onChange={handleChange}
                        placeholder="请输入价格"
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                    {errors.price && (
                        <p className="mt-1 text-sm text-red-500">{errors.price}</p>
                    )}
                  </div>
                </div>
              </div>

              {/* 分类与存放 */}
              <div className="space-y-4">
                <h3 className="border-l-4 border-green-400 pl-2 text-lg font-semibold text-gray-700 dark:text-gray-200">
                  分类与存放
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* 分类 */}
                  <div className="flex flex-col">
                    <Label>
                      图书分类 <span className="text-red-500">*</span>
                    </Label>
                    <Select
                        value={String(formData.categoryId)}
                        onValueChange={v => setFormData(p => ({ ...p, categoryId: +v }))}
                    >
                      <SelectTrigger
                          className="h-12 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md"
                      >
                        <SelectValue placeholder="请选择分类" />
                      </SelectTrigger>
                      <SelectContent className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-md">
                        {categories.map(c => (
                            <SelectItem key={c.id} value={String(c.id)}>
                              {c.name}
                            </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {errors.categoryId && (
                        <p className="mt-1 text-sm text-red-500">{errors.categoryId}</p>
                    )}
                  </div>
                  {/* 书架 */}
                  <div className="flex flex-col">
                    <Label>
                      存放书架 <span className="text-red-500">*</span>
                    </Label>
                    <Select
                        value={String(formData.shelfId)}
                        onValueChange={v => setFormData(p => ({ ...p, shelfId: +v }))}
                    >
                      <SelectTrigger
                          className="h-12 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md"
                      >
                        <SelectValue placeholder="请选择书架" />
                      </SelectTrigger>
                      <SelectContent className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-md">
                        {shelves.map(s => (
                            <SelectItem key={s.id} value={String(s.id)}>
                              {s.shelfCode}（{s.locationDescription}）
                            </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {errors.shelfId && (
                        <p className="mt-1 text-sm text-red-500">{errors.shelfId}</p>
                    )}
                  </div>
                </div>
              </div>

              {/* 其他信息 */}
              <div className="space-y-4">
                <h3 className="border-l-4 border-purple-400 pl-2 text-lg font-semibold text-gray-700 dark:text-gray-200">
                  其他信息
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="flex flex-col">
                    <Label htmlFor="edition">版次</Label>
                    <Input
                        id="edition" name="edition"
                        value={formData.edition}
                        onChange={handleChange}
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                  </div>
                  <div className="flex flex-col">
                    <Label htmlFor="printTimes">印次</Label>
                    <Input
                        id="printTimes" name="printTimes"
                        value={formData.printTimes}
                        onChange={handleChange}
                        className="h-12 bg-gray-100 dark:bg-gray-700"
                    />
                  </div>
                  <div className="flex flex-col">
                    <Label>状态</Label>
                    <Select
                        value={formData.status}
                        onValueChange={v => setFormData(p => ({ ...p, status: v }))}
                    >
                      <SelectTrigger
                          className="h-12 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md"
                      >
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-md">
                        {['可借阅', '已借出', '维修中', '丢失'].map(s => (
                            <SelectItem key={s} value={s}>{s}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>
            </CardContent>

            <CardFooter className="bg-gray-100 dark:bg-gray-700 p-6 flex justify-end gap-4">
              <Button
                  variant="default"
                  size="lg"
                  onClick={() => handleSubmit(false)}
                  disabled={loading}
                  className="bg-blue-600 hover:bg-blue-700 text-white transition-transform hover:-translate-y-0.5"
              >
                <FaCheckCircle className="mr-2" /> {loading ? '处理中...' : '添加'}
              </Button>
              <Button
                  variant="default"
                  size="lg"
                  onClick={() => handleSubmit(true)}
                  disabled={loading}
                  className="bg-green-600 hover:bg-green-700 text-white transition-transform hover:-translate-y-0.5"
              >
                <FaCheckCircle className="mr-2" /> {loading ? '处理中...' : '添加并清空'}
              </Button>
              <Button
                  variant="outline"
                  size="lg"
                  onClick={clearForm}
                  className="text-gray-700 dark:text-gray-200 bg-transparent hover:bg-gray-200 dark:hover:bg-gray-600 transition-transform hover:-translate-y-0.5"
              >
                <AiOutlineClear className="mr-2" /> 清空
              </Button>
            </CardFooter>
          </Card>

          {/* 操作指南区 */}
          <Card className="bg-white dark:bg-gray-800 shadow-lg rounded-xl p-6">
            <CardHeader>
              <CardTitle className="text-xl font-semibold text-gray-700 dark:text-gray-200">
                操作指南
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-gray-600 dark:text-gray-300">
              <ul className="list-disc list-inside space-y-2">
                <li>所有带 <span className="text-red-500">*</span> 的字段为必填项</li>
                <li>ISBN 格式必须为 13 位数字</li>
                <li>价格须为大于 0 的数字</li>
                <li>完成后可在“图书管理”列表中查看新添加的图书</li>
              </ul>
            </CardContent>
          </Card>
        </div>
      </section>
  )
}

export default AddBookForm