import BoardForm from './content/board';
import BoardManagement from './content/BoardManagement';
import PostForm from './content/post';
import CommentForm from './content/comment';
import TagsForm from './content/tags';
import QueueForm from './review/queue';
import RulesForm from './review/rules';
import EmbedForm from './review/embed';
import LlmForm from './review/llm';
import FallbackForm from './review/fallback';
import LogsForm from './review/logs';
import RiskTagsForm from './review/risk-tags';
import TitleGenForm from './semantic/title-gen';
import MultiLabelForm from './semantic/multi-label';
import SummaryForm from './semantic/summary';
import TranslateForm from './semantic/translate';
import VectorIndexForm from './retrieval/vector-index';
import HybridSearchForm from './retrieval/hybrid';
import ContextClipForm from './retrieval/context';
import CitationForm from './retrieval/citation';
import MetricsForm from './metrics/metrics';
import AbtestForm from './metrics/abtest';
import TokenForm from './metrics/token';
import LabelQualityForm from './metrics/label-quality';
import CostForm from './metrics/cost';
import UserRoleForm from './users/user-role';
import RolesForm from './users/roles';
import MatrixForm from './users/matrix';
import TwoFAForm from './users/2fa';

export const formsRegistry: Record<string, React.FC> = {
  'board': BoardForm,
  'board-management': BoardManagement,
  'post': PostForm,
  'comment': CommentForm,
  'tags': TagsForm,
  'queue': QueueForm,
  'rules': RulesForm,
  'embed': EmbedForm,
  'llm': LlmForm,
  'fallback': FallbackForm,
  'logs': LogsForm,
  'risk-tags': RiskTagsForm,
  'title-gen': TitleGenForm,
  'multi-label': MultiLabelForm,
  'summary': SummaryForm,
  'translate': TranslateForm,
  'index': VectorIndexForm,
  'hybrid': HybridSearchForm,
  'context': ContextClipForm,
  'citation': CitationForm,
  'metrics': MetricsForm,
  'abtest': AbtestForm,
  'token': TokenForm,
  'label-quality': LabelQualityForm,
  'cost': CostForm,
  'user-role': UserRoleForm,
  'roles': RolesForm,
  'matrix': MatrixForm,
  '2fa': TwoFAForm,
};
