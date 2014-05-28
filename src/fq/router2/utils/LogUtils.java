package fq.router2.utils;

import android.util.Log;

import java.io.*;
import java.util.Date;

public class LogUtils {

    private static File logFile;

    public static String e(String msg) {
        try {
            try {
                Log.e("twittrouter", msg);
                writeLogFile("ERROR", msg);
            } catch (Exception e) {
                System.out.println(msg);
            }
            return msg;
        } catch (Exception e) {
            // ignore
            return msg;
        }
    }

    public static String e(String msg, Throwable exception) {
        try {
            try {
                Log.e("twittrouter", msg, exception);
                writeLogFile("ERROR", msg + "\r\n" + formatException(exception));
            } catch (Exception e) {
                System.out.println(msg);
                exception.printStackTrace();
            }
            return msg;
        } catch(Exception e) {
            // ignore
            return msg;
        }
    }

    private static String formatException(Throwable e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        e.printStackTrace(ps);
        ps.close();
        return baos.toString();
    }

    public static void i(String msg) {
        try {
            try {
                Log.i("twittrouter", msg);
                writeLogFile("INFO", msg);
            }  catch (Exception e) {
                System.out.println(msg);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void writeLogFile(String level, String line) {
    	Log.e("twittrouter", "skip to save log");
    	return;       
    }
}
