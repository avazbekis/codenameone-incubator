/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.codename1.js;

import com.codename1.io.Log;
import com.codename1.io.Util;
import com.codename1.ui.BrowserComponent;
import com.codename1.ui.Display;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;
import com.codename1.ui.events.BrowserNavigationCallback;
import com.codename1.util.StringUtil;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Represents a Javascript context of a single BrowserComponent.  This provides
 * support for executing Javascript in the BrowserComponent, registering Java
 * callbacks to allow Javascript to call Java functions, and returning values
 * from Javascript to Java.
 * 
 * <p>Typically you would obtain a context for a BrowserComponent via its constructor,
 * passing the BrowserComponent to the context.</p>
 * <p>E.g.</p>
 * <code><pre>
 * WebBrowser b = new WebBrowser();
 * BrowserComponent bc = (BrowserComponent)b.getInternal();
 * JavascriptContext ctx = new JavascriptContext(bc);
 * JSObject window = (JSObject)ctx.get("window");
 * </pre></code>
 * 
 * @author shannah
 */
public class JavascriptContext  {
    
    /**
     * Flag to enable/disable logging to a debug log.
     */
    public static boolean DEBUG=false;
    
    /**
     * The browser component on which this context operates.
     * @see setBrowserComponent()
     * @see getBrowserComponent()
     */
    BrowserComponent browser;
    
    /**
     * Listener that listens for JavascriptEvents.  A Javascript event
     * is packaged by the JavascriptContext class in response to a 
     * BrowserNavigationCallback.
     */
    private ActionListener scriptMessageListener;
    
    /**
     * A handler for navigation attempts.  This intercepts URLs of the 
     * form cn1command:... .  This is how Javascript communicates/calls
     * methods in this context.
     */
    private BrowserNavigationCallback browserNavigationCallback;
    
    /**
     * Stores the previous BrowserNavigationCallback object if one 
     * was registered on the BrowserComponent.
     */
    private BrowserNavigationCallback previousNavigationCallback;
    
    /**
     * The name of the Javascript lookup table that is used to store and
     * look up Javascript objects that have a JSObject proxy.
     */
    String jsLookupTable;
    
    /**
     * A running counter for the next object ID that is to be assigned to
     * the next JSObject.  Each JSObject has an id associated with it which
     * corresponds with its position in the Javascript lookup table.
     */
    int objectId = 0;
    
    /**
     * Stores registered JSFunction callbacks which can be called in response
     * to a JavascriptEvent.
     */
    private Hashtable callbacks = new Hashtable();
    
    /**
     * Running counter to mark the context ID.  Each javascript context has its
     * own lookup table, and this running counter allows us to generate a unique
     * name for each lookup table.
     */
    private static int contextId = 0;
    
    /**
     * A dummy javascript variable that is used occasionally to workaround some bugs.
     */
    static final String DUMMY_VAR = "ca_weblite_codename1_js_JavascriptContext_DUMMY_VAR";
    
    /**
     * Javascript variable to store the return value of get() requests so that the value can be
     * returned.
     */
    static final String RETURN_VAR = "ca_weblite_codename1_js_JavascriptContext_RETURN_VAR";
    
    /**
     * The base name of the lookup table.  The actual name of the lookup table will have the
     * contextId appended to it, and be stored as the member variable jsLookupTable.
     */
    static final String LOOKUP_TABLE = "ca_weblite_codename1_js_JavascriptContext_LOOKUP_TABLE";
    
    /**
     * Creates a Javascript context for the given BrowserComponent.
     * @param c 
     */
    public JavascriptContext(BrowserComponent c){
        jsLookupTable = LOOKUP_TABLE+(contextId++);
        this.browserNavigationCallback = new NavigationCallback();
        this.scriptMessageListener = new ScriptMessageListener();
        this.setBrowserComponent(c);
    }
    /**
     * Sets the BrowserComponent on which this javascript context runs.
     * 
     * @param c The BrowserComponent on which the context runs.
     */
    public final void setBrowserComponent(BrowserComponent c){
        if ( c != browser ){
            if ( browser != null ){
                this.uninstall();
            }
            browser = c;
            if ( browser != null ){
                this.install();
                
            }
        }
    }
    
