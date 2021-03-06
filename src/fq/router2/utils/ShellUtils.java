package fq.router2.utils;

import java.io.*;
import java.util.*;


public class ShellUtils {
	
	public static File MANAGER_MAIN_PY = new File("/data/data/io.github.scola.twittrouter/twittrouter/twittrouter.py");
	public static File PYTHON_LAUNCHER = new File("/data/data/io.github.scola.twittrouter/twittrouter/python-launcher.sh");
	
	private final static String TWITTROUTER_LOG = "/data/data/io.github.scola.twittrouter/twittrouter/twittrouter.log";

    private final static String[] BINARY_PLACES = {"/data/bin/", "/system/bin/", "/system/xbin/", "/sbin/",
            "/data/local/xbin/", "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/",
            "/data/local/"};
    private final static String PYTHON_HOME = "/data/data/fq.router2/python";
    public static File DATA_DIR = new File("/data/data/fq.router2");
    public static File BUSYBOX_FILE = new File(DATA_DIR, "busybox");
    private static Boolean IS_ROOTED = null;

    public static String execute(String... command) throws Exception {
        Process process = executeNoWait(new HashMap<String, String>(), command);
        return waitFor(Arrays.toString(command), process);
    }

    public static String execute(Map<String, String> env, String... command) throws Exception {
        return waitFor(Arrays.toString(command), executeNoWait(env, command));
    }

