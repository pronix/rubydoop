package rubydoop;


import org.apache.hadoop.conf.Configuration;

import org.jruby.CompatVersion;
import org.jruby.embed.ScriptingContainer;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.EvalFailedException;


public class InstanceContainer {
    public static final String JOB_SETUP_SCRIPT_KEY = "rubydoop.job_setup_script";

    private static ScriptingContainer globalRuntime;

    private final ScriptingContainer runtime;
    private final Object instance;

    public InstanceContainer(ScriptingContainer runtime, Object instance) {
        this.runtime = runtime;
        this.instance = instance;
    }

    public static synchronized ScriptingContainer getRuntime() {
        if (globalRuntime == null) {
            globalRuntime = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);
            globalRuntime.setCompatVersion(CompatVersion.RUBY1_9);
            Object kernel = globalRuntime.get("Kernel");
            globalRuntime.callMethod(kernel, "require", "setup_load_path");
            globalRuntime.callMethod(kernel, "require", "rubydoop");
        }
        return globalRuntime;
    }

    public static InstanceContainer createInstance(Configuration conf, String rubyClassProperty) {
        ScriptingContainer runtime = getRuntime();
        Object rubyClass = lookupClassInternal(runtime, conf, rubyClassProperty);
        return new InstanceContainer(runtime, runtime.callMethod(rubyClass, "new"));
    }

    public static InstanceContainer lookupClass(Configuration conf, String rubyClassProperty) {
        ScriptingContainer runtime = getRuntime();
        Object rubyClass = lookupClassInternal(runtime, conf, rubyClassProperty);
        return new InstanceContainer(runtime, rubyClass);
    }

    private static Object lookupClassInternal(ScriptingContainer runtime, Configuration conf, String rubyClassProperty) {
        String jobConfigScript = conf.get(JOB_SETUP_SCRIPT_KEY);
        String rubyClassName = conf.get(rubyClassProperty);
        try {
            runtime.callMethod(runtime.get("Kernel"), "require", jobConfigScript);
            Object rubyClass = runtime.get("Object");
            for (String name : rubyClassName.split("::")) {
              rubyClass = runtime.callMethod(rubyClass, "const_get", name);
            }
            return rubyClass;
        } catch (EvalFailedException e) {
            throw new RubydoopConfigurationException(String.format("Cannot load class %s: \"%s\"", rubyClassName, e.getMessage()), e);
        }
    }


    public boolean isDefined() {
        return instance != null;
    }

    public boolean respondsTo(String methodName) {
        return isDefined() && runtime.callMethod(instance, "respond_to?", methodName, Boolean.class);
    }

    public Object callMethod(String name) {
        return runtime.callMethod(instance, name);
    }

    public Object callMethod(String name, Object... args) {
        return runtime.callMethod(instance, name, args);
    }

    public Object maybeCallMethod(String name, Object... args) {
        return respondsTo(name) ? callMethod(name, args) : null;
    }
}
