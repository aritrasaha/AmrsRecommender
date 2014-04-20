define([ "angular", "bootstrap" ], function () {
    angular.module("amrs", []).controller("bodyCtrl", [ "$scope", function ($scope) {
        $scope.name = "Aritra Saha";
    }]);
    angular.bootstrap(document, ["amrs"]);
});
