var casper = require("casper").create({
    pageSettings: {
        loadImages:  false,
        loadPlugins: false,
        userAgent:   'Mozilla/5.0 (Windows NT 6.1; rv:53.0) Gecko/20100101 Firefox/53.0'
    },
    logLevel: "info",
    verbose: false
});
var system = require('system');

var target = casper.cli.get(0);
var ENCARGO = system.env.ENCARGO || 7;
var TIMEOUT = system.env.SCRAP_TIMEOUT || 45;
var flight = {};

casper.on('error', function(msg, backtrace) {
    flight.msg = 'Error message: ' + msg + '\nBacktrace: ' + backtrace;
    console.log(JSON.stringify(flight, null, null));
    this.exit();
});

casper.start(target, function() {
    this.wait(10000);

    //Waiting page load
    this.waitWhileVisible('#vn-content-view > div.fluxo-content > div > span',
        function sucess() {
            this.wait(5000);

            var totalPassagens = this.evaluate(function() {
                return document.querySelector('#vn-content-view > div.fluxo-content > flight-detail > ul > li:nth-child(1) > div.price.sticky_price > div > p.ng-binding > span').textContent;
            });
            var fltTotalPassagens = totalPassagens.trim().substring(3).replace('.','').replace(',','.')

            var taxasEncargos = this.evaluate(function() {
                return document.querySelector('#vn-content-view > div.fluxo-content > flight-detail > ul > li:nth-child(1) > div.price.sticky_price > div > p:nth-child(2) > span').textContent;
            });
            var fltTaxasEncargos = taxasEncargos.trim().substring(3).replace('.','').replace(',','.');

            var cia = this.evaluate(function() {
                return document.querySelector('#vn-content-view > div.fluxo-content > flight-detail > ul > li:nth-child(1) > div.flights > ul.ng-scope.ida > li.flight.ng-scope.flight-ida > label > div.list-cias > div > span').textContent;
            });

            var total = parseFloat((1 - (ENCARGO/100)) * fltTotalPassagens) + parseFloat(fltTaxasEncargos);
            var fltTotal = total.toFixed(2);

            flight.cia = cia;
            flight.valor = fltTotal;
            console.log(JSON.stringify(flight, null, null));
        },
        function fail() {
            flight.msg = 'Timeout for ' + target;
            console.log(JSON.stringify(flight, null, null));
            this.exit();
        },
        TIMEOUT * 1000
    );
});

casper.run();