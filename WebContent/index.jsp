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
			<nav class="navbar navbar-default" role="navigation">
				<div class="container-fluid">
					<!-- Brand and toggle get grouped for better mobile display -->
					<div class="navbar-header">
						<a class="navbar-brand" href="">Music Recommender System</a>
					</div>
				</div><!-- /.container-fluid -->
			</nav>
			<div class="col-md-4 sidebar">
				<div class="well well-sm">
					<strong>Controls</strong>
				</div>
				<form class="form-horizontal" role="form" id="controls" name="controls">
					<div class="form-group">
						<label for="user" class="col-md-4 control-label">User</label>
						<div class="col-md-8">
							<input type="text" class="form-control" id="user" name="user" placeholder="Last.FM UserName" ng-model="user" typeahead="user for user in getUsers($viewValue)" />
						</div>
					</div>
					<div class="form-group">
						<label for="moodLength" class="col-md-4 control-label">Mood Length</label>
						<div class="col-md-8">
							<input type="number" class="form-control" id="moodLength" name="moodLength" placeholder="# of Tracks" ng-model="moodLength" />
						</div>
					</div>
					<div class="form-group">
						<label for="kNN" class="col-md-4 control-label"># of Users</label>
						<div class="col-md-8">
							<input type="number" class="form-control" id="kNN" name="kNN" placeholder="# of Users" ng-model="kNN" />
						</div>
					</div>
					<hr>
					<h4>Similarity Weightages</h4>
					<div class="form-group">
						<label for="artistWTG" class="col-md-4 control-label">Artist</label>
						<div class="col-md-8">
							<div class="input-group">
								<input type="number" class="form-control" id="artistWTG" name="artistWTG" placeholder="Artist weightage (in %)" ng-model="artistWTG" min="0" max="100" />
								<span class="input-group-addon">%</span>
							</div>
						</div>
					</div>
					<div class="form-group">
						<label for="loudnessWTG" class="col-md-4 control-label">Loudness</label>
						<div class="col-md-8">
							<div class="input-group">
								<input type="number" class="form-control" id="loudnessWTG" name="loudnessWTG" placeholder="Loudness weightage (in %)" ng-model="loudnessWTG" min="0" max="100" />
								<span class="input-group-addon">%</span>
							</div>
						</div>
					</div>
					<div class="form-group">
						<label for="tempoWTG" class="col-md-4 control-label">Tempo</label>
						<div class="col-md-8">
							<div class="input-group">
								<input type="number" class="form-control" id="tempoWTG" name="tempoWTG" placeholder="Tempo weightage (in %)" ng-model="tempoWTG" min="0" max="100" />
								<span class="input-group-addon">%</span>
							</div>
						</div>
					</div>
					<div class="form-group">
						<div class="col-sm-offset-4 col-sm-8">
							<span class="help-block" ng-show="similarityError">Sum of weightages must be 100.</span>
						</div>
					</div>
					<hr>
					<h4>Recommendation Weightages</h4>
					<div class="form-group">
						<label for="similarityWTG" class="col-md-4 control-label">Similarity</label>
						<div class="col-md-8">
							<div class="input-group">
								<input type="number" class="form-control" id="similarityWTG" name="similarityWTG" placeholder="Similarity weightage (in %)" ng-model="similarityWTG" min="0" max="100" />
								<span class="input-group-addon">%</span>
							</div>
						</div>
					</div>
					<div class="form-group">
						<label for="popularityWTG" class="col-md-4 control-label">Popularity</label>
						<div class="col-md-8">
							<div class="input-group">
								<input type="number" class="form-control" id="popularityWTG" name="popularityWTG" placeholder="Popularity weightage (in %)" ng-model="popularityWTG" min="0" max="100" />
								<span class="input-group-addon">%</span>
							</div>
						</div>
					</div>
					<div class="form-group">
						<div class="col-sm-offset-4 col-sm-8">
							<span class="help-block" ng-show="recommendationError">Sum of weightages must be 100.</span>
						</div>
					</div>
					<div class="form-group">
						<div class="col-sm-offset-4 col-sm-8">
							<a type="button" class="btn btn-default" ng-click="getRecommendations()">Recommend</a>
						</div>
					</div>
				</form>
			</div>
			<div class="col-md-offset-1 col-md-7 main">
				<div class="well well-sm">
					<strong>Recommendations<span ng-show="recommendations.length != 0"> for <em>{{user}}</em></span></strong>
				</div>
				<div ng-show="loadingRecommendations">
					<div class="well well-lg">
						<img src="assets/images/loading.gif" width="50px" />
					</div>
				</div>
				<div ng-hide="loadingRecommendations">
					<div class="well well-lg">
						<table class="table table-striped table-hover">
							<thead>
								<tr>
									<th>Title</th>
									<th>Artist</th>
								</tr>
							</thead>
							<tbody>
								<tr ng-repeat="recommendation in recommendations | orderBy:confidence:reverse">
									<td>{{recommendation.title}}</td>
									<td>{{recommendation.artist_name}}</td>
								</tr>
							</tbody>
						</table>
						<div ng-show="recommendations.length == 0">
							<p>No recommendations to show at this point</p>
						</div>
					</div>
				</div>
			</div>
		</div>
	</body>
</html>