    /**
     * Executes a Javascript string and returns the string.  It is synchronized
     * to disallow multiple threads from running javascript on the same BrowserComponent.
     * 
     * <p>This is just a thin wrapper around the BrowserComponent.executeAndReturnString() method.</p>
     * 
     * @param js
     * @return The string result of executing the Javascript string.
     */
    private synchronized String exec(String js){
        if ( DEBUG ){
            //Log.p("About to execute "+js);
        }
        return browser.executeAndReturnString(installCode()+"("+js+")");
    }
    
    /**
     * Uninstalls the context from the browser component.  This just includes
     * the listeners that are registered with the BrowserComponent so that 
     * the context is informed of navigation callbacks and script message listeners.
     * 
     * @see install()
     * 
     */
    private void uninstall(){
        //browser.removeWebEventListener("shouldLoadURL", urlListener);
        browser.setBrowserNavigationCallback(previousNavigationCallback);
        browser.removeWebEventListener("scriptMessageReceived", scriptMessageListener);
    }
    
    /**
     * Installs the context in the current browser component.  This effectively
     * installs listeners in the browser component so that the context can 
     * be notified of events like navigation callbacks and script message received
     * events.
     */
    private void install(){
        //browser.addWebEventListener("shouldLoadURL", urlListener);
        previousNavigationCallback = browser.getBrowserNavigationCallback();
        browser.setBrowserNavigationCallback(browserNavigationCallback);
        
        browser.addWebEventListener("scriptMessageReceived", scriptMessageListener);
    }
    
    
    /**
     * Executes a javascript string and returns the result of the execution as
     * an appropriate object value depending on the type of value that was returned.
     * 
     * <p>Return value types will depend on the Javascript type returned.  The following
     * table shows the mappings:</p>
     * <table>
     *  <thead>
     *      <tr><th>Javascript Type</th><th>Java Return Type</th></tr>
     *  </thead>
     *  <tbody>
     *      <tr><td>Number</td><th>java.lang.Double</td></tr>
     *      <tr><td>String</td><th>java.lang.String</td><tr>
     *      <tr><td>Boolean</td><td>java.lang.Boolean</td></tr>
     *      <tr><td>Object</td><td>JSObject</td></tr>
     *      <tr><td>Function</td><td>JSObject</td></tr>
     *      <tr><td>null</td><td>null</td></tr>
     *      <tr><td>undefined</td><td>null</td></tr>
     *  </tbody>
     * </table>
     * 
     * <h5>Example</h5>
     * <code><pre>
     * //Get the window object
     * JSObject window = (JSObject)ctx.get("window");
     * 
     * // Create a new empty Javascript Object
     * JSObject newObj = (JSObject)ctx.get("{}");
     * 
     * // Get the current document body contents as a string.
     * String html = (String)ctx.get("document.body.innerHTML");
     * 
     * // Get a numerical result
     * Double result = (Double)ctx.get("1+2");
     * 
     * // Get a Javascript function object
     * JSObject func = (JSObject)ctx.get("function(a,b){ return a+b }");
     * 
     * // Get a boolean result
     * Boolean res = (Boolean)ctx.get("1 &lt; 2");
     * </pre></code>
     * @param javascript The javascript to be executed.
     * @return The result of the javascript expression.
     */
    public synchronized Object get(String javascript){
        String js2 = RETURN_VAR+"=("+javascript+")";
        String res = exec(js2);
        String typeQuery = "typeof("+RETURN_VAR+")";
        String type = exec(typeQuery);
        try {
            if ( "string".equals(type)){
                return res;
            } else if ( "number".equals(type)){
                return Double.valueOf(res);
            } else if ( "boolean".equals(type)){
                return "true".equals(res)?Boolean.TRUE:Boolean.FALSE;
            } else if ( "object".equals(type) || "function".equals(type)){
                return new JSObject(this, RETURN_VAR);
            } else {
                return null;
            }
        } catch ( Exception ex){
            Log.e(new RuntimeException("Failed to get javascript "+js2+".  The error was "+ex.getMessage()+".  The result was "+res+".  The type result was "+type+"."));
            return null;
        }
    }
    
