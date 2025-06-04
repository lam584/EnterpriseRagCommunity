<!DOCTYPE html>
<html>
<head><title>Welcome</title></head>
<body>
<h1><a href="/home">测试jsp</a></h1>
<h1><a href="/library">测试React</a></h1>
<h1>欢迎，${user.username}！</h1>
<h2>登录信息</h2>
<ul>
    <li>用户名：${user.username}</li>
    <li>密码：${user.password}</li>
    <li>性别：${user.gender}</li>
    <li>邮箱：${user.email}</li>
    <li>电话：${user.phone!''}</li>
    <li>学号：${user.studentId!''}</li>
    <li>注册时间：${user.createdAt?string("yyyy-MM-dd HH:mm:ss")}</li>
</ul>

<form method="post" action="/logout">
    <#if _csrf??>
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    </#if>
    <button type="submit">登出</button>
</form>
</body>
</html>