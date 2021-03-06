package no.obos.util.servicebuilder.usertoken;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import no.obos.iam.tokenservice.TokenServiceClient;
import no.obos.iam.tokenservice.TokenServiceClientException;
import no.obos.iam.tokenservice.UserToken;
import no.obos.util.servicebuilder.addon.UserTokenFilterAddon;
import no.obos.util.servicebuilder.model.Constants;
import no.obos.util.servicebuilder.model.UibBruker;
import org.jvnet.hk2.annotations.Optional;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class UserTokenFilter implements ContainerRequestFilter {

    private final TokenServiceClient tokenServiceClient;
    private final UserTokenFilterAddon configuration;
    private final UserTokenAuthenticatedHandler authenticatedHandler;

    @Inject
    public UserTokenFilter(
            TokenServiceClient tokenServiceClient,
            UserTokenFilterAddon configuration,
            @Optional UserTokenAuthenticatedHandler authenticatedHandler)
    {
        this.tokenServiceClient = tokenServiceClient;
        this.configuration = configuration;
        this.authenticatedHandler = authenticatedHandler;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String usertokenId = requestContext.getHeaderString(Constants.USERTOKENID_HEADER);

        if (Strings.isNullOrEmpty(usertokenId)) {
            return;
        }

        UserToken userToken;
        try {
            userToken = tokenServiceClient.getUserTokenById(usertokenId);
        } catch (TokenServiceClientException e) {
            throw new NotAuthorizedException("UsertokenId: '" + usertokenId + "' not valid", e);
        }

        UibBrukerPrincipal brukerPrincipal = UibBrukerPrincipal.ofUserToken(userToken);
        ImmutableSet<String> tilganger = extractRolesAllowed(userToken, brukerPrincipal.uibBruker);

        requestContext.setSecurityContext(new AutentiseringsContext(brukerPrincipal, tilganger));

        if (authenticatedHandler != null) {
            authenticatedHandler.handle(requestContext);
        }
    }

    private ImmutableSet<String> extractRolesAllowed(UserToken userToken, UibBruker bruker) {
        List<String> tilgangerList = Lists.newArrayList();
        tilgangerList.addAll(configuration.userTokenTilganger.apply(userToken));
        tilgangerList.addAll(configuration.uibBrukerTilganger.apply(bruker));
        tilgangerList.addAll(configuration.rolleGirTilgang.keySet().stream()
                .filter(tilgang ->
                        bruker.roller.stream().anyMatch(configuration.rolleGirTilgang.get(tilgang))
                ).collect(Collectors.toSet())
        );
        return ImmutableSet.copyOf(tilgangerList.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toList())
        );
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    public static class AutentiseringsContext implements SecurityContext {

        UibBrukerPrincipal bruker;

        @Singular("tilgang")
        ImmutableSet<String> tilganger;

        @Override
        public Principal getUserPrincipal() {
            return bruker;
        }

        @Override
        public boolean isUserInRole(String role) {
            return tilganger.contains(role.trim().toUpperCase());
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return SecurityContext.BASIC_AUTH;
        }

    }
}
