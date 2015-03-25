<html>
<head>
<title>Composer API Configuration</title>
<meta name="ROBOTS" content="NOINDEX, NOFOLLOW"/>
</head>
<body>
<h2>Composer API Configuration</h2>
<c.if test="${links.size > 0}">
<ul>
<c:forEach items="${patterns}" var="pattern">
<li><a href="${request.getAttribute("javax.servlet.forward.request_uri")}/${pattern}">${pattern}</a></li>
</c:forEach>
</ul>
<c:else>
<p>There are no REST extensions installed.  If you expected some, please verify your installation (did you restart the server?).</p>
</c:if>
</body>
</html>
