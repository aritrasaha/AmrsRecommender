define([ "angular", "bootstrap" ], function () {
    angular.module("amrs", ["ui.bootstrap"]);
    angular.module("amrs").controller("body-ctrl", [ "$scope", "$log", function ($scope, $log) {
        $scope.name = "Aritra Saha";
        $log.debug("AMRS loaded with Angular");
    }]);
    console.log("AMRS loaded");
});
