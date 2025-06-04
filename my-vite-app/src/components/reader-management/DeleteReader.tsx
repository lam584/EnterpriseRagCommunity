// src/components/reader-management/DeleteReader.tsx
import React, {useState} from 'react';

interface Reader {
    id: string;
    name: string;
    gender: string;
    phone: string;
    email: string;
    type: string;
    idCard: string;
    address: string;
}

const DeleteReader: React.FC = () => {
    const [readerId, setReaderId] = useState('');
    const [showConfirmation, setShowConfirmation] = useState(false);

    // 模拟的读者数据，实际项目中应从API获取
    const readerDetails: Reader = {
        id: 'R001',
        name: '张三',
        gender: '男',
        phone: '13800138000',
        email: 'zhangsan@example.com',
        type: '学生',
        idCard: '330102199001010001',
        address: '浙江省杭州市西湖区'
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setReaderId(e.target.value);
    };

    const handleSearch = () => {
        // 实际项目中这里应该调用API查询读者
        console.log('搜索读者ID:', readerId);
        // 模拟找到读者后显示确认界面
        setShowConfirmation(true);
    };

    const handleConfirmDelete = () => {
        // 实际项目中这里应该调用API删除读者
        console.log('确认删除读者ID:', readerId);
        // 删除成功后的逻辑处理
        setShowConfirmation(false);
        setReaderId('');
        // 可以添加提示信息或重定向
    };

    const handleCancelDelete = () => {
        setShowConfirmation(false);
    };

    return (
        <div className="flex">
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h1 className="text-2xl font-bold mb-4">删除读者</h1>

                <div className="mb-4">
                    <label htmlFor="readerId" className="block text-gray-700 mb-2">请输入读者ID:</label>
                    <input
                        type="text"
                        id="readerId"
                        value={readerId}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <button
                    onClick={handleSearch}
                    className="bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
                >
                    删除
                </button>

                {showConfirmation && (
                    <>
                        <div className="mt-6">
                            <p className="text-gray-700"><strong>读者ID:</strong> {readerDetails.id}</p>
                            <p className="text-gray-700"><strong>姓名:</strong> {readerDetails.name}</p>
                            <p className="text-gray-700"><strong>性别:</strong> {readerDetails.gender}</p>
                            <p className="text-gray-700"><strong>电话号码:</strong> {readerDetails.phone}</p>
                            <p className="text-gray-700"><strong>邮箱地址:</strong> {readerDetails.email}</p>
                            <p className="text-gray-700"><strong>读者类型:</strong> {readerDetails.type}</p>
                            <p className="text-gray-700"><strong>身份证号:</strong> {readerDetails.idCard}</p>
                            <p className="text-gray-700"><strong>地址:</strong> {readerDetails.address}</p>
                        </div>

                        <div className="mt-6 border-t pt-4">
                            <p className="text-gray-700 font-bold mb-4">确认删除该读者?</p>
                            <div className="flex space-x-4">
                                <button
                                    onClick={handleConfirmDelete}
                                    className="bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
                                >
                                    确认删除
                                </button>
                                <button
                                    onClick={handleCancelDelete}
                                    className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
                                >
                                    取消删除
                                </button>
                            </div>
                        </div>
                    </>
                )}

            </div>
            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">删除读者功能使用说明</p>
                </div>
            </div>
        </div>
    );
};

export default DeleteReader;
