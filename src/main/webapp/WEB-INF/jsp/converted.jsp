<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://example.com/tld/myfunctions" prefix="mF" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>转换结果</title></head>
<body>
原文：${param.content} <br/>
转换后（全小写）：${mF:toLowercase(param.content)}
<p><a href="home">回到首页</a></p>
</body>
</html>