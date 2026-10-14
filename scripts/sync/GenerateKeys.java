import java.security.*;
import java.util.Base64;

/** Run with Java 17: java GenerateKeys.java. Output belongs only in an ignored local config. */
class GenerateKeys {
    public static void main(String[] args) throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] token=new byte[48];new SecureRandom().nextBytes(token);
        System.out.println("{\"privateKey\":\""+Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                +"\",\"publicKey\":\""+Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                +"\",\"token\":\""+Base64.getEncoder().encodeToString(token)+"\"}");
    }
}
