package io.github.scola.twittrouter;

import fq.router2.utils.IOUtils;
import fq.router2.utils.ZipFileUtil;
import fq.router2.utils.ShellUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipFile;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

public class DeployService extends IntentService {
	
	private static final String TAG = "DeployService";
	public static File DATA_DIR = new File("/data/data/io.github.scola.twittrouter");
	public static File PAYLOAD_ZIP = new File(DATA_DIR, "payload.zip");
	public static File PYTHON_DIR = new File(DATA_DIR, "twittrouter");
	public static File PAYLOAD_CHECKSUM = new File(DATA_DIR, "payload.checksum");
	public static File PYTHON_LAUNCHER = new File(PYTHON_DIR, "python-launcher.sh");
	public static File PYTHON_LAUNCHER_OLD = new File("/data/data/fq.router2/python/bin/python");
	public static File MANAGER_MAIN_PY = new File(PYTHON_DIR, "twittrouter.py");
	private int result  = Activity.RESULT_OK;

	public DeployService() {
		super("DeployService");
	}

	private void copyPayloadZip() throws Exception {
		/*
		if (!DATA_DIR.exists()) {
			if(!DATA_DIR.mkdirs())
				Log.e(TAG, "create /data/data/twittrouter directory failed");
        }
        */
        if (PAYLOAD_ZIP.exists()) {
            Log.i(TAG, "skip copy payload.zip as it already exists");
            return;
        }
        if (PYTHON_DIR.exists()) {
            Log.i(TAG, "skip copy payload.zip as it has already been unzipped");
            return;
        }
        Log.i(TAG, "copying payload.zip to data directory");
        InputStream inputStream = this.getAssets().open("payload.zip");
        try {
            OutputStream outputStream = new FileOutputStream(PAYLOAD_ZIP);
            try {
                String checksum = IOUtils.copy(inputStream, outputStream);
                IOUtils.writeToFile(PAYLOAD_CHECKSUM, checksum);
            } finally {
                outputStream.close();
            }
        } finally {
            inputStream.close();
        }
        Log.i(TAG, "successfully copied payload.zip");
    }
	
	private boolean shouldDeployPayload() throws Exception {
        if (!PAYLOAD_CHECKSUM.exists()) {
            Log.i(TAG, "no checksum, assume it is old");
            return true;
        }
        String oldChecksum = IOUtils.readFromFile(PAYLOAD_CHECKSUM);
        InputStream inputStream = this.getAssets().open("payload.zip");
        try {
            String newChecksum = IOUtils.copy(inputStream, null);
            if (oldChecksum.equals(newChecksum)) {
                Log.i(TAG, "no payload update found");
                return false;
            } else {
                Log.i(TAG, "found payload update");
                return true;
            }
        } finally {
            inputStream.close();
        }
    }
	
	private void deleteDirectory(String path) throws Exception {
        if (new File(path).exists()) {
            try {
                ShellUtils.execute("/data/data/fq.router2/busybox", "rm", "-rf", path);
            } catch (Exception e) {
                Log.e(TAG, "failed to delete " + path + e);
            }
        }
        if (new File(path).exists()) {
            Log.e(TAG, "failed to delete " + path);
        }
    }
	

	private void makeExecutable(File file) throws Exception {
        try {
            Method setExecutableMethod = File.class.getMethod("setExecutable", boolean.class, boolean.class);
            if ((Boolean) setExecutableMethod.invoke(file, true, true)) {
                Log.i(TAG, "successfully made " + file.getName() + " executable");
            } else {
                Log.i(TAG, "failed to make " + file.getName() + " executable");
                ShellUtils.sudo(ShellUtils.findCommand("chmod"), "0700", file.getCanonicalPath());
            }
        } catch (NoSuchMethodException e) {
            ShellUtils.execute("/data/data/fq.router2/busybox", "chmod", "0700", file.getAbsolutePath());
            Log.i(TAG, "successfully made " + file.getName() + " executable");
        }
    }
	
	// Will be called asynchronously be Android
	@Override
	protected void onHandleIntent(Intent intent) {
		
		if(!ShellUtils.exists()) {
			ShellUtils.checkRooted();
		}
				
		boolean foundPayloadUpdate = false;
        try {
            foundPayloadUpdate = shouldDeployPayload();
        } catch (Exception e) {
            Log.e(TAG, "failed to check update" + e);
        }
		
        if (foundPayloadUpdate) {            
            try {
                try {
                    ShellUtils.kill();
                } catch (Exception e) {
                    Log.e(TAG, "failed to kill python2 before redeploy", e);
                    // ignore and continue
                }
                deleteDirectory(DATA_DIR + "twittrouter");
            } catch (Exception e) {
                Log.e(TAG, "failed to clear data directory" + e);
            }
        }
        
		try {
			copyPayloadZip();
        } catch (Exception e) {
            Log.e(TAG, "failed to copy payload.zip" + e);
            result = Activity.RESULT_CANCELED;
        }
		if(!PYTHON_DIR.exists()) {
			try {
				ZipFile zipFile = new ZipFile(PAYLOAD_ZIP);
				ZipFileUtil.unzipFileIntoDirectory(zipFile, DATA_DIR);
				if(!PAYLOAD_ZIP.delete()) {
					Log.e(TAG, "remove payload.zip failed");
				}
				makeExecutable(PYTHON_LAUNCHER);
			} catch (Exception e) {
				Log.e(TAG, "failed to unzip payload.zip" + e);
	            result = Activity.RESULT_CANCELED;
			}
        }
		
		Bundle extras = intent.getExtras();
		if (extras != null) {
			Messenger messenger = (Messenger) extras.get("MESSENGER");
			Message msg = Message.obtain();
			msg.arg1 = result;
			try {
				Log.i(TAG, "send msg");
				messenger.send(msg);
			} catch (android.os.RemoteException e1) {
				Log.w(getClass().getName(), "Exception sending message", e1);
			}
		}
		
		//ShellUtils.executeTwittrouter();		
	}
}
