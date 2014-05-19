define([ "angular", "bootstrap", "angular-ui-bootstrap" ], function () {
    angular.module("amrs", []).controller("bodyCtrl", [ "$scope", "$http", function ($scope, $http) {
        $scope.name = "Aritra Saha";
        
        $scope.kNN = 50;
        $scope.moodLength = 5;
        $scope.artistWTG = 60;
        $scope.loudnessWTG = 20;
        $scope.tempoWTG = 20;
        $scope.similarityWTG = 80;
        $scope.popularityWTG = 20;
        
        $scope.recommend = function () {
        	console.log("Mood Length: " + $scope.moodLength);
        	console.log("# of Users: " + $scope.kNN);
        };
        
        $scope.getUsers = function (suggestion) {
        	console.log("HAHA");
        	return $http.get("rest/users/", {
        		params: {
        			"suggestion" : suggestion
        		}
        	}).then(function (data) {
        		return data;
        	});
        };
        
        $scope.loadingRecommendations = false;
        
        $scope.recommendations = [];
        
        $scope.similarityError = false;
        $scope.recommendationError = false;
        
        var isError = function () {
        	$scope.similarityError = $scope.artistWTG + $scope.loudnessWTG + $scope.tempoWTG != 100;
        	$scope.recommendationError = $scope.similarityWTG + $scope.popularityWTG != 100;
        	return $scope.similarityError || $scope.recommendationError;
        };
        
        $scope.getRecommendations = function () {
        	if (isError()) {
        		return;
        	}
        	
        	$scope.loadingRecommendations = true;
        	$scope.recommendations = [];
        	$http({
        		method: "GET",
        		url: "rest/recommend/",
        		params: {
        			"user": $scope.user,
        			"k": $scope.kNN,
        			"mood-length": $scope.moodLength,
        			"artist-wtg" : $scope.artistWTG,
        			"loudness-wtg" : $scope.loudnessWTG,
        			"tempo-wtg" : $scope.tempoWTG,
        			"similarity-wtg" : $scope.similarityWTG,
        			"popularity-wtg" : $scope.popularityWTG,
        		}
        	}).success(function (data, status, headers, config) {
        		for (var i = 0; i < data.length; i++) {
        			var key = data[i].key;
        			var value = data[i].value;
        			$http({
        				method: "GET",
        				url: "rest/recommend/track-info",
        				params: {
        					"id": key
        				}
        			}).success(function (data, status, headers, config) {
        				data.confidence = value;
        				$scope.recommendations.push(data);
        			});
        		};
        		$scope.loadingRecommendations = false;
        	}).error(function(data, status, headers, config) {
        		$scope.loadingRecommendations = false;
        	});
        };
    }]);
    angular.bootstrap(document, ["amrs"]);
});
