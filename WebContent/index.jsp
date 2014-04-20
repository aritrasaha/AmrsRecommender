<%@ page language="java" contentType="text/html; charset=US-ASCII" pageEncoding="US-ASCII"%>
<!DOCTYPE>
<html lang="en">
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=US-ASCII" />
		<title>AMRS</title>
		
		<script type="text/javascript" src="assets/libs/requirejs/require.js"></script>
		<script type="text/javascript">
			require(["assets/resources/main"], function () {
				require(["resources/amrs"]);
			});
		</script>
		
		<link rel="stylesheet" type="text/css" href="assets/libs/bootstrap-3.1.1-dist/css/bootstrap.min.css" />
		<link rel="stylesheet" type="text/css" href="assets/resources/global.css" />
	</head>
	
	<body>
		<div class="container" ng-controller="bodyCtrl">
			<p>{{name}}</p>
		</div>
	</body>
</html>
