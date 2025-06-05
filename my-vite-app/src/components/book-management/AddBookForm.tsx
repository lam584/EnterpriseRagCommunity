// src/components/book-management/AddBookForm.tsx
import React, { useState, useEffect } from 'react';
import { fetchCategories, CategoryDTO } from '../../services/categoryService';
import { fetchShelves, ShelfDTO } from '../../services/shelfService';
import { createBook } from '../../services/bookService';

const AddBookForm: React.FC = () => {
  // 表单状态
  const [formData, setFormData] = useState({
    isbn: '',
    title: '',
    author: '',
    publisher: '',
    edition: '1',
    price: '',
    printTimes: '1',
    categoryId: 0,
    shelfId: 0,
    status: '可借阅'
  });

  // 选项数据
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [shelves, setShelves] = useState<ShelfDTO[]>([]);

  // UI状态
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});

  // 加载分类和书架数据
  useEffect(() => {
    const loadData = async () => {
      try {
        const [categoriesData, shelvesData] = await Promise.all([
          fetchCategories(),
          fetchShelves()
        ]);
        setCategories(categoriesData);
        setShelves(shelvesData);
      } catch {
        setMessage({ type: 'error', text: '加载数据失败，请刷新页面重试' });
      }
    };
    loadData();
  }, []);

  // 表单验证
  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    if (!formData.isbn.trim()) newErrors.isbn = 'ISBN不能为空';
    else if (!/^\d{13}$/.test(formData.isbn)) newErrors.isbn = 'ISBN必须为13位数字';
    if (!formData.title.trim()) newErrors.title = '书名不能为空';
    if (!formData.author.trim()) newErrors.author = '作者不能为空';
    if (!formData.publisher.trim()) newErrors.publisher = '出版社不能为空';
    if (!formData.price || isNaN(Number(formData.price)) || Number(formData.price) <= 0) newErrors.price = '价格必须大于0';
    if (formData.categoryId === 0) newErrors.categoryId = '请选择图书分类';
    if (formData.shelfId === 0) newErrors.shelfId = '请选择存放书架';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 处理输入变化
  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;

    setFormData(prev => ({ ...prev, [name]: value }));

    // 清除该字段的错误
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 清空表单
  const clearForm = () => {
    setFormData({
      isbn: '',
      title: '',
      author: '',
      publisher: '',
      edition: '1',
      price: '',
      printTimes: '1',
      categoryId: 0,
      shelfId: 0,
      status: '可借阅'
    });
    setErrors({});
    setMessage({ type: '', text: '' });
  };

  // 表单提交，clearAfterSuccess决定是否成功后清空表单
  const handleSubmit = async (clearAfterSuccess: boolean) => {
    if (!validateForm()) return;
    setLoading(true);
    setMessage({ type: '', text: '' });
    try {
      const bookData = {
        isbn: formData.isbn,
        title: formData.title,
        author: formData.author,
        publisher: formData.publisher,
        edition: formData.edition,
        price: formData.price,
        printTimes: formData.printTimes,
        status: formData.status,
        category: { id: formData.categoryId },
        shelf: { id: formData.shelfId }
      };
      await createBook(bookData);
      setMessage({ type: 'success', text: '图书添加成功！' });
      if (clearAfterSuccess) clearForm();
    } catch (err) {
      console.error('添加图书时出错:', err);
      setMessage({ type: 'error', text: '添加图书失败，请检查填写内容或稍后重试' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex">
      <div className="w-3/4 bg-white shadow-md rounded-md p-6">
        <h2 className="text-xl font-bold mb-4">添加新图书</h2>

        {message.text && (
          <div
            className={`p-4 mb-4 rounded ${
              message.type === 'success'
                ? 'bg-green-100 text-green-800'
                : 'bg-red-100 text-red-800'
            }`}
          >
            {message.text}
          </div>
        )}

        <form className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            {/* ISBN */}
            <div>
              <label className="block mb-1">
                ISBN号 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="isbn"
                value={formData.isbn}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.isbn ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="请输入ISBN号"
              />
              {errors.isbn && (
                <p className="text-red-500 text-sm mt-1">{errors.isbn}</p>
              )}
            </div>

            {/* 书名 */}
            <div>
              <label className="block mb-1">
                书名 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="title"
                value={formData.title}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.title ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="请输入书名"
              />
              {errors.title && (
                <p className="text-red-500 text-sm mt-1">{errors.title}</p>
              )}
            </div>

            {/* 作者 */}
            <div>
              <label className="block mb-1">
                作者 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="author"
                value={formData.author}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.author ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="请输入作者"
              />
              {errors.author && (
                <p className="text-red-500 text-sm mt-1">{errors.author}</p>
              )}
            </div>

            {/* 出版社 */}
            <div>
              <label className="block mb-1">
                出版社 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="publisher"
                value={formData.publisher}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.publisher ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="请输入出版社"
              />
              {errors.publisher && (
                <p className="text-red-500 text-sm mt-1">{errors.publisher}</p>
              )}
            </div>

            {/* 价格 */}
            <div>
              <label className="block mb-1">
                定价 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="price"
                value={formData.price}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.price ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="请输入价格"
              />
              {errors.price && (
                <p className="text-red-500 text-sm mt-1">{errors.price}</p>
              )}
            </div>

            {/* 版次 */}
            <div>
              <label className="block mb-1">版次</label>
              <input
                type="text"
                name="edition"
                value={formData.edition}
                onChange={handleChange}
                className="w-full border border-gray-300 p-2 rounded"
                placeholder="请输入版次"
              />
            </div>

            {/* 印次 */}
            <div>
              <label className="block mb-1">印次</label>
              <input
                type="text"
                name="printTimes"
                value={formData.printTimes}
                onChange={handleChange}
                className="w-full border border-gray-300 p-2 rounded"
                placeholder="请输入印次"
              />
            </div>

            {/* 图书分类 */}
            <div>
              <label className="block mb-1">
                图书分类 <span className="text-red-500">*</span>
              </label>
              <select
                name="categoryId"
                value={formData.categoryId}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.categoryId ? 'border-red-500' : 'border-gray-300'
                }`}
              >
                <option value={0}>请选择分类</option>
                {categories.map(cat => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
              {errors.categoryId && (
                <p className="text-red-500 text-sm mt-1">
                  {errors.categoryId}
                </p>
              )}
            </div>

            {/* 书架 */}
            <div>
              <label className="block mb-1">
                存放书架 <span className="text-red-500">*</span>
              </label>
              <select
                name="shelfId"
                value={formData.shelfId}
                onChange={handleChange}
                className={`w-full border p-2 rounded ${
                  errors.shelfId ? 'border-red-500' : 'border-gray-300'
                }`}
              >
                <option value={0}>请选择书架</option>
                {shelves.map(shelf => (
                  <option key={shelf.id} value={shelf.id}>
                    {shelf.shelfCode} ({shelf.locationDescription})
                  </option>
                ))}
              </select>
              {errors.shelfId && (
                <p className="text-red-500 text-sm mt-1">{errors.shelfId}</p>
              )}
            </div>

            {/* 状态 */}
            <div>
              <label className="block mb-1">状态</label>
              <select
                name="status"
                value={formData.status}
                onChange={handleChange}
                className="w-full border border-gray-300 p-2 rounded"
              >
                <option value="可借阅">可借阅</option>
                <option value="已借出">已借出</option>
                <option value="维修中">维修中</option>
                <option value="丢失">丢失</option>
              </select>
            </div>
          </div>

          <div className="flex space-x-4">
            <button
              type="button"
              onClick={() => handleSubmit(false)}
              disabled={loading}
              className={`bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600 ${
                loading ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {loading ? '处理中...' : '添加图书'}
            </button>
            <button
              type="button"
              onClick={() => handleSubmit(true)}
              disabled={loading}
              className={`bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600 ${
                loading ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {loading ? '处理中...' : '添加并清空'}
            </button>
            <button
              type="button"
              onClick={clearForm}
              className="bg-gray-300 text-gray-800 px-6 py-2 rounded hover:bg-gray-400"
            >
              清空表单
            </button>
          </div>
        </form>
      </div>
      <div className="w-1/4 ml-6">
        <div className="bg-white shadow-md rounded-md p-6">
          <h2 className="text-xl font-bold mb-4">操作指南</h2>
          <ul className="list-disc pl-5 space-y-2 text-gray-700">
            <li>
              所有带<span className="text-red-500">*</span>的字段为必填项
            </li>
            <li>ISBN格式必须为13位数字</li>
            <li>添加成功后可在图书列表查看</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default AddBookForm;
