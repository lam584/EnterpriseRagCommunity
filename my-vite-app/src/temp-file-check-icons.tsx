// 这是一个临时文件，用于测试可用的图标
import * as FaIcons from 'react-icons/fa';

// 创建一个组件来检查所有可用的图标
const IconsTest = () => {
  console.log(Object.keys(FaIcons));
  return (
    <div>Testing available icons</div>
  );
};

export default IconsTest;
