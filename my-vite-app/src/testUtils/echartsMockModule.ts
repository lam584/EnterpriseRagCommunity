export type EChartsOption = unknown;

export type EChartsType = {
  setOption: (option: EChartsOption, opts?: unknown) => void;
  resize: () => void;
  dispose: () => void;
  clear: () => void;
  __dom: HTMLElement | null;
  __lastSetOption: { option: EChartsOption; opts: unknown } | null;
  __disposed: boolean;
};

function createChart(dom: HTMLElement | null): EChartsType {
  return {
    __dom: dom,
    __lastSetOption: null,
    __disposed: false,
    setOption(option, opts) {
      this.__lastSetOption = { option, opts };
    },
    resize() {},
    dispose() {
      this.__disposed = true;
    },
    clear() {
      this.__lastSetOption = null;
    },
  };
}

export function init(dom: HTMLElement, ...args: unknown[]): EChartsType {
  void args;
  return createChart(dom);
}
