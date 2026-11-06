package com.fieldview.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.PrivateKey;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Camera/broker secrets stay encrypted in app-private storage and out of backups. */
public final class ConfigStore {
    private static final String ALIAS = "fieldview.settings.v1";
    private static final String LEGACY_ALIAS = ALIAS + ".rsa-wrap";
    private static final String WRAPPED_KEY = "legacy_wrapped_key";
    private final Context context;
    private final SharedPreferences preferences;
    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("private-settings", Context.MODE_PRIVATE);
    }

    private SecretKey key(boolean allowCreate) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        // Keep wrapped keys readable if an older device later receives an OS update.
        if (preferences.contains(WRAPPED_KEY) || Build.VERSION.SDK_INT < 23) return legacyKey(store, allowCreate);
        return ModernKeys.get(store, allowCreate && !preferences.contains("encrypted"));
    }

    @android.annotation.TargetApi(23)
    private static final class ModernKeys {
        static SecretKey get(KeyStore store, boolean allowCreate) throws Exception {
            if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
            if (!allowCreate) throw new IllegalStateException("Settings encryption key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
    }

    @SuppressWarnings("deprecation")
    private SecretKey legacyKey(KeyStore store, boolean allowCreate) throws Exception {
        String wrapped = preferences.getString(WRAPPED_KEY, null);
        if (wrapped != null) {
            if (!store.containsAlias(LEGACY_ALIAS)) throw new IllegalStateException("Settings encryption key unavailable");
            Cipher rsa = rsaCipher();
            rsa.init(Cipher.DECRYPT_MODE, (PrivateKey) store.getKey(LEGACY_ALIAS, null));
            byte[] raw = rsa.doFinal(Base64.decode(wrapped, Base64.NO_WRAP));
            try {
                if (raw.length != 16) throw new IllegalStateException("Invalid settings encryption key");
                return new SecretKeySpec(raw, "AES");
            } finally { Arrays.fill(raw, (byte) 0); }
        }
        if (!allowCreate || preferences.contains("encrypted")) throw new IllegalStateException("Settings encryption key unavailable");
        if (!store.containsAlias(LEGACY_ALIAS)) {
            Calendar end = Calendar.getInstance(); end.add(Calendar.YEAR, 50);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(LEGACY_ALIAS).setKeySize(2048)
                    .setSubject(new X500Principal("CN=Property Tracker Settings")).setSerialNumber(BigInteger.ONE)
                    .setStartDate(new Date(0)).setEndDate(end.getTime()).build());
            generator.generateKeyPair();
        }
        byte[] raw = new byte[16]; new SecureRandom().nextBytes(raw);
        try {
            Cipher rsa = rsaCipher(); rsa.init(Cipher.ENCRYPT_MODE, store.getCertificate(LEGACY_ALIAS).getPublicKey());
            String encrypted = Base64.encodeToString(rsa.doFinal(raw), Base64.NO_WRAP);
            if (!preferences.edit().putString(WRAPPED_KEY, encrypted).commit()) throw new IllegalStateException("Settings key was not saved");
            return new SecretKeySpec(raw, "AES");
        } finally { Arrays.fill(raw, (byte) 0); }
    }

    private Cipher rsaCipher() throws Exception {
        return Build.VERSION.SDK_INT < 23 ? Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL")
                : Cipher.getInstance("RSA/ECB/PKCS1Padding");
    }

    public AppConfig load() throws Exception {
        String value = preferences.getString("encrypted", null);
        if (value == null) return new AppConfig();
        String[] pieces = value.split("\\.", 2);
        if (pieces.length != 2) throw new IllegalStateException("Invalid saved settings");
        byte[] clear = SettingsCipher.decrypt(key(false), new SettingsCipher.Envelope(
                Base64.decode(pieces[0], Base64.NO_WRAP), Base64.decode(pieces[1], Base64.NO_WRAP)));
        return AppConfig.fromJson(new JSONObject(new String(clear, StandardCharsets.UTF_8)));
    }

    public void save(AppConfig config) throws Exception {
        SettingsCipher.Envelope encrypted = SettingsCipher.encrypt(key(true), config.toJson().toString().getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP);
        if (!preferences.edit().putString("encrypted", value).commit()) throw new IllegalStateException("Settings were not saved");
    }
}