    public static Process executeNoWait(Map<String, String> env, String... command) throws IOException {
        LogUtils.i("command: " + Arrays.toString(command));
        List<String> envp = new ArrayList<String>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            envp.add(entry.getKey() + "=" + entry.getValue());
        }
        return Runtime.getRuntime().exec(command, envp.toArray(new String[envp.size()]));
    }

    public static String sudo(Map<String, String> env, String... command) throws Exception {
        if (Boolean.FALSE.equals(IS_ROOTED)) {
            if (BUSYBOX_FILE.exists()) {
                Process process = ShellUtils.executeNoWait(env, BUSYBOX_FILE.getCanonicalPath(), "sh");
                OutputStreamWriter stdin = new OutputStreamWriter(process.getOutputStream());
                try {
                    LogUtils.i("write to stdin: " + Arrays.toString(command));
                    for (String c : command) {
                        stdin.write(c);
                        stdin.write(" ");
                    }
                    stdin.write("\nexit\n");
                } finally {
                    stdin.close();
                }
                return waitFor(Arrays.toString(command), process);
            } else {
                return waitFor(Arrays.toString(command), executeNoWait(env, command));
            }
        } else {
            Process process = sudoNoWait(env, command);
            return waitFor(Arrays.toString(command), process);
        }
    }

    public static String sudo(String... command) throws Exception {
        return sudo(new HashMap<String, String>(), command);
    }


    public static Process sudoNoWait(Map<String, String> env, String... command) throws Exception {
        if (Boolean.FALSE.equals(IS_ROOTED)) {
            return executeNoWait(env, command);
        }
        LogUtils.i("sudo: " + Arrays.toString(command));
        ProcessBuilder processBuilder = new ProcessBuilder();
        Process process = processBuilder
                .command(findCommand("su"))
                .redirectErrorStream(true)
                .start();
        OutputStreamWriter stdin = new OutputStreamWriter(process.getOutputStream());
        try {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                stdin.write(entry.getKey());
                stdin.write("=");
                stdin.write(entry.getValue());
                stdin.write(" ");
            }
            for (String c : command) {
                stdin.write(c);
                stdin.write(" ");
            }
            stdin.write("\nexit\n");
        } finally {
            stdin.close();
        }
        return process;
    }

    public static String findCommand(String command) {
        for (String binaryPlace : BINARY_PLACES) {
            String path = binaryPlace + command;
            if (new File(path).exists()) {
                return path;
            }
        }
        return command;
    }

    public static String waitFor(String command, Process process) throws Exception {
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        try {
            String line;
            while (null != (line = stdout.readLine())) {
                output.append(line);
                output.append("\n");
            }
        } finally {
            stdout.close();
        }
        process.waitFor();
        int exitValue = process.exitValue();
        if (0 != exitValue) {
            throw new Exception("failed to execute: " + command + ", exit value: " + exitValue + ", output: " + output);
        }
        return output.toString();
    }

    public static Map<String, String> pythonEnv() {
        return new HashMap<String, String>() {{
            put("PYTHONHOME", PYTHON_HOME);
            put("PYTHONPATH", PYTHON_HOME + "/lib/python2.7/lib-dynload:" + PYTHON_HOME + "/lib/python2.7");
            put("PATH", PYTHON_HOME + "/bin:" + System.getenv("PATH"));
            put("LD_LIBRARY_PATH", System.getenv("LD_LIBRARY_PATH") + ":" + PYTHON_HOME + "/lib:" + PYTHON_HOME + "/lib/python2.7/lib-dynload");
        }};
    }

    public static boolean checkRooted() {
        IS_ROOTED = null;
        try {
            IS_ROOTED = sudo("echo", "hello").contains("hello");
        } catch (Exception e) {
            IS_ROOTED = false;
        }
        return IS_ROOTED;
    }

    public static boolean isRooted() {
        return Boolean.TRUE.equals(IS_ROOTED);
    }
    
    public static void kill() throws Exception {
        if (!exists()) {
            LogUtils.i("python2 is not running");
            return;
            /*
            try {
                if ("run-needs-su".equals(getRunMode())) {
                    ShellUtils.execute(
                            ShellUtils.pythonEnv(), Deployer.PYTHON_LAUNCHER.getAbsolutePath(),
                            Deployer.MANAGER_MAIN_PY.getAbsolutePath(), "clean");
                } else {
                    ShellUtils.sudo(
                            ShellUtils.pythonEnv(), Deployer.PYTHON_LAUNCHER.getAbsolutePath(),
                            Deployer.MANAGER_MAIN_PY.getAbsolutePath(), "clean");
                }
            } catch (Exception e) {
                LogUtils.e("failed to clean", e);
            }
            */
        }
        LogUtils.i("killall python2");
        if (new File("/data/data/fq.router2/busybox").exists()) {
            ShellUtils.sudo("/data/data/fq.router2/busybox", "killall", "python2");
        } else {
            ShellUtils.sudo(ShellUtils.findCommand("killall"), "python2");
        }
        for (int i = 0; i < 10; i++) {
            if (exists()) {
                Thread.sleep(3000);
            } else {
                LogUtils.i("killall python2 done cleanly");
                return;
            }
        }
        LogUtils.e("killall python2 by force");
        if (new File("/data/data/fq.router2/busybox").exists()) {
            ShellUtils.sudo("/data/data/fq.router2/busybox", "killall", "-KILL", "python2");
        } else {
            ShellUtils.sudo(ShellUtils.findCommand("killall"), "-KILL", "python2");
        }
    }
    
	public static void executeTwittrouter() {
		LogUtils.i("execute Twittrouter");
		try {
        	kill();
        } catch (Exception e) {
            LogUtils.e("failed to kill manager process", e);
        }
		
		try {
			ShellUtils.sudo("source", PYTHON_LAUNCHER.getAbsolutePath(), MANAGER_MAIN_PY.getAbsolutePath(), ">", TWITTROUTER_LOG, "2>&1");			
		} catch (Exception e) {
			LogUtils.e("the phone is not rooted ", e);
            //result = Activity.RESULT_CANCELED;
		}
	}
/*
    public static boolean exists() {
        try {
            String output;
            if (new File("/data/data/fq.router2/busybox").exists()) {
                output = ShellUtils.sudo("/data/data/fq.router2/busybox", "killall", "-0", "python2");
            } else {
                output = ShellUtils.sudo(ShellUtils.findCommand("killall"), "-0", "python2");
            }
            if (output.contains("no process killed")) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
    */
    public static boolean exists() {
        try {
            String output;         
            output = ShellUtils.execute(ShellUtils.findCommand("ps"));            
            if (output.contains("python2")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean exists(String Uri) {
        try {
            String output;         
            output = ShellUtils.execute(ShellUtils.findCommand("ps"));            
            if (output.lastIndexOf(Uri) != output.indexOf(Uri)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
