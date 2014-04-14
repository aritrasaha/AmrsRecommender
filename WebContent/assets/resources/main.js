require.config({
    baseUrl : "assets",
    paths : {
        "angular" : "libs/angular-1.2.16/angular.min",
        "angular-ui-bootstrap" : "libs/angular-ui/ui-bootstrap-tpls-0.10.0.min",
        "bootstrap" : "libs/bootstrap-3.1.1-dist/js/bootstrap.min",
        "jquery" : "libs/jQuery/jquery-2.1.0.min"
    },
    shim : {
        "angular-ui-bootstrap" : {
            deps : [ "angular", "bootstrap" ]
        },
        "bootstrap" : {
            deps : [ "jquery" ]
        },
        "jquery" : {
            exports : "$"
        }
    }
});

require([ "resources/amrs" ], function() {
    console.log("I'm done loading");
});
