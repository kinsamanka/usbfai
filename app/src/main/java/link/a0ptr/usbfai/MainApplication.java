package link.a0ptr.usbfai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public class MainApplication extends Application {
    private final static String TAG = "MainApplication";
    private final static boolean DEBUG = true;

    private static int created = 0;
    private static int destroyed = 0;

    @Override
    public void onCreate() {
        registerActivityLifecycleCallbacks(new MyLifecycleHandler());
    }

    public final class MyLifecycleHandler implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            if (DEBUG) Log.d(TAG, "onActivityCreated(): " + activity.getLocalClassName());

            if (activity.getLocalClassName().equals("MainActivity"))
                ++created;
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activity.getLocalClassName().equals("MainActivity"))
                ++destroyed;
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

    }

    public static boolean isMainCreated() {
        if (DEBUG) Log.d(TAG, "isMainCreated(): " + (created > destroyed));
        return (created > destroyed);
    }
}
