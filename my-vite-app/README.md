# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react/README.md) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default tseslint.config({
  extends: [
    // Remove ...tseslint.configs.recommended and replace with this
    ...tseslint.configs.recommendedTypeChecked,
    // Alternatively, use this for stricter rules
    ...tseslint.configs.strictTypeChecked,
    // Optionally, add this for stylistic rules
    ...tseslint.configs.stylisticTypeChecked,
  ],
  languageOptions: {
    // other options...
    parserOptions: {
      project: ['./tsconfig.node.json', './tsconfig.app.json'],
      tsconfigRootDir: import.meta.dirname,
    },
  },
})
```

## Tests & Coverage

### Commands
- `npm run test`: run Vitest once
- `npm run test:ci`: run Vitest with coverage + generate reports + enforce incremental coverage gate

### Coverage reports
- HTML: `test-reports/vitest-coverage/index.html`
- JUnit: `test-reports/vitest-junit.xml`

### Coverage scope (Vitest config)
Coverage includes `src/**/*.{ts,tsx}` and excludes:
- `src/**/*.test.{ts,tsx}`
- `src/**/*.d.ts`
- `src/assets/**`
- `src/pages/**`
- `src/types/**`
- `src/vite-env.d.ts`
- `src/main.tsx`

### Incremental coverage gate
`npm run test:ci` runs `scripts/check-changed-files-coverage.mjs`, which enforces 100% (lines/branches/functions/statements) for changed `src/**` non-test `ts/tsx` files, excluding the same non-business-logic paths as above.

### Note on HTML navigation
If you open `test-reports/vitest-coverage/src/index.html`, it is a directory view for `src`. This repository also generates directory pages (e.g. `src/services/index.html`). The post-step `scripts/fix-istanbul-src-index.mjs` makes `src/index.html` list key subdirectories for easier navigation.

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default tseslint.config({
  plugins: {
    // Add the react-x and react-dom plugins
    'react-x': reactX,
    'react-dom': reactDom,
  },
  rules: {
    // other rules...
    // Enable its recommended typescript rules
    ...reactX.configs['recommended-typescript'].rules,
    ...reactDom.configs.recommended.rules,
  },
})
```
