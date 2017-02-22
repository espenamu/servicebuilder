package no.obos.util.servicebuilder.client;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Wither;
import no.obos.util.servicebuilder.Constants;
import no.obos.util.servicebuilder.util.GuavaHelper;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StubGenerator {
    //    final String appToken;
    final Client client;
    final URI uri;
    @Wither
    final String userToken;
    @Wither
    final boolean logging;
    @Wither(AccessLevel.PRIVATE)
    final ImmutableList<Cookie> cookies;
    @Wither(AccessLevel.PRIVATE)
    final ImmutableMap<String, String> headers;

    public static StubGenerator defaults(Client client, URI uri) {
        return new StubGenerator(client, uri, null, true, ImmutableList.of(), ImmutableMap.of());
    }

    public <T> T generateClient(Class<T> resource) {
        Client clientToUse = client != null
                ? client
                : ClientBuilder.newClient();

        MultivaluedMap<String, Object> headerArg = new MultivaluedHashMap<>(headers);
        if (! Strings.isNullOrEmpty(userToken)) {
            headerArg.putSingle(Constants.USERTOKENID_HEADER, userToken);
        }

        WebTarget webTarget = clientToUse.target(uri);
        webTarget.register(ClientErrorResponseFilter.class);
        webTarget.register(RequestIdClientFilter.class);
        if (logging) {
            webTarget.register(ClientLogFilter.class);
        }

        return WebResourceFactory.newResource(resource, webTarget, false, headerArg, cookies, new Form());
    }

    public StubGenerator plusHeader(String key, String value) {return withHeaders(GuavaHelper.plus(headers, key, value));}

    public StubGenerator plusCookie(Cookie cookie) {return withCookies(GuavaHelper.plus(cookies, cookie));}
}
