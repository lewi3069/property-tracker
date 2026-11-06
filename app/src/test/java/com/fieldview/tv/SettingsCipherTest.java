package com.fieldview.tv;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsCipherTest {
    private SecretKey key() throws Exception { KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(128); return generator.generateKey(); }

    @Test public void readsLegacyAndroid128BitNonces() throws Exception {
        SecretKey key = key(); byte[] iv = new byte[16]; new SecureRandom().nextBytes(iv);
        byte[] clear = "legacy private settings".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        assertArrayEquals(clear, SettingsCipher.decrypt(key, new SettingsCipher.Envelope(iv, cipher.doFinal(clear))));
    }

    @Test public void repeatedSavesUseDifferentNoncesAndStayReadable() throws Exception {
        SecretKey key = key(); byte[] clear = "private camera settings".getBytes(StandardCharsets.UTF_8);
        SettingsCipher.Envelope first = SettingsCipher.encrypt(key, clear), second = SettingsCipher.encrypt(key, clear);
        assertFalse(Arrays.equals(first.iv, second.iv));
        assertFalse(Arrays.equals(first.ciphertext, second.ciphertext));
        assertArrayEquals(clear, SettingsCipher.decrypt(key, first));
        assertArrayEquals(clear, SettingsCipher.decrypt(key, second));
    }

    @Test(expected = AEADBadTagException.class) public void changedCiphertextFailsAuthentication() throws Exception {
        SecretKey key = key(); SettingsCipher.Envelope envelope = SettingsCipher.encrypt(key, new byte[]{1, 2, 3});
        envelope.ciphertext[0] ^= 1;
        SettingsCipher.decrypt(key, envelope);
    }

    @Test(expected = AEADBadTagException.class) public void wrongKeyDoesNotReturnSettings() throws Exception {
        SettingsCipher.decrypt(key(), SettingsCipher.encrypt(key(), new byte[]{1, 2, 3}));
    }

    @Test(expected = IllegalArgumentException.class) public void incompleteEnvelopeIsRejected() throws Exception {
        SettingsCipher.decrypt(key(), new SettingsCipher.Envelope(new byte[12], new byte[15]));
    }
}
