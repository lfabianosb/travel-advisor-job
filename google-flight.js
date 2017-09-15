var casper = require("casper").create({
    pageSettings: {
        loadImages:  false,
        loadPlugins: false,
        userAgent:   'Mozilla/5.0 (Windows NT 6.1; rv:53.0) Gecko/20100101 Firefox/53.0'
    },
    logLevel: "info",
    verbose: true
});

var target = casper.cli.get(0);
var flight = {};

casper.start(target, function() {
    this.wait(15000, function() {
    	console.log('\n[' + new Date().toLocaleString() + '] ----------------------------> image 1');
    	this.capture('C:\\tmp\\google-flight1.png');

        var voo = this.evaluate(function() {
            return document.querySelector('.EIGTDNC-d-t div.EIGTDNC-d-Ab');
        });

        if (voo) {
            flight.valor = voo.textContent.trim().substring(2).replace('.','').replace(',','.');
            var cia = this.evaluate(function() {
                return document.querySelector('.EIGTDNC-d-t div.EIGTDNC-d-Ab').parentNode.parentNode.parentNode.childNodes[1].childNodes[2];
            });
            var escalas = this.evaluate(function() {
                return document.querySelector('.EIGTDNC-d-t div.EIGTDNC-d-Ab').parentNode.parentNode.parentNode.childNodes[3].childNodes[0];
            });
            var duracao = this.evaluate(function() {
                return document.querySelector('.EIGTDNC-d-t div.EIGTDNC-d-Ab').parentNode.parentNode.parentNode.childNodes[2].childNodes[0];
            });


            if (cia) {
                flight.cia = cia.textContent;
                if (escalas) {
                    flight.escalas = escalas.textContent;
                }
                if (duracao) {
                    flight.duracao = duracao.textContent;
                }
            } else {
                flight.msg = 'Companhia aérea não encontrada'
            }
        } else {
            flight.msg = 'Voo não encontrado'
        }

        console.log(JSON.stringify(flight, null, null));
    });
});

casper.run();