package org.deskconn.deskconn.utils;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class Helpers extends ContextWrapper {

    private static final String KEY_PAIR_CREATED = "key_pair_created";
    private static final String KEY_PUBLIC = "key_public";
    private static final String KEY_SECRET = "key_secret";

    private SharedPreferences mPrefs;

    public Helpers(Context base) {
        super(base);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public void saveKeys(String publicKey, String privateKey) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(KEY_PUBLIC, publicKey);
        editor.putString(KEY_SECRET, privateKey);
        editor.putBoolean(KEY_PAIR_CREATED, true);
        editor.apply();
    }

    public boolean areKeysSet() {
        return mPrefs.getBoolean(KEY_PAIR_CREATED, false);
    }

    public KeyPair getKeyPair() {
        return new KeyPair(mPrefs.getString(KEY_PUBLIC, null), mPrefs.getString(KEY_SECRET, null));
    }

    static class KeyPair {
        private final String mPublicKey;
        private final String mPrivateKey;

        KeyPair(String publicKey, String privateKey) {
            mPublicKey = publicKey;
            mPrivateKey = privateKey;
        }

        String getPublicKey() {
            return mPublicKey;
        }

        String getPrivateKey() {
            return mPrivateKey;
        }
    }

    public static boolean hasPermission(Activity activity, String type) {
        return ActivityCompat.checkSelfPermission(activity, type) == PERMISSION_GRANTED;
    }
}
