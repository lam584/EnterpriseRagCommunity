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

const fontsDir = resolve(__dirname, 'src/assets/fonts');
const enableFontsCopy = hasFiles(fontsDir);

export default defineConfig({
    resolve: {
        alias: {
            '/components': path.resolve(__dirname, 'src/components'),
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
        rollupOptions: {
            input,
            output: {
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
