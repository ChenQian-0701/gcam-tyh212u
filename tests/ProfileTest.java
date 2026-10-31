package com.tyh212u.gcamhook;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProfileTest {
    public static void main(String[] args) throws Exception {
        if (!CompatibilityProfile.matchesDevice("TYH212U", "ARES", 30)) throw new AssertionError();
        String[][] invalid = {{"Other", "ARES"}, {"TYH212U", "Other"}, {null, "ARES"}};
        for (String[] values : invalid)
            if (CompatibilityProfile.matchesDevice(values[0], values[1], 30)) throw new AssertionError();
        if (CompatibilityProfile.matchesDevice("TYH212U", "ARES", 31)) throw new AssertionError();
        Path file=Files.createTempFile("gcam-profile-", ".txt");
        try {
            Files.write(file, new byte[]{97,98,99});
            if (!CompatibilityProfile.sha256(file.toString()).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")) throw new AssertionError();
            if (CompatibilityProfile.APK_SHA256.equals(CompatibilityProfile.sha256(file.toString()))) throw new AssertionError();
        } finally { Files.delete(file); }
        System.out.println("Profile negative cases and APK digest: PASS");
    }
}
