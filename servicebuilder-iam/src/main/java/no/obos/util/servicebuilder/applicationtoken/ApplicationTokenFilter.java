package no.obos.util.servicebuilder.applicationtoken;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import no.obos.iam.access.TokenCheckResult;
import no.obos.iam.tokenservice.ApplicationToken;
import no.obos.iam.tokenservice.TokenServiceClient;
import no.obos.iam.tokenservice.TokenServiceClientException;
import no.obos.util.model.ProblemResponse;
import no.obos.util.servicebuilder.addon.ApplicationTokenFilterAddon;
import no.obos.util.servicebuilder.annotations.AppIdWhitelist;
import no.obos.util.servicebuilder.annotations.AppTokenRequired;
import no.obos.util.servicebuilder.model.Constants;
import no.obos.util.servicebuilder.util.AnnotationUtil;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static no.obos.iam.access.TokenCheckResult.AUTHORIZED;
import static no.obos.iam.access.TokenCheckResult.UNAUTHORIZED;

@Priority(Priorities.AUTHENTICATION)
@Slf4j
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class ApplicationTokenFilter implements ContainerRequestFilter {

    @Deprecated
    public static final String APPTOKENID_HEADER = Constants.APPTOKENID_HEADER;

    private final NumericAppIdApplicationTokenAccessValidator applicationTokenAccessValidator;
    final ApplicationTokenFilterAddon configuration;
    private final TokenServiceClient tokenServiceClient;

    final private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (alwaysAccept(requestContext)) {
            return;
        }

        String apptokenid = requestContext.getHeaderString(Constants.APPTOKENID_HEADER);

        if (apptokenid == null || apptokenid.trim().isEmpty()) {
            handleErrorNoAppToken(requestContext);
        } else {
            TokenCheckResult result = checkAppTokenAndWhitelist(apptokenid);

            if (result != AUTHORIZED) {
                handleErrorUnauthorized(requestContext, apptokenid, result);
            } else {
                requestContext.setSecurityContext(new AutentiseringsContext(getApplicationToken(apptokenid)));
            }
        }
    }

    private TokenCheckResult checkAppTokenAndWhitelist(String apptokenid) {
        TokenCheckResult result = applicationTokenAccessValidator.checkApplicationTokenId(apptokenid);
        return adjustForWhitelist(result, getApplicationId(apptokenid));
    }

    private TokenCheckResult adjustForWhitelist(TokenCheckResult result, Integer applicationId) {
        if (result == UNAUTHORIZED && isInWhitelist(applicationId)) {
            return AUTHORIZED;
        }
        if (result == AUTHORIZED && isExclusiveWhitelist() && ! isInWhitelist(applicationId)) {
            return UNAUTHORIZED;
        }
        return result;
    }

    private ApplicationToken getApplicationToken(String apptokenid) {
        try {
            return tokenServiceClient.getApptokenById(apptokenid);
        } catch (TokenServiceClientException e) {
            return null;
        }
    }

    private Integer getApplicationId(String apptokenid) {
        return Optional.ofNullable(getApplicationToken(apptokenid))
                .map(ApplicationToken::getApplicationId)
                .map(Integer::parseInt)
                .orElse(null);
    }

    private Boolean isExclusiveWhitelist() {
        return Optional.ofNullable(getWhitelistAnnotation())
                .map(AppIdWhitelist::exclusive)
                .orElse(false);
    }

    private boolean isInWhitelist(Integer applicationId) {
        if (applicationId == null) {
            return false;
        }
        return Optional.ofNullable(getWhitelistAnnotation())
                .map(AppIdWhitelist::value)
                .map(Arrays::stream)
                .map(whitelistAppIds -> whitelistAppIds.anyMatch(whitelistedAppId -> applicationId == whitelistedAppId))
                .orElse(false);
    }

    private AppIdWhitelist getWhitelistAnnotation() {
        return AnnotationUtil.getAnnotation(AppIdWhitelist.class, resourceInfo.getResourceMethod());
    }

    private void handleErrorUnauthorized(ContainerRequestContext requestContext, String apptokenid, TokenCheckResult result) {
        handleUnauthorized(requestContext, "Apptokenid '" + apptokenid + "' is " + result);
    }

    private void handleErrorNoAppToken(ContainerRequestContext requestContext) {
        handleUnauthorized(requestContext, "Header (" + Constants.APPTOKENID_HEADER + ") for application token ID is missing");
    }

    private static void handleUnauthorized(ContainerRequestContext requestContext, String msg) {
        log.warn(msg);
        requestContext.abortWith(Response
                .status(Status.UNAUTHORIZED)
                .entity(new ProblemResponse("ERROR", msg, Status.UNAUTHORIZED.getStatusCode(), UUID.randomUUID().toString()))
                .build());
    }

    public boolean alwaysAccept(ContainerRequestContext requestContext) {
        String absolutePath = requestContext.getUriInfo().getAbsolutePath().toString();
        String requestMethod = requestContext.getMethod();

        AppTokenRequired methodAnnotation = resourceInfo.getResourceMethod() != null
                ? resourceInfo.getResourceMethod().getAnnotation(AppTokenRequired.class)
                : null;
        AppTokenRequired classAnnotation = resourceInfo.getResourceClass() != null
                ? resourceInfo.getResourceClass().getAnnotation(AppTokenRequired.class)
                : null;
        boolean annotationFasttrack = ! configuration.requireAppTokenByDefault;
        if (methodAnnotation != null) {
            annotationFasttrack = ! methodAnnotation.value();
        } else if (classAnnotation != null) {
            annotationFasttrack = ! classAnnotation.value();
        }

        return absolutePath.contains("swagger") ||
                "OPTIONS".equals(requestMethod) ||
                configuration.fasttrackFilter.test(requestContext) ||
                annotationFasttrack;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    public static class AutentiseringsContext implements SecurityContext {

        ApplicationToken applicationToken;

        @Override
        public Principal getUserPrincipal() {
            return new ApplicationPrincipal(applicationToken);
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
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

