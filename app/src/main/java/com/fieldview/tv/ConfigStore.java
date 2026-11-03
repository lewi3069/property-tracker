package com.fieldview.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Camera/broker secrets stay encrypted in app-private storage and out of backups. */
public final class ConfigStore {
    private static final String ALIAS = "fieldview.settings.v1";
    private final SharedPreferences preferences;
    public ConfigStore(Context context) { preferences = context.getSharedPreferences("private-settings", Context.MODE_PRIVATE); }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    public AppConfig load() throws Exception {
        String value = preferences.getString("encrypted", null);
        if (value == null) return new AppConfig();
        String[] pieces = value.split("\\.", 2);
        if (pieces.length != 2) throw new IllegalStateException("Invalid saved settings");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)));
        byte[] clear = cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP));
        return AppConfig.fromJson(new JSONObject(new String(clear, StandardCharsets.UTF_8)));
    }

    public void save(AppConfig config) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(config.toJson().toString().getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!preferences.edit().putString("encrypted", value).commit()) throw new IllegalStateException("Settings were not saved");
    }
}