    /**
     * Sets a Javascript value given a compatible Java object value.  This is an abstraction
     * upon javascript to execute <code>key = value</code>.
     * 
     * <p>The key is any Javascript expression whose result can be assigned. The value
     * is a Java object that will be converted into a Javascript object as follows:</p>
     * 
     * <table>
     *  <thead>
     *      <tr><th>Java type</th><th>Converted to</th></tr>
     *  </thead>
     *  <tbody>
     *      <tr><td>Double</td><th>Number</td></tr>
     *      <tr><td>Integer</td><th>Number</td><tr>
     *      <tr><td>Float</td><td>Number</td></tr>
     *      <tr><td>Long</td><td>Number</td></tr>
     *      <tr><td>String</td><td>String</td></tr>
     *      <tr><td>JSObject</td><td>Object by ref</td></tr>
     *      <tr><td>null</td><td>null</td></tr>
     *  </tbody>
     * </table>
     * 
     * <p>Hence if you want to set a Javascript string value, you can just
     * pass a Java string into this method and it will be converted. </p>
     * 
     * <h5>JSObject "By Ref"</h5>
     * <p>You may notice that if you pass a JSObject as the value parameter, the 
     * table above indicates that it is passed by reference.  A JSObject merely 
     * stores a reference to a Javascript object from a lookup table in the 
     * Javascript runtime environment.  It is this lookup that is ultimately 
     * assigned to the "key" when you pass a JSObject as the value.   This has
     * the effect of setting the actual Javascript Object to this value, which
     * is effectively a pass-by-reference scenario.</p>
     * 
     * <h5>Examples</h5>
     * 
     * <code><pre>
     * // Set the window.location.href to a new URL
     * ctx.set("window.location.href", "http://google.com");
     * 
     * // Create a new JSObject, and set it as a property of another JSObject
     * JSObject camera = (JSObject)ctx.get("{}");
     * ctx.set("window.camera", camera);
     * 
     * // Set the name of the camera via JSObject.set()
     * camera.set("name", "My Camera");
     * 
     * // Get the camera's name via Javascript
     * String cameraName = (String)ctx.get("window.camera.name");
     *     // Should be "My Camera"
     * 
     * // Set the camera name via context.set()
     * ctx.set("camera.name", "New name");
     * 
     * String newName = (String)camera.get("name");
     *     // Should be "New name"
     * 
     * </pre></code>
     * @param key A javascript expression whose result is being assigned the value.
     * @param value The object or value that is being assigned to the Javascript variable
     * on the left.</p>
     */
    public synchronized void set(String key, Object value){
        String lhs = key;
        String rhs = "undefined";
      
        if ( String.class.isInstance(value)){
            String escaped = StringUtil.replaceAll((String)value, "\\", "\\\\");
            escaped = StringUtil.replaceAll(escaped, "'", "\\'");
            rhs = "'"+escaped+"'";
        } else if ( value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double ){
            rhs =value.toString();
        } else if ( JSObject.class.isInstance(value)){
            rhs = ((JSObject)value).toJSPointer();
        } else if (value instanceof Boolean){
            rhs = ((Boolean)value).booleanValue()?"true":"false";
        } else {
            rhs = "null";
        }
        
        exec(lhs+"="+rhs);
    }
    
    
    /**
     * Calls the appropriate callback method given a URL that was received 
     * from the NavigationCallback.  It is set up to accept URLs of the 
     * form cn1command:object.method?type1=value1&type2=value2&...&typen=valuen
     * 
     * <p>This method parses the URL and converts all arguments (including the 
     * object and method) into their associated Java representations, then 
     * generates a JavascriptEvent to fire on the scriptMessageReceived
     * browser event.</p>
     * 
     * <p>This method will usually be called on the native platform's GUI
     * thread, but it dispatches the resulting JavascriptEvent on the EDT
     * using Display.callSerially()</p>
     * @param request The URL representing the command that is being called.
     */
    private void dispatchCallback(final String request){
        Runnable r = new Runnable(){
            public void run(){
                String command = request.substring(request.indexOf(":")+1);
                // Get the callback id
                String objMethod = command.substring(0, command.indexOf("?"));
                command = command.substring(command.indexOf("?")+1);

                final String self = objMethod.substring(0, objMethod.indexOf("."));
                String method = objMethod.substring(objMethod.indexOf(".")+1);

                // Now let's get the parameters
                String[] keyValuePairs = Util.split(command, "&");
                //Vector params = new Vector();

                int len = keyValuePairs.length;
                Object[] params = new Object[len];
                for ( int i=0; i<len; i++){
                    String[] parts = Util.split(keyValuePairs[i], "=");
                    if ( parts.length < 2 ){
                        continue;
                    }
                    String ptype = Util.decode(parts[0], null, true);
                    String pval = Util.decode(parts[1], null, true);
                    if ( "object".equals(ptype) || "function".equals(ptype) ){
                        params[i] = new JSObject(JavascriptContext.this, pval);
                    } else if ( "number".equals(ptype) ){
                        params[i] = Double.valueOf(pval);
                    } else if ( "string".equals(ptype)){
                        params[i] = pval;
                    } else if ( "boolean".equals(ptype)){
                        params[i] = "true".equals(pval)?Boolean.TRUE:Boolean.FALSE;
                    } else {
                        params[i] = null;
                    }
                }
                JSObject selfObj = new JSObject(JavascriptContext.this, self);

                JavascriptEvent evt = new JavascriptEvent(selfObj, method, params);
                browser.fireWebEvent("scriptMessageReceived", evt);
            }
        };
        
        Display.getInstance().callSerially(r);
        
        
        
    }
    
