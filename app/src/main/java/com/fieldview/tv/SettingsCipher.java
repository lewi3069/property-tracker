package com.fieldview.tv;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The same authenticated settings envelope is used by both key-storage paths. */
final class SettingsCipher {
    static final class Envelope {
        final byte[] iv, ciphertext;
        Envelope(byte[] iv, byte[] ciphertext) { this.iv = iv; this.ciphertext = ciphertext; }
    }

    static Envelope encrypt(SecretKey key, byte[] clear) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return new Envelope(cipher.getIV(), cipher.doFinal(clear));
    }

    static byte[] decrypt(SecretKey key, Envelope envelope) throws Exception {
        // Older Android OpenSSL generates a 128-bit GCM nonce; modern
        // AndroidKeyStore generates 96 bits. Both retain a 128-bit auth tag.
        if ((envelope.iv.length != 12 && envelope.iv.length != 16) || envelope.ciphertext.length < 16)
            throw new IllegalArgumentException("Invalid encrypted settings");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope.iv));
        return cipher.doFinal(envelope.ciphertext);
    }
}
