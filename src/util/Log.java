package util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Logging API.
 *
 */
public final class Log {

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    private Log() {}

    public static void v(String tag, String msg) {
        log(VERBOSE, tag, msg);
    }

    public static void v(String tag, String msg, Throwable tr) {
        log(VERBOSE, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static void d(String tag, String msg) {
        log(DEBUG, tag, msg);
    }

    public static void d(String tag, String msg, Throwable tr) {
        log(DEBUG, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static void i(String tag, String msg) {
        log(INFO, tag, msg);
    }

    public static void i(String tag, String msg, Throwable tr) {
        log(INFO, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static void w(String tag, String msg) {
        log(WARN, tag, msg);
    }

    public static void w(String tag, String msg, Throwable tr) {
        log(WARN, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static void w(String tag, Throwable tr) {
        log(WARN, tag, getStackTraceString(tr));
    }

    public static void e(String tag, String msg) {
        log(ERROR, tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        log(ERROR, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null)
            return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        return sw.toString();
    }

    public static void log(int priority, String tag, String msg) {
        System.out.printf("\tpriority=%d,%n\ttag=%s,%n\tmessage=%s%n", priority, tag, msg);
    }

}