package dev.sergiomarin.sitewatch.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = { "127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.5", "172.16.0.1", "172.31.255.255",
            "192.168.1.10", "169.254.169.254", "100.64.0.1", "198.18.0.1", "192.0.2.1", "224.0.0.1",
            "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1",
            "::ffff:169.254.169.254", "::10.0.0.1", "64:ff9b::a9fe:a9fe", "2002:c0a8:0101::1", "2001:db8::1" })
    void rejectsNonPublicAddresses(String address) throws UnknownHostException {
        assertThat(PublicAddresses.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.1.1.1", "8.8.8.8", "172.15.0.1", "172.32.0.1", "100.128.0.1", "93.184.215.14",
            "2606:4700:4700::1111", "2002:0808:0808::1", "64:ff9b::808:808" })
    void acceptsPublicAddresses(String address) throws UnknownHostException {
        assertThat(PublicAddresses.isPublic(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void resolverRejectsHostsPointingAtPrivateAddresses() throws UnknownHostException {
        InetAddress metadata = InetAddress.getByName("169.254.169.254");
        InetAddress publicIp = InetAddress.getByName("93.184.215.14");
        var resolver = new GuardedDnsResolver(new FakeDns(publicIp, metadata), false);

        // One bad address is enough: the client could connect to any of them.
        assertThatThrownBy(() -> resolver.resolve("rebind.example.com"))
                .isInstanceOf(BlockedAddressException.class)
                .hasMessageContaining("169.254.169.254");
        assertThatThrownBy(() -> resolver.resolve("rebind.example.com", 443))
                .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    void resolverPassesPublicHostsThrough() throws UnknownHostException {
        InetAddress publicIp = InetAddress.getByName("93.184.215.14");
        var resolver = new GuardedDnsResolver(new FakeDns(publicIp), false);

        assertThat(resolver.resolve("example.com")).containsExactly(publicIp);
    }

    @Test
    void resolverNeverReturnsEmptyResults() {
        var resolver = new GuardedDnsResolver(new FakeDns(), true);

        assertThatThrownBy(() -> resolver.resolve("nothing.example.com")).isInstanceOf(UnknownHostException.class);
    }

    private record FakeDns(InetAddress... addresses) implements org.apache.hc.client5.http.DnsResolver {

        @Override
        public InetAddress[] resolve(String host) {
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }
}
