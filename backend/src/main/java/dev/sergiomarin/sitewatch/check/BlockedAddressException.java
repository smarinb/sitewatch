package dev.sergiomarin.sitewatch.check;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** A host resolved to an address checks may not connect to. */
class BlockedAddressException extends UnknownHostException {

    BlockedAddressException(String host, InetAddress address) {
        super(host + " resolves to a non-public address (" + address.getHostAddress() + ")");
    }
}
