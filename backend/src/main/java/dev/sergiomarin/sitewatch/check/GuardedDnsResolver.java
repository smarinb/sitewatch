package dev.sergiomarin.sitewatch.check;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Resolves hosts and refuses any whose addresses are not all public. The HTTP
 * client connects to exactly the addresses returned here, so the check also
 * covers every redirect and cannot be bypassed by DNS rebinding between a
 * validation step and the connection.
 */
class GuardedDnsResolver implements DnsResolver {

    private final DnsResolver delegate;
    private final boolean allowPrivate;

    GuardedDnsResolver(boolean allowPrivate) {
        this(SystemDefaultDnsResolver.INSTANCE, allowPrivate);
    }

    GuardedDnsResolver(DnsResolver delegate, boolean allowPrivate) {
        this.delegate = delegate;
        this.allowPrivate = allowPrivate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);
        if (addresses == null || addresses.length == 0) {
            // Never return null: the client would fall back to an unresolved, unchecked address.
            throw new UnknownHostException(host);
        }
        if (!allowPrivate) {
            for (InetAddress address : addresses) {
                if (!PublicAddresses.isPublic(address)) {
                    throw new BlockedAddressException(host, address);
                }
            }
        }
        return addresses;
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
        return Arrays.stream(resolve(host)).map(a -> new InetSocketAddress(a, port)).toList();
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }
}
