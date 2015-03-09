/*
 This file is for the WebDriver integration, which is basically leeching off the Server instance WebDriver uses
 to communicate with the Java runtime.
 */

/* fake global for this demo*/
$ttw = {settings:{},log:function(){console.log.apply(console,arguments);}};

$ttw.initWebDriverExt = function() {

    //Get the WebDriver server object reference
    $ttw.wdServer = (window.driver || parent.driver || opener && opener.driver || {}).server;

    $ttw.wdServer && $ttw.wdServer.server_.registerGlobHandler(".*/browserProperty", {

        handle: /**
         * Callback for handling browserProperty service. Basically just takes the call body and JSON parses it.
         * Expected body: {  value: XXX  } where XXX is the value you wish allowScriptNavigation to be.
         * @param {Ci.nsIHttpRequest} req
         * @param {Ci.nsIHttpResponse} resp
         */
            function (req, resp) {
            var ins = Components.classes["@mozilla.org/scriptableinputstream;1"].createInstance(Components.interfaces.nsIScriptableInputStream);
            ins.init(req.bodyInputStream);
            for (var c = "", d = ins.read(1024); d; c += d, d = ins.read(1024)) {
            }
            try {
                var job = JSON.parse(c);
                if (job && job.name) {
                    if (req.method == "POST" && job.value != undefined) {
                        $ttw.settings[job.name] = job.value;
                        $ttw.log("Set browser property: " + job.name + " to value:" + job.value);
                        resp.setStatusLine(null, 204, "No Content");
                        return;
                    } else if (req.method == "GET") {
                        resp.setHeader("Content-Type", "application/json; charset=utf-8", !1);
                        resp.write(JSON.stringify({value: ($ttw.settings[job.name] || "")}));
                        return;
                    }
                }
                $ttw.log("Got bad request to browserProperty, job object: " + c);
                resp.setStatusLine(null, 400, "Bad Request - Content does not contain required fields");
            } catch (e) {
                $ttw.log("Error caused by browserProperty: " + c, e);
                resp.setStatusLine(null, 500, "Request caused an error in the browser, " + e);
            }
            resp.setHeader("Content-Length", 0, !1);
            //resp.finish();
        }
    });

    $ttw.wdServer && $ttw.wdServer.server_.registerGlobHandler(".*/jseval", {

        handle: /**
         * Callback for handling js evaluation service. Basically just takes the call body and JSON parses it.
         * Expected body: {  name: YYY, value: XXX  } where YYY is the function to evaluate and XXX are any params.
         * @param {Ci.nsIHttpRequest} req
         * @param {Ci.nsIHttpResponse} resp
         */
            function (req, resp) {
            var ins = Components.classes["@mozilla.org/scriptableinputstream;1"].createInstance(Components.interfaces.nsIScriptableInputStream);
            ins.init(req.bodyInputStream);
            for (var c = "", d = ins.read(1024); d; c += d, d = ins.read(1024)) {
            }
            try {
                var job = JSON.parse(c);
                if (job && job.name) {
                    var fun = eval("(" + job.name + ")");
                    if (typeof fun == "function") {
                        fun = fun.apply(this, job.value || []);
                    }
                    if (fun != null) {
                        resp.setHeader("Content-Type", "application/json; charset=utf-8", !1);
                        resp.write(JSON.stringify({value: fun}));
                        //resp.setHeader("Content-Length", content.length, !1);
                    } else {
                        $ttw.log("evaluated " + job.name + " with no result");
                        resp.setStatusLine(null, 204, "No Content");
                        resp.setHeader("Content-Length", 0, !1);
                    }
                    return;
                }
                $ttw.log("Got bad request to browserProperty, job object: " + c);
                resp.setStatusLine(null, 400, "Bad Request - Content does not contain required fields");
            } catch (e) {
                $ttw.log("Error caused by jseval: " + c, e);
                resp.setStatusLine(null, 500, "Request caused an error in the browser, " + e);
            }
        }
    });
};

window.addEventListener("load", $ttw.initWebDriverExt, false);