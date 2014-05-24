package io.github.scola.twittrouter;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;


import java.io.File;

import fq.router2.feedback.DownloadService;
import fq.router2.utils.LogUtils;
import fq.router2.utils.ShellUtils;
import fq.router2.utils.HttpUtils;

public class ExitService extends IntentService {

    public ExitService() {
        super("Exit");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        exit();
    }

    private void exit() {
        try  {
            //MainActivity.displayNotification(this, getResources().getString(R.string.status_exiting));
            sendBroadcast(new ExitingIntent());
            LogUtils.i("Exiting..." );
            new Thread(new Runnable() {
                @Override
                public void run() {
                    stopService(new Intent(ExitService.this, DownloadService.class));
                    stopService(new Intent(ExitService.this, DeployService.class));
                    /*
                    try {
                    	String content = HttpUtils.get("http://127.0.0.1:8888/clean");
                    	LogUtils.i(content + " done");
                    }  catch (Exception e) {
                    	LogUtils.e("failed clean iptables", e);
                    }
                    
                    //LogUtils.i("Exiting..." );
                	
                    if (ShellUtils.isRooted()) {
                        for (File file : new File[]{IOUtils.ETC_DIR, IOUtils.LOG_DIR, IOUtils.VAR_DIR}) {
                            if (file.listFiles().length > 0) {
                                try {
                                    ShellUtils.sudo(ShellUtils.BUSYBOX_FILE + " chmod 666 " + file + "/*");
                                } catch (Exception e) {
                                    LogUtils.e("failed to chmod files to non-root", e);
                                }
                            }
                        }
                    }
                    */
                }
            }).start();
            try {
            	ShellUtils.kill();
            } catch (Exception e) {
                LogUtils.e("failed to kill manager process", e);
            }
            sendBroadcast(new ExitedIntent());
            //MainActivity.clearNotification(this);
        } finally {
            Twittrouter.isServerRunning = false;
        }
    }

    public static void execute(Context context) {
        context.startService(new Intent(context, ExitService.class));
    }
}