    /**
     * A navigation callback that handles navigations to urls of the form
     * cn1command:...
     * 
     * An instance of this class is installed in the resident BrowserComponent.
     */
    private class NavigationCallback implements BrowserNavigationCallback {

        public boolean shouldNavigate(String url) {
            
            if ( url.indexOf("cn1command:") == 0 ){
                //.. Handle the cn1 callbacks
                dispatchCallback(url);
                return false;
            }
            return previousNavigationCallback.shouldNavigate(url);
        }
        
    }
    
    
    /**
     * Handler for scriptMessageReceived events.  It processes
     * JavascriptEvents that encapsulate commands received from Javascript
     * to Java.  The dispatchCallback() method intercepts the requests using
     * a Navigation callback, then builds the JavascriptEvent object that
     * encapsulates a Javascript method call.  This event is ultimately fired
     * and then processed by this listener.
     * 
     * <p>This is intended to only process functions that are backed by 
     * a JSFunction object.  Prior to this call, it is assumed that a JSFunction
     * has been registered as a callback at the Javascript address provided via
     * the JSObject.addCallback() or JavascriptContext.addCallback() methods.</p>
     * 
     * <p>If there is no JSFunction registered to handle this command then nothing
     * will happen as a result of the request.</p>
     * 
     * @see JSFunction
     * @see addCallback()
     * @see JSObject.addCallback()
     * 
     */
    private class ScriptMessageListener implements ActionListener {

