package com.outertia;

import com.google.common.collect.ImmutableMap;
import com.sun.istack.internal.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.io.TemporaryFilesystem;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.remote.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class OWebDriver {
    private HttpCommandExecutor commander;
    private Logger logger = Logger.getLogger(OWebDriver.class);
    protected RemoteWebDriver webDriver;
    private File profileDirectory = TemporaryFilesystem.getDefaultTmpFS().createTempDir("anon", "webdriver-profile");

    public static final ArrayList<String> COMMANDS = new ArrayList<String>(Arrays.asList(new String[]{ "jseval", "browserProperty" }));

    public OWebDriver init(){
        webDriver = new FirefoxDriver(createProfile());
        createCustomCommands();
        return this;
    }

    public RemoteWebDriver get(){
        return this.webDriver;
    }

    private void createCustomCommands(){
        try {
            //Initialize "commander" for sending remote commands to Firefox instance.
            //We don't know the port which WebDriver has chosen to use
            //So we have to dig into a bunch of private objects to get it
            Object ec = webDriver.getCommandExecutor();
            Field f = ec.getClass().getDeclaredField("connection");
            f.setAccessible(true);
            ec = f.get(ec);
            f = ec.getClass().getDeclaredField("delegate");
            f.setAccessible(true);
            ec = f.get(ec);
            f = ec.getClass().getDeclaredField("remoteServer");
            f.setAccessible(true);

            //Above was just to get to this point, now we have the remote server URL (http://localhost:XXXX/hub)
            //Add any service calls you want to a new CommandExecutor, use the URL minus the path.
            //PS. Can't use the path because server on Firefox side has listener for .*/hub/.* which prevents
            //a return value to any custom service URL.
            commander = new HttpCommandExecutor(
                    ImmutableMap.of(
                            "browserProperty", new CommandInfo("ocommand/browserProperty", HttpMethod.POST),
                            "jseval", new CommandInfo("ocommand/jseval", HttpMethod.POST)
                    ),
                    ((URL)f.get(ec)).toURI().resolve("").toURL());

        } catch (Exception e) {
            logger.severe("Error setting custom WD server URL - this will prevent custom calls to WD. Due to Java security restrictions.", e);
            //If you have the PORT which the RemoteWebDriver is using, then you don't need the Java reflection code above which caused this error.
            //Just use new URL("http://localhost:PORT/") instead of ((URL)f.get(ec)).toURI().resolve("").toURL(), above.
        }
    }

    private FirefoxProfile createProfile(){
        try{
            File chrome = new File(profileDirectory,"chrome/userContent.css");
            FileUtils.writeStringToFile(chrome, "@-moz-document url(\"about:blank\"){*{background:url(\"chrome://netexport/skin/autoexport-active.png\");background-repeat:no-repeat;}}");
        }catch(IOException e){  logger.warning("couldn't create profile about:blank custom css");  }

        FirefoxProfile firefoxProfile = new FirefoxProfile(profileDirectory);

        firefoxProfile.setPreference("browser.newtab.url", "about:blank");
        firefoxProfile.setPreference("browser.startup.homepage", "about:blank");
        firefoxProfile.setPreference("network.http.use-cache", false);
        firefoxProfile.setPreference("browser.cache.disk.enable", false);
        firefoxProfile.setPreference("browser.cache.memory.enable", false);
        firefoxProfile.setPreference("network.http.max-connections", 100);
        firefoxProfile.setPreference("network.http.max-connections-per-server", 20);
        firefoxProfile.setPreference("network.http.max-persistent-connections-per-server", 10);
        firefoxProfile.setPreference("network.http.max-persistent-connections-per-proxy ", 10);
        firefoxProfile.setPreference("network.http.pipelining ", true);
        firefoxProfile.setPreference("network.http.proxy.pipelining", true);
        firefoxProfile.setPreference("network.http.pipelining.maxrequests", 8);
        firefoxProfile.setPreference("network.http.request.max-start-delay", 0);
        firefoxProfile.setPreference("network.prefetch-next", false);
        firefoxProfile.setPreference("content.notify.interval", 500000);
        firefoxProfile.setPreference("content.notify.ontimer", true);
        firefoxProfile.setPreference("content.interrupt.parsing", true);
        firefoxProfile.setPreference("content.switch.threshold", 200000);
        firefoxProfile.setPreference("nglayout.initialpaint.delay", 500);

        firefoxProfile.setPreference("nglayout.debug.disable_xul_cache", true);

        firefoxProfile.setPreference("browser.search.update", false);
        firefoxProfile.setPreference("app.update.auto", false);
        firefoxProfile.setPreference("app.update.enabled", false);
        firefoxProfile.setPreference("extensions.update.autoUpdateDefault", false);
        firefoxProfile.setPreference("extensions.update.enabled", false);

        String userAgent = System.getProperty("webdriver.useragent");
        if(userAgent!=null && !userAgent.isEmpty())
        {
            firefoxProfile.setPreference("general.useragent.override", userAgent);
        }

        return firefoxProfile;
    }

    public Object sendMessage(String commandName, Map<String,?> parameters){
        if(!COMMANDS.contains(commandName)) return null;

        Command command = new Command(webDriver.getSessionId(), commandName, parameters);
        Object o = null;
        try {
            Response response;
            int now = 0, MAX = 10;
            for(response = commander.execute(command); response.getStatus() == ErrorCodes.UNKNOWN_COMMAND && now < MAX; response = commander.execute(command), now++){}
            o = response.getStatus() == ErrorCodes.SUCCESS ? response.getValue() : null;
        }catch( org.apache.http.ConnectionClosedException e){
            //because response was unexpected
            //cant do anything about it, so ignore
            logger.warning("Command "+commandName+" was sent to the remote web driver but was given no response");
        } catch (IOException e) {
            logger.severe("On command: "+commandName+", sent with params: "+parameters,e);
        }
        return o;
    }
}
