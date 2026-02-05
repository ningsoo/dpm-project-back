package dopamine.soundock.global;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 외부 API로 부터 받은 암호화된 패스워드리스 토큰을 복호화 할 때 사용하는 클래스
public class AESUtil {

    public static String decrypt(String cipherText, String key) throws Exception {
        // The spec uses the same 16-byte key for both Key and IV
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec iv = new IvParameterSpec(keyBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

        byte[] decodedBytes = Base64.getDecoder().decode(cipherText);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
