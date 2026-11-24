package dev.sergiomarin.sitewatch.check;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * Decides whether an address is on the public internet. Checks run on the
 * server, so without this a user could point a site at {@code localhost}, the
 * database, the private network or the cloud metadata endpoint
 * ({@code 169.254.169.254}) and read the response (SSRF).
 */
final class PublicAddresses {

    private PublicAddresses() {
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        return address instanceof Inet4Address ? isPublicV4(b) : isPublicV6(b);
    }

    private static boolean isPublicV6(byte[] b) {
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        if ((b0 & 0xfe) == 0xfc) {
            return false; // fc00::/7 unique local
        }
        if (b0 == 0x20 && b1 == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
            return false; // 2001:db8::/32 documentation
        }
        if (b0 == 0x20 && b1 == 0x02) {
            return isPublicV4(Arrays.copyOfRange(b, 2, 6)); // 2002::/16 6to4 embeds an IPv4
        }
        if (startsWith(b, 0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0)) {
            return isPublicV4(Arrays.copyOfRange(b, 12, 16)); // 64:ff9b::/96 NAT64
        }
        if (startsWith(b, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)) {
            // ::/80 covers IPv4-compatible (::a.b.c.d) and IPv4-mapped (::ffff:a.b.c.d)
            int b10 = b[10] & 0xff;
            int b11 = b[11] & 0xff;
            if ((b10 == 0 && b11 == 0) || (b10 == 0xff && b11 == 0xff)) {
                return isPublicV4(Arrays.copyOfRange(b, 12, 16));
            }
            return false;
        }
        return true;
    }

    private static boolean isPublicV4(byte[] b) {
        int a = b[0] & 0xff;
        int c = b[1] & 0xff;
        int d = b[2] & 0xff;
        boolean reserved = a == 0                          // 0.0.0.0/8 "this network"
                || a == 10                                 // 10.0.0.0/8
                || a == 127                                // loopback
                || (a == 100 && (c & 0xc0) == 64)          // 100.64.0.0/10 carrier-grade NAT
                || (a == 169 && c == 254)                  // link-local, cloud metadata
                || (a == 172 && (c & 0xf0) == 16)          // 172.16.0.0/12
                || (a == 192 && c == 0 && d == 0)          // 192.0.0.0/24 IETF protocol assignments
                || (a == 192 && c == 0 && d == 2)          // TEST-NET-1
                || (a == 192 && c == 168)                  // 192.168.0.0/16
                || (a == 198 && (c & 0xfe) == 18)          // 198.18.0.0/15 benchmarking
                || (a == 198 && c == 51 && d == 100)       // TEST-NET-2
                || (a == 203 && c == 0 && d == 113)        // TEST-NET-3
                || a >= 224;                               // multicast, reserved, broadcast
        return !reserved;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
