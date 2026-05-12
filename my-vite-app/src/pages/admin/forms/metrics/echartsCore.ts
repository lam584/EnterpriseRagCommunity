import { init, use, type ComposeOption, type EChartsType } from 'echarts/core';
import {
  GridComponent,
  type GridComponentOption,
} from 'echarts/components';
import {
  BarChart,
  LineChart,
  type BarSeriesOption,
  type LineSeriesOption,
} from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';

use([
  GridComponent,
  BarChart,
  LineChart,
  CanvasRenderer,
]);

export { init };

export type AppEChartsType = EChartsType;

export type AppEChartsOption = ComposeOption<
  | GridComponentOption
  | BarSeriesOption
  | LineSeriesOption
>;