import React, { useState, useEffect } from 'react';
import { generateTotpKey, saveSetupConfig, testEsConnection, initIndices, completeSetup, checkEnvFile, checkIndicesStatus, encryptValue } from '../../services/setupService';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import Modal from '../common/Modal';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Checkbox } from '../ui/checkbox';
import {
    getConfigInputType,
    getConfigInputValue,
    getConfigLabel,
    getHelpText,
    getReadOnlyInputClass,
    VisibilityToggleButton,
} from './ImportConfigurationForm.utils';
import { 
    Check, 
    Upload, 
    HelpCircle, 
    Server, 
    Shield, 
    Key, 
    Mail,
    Database,
    User,
    AlertCircle,
    Loader2,
    ArrowRight,
    ArrowLeft,
    Wand2,
    Image,
} from 'lucide-react';
import { cn } from '../../lib/utils';

const getFriendlyErrorMessage = (errorMessage: string): string => {
    if (!errorMessage) return '未知错误';
    if (errorMessage.includes('401 Unauthorized') || 
        errorMessage.includes('security_exception') || 
        errorMessage.includes('unable to authenticate')) {
        return '认证失败：请检查 ES API Key 或密码是否正确';
    }
    if (errorMessage.includes('Connection refused')) {
        return '连接失败：无法连接到 ES 服务器，请检查地址配置';
    }
    return errorMessage;
};