        public void actionPerformed(ActionEvent evt) {
            JavascriptEvent jevt = (JavascriptEvent)evt;
            JSObject source = jevt.getSelf();
            String method = jevt.getMethod();
            String key = source.toJSPointer()+"."+method;
            JSFunction func = (JSFunction)callbacks.get(key);
            if ( func == null ){
                // No callback is registered for this method.
                return;
            }
            func.apply(source, jevt.getArgs());
            evt.consume();
        }
        
    }
    
    
    /**
     * Stock Javascript code that is included before all javascript requests to create
     * a lookup table for the JS objects if one hasn't been created yet.
     * @return 
     */
    private String installCode(){
        return "if (typeof("+jsLookupTable+") == 'undefined'){"+jsLookupTable+"=[]}";
    }
    
    
    /**
     * Adds a JSFunction to handle calls to the specified Javascript object.  This 
     * essentially installed a Javascript proxy method that sends a message via
     * a navigation callback to the JavascriptContext so that it can cause Java
     * code to be executed.
     * 
     * 
     * @param source The Javascript object on which the callback is being registered
     * as a member method.
     * @param method The name of the method that will be created to execute our callback.
     * @param callback The callback that is to be executed when source.method() is 
     * executed in Javascript.
     */
    void addCallback(JSObject source, String method, JSFunction callback){
        String key = source.toJSPointer()+"."+method;
        callbacks.put(key, callback);
        
        String id = JSObject.ID_KEY;
        //String lookup = LOOKUP_TABLE;
        String self = source.toJSPointer();
        String js = self+"."+method+"=function(){"+
                "var len=arguments.length;var url='cn1command:"+self+"."+method+"?'; "+
                "for (var i=0; i<len; i++){"+
                    "var val = arguments[i]; var strval=val;"+
                    "if ( (typeof(val) == 'object') || (typeof(val) == 'function')){ "+
                        "var id = val."+id+"; "+
                        "if (typeof(id)=='undefined' || typeof("+jsLookupTable+"[id]) == 'undefined' || "+jsLookupTable+"[id]."+id+"!=id){"+
                            jsLookupTable+".push(val); id="+jsLookupTable+".indexOf(val); Object.defineProperty(val,\""+id+"\",{value:id, enumerable:false});"+
                        "}"+
                        "strval='"+jsLookupTable+"['+id+']'"+
                    "}"+
                    "url += encodeURIComponent(typeof(val))+'='+encodeURIComponent(strval);"+
                    "if (i < len-1){ url += '&';}"+
                //"} var iframe=document.createElement('iframe');iframe.src=url;document.body.appendChild(iframe)"+
                "} window.location.href=url;"+
                //"} return 56;"+
                //"console.log('About to try to load '+url); var el = document.createElement('iframe'); el.setAttribute('src', url); document.body.appendChild(el); el.parentNode.removeChild(el); console.log(el); el = null"+
            "}";
        //String js2 = self+"."+method+"=function(){console.log('This is the alternate java native call method');}";
        exec(js);
           
        
    }
    
    /**
     * Removes a callback from a javascript object.
     * @param source The Javascript object on which the callback is registered
     *  as a method.
     * @param method The name of the method that will be removed from the callback. 
     */
    void removeCallback(JSObject source, String method){
        String key = source.toJSPointer()+"."+method;
        callbacks.remove(key);
        String js = "delete "+source.toJSPointer()+"."+method;
        exec(js);
    }
    
