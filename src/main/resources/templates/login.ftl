<#--resources/templates/login.ftl-->
<!DOCTYPE html>
<html lang="zh-CN">
<head>

    <#macro viteEntry entry>
        <#if viteManifestService?? && viteManifestService.hasAsset(entry)>
            <#assign cssFiles = viteManifestService.getCssFiles(entry)>
        <#if cssFiles?size gt 0>
        <#list cssFiles as cssFile>
        <link rel="stylesheet" href="${cssFile}">
        </#list>
        <#else>
        <#-- 没有 css 字段，但是 file 字段是 css，直接输出 -->
            <#assign assetUrl = viteManifestService.getAssetUrl(entry)>
        <#if assetUrl?ends_with(".css")>
        <link rel="stylesheet" href="${assetUrl}">
        </#if>
        </#if>
        <#if viteManifestService.getAssetUrl(entry)?ends_with(".js")>
            <script type="module" src="${viteManifestService.getAssetUrl(entry)}"></script>
        </#if>
        <#else>
        <link rel="stylesheet" href="/assets/css/style.css">
        </#if>
    </#macro>

    <@viteEntry entry="src/assets/styles/style.css" />

</head>
<body>

<div class="container">

    <h1><a href="/home">测试jsp</a></h1>

    <h1>登录</h1>

    <#-- 如果存在错误信息，则显示提示 -->
    <#if errorMessage?? && (errorMessage != "")>
        <div class="error">${errorMessage}</div>
    </#if>
    <#if Message?? && (Message != "")>
        <div class="error">${Message}</div>
    </#if>
    <form action="/login" method="post">
        <#-- CSRF Token (重要!) -->
        <#-- 需要配置 Freemarker 以识别 _csrf -->
        <#-- 在 application.properties/yml 中添加: spring.freemarker.expose-request-attributes=true -->
        <#if _csrf??>
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        </#if>
        <div class="form-group">
            <label for="username">用户名</label>
            <input type="text" id="username" name="username" placeholder="请输入用户名" required>
        </div>
        <div class="form-group">
            <label for="password">密码</label>
            <input type="password" id="password" name="password" placeholder="请输入密码" required>
        </div>
        <div class="form-group checkbox-group">
            <input type="checkbox" id="remember" name="remember-me">
            <label for="remember-me">记住我</label>
        </div>
        <div class="form-group">
            <button type="submit">登录</button>
        </div>
    </form>
    <p>账号：admin    密码：123456</p>
</div>


</body>
</html>
