// src/mockData/newsData.ts
export interface NewsItem {
  id: string;
  title: string;
  summary: string;
  content: string;
  author: string;
  publishDate: string;
  coverImage?: string;
  topics: string[];
  views: number;
}

export interface Topic {
  id: string;
  name: string;
  description?: string;
}

// mn主题数据
export const mockTopics: Topic[] = [
  { id: '1', name: '时政', description: '时事政治新闻' },
  { id: '2', name: '科技', description: '最新科技动态' },
  { id: '3', name: '教育', description: '教育相关新闻' },
  { id: '4', name: '文化', description: '文化艺术资讯' },
  { id: '5', name: '体育', description: '体育赛事报道' },
];

// mn新闻数据
export const mockNews: NewsItem[] = [
  {
    id: '1',
    title: '国家发展改革委召开新闻发布会',
    summary: '国家发展改革委今日召开新闻发布会，解读关于促进消费恢复提质的政策措施',
    content: `国家发展改革委今日召开新闻发布会，解读《关于促进消费恢复提质加快形成强大国内市场的实施方案》。
    
    会上，发改委相关负责人表示，将从六个方面发力，促进消费恢复提质：一是着力稳定和扩大传统消费；二是积极培育新型消费；三是大力发展服务消费；四是持续提升消费能力；五是不断优化消费环境；六是全面取消对消费的不合理限制。
    
    针对房地产市场，发改委表示将落实好现有房地产政策，继续做好保交楼、保民生、保稳定各项工作，保持房地产市场平稳健康发展。`,
    author: '央视新闻',
    publishDate: '2025-06-20',
    coverImage: 'https://picsum.photos/800/450',
    topics: ['1'],
    views: 1256
  },
  {
    id: '2',
    title: '人工智能大模型迎来新进展',
    summary: '最新研究表明，大规模语言模型在科学发现领域展现出突破性能力',
    content: `近日，多家科技公司和研究机构发布了最新一代大规模语言模型，这些模型不仅在自然语言处理能力上有所提升，更在科学发现领域展现出突破性能力。
    
    据报道，新一代AI模型能够分析大量科学文献，提出有价值的研究假设，甚至在某些领域已经能够辅助科学家进行实验设计。在医药研发、材料科学等领域，AI辅助研究已经取得了显著成果。
    
    专家表示，人工智能与科研工作的深度结合，将大大加速科学发现的进程，推动人类知识边界的拓展。`,
    author: '科技日报',
    publishDate: '2025-06-19',
    coverImage: 'https://picsum.photos/800/451',
    topics: ['2'],
    views: 3421
  },
  {
    id: '3',
    title: '高校毕业生就业形势分析',
    summary: '今年全国高校毕业生人数再创新高，就业工作面临挑战与机遇',
    content: `2025年全国高校毕业生人数达到1158万人，再创历史新高。面对庞大的就业需求，各地各高校积极开展就业指导和服务工作。
    
    教育部门统计数据显示，截至5月底，全国高校毕业生就业率为72.8%，与去年同期基本持平。其中，理工类专业毕业生就业率相对较高，人文社科类专业毕业生就业压力较大。
    
    针对当前就业形势，教育部与人社部联合推出了"2025届高校毕业生就业创业促进计划"，包括扩大基层就业项目规模、增加国企招聘岗位、支持创业带动就业等一系列措施。`,
    author: '中国教育报',
    publishDate: '2025-06-18',
    coverImage: 'https://picsum.photos/800/452',
    topics: ['3'],
    views: 2134
  },
  {
    id: '4',
    title: '全国大学生创新创业大赛落幕',
    summary: '第十六届"互联网+"大学生创新创业大赛总决赛在京举行',
    content: `第十六届中国"互联网+"大学生创新创业大赛总决赛昨日在北京圆满落幕。本届大赛共吸引了来自全国及海外高校的150多万个项目、580多万名大学生参赛，创历史新高。
    
    经过激烈角逐，清华大学"量子计算云平台"项目获得冠军，该项目成功研发了具有自主知识产权的量子计算系统，并通过云服务模式向科研机构和企业开放。
    
    大赛评委、著名创业投资人李明表示，本届大赛展示的项目整体水平显著提升，特别是在硬科技领域，不少项目已具备产业化潜力。据统计，往届大赛中涌现的优秀项目已有30%实现了成功转化。`,
    author: '科技创新报',
    publishDate: '2025-06-17',
    coverImage: 'https://picsum.photos/800/453',
    topics: ['2', '3'],
    views: 1876
  },
  {
    id: '5',
    title: '国家博物馆举办"丝路文明"特展',
    summary: '汇集16国珍贵文物，全面展示丝绸之路文化交流历史',
    content: `由国家文物局主办、中国国家博物馆承办的"丝路文明：古代东西方文化交流珍宝展"昨日在北京开幕。本次展览汇集了中国及沿线15个国家的260余件珍贵文物，全面展示了丝绸之路沿线国家两千多年来的文化交流历史。
    
    展览分为"道路联通""商贸互通""文化相通"三个主题单元，通过精美的文物实物、多媒体互动装置等多种形式，生动呈现了丝绸之路在促进东西方文明交流互鉴中的重要作用。
    
    国家博物馆馆长王强表示，本次展览是近年来规模最大、参展国家最多的丝路文明主题展览，对增进沿线国家人民相互了解和友谊具有重要意义。展览将持续至9月底，预计参观人数将超过50万。`,
    author: '文化周报',
    publishDate: '2025-06-16',
    coverImage: 'https://picsum.photos/800/454',
    topics: ['4'],
    views: 2567
  },
  {
    id: '6',
    title: '全运会倒计时100天 各项筹备工作就绪',
    summary: '第十五届全国运动会将于9月在浙江举行，目前各项筹备工作已基本就绪',
    content: `第十五届全国运动会开幕倒计时100天新闻发布会今天在杭州举行。组委会表示，目前各项筹备工作已基本就绪，43个比赛场馆全部完成建设和改造，赛事组织、场馆运行、安保、交通等保障方案已全部制定完毕。
    
    本届全运会将设置38个大项、410个小项的比赛，预计有来自各省、自治区、直辖市及港澳台地区的约12000名运动员参赛。为确保比赛顺利进行，组委会已组建了一支近4万人的志愿者队伍，提供全方位服务。
    
    据了解，本届全运会将首次全面实现5G+8K超高清转播，观众可通过电视、网络等多种渠道观看高清赛事直播。此外，组委会还推出了数字火炬传递、AI体育解说等创新项目，打造"智慧全运"。`,
    author: '体育新闻网',
    publishDate: '2025-06-15',
    coverImage: 'https://picsum.photos/800/455',
    topics: ['5'],
    views: 3245
  },
];

// 根据主题ID获取新闻列表
export const getNewsByTopic = (topicId: string): NewsItem[] => {
  return mockNews.filter(news => news.topics.includes(topicId));
};

// 根据新闻ID获取新闻详情
export const getNewsById = (newsId: string): NewsItem | undefined => {
  return mockNews.find(news => news.id === newsId);
};

// 获取最新新闻（按发布日期排序）
export const getLatestNews = (limit: number = 10): NewsItem[] => {
  return [...mockNews].sort((a, b) =>
    new Date(b.publishDate).getTime() - new Date(a.publishDate).getTime()
  ).slice(0, limit);
};

// 获取热门新闻（按浏览量排序）
export const getHotNews = (limit: number = 5): NewsItem[] => {
  return [...mockNews].sort((a, b) => b.views - a.views).slice(0, limit);
};
