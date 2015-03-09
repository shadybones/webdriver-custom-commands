package com.outertia;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class MyCrawler {
    private OWebDriver wd;
    public MyCrawler(){
        wd = new OWebDriver().init();
    }
    public void gotoURL(String url){
        wd.get().get(url);
    }
    public Object evalJS(String js, Object... args){
        return wd.sendMessage(OWebDriver.COMMANDS.get(0), ImmutableMap.of("value",args,"name",js));
    }
    public static void main(String[] args){
        MyCrawler crawler = new MyCrawler();
        crawler.gotoURL("http://github.com");

        String currentURL = (String) crawler.evalJS("content.window.location.href");
        System.out.println("The current url is: "+currentURL);

        Map<String,?> navigator = (Map) crawler.evalJS("content.window.navigator");
        System.out.println("my user agent is: "+navigator.get("userAgent"));

    }
}