    /**
     * Calls a Javascript function (encapsulated in a JSObject) with a specified
     * Javascript Object as the "this" context for the function call.  Also passes
     * a set of arguments to the method.
     * 
     * <p>This operates almost exactly like the Javascript <a href="https://developer.mozilla.org/en-US/docs/JavaScript/Reference/Global_Objects/Function/apply">Function.apply() method</a>.</p>
     * 
     * <p>Note that JSObject also has a couple of <code>call()</code> methods 
     * that may be more convenient to use as they will automatically set the "self"
     * parameter to the JSObject callee.  This version of the method is handy in cases
     * where you have been passed a function (perhaps as a callback) and you need to 
     * execute that function in a particular context.</p>
     * 
     * <h5>Example</h5>
     * 
     * <code><pre>
     * // Get the Array.push method as an object
     * JSObject push = (JSObject)ctx.get("Array.prototype.push");
     * 
     * // Create a new array
     * JSObject colors = (JSObject)ctx.get("['red', 'green', 'blue']");
     * 
     * // "Push" a new color onto the array directly using the JSObject's call()
     * // method
     * colors.call("push", "purple");
     * 
     * // Alternate method using JavascriptContext.call()
     * ctx.call(push, colors, "orange");
     * 
     * // Check size of colors array now
     * Double size = (Double)colors.get("length");
     *     // Should be 5.0
     * 
     * // Get 4th color (should be purple)
     * String purple = (String)colors.get(3);
     * 
     * // Get 5th color (should be orange)
     * String orange = (String)colors.get(4);
     * </pre></code>
     * 
     * 
     * 
     * @param func The Javascript function object that is being called.
     * @param self Javascript Object that should act as "this" for the function call.
     * @param params The parameters that should be passed to the function.  These
     * parameters should be passed as Java objects but will be converted into their
     * associated Javascript version.
     * @return The result of the function call.  Javascript results will be automatically
     * converted to their associated Java types.
     */
    public Object call(JSObject func, JSObject self, Object[] params){
        return call(func.toJSPointer(), self, params);
    }
    /**
     * Calls a Javascript function with the given parameters.  This would translate
     * roughly into executing the following javascript:
     * 
     * <code>jsFunc.call(self, param1, param1, ..., paramn)</code>
     * 
     * 
     * 
     * @param jsFunc A javascript expression that resolves to a function object that
     * is to be called.
     * @param self The Javascript object that is used as "this" for the method call.
     * @param params Array of the Javascript parameters, as Java objects.  These use
     * the same conversions as are described in the docs for set().
     * 
     * @return Returns the return value converted to the corresponding Java
     * object type.
     */
    public Object call(String jsFunc, JSObject self, Object[] params){
        String var = RETURN_VAR+"_call";
        String js = var+"=("+jsFunc+").call("+self.toJSPointer();
        int len = params.length;
        for ( int i=0; i<len; i++){
            Object param = params[i];
            js += ", ";
            
            if ( param instanceof Integer || param instanceof Long || param instanceof Double || param instanceof Float){
                js += param.toString();
            } else if ( param instanceof Boolean ){
                js += ((Boolean)param).booleanValue()?"true":"false";
            } else if ( param instanceof String ){
                String escaped = StringUtil.replaceAll((String)param, "\\", "\\\\");
                escaped = StringUtil.replaceAll(escaped, "'", "\\'");
                js += "'"+escaped+"'";
            } else if ( param instanceof JSObject ){
                js += ((JSObject)param).toJSPointer();
            } else if ( param instanceof JSFunction ){
                // We need to assign this JSFunction to something.
                JSObject temp = (JSObject)this.get("{}");
                temp.set("callback", param);
                js += temp.toJSPointer()+".callback";
            } else {
                js += "null";
            }
            
        }
        js += ")";
        
        // We need to intialize the var to undefined in case the actual
        // javascript adjusts the window.location or doesn't cause a 
        // result for some reason.
        try {
            exec(var+"=undefined");
        } catch (Exception ex){
            Log.e(new RuntimeException("Failed to execute javascript "+var+"=undefined.  The error was "+ex.getMessage()));
            return null;
        }
        try {
            exec(js);
        } catch (Exception ex){
            Log.e(new RuntimeException("Failed to execute javascript "+js+".  The error was "+ex.getMessage()));
            return null;
        }
        try {
            return get(var);
        } catch (Exception ex){
            Log.e(new RuntimeException("Failed to get the javascript variable "+var+".  The error was "+ex.getMessage()));
            return null;
        }
    }
    
    
    
    
}
