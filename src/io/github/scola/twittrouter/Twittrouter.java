package io.github.scola.twittrouter;

import io.github.scola.twittrouter.R;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import fq.router2.utils.ApkUtils;
import fq.router2.utils.HttpUtils;
import fq.router2.utils.ShellUtils;
import fq.router2.utils.LogUtils;
import fq.router2.feedback.*;

import java.lang.reflect.Method;
import java.util.List;

public class Twittrouter extends Activity implements
	DownloadingIntent.Handler,
	DownloadedIntent.Handler,
	DownloadFailedIntent.Handler,
	HandleFatalErrorIntent.Handler,
	ExitingIntent.Handler,
	ExitedIntent.Handler{
	private static final String TAG = "twittrouter";
	private static final String fqrouter = "fq.router2";
	private boolean downloaded;
	private final static int ITEM_ID_EXIT = 1;	
    public final static int SHOW_AS_ACTION_IF_ROOM = 1;
    
    public static boolean isServerRunning;
    private String upgradeUrl;
	
	
	private Handler handler = new Handler() {
		public void handleMessage(Message message) {
			if (message.arg1 == Activity.RESULT_OK) {
				Log.i(TAG, "deploy success");
				//isServerRunning = true;
				//runTwittrouter();
				/*
				*/
				if(!ShellUtils.exists()) {
					runTwittrouter();					
				}
				try {
		            Thread.sleep(1000);
		        } catch (InterruptedException e) {
		            throw new RuntimeException(e);
		        }
				if(ShellUtils.exists()){
					checkServerRunning();
				}
				
								
			} else {
				Toast.makeText(Twittrouter.this, "deploy failed.",
						Toast.LENGTH_LONG).show();
			}

		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ExitedIntent.register(this);
		ExitingIntent.register(this);
		HandleFatalErrorIntent.register(this);
		DownloadingIntent.register(this);
        DownloadedIntent.register(this);
        DownloadFailedIntent.register(this);
		setContentView(R.layout.main);
		launchDeployService();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        addMenuItem(menu, ITEM_ID_EXIT, getResources().getString(R.string.menu_exit));
        return super.onCreateOptionsMenu(menu);
    }
	
	private MenuItem addMenuItem(Menu menu, int menuItemId, String caption) {
        MenuItem menuItem = menu.add(Menu.NONE, menuItemId, Menu.NONE, caption);
        try {
        	Method method = MenuItem.class.getMethod("setShowAsAction", int.class);
            try {
                method.invoke(menuItem, SHOW_AS_ACTION_IF_ROOM);
            } catch (Exception e) {
            }
        } catch (NoSuchMethodException e) {
        }
        return menuItem;
    }
	
	@Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (ITEM_ID_EXIT == item.getItemId()) {
        	try {
                //ShellUtils.kill();
                //finish();
        		exit();
            } catch (Exception e) {
                Log.e(TAG, "failed to kill python2 before redeploy", e);
                // ignore and continue
            }
        }
        return super.onMenuItemSelected(featureId, item);
    }
	
    @Override
    protected void onResume() {
        super.onResume();
        if(!ShellUtils.exists()){
        	runTwittrouter();        	        	
        }
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        
        if(ShellUtils.exists()){
        	checkServerRunning();
        }
        
    }
    
    public static String getMyVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (null == packageInfo.versionName) {
                return "Unknown";
            } else {
                return packageInfo.versionName;
            }
        } catch (Exception e) {
            LogUtils.e("failed to get package info", e);
            return "Unknown";
        }
    }
    
    private void checkServerRunning() {
    	LogUtils.i("check Server Running");
        	new Thread(new Runnable() {
                @Override
                public void run() {
                	int retry = 20;
                	while(retry-- > 0){
                		try {
                        	String content = HttpUtils.get("http://127.0.0.1:8888/echo");
                        	if(content.contains("helloworld")) {
                        		LogUtils.i("Echo " + content);
                        		isServerRunning = true;
                        		break;
                        	}                       		
                        	LogUtils.e("Echo " + content);
                        	LogUtils.e("Server is not running or died");
                        	Thread.sleep(500);
                        }  catch (Exception e) {
                        	LogUtils.e("Server is not running", e);
                        }
                		isServerRunning = false;
                		
                	}              	
                	
                }
            }).start();      	
    }
    
    private void runTwittrouter() {
    	if(ShellUtils.isRooted() && appRunningOrNot(fqrouter)){
        	new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                    	ShellUtils.executeTwittrouter();
                    } catch (Exception e) {
                        LogUtils.e("failed to execute twittrouter");
                        isServerRunning = false;
                    }
                }
            }).start();        	
        }
    }
    
    @Override
    protected void onDestroy() {
    	LogUtils.i("onDestroy");
    	try {
        	String content = HttpUtils.get("http://127.0.0.1:8888/clean");
        	LogUtils.i(content + " done");
        }  catch (Exception e) {
        	LogUtils.e("failed clean iptables", e);
        }
    	super.onDestroy();
    }
    public void updateStatus(String status, int progress) {
        LogUtils.i(status);
        TextView textView = (TextView) findViewById(R.id.statusTextView);
        textView.setText(status);
        //ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        //progressBar.setProgress(progress);
    }
    
    @Override
    public void onHandleFatalError(String message) {
        LogUtils.e("fatal error: " + message);
        //findViewById(R.id.progressBar).setVisibility(View.GONE);
        TextView statusTextView = (TextView) findViewById(R.id.statusTextView);
        statusTextView.setTextColor(Color.RED);
        statusTextView.setText(message);
        //checkUpdate();
    }
    
    public void exit() {
        ExitService.execute(this);
        //displayNotification(this, _(R.string.status_exiting));
    }
    
    @Override
    public void onExiting() {
        //displayNotification(this, _(R.string.status_exiting));
    	isServerRunning = false;
        
        //ActivityCompat.invalidateOptionsMenu(this);
        
        findViewById(R.id.webView).setVisibility(View.GONE);
        findViewById(R.id.footer).setVisibility(View.GONE);
        findViewById(R.id.editText1).setVisibility(View.GONE);
        findViewById(R.id.editText2).setVisibility(View.GONE);
        findViewById(R.id.editText3).setVisibility(View.GONE);
        findViewById(R.id.editText4).setVisibility(View.GONE);
        findViewById(R.id.statusTextView).setVisibility(View.GONE);
        findViewById(R.id.exiting).setVisibility(View.VISIBLE);
        TextView exitTextView = (TextView) findViewById(R.id.exiting);
        exitTextView.setTextColor(Color.RED);
    }
    @Override
    public void onExited() {
        //clearNotification(this);
        finish();
    }
    
    @Override
    public void onDownloadFailed(final String url, String downloadTo) {
        //ActivityCompat.invalidateOptionsMenu(this);
        onHandleFatalError(_(R.string.status_download_failed) + " " + Uri.parse(url).getLastPathSegment());
        Toast.makeText(this, R.string.upgrade_via_browser_hint, 3000).show();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }, 3000);
    }

    @Override
    public void onDownloaded(String url, String downloadTo) {
        downloaded = true;
        //ActivityCompat.invalidateOptionsMenu(this);
        updateStatus(_(R.string.status_downloaded) + " " + Uri.parse(url).getLastPathSegment(), 5);


        ApkUtils.install(this, downloadTo);
    }

    @Override
    public void onDownloading(String url, String downloadTo, int percent) {
        if (System.currentTimeMillis() % (2 * 1000) == 0) {
            //displayNotification(this, _(R.string.status_downloading) + " " + Uri.parse(url).getLastPathSegment() + ": " + percent + "%");
        }
        TextView textView = (TextView) findViewById(R.id.statusTextView);
        textView.setText(_(R.string.status_downloaded) + " " + percent + "%");
    }
	
    private void openAbout() {
        WebView web = new WebView(this);
        web.loadUrl(_(R.string.about_page));
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });
        new AlertDialog.Builder(this)
                .setTitle(String.format(_(R.string.about_info_title), getMyVersion(this)))
                .setCancelable(false)
                .setPositiveButton(R.string.about_info_share, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        intent.putExtra(Intent.EXTRA_SUBJECT, _(R.string.share_subject));
                        
                        //shareUrl = "https://s3-ap-southeast-1.amazonaws.com/fqrouter/fqrouter-latest.apk";
                       
                        intent.putExtra(Intent.EXTRA_TEXT, _(R.string.share_content));
                        startActivity(Intent.createChooser(intent, _(R.string.share_channel)));
                    }
                })
                .setNegativeButton(R.string.about_info_close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setView(web)
                .create()
                .show();        
    }
    
	public void shareVia(View view) {
		//Toast.makeText(this, R.string.menu_share, 3000).show();
		openAbout();
	}

	public void startrun(View view) {
		//Toast.makeText(this, "Still interactive", Toast.LENGTH_SHORT).show();
		try {
			if (!ShellUtils.isRooted()){
				ShellUtils.checkRooted();
			}
			
			if (!ShellUtils.isRooted()){
				if(appInstalledOrNot(fqrouter)){
					Toast.makeText(Twittrouter.this, _(R.string.device_not_root),
							Toast.LENGTH_LONG).show();
				} else if(isDownloadServiceRunning() == false) {
					String upgradeUrl = "http://dl.geekcantalk.com/2.11.2.apk";
					onUpdateFound(upgradeUrl);
				} else {
					Toast.makeText(Twittrouter.this, _(R.string.wait_downloading_fqrouter),
							Toast.LENGTH_SHORT).show();
				}
			} else {
				if(!appInstalledOrNot(fqrouter)) {
					if(isDownloadServiceRunning() == false) {
						String upgradeUrl = "http://dl.geekcantalk.com/2.11.2.apk";
						onUpdateFound(upgradeUrl);
					}else {
						Toast.makeText(Twittrouter.this, _(R.string.wait_downloading_fqrouter),
								Toast.LENGTH_SHORT).show();
					}					
				}
				else if (!appRunningOrNot(fqrouter)) {
					popupRunFqrouterAlert();
				} else if(isServerRunning) {
					if (Build.VERSION.SDK_INT < 14) {
	                    Uri uri = Uri.parse("http://127.0.0.1:8888/config");
	                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
	                } else {
	                	loadWebView();
						showWebView();
	                }
					
				} else{
					Toast.makeText(Twittrouter.this, _(R.string.server_died_or_not_running),
							Toast.LENGTH_SHORT).show();
				}
			}			
            
		} catch (Exception e) {
            Log.e(TAG, "failed to show web page " + e);
        }			
	}
	
	private void launchDeployService(){
		Intent intent = new Intent(this, DeployService.class);
		// Create a new Messenger for the communication back
		Messenger messenger = new Messenger(handler);
		intent.putExtra("MESSENGER", messenger);				
		startService(intent);
	}
	
	//@Override
    public void onUpdateFound(final String upgradeUrl) {
        final String downloadTo = "/sdcard/fqrouter-latest.apk";
        if (downloaded) {
            onDownloaded(upgradeUrl, downloadTo);
            return;
        }
        this.upgradeUrl = upgradeUrl;
        int alertMsg;
        if (ShellUtils.isRooted()) {
        	alertMsg = R.string.fqrouter_not_install_alert_message;
        } else {
        	alertMsg = R.string.fqrouter_not_install_or_root_alert_message;
        }
        
        //ActivityCompat.invalidateOptionsMenu(this);
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.fqrouter_not_install_alert_title)
                .setMessage(alertMsg)
                .setPositiveButton(R.string.new_version_alert_yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updateStatus(_(R.string.status_downloading) + " " + Uri.parse(upgradeUrl).getLastPathSegment(), 5);
                        DownloadService.execute(
                                Twittrouter.this, upgradeUrl, downloadTo);
                    }

                })
                .setNegativeButton(R.string.new_version_alert_no, null)
                .show();
    }
    
    public void popupRunFqrouterAlert() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.fqrouter_not_run_alert_title)
                .setMessage(R.string.fqrouter_not_run_alert_message)
                .setPositiveButton(R.string.new_version_alert_yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    	Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(fqrouter);
        				startActivity(LaunchIntent);
                    }

                })
                .setNegativeButton(R.string.new_version_alert_no, null)
                .show();
    }
	
    private String _(int id) {
        return getResources().getString(id);
    }
	
	private void showWebView() {
        if (Build.VERSION.SDK_INT < 14) {
            return;
        }
        if(isServerRunning == true) {
			findViewById(R.id.startButton).setVisibility(View.GONE);
			findViewById(R.id.shareButton).setVisibility(View.GONE);
			findViewById(R.id.webView).setVisibility(View.VISIBLE);
		}
    }
	
	private void loadWebView() {
        if (Build.VERSION.SDK_INT < 14) {
            return;
        }
        if(isServerRunning == true) {
	        WebView webView = (WebView) findViewById(R.id.webView);
	        webView.getSettings().setJavaScriptEnabled(true);
	        webView.getSettings().setAppCacheEnabled(false);
	        webView.loadUrl("http://127.0.0.1:8888/config");
	        webView.setWebViewClient(new WebViewClient() {
	            @Override
	            public boolean shouldOverrideUrlLoading(WebView view, String url) {
	                LogUtils.i("url: " + url);
	                //startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
	                view.loadUrl(url);
	                return false;
	            }
	
	            @Override
	            public void onPageFinished(WebView view, String url) {
	                CookieSyncManager.getInstance().sync();
	            }
	        });
        }
    }
	
    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = getPackageManager();
        boolean app_installed = false;
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            app_installed = true;
        }
        catch (PackageManager.NameNotFoundException e) {
            app_installed = false;
        }
        return app_installed ;
    }
    
    private boolean appRunningOrNot(String uri) {
        ActivityManager activityManager = (ActivityManager) this.getSystemService( ACTIVITY_SERVICE );
        List<RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        for(int i = 0; i < procInfos.size(); i++)
        {
            if(procInfos.get(i).processName.equals(uri)) 
            {
                return true;
            }
        }
    	return false;
    }
    
    private boolean isDownloadServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DownloadService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}