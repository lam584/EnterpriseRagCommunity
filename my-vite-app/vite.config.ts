//文件路径：my-vite-app/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { resolve, join } from 'path'
import { readdirSync, statSync } from 'fs'

function getAllCssFiles(dir: string): string[] {
    let results: string[] = [];
    const list = readdirSync(dir);
    list.forEach(file => {
        const filePath = join(dir, file);
        const stat = statSync(filePath);
        if (stat && stat.isDirectory()) {
            results = results.concat(getAllCssFiles(filePath));
        } else if (file.endsWith('.css')) {
            results.push(filePath);
        }
    });
    return results;
}

const styleDir = resolve(__dirname, 'src/assets/styles');
const cssFiles = getAllCssFiles(styleDir);

const input: Record<string, string> = {
    main: resolve(__dirname, 'index.html')
};
cssFiles.forEach(fullPath => {
    const key = fullPath.replace(styleDir + '\\', '').replace(styleDir + '/', '').replace(/\\/g, '/');
    input[key] = fullPath;
});

// 可选地加入 viteStaticCopy 插件保证 webfonts 目录也被 build 到 dist
import { viteStaticCopy } from 'vite-plugin-static-copy'
import * as path from "node:path";

function hasFiles(dir: string): boolean {
    try {
        const entries = readdirSync(dir);
        return entries.some(name => {
            // ignore common placeholders
            if (name === '.gitkeep' || name === '.DS_Store') return false;
            try {
                return statSync(join(dir, name)).isFile();
            } catch {
                return false;
            }
        });
    } catch {
        return false;
    }
}

function manualChunks(id: string): string | undefined {
    if (!id.includes('node_modules')) {
        return undefined;
    }

    const normalizedId = id.replace(/\\/g, '/');
    const packagePath = normalizedId.split('/node_modules/').pop();
    if (!packagePath) {
        return undefined;
    }

    const packageName = packagePath.startsWith('@')
        ? packagePath.split('/').slice(0, 2).join('/')
        : packagePath.split('/')[0];

    if (['react', 'react-dom', 'react-router', 'react-router-dom', 'scheduler'].includes(packageName)) {
        return 'vendor-react';
    }

    const markdownPackages = [
        'react-markdown',
        'property-information',
        'parse5',
        'entities',
        'hastscript',
        'inline-style-parser',
        'style-to-js',
        'style-to-object',
        'bail',
        'devlop',
        'trough',
        'zwitch',
        'web-namespaces',
        'space-separated-tokens',
        'comma-separated-tokens',
        'html-url-attributes',
        'html-void-elements',
        'decode-named-character-reference',
        'ccount',
        'is-plain-obj',
        'longest-streak',
        'markdown-table',
        'trim-lines'
    ];

    if (['rehype-highlight', 'highlight.js', 'lowlight'].includes(packageName)) {
        return 'vendor-markdown-highlight';
    }

    if (
        markdownPackages.includes(packageName) ||
        packageName.startsWith('remark-') ||
        packageName.startsWith('rehype-') ||
        packageName === 'highlight.js' ||
        packageName.startsWith('hast-') ||
        packageName.startsWith('mdast-') ||
        packageName.startsWith('micromark') ||
        packageName.startsWith('unist-') ||
        packageName.startsWith('vfile')
    ) {
        return 'vendor-markdown';
    }

    if (packageName === 'echarts') {
        return 'vendor-charts-core';
    }

    if (packageName === 'zrender') {
        return 'vendor-charts-runtime';
    }

    if (['@fortawesome/fontawesome-svg-core', '@fortawesome/free-brands-svg-icons', '@fortawesome/free-regular-svg-icons', '@fortawesome/free-solid-svg-icons', '@fortawesome/react-fontawesome', 'react-icons', 'lucide-react', '@heroicons/react'].includes(packageName)) {
        return 'vendor-icons';
    }

    if (packageName.startsWith('@radix-ui/')) {
        return 'vendor-ui';
    }

    if (packageName === 'react-datepicker' || packageName === 'date-fns') {
        return 'vendor-react-datepicker';
    }

    if (packageName === 'qrcode.react') {
        return 'vendor-qrcode.react';
    }

    return undefined;
}

const fontsDir = resolve(__dirname, 'src/assets/fonts');
const enableFontsCopy = hasFiles(fontsDir);

export default defineConfig({
    resolve: {
        alias: {
            '/components': path.resolve(__dirname, 'src/components'),
            '/services': path.resolve(__dirname, 'src/services'),
        },
    },
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8099',
                changeOrigin: true,
            },
            '/uploads': {
                target: 'http://localhost:8099',
                changeOrigin: true,
            },
            '/admin': {
                target: 'http://localhost:8099',
                changeOrigin: true,
            },
        },
    },
    plugins: [
        react(),
        ...(enableFontsCopy
            ? [
                viteStaticCopy({
                    targets: [
                        { src: 'src/assets/fonts/*', dest: 'fonts' }
                    ]
                })
            ]
            : [])
    ],
    build: {
        manifest: true,
        outDir: 'dist',
        assetsDir: 'assets',
        chunkSizeWarningLimit: 900,
        rollupOptions: {
            input,
            output: {
                manualChunks,
                chunkFileNames: 'assets/js/[name]-[hash].js',
                entryFileNames: 'assets/js/[name]-[hash].js',
                assetFileNames: assetInfo => {
                    if (assetInfo.name?.endsWith('.css')) {
                        return 'assets/css/[name]-[hash][extname]';
                    }
                    if (/\.(png|jpe?g|gif|svg|webp)$/.test(assetInfo.name ?? '')) {
                        return 'assets/img/[name]-[hash][extname]';
                    }
                    if (/\.(woff2?|ttf|eot|otf)$/.test(assetInfo.name ?? '')) {
                        return 'assets/fonts/[name]-[hash][extname]';
                    }
                    return 'assets/other/[name]-[hash][extname]';
                }
            }
        }
    }
})
