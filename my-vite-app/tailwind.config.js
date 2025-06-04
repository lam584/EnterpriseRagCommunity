module.exports = {
  content: [
    // Vite HTML 入口
    "./index.html",
    // React 源码
    "./src/**/*.{js,ts,jsx,tsx}",
    // 如果你在 FreeMarker (.ftl) 里也想用 Tailwind 类，可以加：
    "../src/main/resources/templates/login.ftl"
  ],
  theme: {
    extend: {
      // 根据需要自定义主题
    }
  },
  plugins: [
    // 官方插件举例
    // require('@tailwindcss/forms'),
    // require('@tailwindcss/typography'),
  ],
}