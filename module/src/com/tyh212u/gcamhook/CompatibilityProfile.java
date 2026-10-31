package com.tyh212u.gcamhook;

import java.io.FileInputStream;
import java.security.MessageDigest;

/** Deliberately narrow profile. Porting means adding evidence, not bypassing it. */
final class CompatibilityProfile {
    static final String APK_SHA256 = "4ad2f119c3d3c70d05a31bf6fb70ce1d2ba22eed4b52097b37a5f2cea96c9c6d";

    static boolean matchesDevice(String model, String device, int sdk) {
        return "TYH212U".equals(model) && "ARES".equals(device) && sdk == 30;
    }

    static String sha256(String file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