const ImportConfigurationForm: React.FC = () => {
    const navigate = useNavigate();
    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [helpKey, setHelpKey] = useState<string | null>(null);
    const [envFileDetected, setEnvFileDetected] = useState(false);
    const [visibleFields, setVisibleFields] = useState<Record<string, boolean>>({});
    const [encryptedValues, setEncryptedValues] = useState<Record<string, string>>({});

    const toggleVisibility = async (key: string) => {
        const willBeVisible = !visibleFields[key];
        
        if (willBeVisible) {
            if (!encryptedValues[key] && configs[key]) {
                try {
                    const encrypted = await encryptValue(configs[key]);
                    setEncryptedValues(prev => ({ ...prev, [key]: encrypted }));
                } catch (_e) {
                    toast.error('加密失败');
                    return;
                }
            }
        }
        setVisibleFields(prev => ({ ...prev, [key]: willBeVisible }));
    };

    useEffect(() => {
        checkEnvFile().then(res => {
            if (res.exists) {
                setEnvFileDetected(true);
            }
        }).catch(console.error);
    }, []);

    // Step 1: Configs
    const [configs, setConfigs] = useState<Record<string, string>>({
        APP_AI_API_KEY: '',
        APP_TOTP_MASTER_KEY: '',
        'spring.elasticsearch.uris': 'http://127.0.0.1:9200',
        APP_ES_API_KEY: '',
        APP_AI_TOKENIZER_API_KEY: '',
        APP_SITE_COPYRIGHT: '',
        APP_SITE_BEIAN: '',
        APP_MAIL_HOST: '',
        APP_MAIL_PORT: '465',
        APP_MAIL_INBOX_HOST: '',
        APP_MAIL_INBOX_PORT: '993',
        APP_MAIL_FROM_ADDRESS: '',
        APP_MAIL_USERNAME: '',
        APP_MAIL_PASSWORD: '',
        IMAGE_STORAGE_MODE: 'DASHSCOPE_TEMP',
        IMAGE_STORAGE_OSS_ENDPOINT: '',
        IMAGE_STORAGE_OSS_BUCKET: '',
        IMAGE_STORAGE_OSS_ACCESS_KEY_ID: '',
        IMAGE_STORAGE_OSS_ACCESS_KEY_SECRET: '',
        IMAGE_STORAGE_OSS_REGION: '',
    });

    // Step 2: ES
    const [esConnected, setEsConnected] = useState(false);
    const indices = ['ad_violation_samples_v1', 'rag_post_chunks_v1_comments', 'rag_post_chunks_v1', 'rag_file_assets_v1'];
    const [selectedIndices, setSelectedIndices] = useState<string[]>(indices);
    const [indicesCreated, setIndicesCreated] = useState(false);
    const [indicesStatus, setIndicesStatus] = useState<Record<string, string>>({});

    // Step 3: Admin
    const [adminForm, setAdminForm] = useState({ email: '', username: '', password: '', confirmPassword: '' });

    const handleConfigChange = (key: string, value: string) => {
        setConfigs(prev => ({ ...prev, [key]: value }));
        setEncryptedValues(prev => {
            const next = { ...prev };
            delete next[key];
            return next;
        });
    };

    const generateKey = async () => {
        try {
            const key = await generateTotpKey();
            handleConfigChange('APP_TOTP_MASTER_KEY', key);
        } catch (_e) {
            setError('生成密钥失败');
        }
    };

    const processConfigText = (text: string) => {
        const lines = text.split('\n');
        const newConfigs = { ...configs };
        const newAdminForm = { ...adminForm };

        lines.forEach(line => {
            const [k, v] = line.split('=');
            if (k && v) {
                let key = k.trim();
                let value = v.trim();

                if (key === 'APP_MAIL_FROM') key = 'APP_MAIL_FROM_ADDRESS';
                if (key === 'image.storage.mode') key = 'IMAGE_STORAGE_MODE';
                if (key === 'IMAGE_STORAGE_MODE') {
                    const normalizedValue = value.toUpperCase();
                    if (['LOCAL', 'DASHSCOPE_TEMP', 'ALIYUN_OSS'].includes(normalizedValue)) {
                        value = normalizedValue;
                    }
                }
                
                if (Object.keys(configs).includes(key)) {
                    newConfigs[key] = value;
                }

                // Support importing admin account
                if (key === 'ADMIN_EMAIL') {
                    newAdminForm.email = value;
                } else if (key === 'ADMIN_USERNAME') {
                    newAdminForm.username = value;
                } else if (key === 'ADMIN_PASSWORD') {
                    newAdminForm.password = value;
                    newAdminForm.confirmPassword = value;
                }
            }
        });
        setConfigs(newConfigs);
        setAdminForm(newAdminForm);
        toast.success('配置导入成功');
    };

    const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (evt) => {
            const text = evt.target?.result as string;
            processConfigText(text);
        };
        reader.readAsText(file);
    };

    const nextStep = async () => {
        setError(null);
        if (step === 1) {
            setStep(2);
        } else if (step === 2) {
            setLoading(true);
            try {
                if (!esConnected) {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const res = await testEsConnection(configs);
                    if (res.success) {
                        setEsConnected(true);
                    } else {
                        setError(getFriendlyErrorMessage(res.message));
                        setLoading(false);
                        return;
                    }
                }

                await initIndices(selectedIndices);
                setIndicesCreated(true);
                toast.success('索引初始化成功');
                setStep(3);
            } catch (e: any) {
                setError('操作失败：' + getFriendlyErrorMessage(e.message));
            } finally {
                setLoading(false);
            }
        }
    };
    const testEs = async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await testEsConnection(configs);
            if (res.success) {
                setEsConnected(true);
                toast.success('连接成功！');
                try {
                    const status = await checkIndicesStatus(configs, indices);
                    setIndicesStatus(status);
                } catch (e) {
                    console.error("Failed to check indices status", e);
                }
            } else {
                setEsConnected(false);
                setError(getFriendlyErrorMessage(res.message));
            }
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } catch (e: any) {
            setEsConnected(false);
            setError(getFriendlyErrorMessage(e.message));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (step === 2 && configs['spring.elasticsearch.uris'] && configs['APP_ES_API_KEY'] && !esConnected) {
            testEs();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [step]);

    const handleFinalSubmit = async () => {
        if (!adminForm.email || !adminForm.username || !adminForm.password || !adminForm.confirmPassword) {
            setError("请填写所有必填项");
            return;
        }
        if (adminForm.password !== adminForm.confirmPassword) {
            setError("密码不匹配");
            return;
        }
        setLoading(true);
        try {
            // 1. Save Configs
            await saveSetupConfig(configs);

            // 2. Init Indices
            if (!indicesCreated) {
                await initIndices(selectedIndices);
            }

            // 3. Register Admin
            await completeSetup({
                email: adminForm.email,
                username: adminForm.username,
                password: adminForm.password
            });

            toast.success("设置完成！");
            navigate('/login', { state: { setupJustCompleted: true } });
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } catch (e: any) {
            const msg = getFriendlyErrorMessage(e.message);
            setError(msg);
            toast.error(msg);
        } finally {
            setLoading(false);
        }
    };

    const renderStepIndicator = () => {
        const steps = [
            { id: 1, title: "密钥配置", icon: Key },
            { id: 2, title: "ES 初始化", icon: Database },
            { id: 3, title: "管理员注册", icon: User }
        ];

        return (
            <div className="flex items-center justify-between mb-8 px-4">
                {steps.map((s, index) => {
                    const isActive = step === s.id;
                    const isCompleted = step > s.id;
                    const Icon = s.icon;

                    return (
                        <div key={s.id} className="flex-1 flex flex-col items-center relative z-10">
                            <div className={cn(
                                "w-10 h-10 rounded-full flex items-center justify-center border-2 transition-all duration-300",
                                isActive ? "bg-blue-600 border-blue-600 text-white shadow-lg scale-110" :
                                    isCompleted ? "bg-green-500 border-green-500 text-white" :
                                        "bg-white border-gray-300 text-gray-400"
                            )}>
                                {isCompleted ? <Check className="w-6 h-6" /> : <Icon className="w-5 h-5" />}
                            </div>
                            <span className={cn(
                                "mt-2 text-xs font-medium uppercase tracking-wider",
                                isActive ? "text-blue-600" :
                                    isCompleted ? "text-green-500" :
                                        "text-gray-400"
                            )}>
                                {s.title}
                            </span>
                            {index < steps.length - 1 && (
                                <div className="absolute top-5 left-1/2 w-full h-[2px] bg-gray-200 -z-10 transform translate-y-[-50%]">
                                    <div className={cn(
                                        "h-full bg-green-500 transition-all duration-500",
                                        isCompleted ? "w-full" : "w-0"
                                    )} />
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        );
    };

    const renderConfigField = (fieldKey: string, nextFieldKey?: string, action?: 'generate') => {
        const visible = !!visibleFields[fieldKey];
        const nextVisible = nextFieldKey ? !!visibleFields[nextFieldKey] : false;

        return (
            <div className="space-y-2">
                <div className="flex items-center justify-between">
                    <Label className="text-gray-700 flex items-center">
                        {getConfigLabel(fieldKey)}
                        <span className="ml-2 text-xs text-gray-400 font-normal">({fieldKey})</span>
                    </Label>
                    <button
                        type="button"
                        onClick={() => setHelpKey(fieldKey)}
                        className="text-gray-400 hover:text-blue-500 transition-colors"
                    >
                        <HelpCircle className="w-4 h-4" />
                    </button>
                </div>
                <div className="flex space-x-2">
                    <div className="relative flex-1">
                        <Input
                            type={getConfigInputType(fieldKey, visible)}
                            value={getConfigInputValue(fieldKey, visible, encryptedValues, configs)}
                            readOnly={visible}
                            onChange={e => !visible && handleConfigChange(fieldKey, e.target.value)}
                            className={cn("pr-10", getReadOnlyInputClass(visible))}
                        />
                        <VisibilityToggleButton
                            fieldKey={fieldKey}
                            visible={visible}
                            onToggle={() => toggleVisibility(fieldKey)}
                            className="absolute right-0 top-0 h-full px-3 text-gray-400 hover:text-blue-500 hover:bg-transparent"
                        />
                    </div>
                    {action === 'generate' && (
                        <Button
                            type="button"
                            variant="outline"
                            onClick={generateKey}
                            className="shrink-0 flex items-center gap-1"
                        >
                            <Wand2 className="w-4 h-4" /> 生成
                        </Button>
                    )}
                </div>

                {nextFieldKey && (
                    <div className="mt-4 pt-2 border-t border-dashed border-gray-200">
                         <div className="flex items-center justify-between mb-2">
                            <Label className="text-gray-700 flex items-center">
                                {getConfigLabel(nextFieldKey)}
                                <span className="ml-2 text-xs text-gray-400 font-normal">({nextFieldKey})</span>
                            </Label>
                            <button
                                type="button"
                                onClick={() => setHelpKey(nextFieldKey)}
                                className="text-gray-400 hover:text-blue-500 transition-colors"
                            >
                                <HelpCircle className="w-4 h-4" />
                            </button>
                        </div>
                        <div className="flex space-x-2">
                            <div className="relative flex-1">
                                <Input
                                    type={getConfigInputType(nextFieldKey, nextVisible)}
                                    value={getConfigInputValue(nextFieldKey, nextVisible, encryptedValues, configs)}
                                    readOnly={nextVisible}
                                    onChange={e => !nextVisible && handleConfigChange(nextFieldKey, e.target.value)}
                                    className={cn("pr-10", getReadOnlyInputClass(nextVisible))}
                                />
                                <VisibilityToggleButton
                                    fieldKey={nextFieldKey}
                                    visible={nextVisible}
                                    onToggle={() => toggleVisibility(nextFieldKey)}
                                    className="absolute right-0 top-0 h-full px-3 text-gray-400 hover:text-blue-500 hover:bg-transparent"
                                />
                            </div>
                        </div>
                    </div>
                )}
            </div>
        );
    };

        return (
            <>
            <div className="min-h-screen bg-gray-50 flex flex-col items-center py-12 px-4 sm:px-6 lg:px-8">
                <div className="max-w-4xl w-full bg-white rounded-2xl shadow-xl overflow-hidden border border-gray-100">
                    {/* Header */}
                    <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-8 py-6 flex justify-between items-center text-white">
                        <div>
                            <h1 className="text-2xl font-bold flex items-center gap-2">
                                <Server className="w-8 h-8 opacity-80" />
                                系统初始化向导
                            </h1>
                            <p className="mt-1 text-blue-100 text-sm">请完成以下步骤以配置您的系统环境</p>
                        </div>
                        <div className="flex gap-3">
                            <Button
                                variant="secondary"
                                size="sm"
                                className="bg-white/10 text-white hover:bg-white/20 border-0"
                                onClick={() => document.getElementById('config-upload')?.click()}
                            >
                                <Upload className="w-4 h-4 mr-2" />
                                导入配置
                                <input
                                    id="config-upload"
                                    type="file"
                                    className="hidden"
                                    onChange={handleImport}
                                />
                            </Button>
                            <Button
                                variant="secondary"
                                size="sm"
                                className="bg-white/10 text-white hover:bg-white/20 border-0"
                                onClick={() => setStep(3)}
                            >
                                跳过
                            </Button>
                        </div>
                    </div>

                    {/* Body */}
                    <div className="p-8">
                        {renderStepIndicator()}

                        {error && (
                            <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg flex items-start gap-3">
                                <AlertCircle className="w-5 h-5 mt-0.5 shrink-0" />
                                <p>{error}</p>
                            </div>
                        )}

                        {step === 1 && (
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 animate-in fade-in slide-in-from-right-4 duration-300">
                                <div className="space-y-6">
                                    <div className="flex items-center gap-2 text-lg font-semibold text-gray-800 border-b pb-2">
                                        <Shield className="w-5 h-5 text-blue-600" />
                                        安全与 AI 配置
                                    </div>
                                    {renderConfigField('APP_AI_API_KEY')}
                                    {renderConfigField('APP_TOTP_MASTER_KEY', undefined, 'generate')}
                                    {renderConfigField('APP_AI_TOKENIZER_API_KEY')}

                                    <div className="pt-2 space-y-6">
                                        <div className="flex items-center gap-2 text-lg font-semibold text-gray-800 border-b pb-2">
                                            <Server className="w-5 h-5 text-blue-600" />
                                            站点信息
                                        </div>
                                        {renderConfigField('APP_SITE_COPYRIGHT')}
                                        {renderConfigField('APP_SITE_BEIAN')}
                                    </div>

                                    <div className="pt-2 space-y-6">
                                        <div className="flex items-center gap-2 text-lg font-semibold text-gray-800 border-b pb-2">
                                            <Image className="w-5 h-5 text-blue-600" />
                                            图片存储配置
                                        </div>
                                        <div className="space-y-2">
                                            <div className="flex items-center justify-between">
                                                <Label className="text-gray-700 flex items-center">
                                                    {getConfigLabel('IMAGE_STORAGE_MODE')}
                                                    <span className="ml-2 text-xs text-gray-400 font-normal">(IMAGE_STORAGE_MODE)</span>
                                                </Label>
                                                <button type="button" onClick={() => setHelpKey('IMAGE_STORAGE_MODE')} className="text-gray-400 hover:text-blue-500 transition-colors">
                                                    <HelpCircle className="w-4 h-4" />
                                                </button>
                                            </div>
                                            <select
                                                className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                                value={configs.IMAGE_STORAGE_MODE || 'DASHSCOPE_TEMP'}
                                                onChange={e => handleConfigChange('IMAGE_STORAGE_MODE', e.target.value)}
                                            >
                                                <option value="LOCAL">本地 URL（需公网 IP）</option>
                                                <option value="DASHSCOPE_TEMP">百炼临时存储（48h 有效期）</option>
                                                <option value="ALIYUN_OSS">阿里云 OSS</option>
                                            </select>
                                        </div>
                                        {configs.IMAGE_STORAGE_MODE === 'ALIYUN_OSS' && (
                                            <>
                                                {renderConfigField('IMAGE_STORAGE_OSS_ENDPOINT', 'IMAGE_STORAGE_OSS_REGION')}
                                                {renderConfigField('IMAGE_STORAGE_OSS_BUCKET')}
                                                {renderConfigField('IMAGE_STORAGE_OSS_ACCESS_KEY_ID')}
                                                {renderConfigField('IMAGE_STORAGE_OSS_ACCESS_KEY_SECRET')}
                                            </>
                                        )}
                                    </div>
                                </div>

                                <div className="space-y-6">
                                    <div className="flex items-center gap-2 text-lg font-semibold text-gray-800 border-b pb-2">
                                        <Mail className="w-5 h-5 text-blue-600" />
                                        邮件服务配置
                                    </div>
                                    {renderConfigField('APP_MAIL_HOST', 'APP_MAIL_PORT')}
                                    {renderConfigField('APP_MAIL_INBOX_HOST', 'APP_MAIL_INBOX_PORT')}
                                    {renderConfigField('APP_MAIL_FROM_ADDRESS')}
                                    {renderConfigField('APP_MAIL_USERNAME')}
                                    {renderConfigField('APP_MAIL_PASSWORD')}
                                </div>
                            </div>
                        )}

                        {step === 2 && (
                            <div className="space-y-8 max-w-2xl mx-auto animate-in fade-in slide-in-from-right-4 duration-300">
                                <div className="bg-blue-50 p-6 rounded-xl border border-blue-100">
                                    <h3 className="text-lg font-semibold text-blue-900 mb-4 flex items-center gap-2">
                                        <Database className="w-5 h-5" />
                                        连接配置
                                    </h3>
                                    <div className="space-y-4">
                                        {renderConfigField('spring.elasticsearch.uris')}
                                        {renderConfigField('APP_ES_API_KEY')}
                                        
                                        <div className="flex items-center gap-4 pt-2">
                                            <Button 
                                                onClick={testEs} 
                                                disabled={loading}
                                                className="bg-amber-500 hover:bg-amber-600 text-white"
                                            >
                                                测试连接
                                            </Button>
                                            {esConnected && (
                                                <span className="flex items-center text-green-600 font-bold bg-green-50 px-3 py-1 rounded-full border border-green-200">
                                                    <Check className="w-4 h-4 mr-1" /> 已连接
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                <div>
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="text-lg font-semibold text-gray-800">初始化索引</h3>
                                </div>
                                <div className="grid grid-cols-1 gap-3">
                                        {indices.map(idx => (
                                            <div key={idx} className="flex items-center p-3 border rounded-lg hover:bg-gray-50 transition-colors">
                                                <Checkbox
                                                    id={idx}
                                                    checked={selectedIndices.includes(idx)}
                                                    onCheckedChange={(checked) => {
                                                        if (checked === true) setSelectedIndices([...selectedIndices, idx]);
                                                        else setSelectedIndices(selectedIndices.filter(i => i !== idx));
                                                    }}
                                                />
                                                <Label htmlFor={idx} className="ml-3 flex-1 cursor-pointer font-medium text-gray-700">
                                                    {idx}
                                                </Label>
                                                <span className={cn(
                                                    "text-xs px-2 py-1 rounded",
                                                    indicesStatus[idx] === '已创建' ? "text-green-600 bg-green-100" :
                                                    indicesStatus[idx] === '未创建' ? "text-yellow-600 bg-yellow-100" :
                                                    "text-gray-400 bg-gray-100"
                                                )}>
                                                    状态：{indicesStatus[idx] || '未知'}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}

                        {step === 3 && (
                            <div className="max-w-md mx-auto space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
                                <div className="text-center mb-8">
                                    <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
                                        <User className="w-8 h-8 text-blue-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-gray-900">创建管理员账户</h2>
                                    <p className="text-gray-500 mt-2">这是系统初始化的最后一步</p>
                                </div>

                                <div className="space-y-4">
                                    <div className="space-y-2">
                                        <Label>管理员邮箱</Label>
                                        <Input 
                                            type="email" 
                                            value={adminForm.email} 
                                            onChange={e => setAdminForm({...adminForm, email: e.target.value})}
                                            placeholder="请输入管理员邮箱"
                                            className="placeholder:text-gray-300"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>用户名</Label>
                                        <Input 
                                            type="text" 
                                            value={adminForm.username} 
                                            onChange={e => setAdminForm({...adminForm, username: e.target.value})}
                                            placeholder="请输入用户名"
                                            className="placeholder:text-gray-300"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>密码</Label>
                                        <Input 
                                            type="password" 
                                            value={adminForm.password} 
                                            onChange={e => setAdminForm({...adminForm, password: e.target.value})}
                                            placeholder="请输入密码"
                                            className="placeholder:text-gray-300"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>确认密码</Label>
                                        <Input 
                                            type="password" 
                                            value={adminForm.confirmPassword} 
                                            onChange={e => setAdminForm({...adminForm, confirmPassword: e.target.value})}
                                            placeholder="请再次输入密码"
                                            className="placeholder:text-gray-300"
                                        />
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Footer Navigation */}
                        <div className="mt-10 pt-6 border-t border-gray-100 flex justify-between">
                            {step > 1 ? (
                                <Button variant="outline" onClick={() => setStep(step - 1)}>
                                    <ArrowLeft className="w-4 h-4 mr-2" /> 上一步
                                </Button>
                            ) : (
                                <div></div> // Spacer
                            )}

                            {step < 3 ? (
                                <Button 
                                    onClick={nextStep} 
                                    disabled={step === 2 && !configs['spring.elasticsearch.uris']}
                                    className={cn(
                                        step === 2 && !configs['spring.elasticsearch.uris'] ? "bg-gray-400" : "bg-blue-600 hover:bg-blue-700"
                                    )}
                                >
                                    {step === 2 ? "创建" : "下一步"} {step !== 2 && <ArrowRight className="w-4 h-4 ml-2" />}
                                </Button>
                            ) : (
                                <Button onClick={handleFinalSubmit} className="bg-green-600 hover:bg-green-700 text-white px-8">
                                    完成设置 <Check className="w-4 h-4 ml-2" />
                                </Button>
                            )}
                        </div>
                    </div>
                </div>

                <Modal
                    isOpen={!!helpKey}
                    onClose={() => setHelpKey(null)}
                    title={`配置说明: ${helpKey}`}
                >
                    <div className="p-4">
                        <p className="text-gray-600 leading-relaxed">
                            {helpKey ? getHelpText(helpKey) : ''}
                        </p>
                    </div>
                </Modal>

                {envFileDetected && (
                    <div className="fixed inset-0 flex items-center justify-center bg-black/50 z-50 animate-in fade-in duration-200">
                        <div className="bg-white p-6 rounded-lg shadow-xl max-w-md w-full mx-4">
                            <h3 className="text-lg font-bold mb-4 flex items-center gap-2">
                                <Upload className="w-5 h-5 text-blue-600" />
                                检测到配置文件
                            </h3>
                            <p className="text-gray-600 mb-6">
                                在项目根目录下检测到 .env 文件。
                                <br/>
                                出于安全原因，服务端不会返回 .env 明文内容。
                                <br/>
                                是否现在从本地手动选择文件导入？
                            </p>
                            <div className="flex justify-end gap-3">
                                <Button 
                                    variant="secondary" 
                                    onClick={() => setEnvFileDetected(false)}
                                    className="bg-gray-100 hover:bg-gray-200 text-gray-800"
                                >
                                    取消
                                </Button>
                                <Button 
                                    onClick={() => {
                                        setEnvFileDetected(false);
                                        document.getElementById('config-upload')?.click();
                                    }}
                                    className="bg-blue-600 hover:bg-blue-700 text-white"
                                >
                                    导入配置
                                </Button>
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {loading && (
                <div className="fixed inset-0 z-50 flex items-center justify-center">
                    <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />
                    <div className="relative bg-white rounded-2xl shadow-xl border border-gray-100 px-10 py-8 w-[90%] max-w-sm text-center">
                        <Loader2 className="w-12 h-12 text-blue-600 animate-spin mx-auto mb-4" />
                        <p className="text-lg font-medium text-gray-700">正在处理中，请稍候...</p>
                    </div>
                </div>
            )}
            </>
        );
    };

    export default ImportConfigurationForm;
