package no.obos.util.servicebuilder.exception;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import no.obos.util.model.ProblemResponse;
import no.obos.util.servicebuilder.ExceptionMapperAddon;
import no.obos.util.servicebuilder.exception.domain.LogLevel;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public final class ExceptionUtil {

    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    public static final String APPLICATION_PROBLEM_XML = MediaType.APPLICATION_XML;


    @Context
    HttpHeaders headers;

    @Inject
    ExceptionMapperAddon.Configuration config;

    @Context
    HttpServletRequest request;


    private final static ImmutableMap<MediaType, String> mediaTypeMap = ImmutableMap.<MediaType, String>builder()
            .put(MediaType.APPLICATION_JSON_TYPE, ExceptionUtil.APPLICATION_PROBLEM_JSON)
            .put(MediaType.APPLICATION_XML_TYPE, ExceptionUtil.APPLICATION_PROBLEM_XML)
            .build();


    public ExceptionDescription withDefaults(ExceptionDescription problem) {
        ExceptionDescription.ExceptionDescriptionBuilder withDefaults = problem.toBuilder();
        if (problem.status == null) {
            withDefaults = withDefaults.status(500);
        }

        if (Strings.isNullOrEmpty(problem.title)) {
            Response.Status status = Response.Status.fromStatusCode(withDefaults.build().status);
            withDefaults = withDefaults.title(status != null ? status.getReasonPhrase() : "Unclassified error");
        }

        if (problem.detail == null) {
            withDefaults = withDefaults.detail(problem.exception.getLocalizedMessage());
        }

        if (problem.reference == null) {
            withDefaults = withDefaults.reference(lagFeilreferanse());
        }

        if (problem.logLevel == null) {
            withDefaults = withDefaults.logLevel(LogLevel.ERROR);
        }

        if (config != null) {
            if (problem.logStackTrace == null) {
                withDefaults = withDefaults.logStackTrace(shouldPrintStacktrace(problem.exception, config.stacktraceConfig));
            }
        } else {
            if (problem.logStackTrace == null) {
                log.warn("Config not set in ExceptionUtil");
                withDefaults = withDefaults.logStackTrace(true);
            }
        }

        if (problem.logger == null) {
            withDefaults = withDefaults.logger(log);
        }


        return withDefaults.build();
    }

    public Response handle(Throwable throwable, Configurator config) {
        ExceptionDescription.ExceptionDescriptionBuilder builder = ExceptionDescription.builder();
        builder.exception(throwable);
        builder = config.apply(builder);
        return toResponse(builder.build());
    }

    public Response toResponse(ExceptionDescription problem) {
        ExceptionDescription problemWithDefaults = withDefaults(problem);
        logProblem(problemWithDefaults);
        return problemDescriptionToReponse(problemWithDefaults);
    }

    public Response problemDescriptionToReponse(ExceptionDescription problem) {
        val problemResponse = new ProblemResponse(problem.title, problem.detail, problem.status, problem.reference);
        String mediaType = getMediaType();
        return Response.status(problem.status).type(mediaType).entity(problemResponse).build();
    }

    public void logProblem(ExceptionDescription problem) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Caught exception of type: %s\n", problem.exception.getClass().getName()));
        sb.append(String.format("Exception message: %s\n", problem.exception.getLocalizedMessage()));
        if (problem.status != null && ! Strings.isNullOrEmpty(problem.title)) {
            sb.append(String.format("Problem type: %d - %s\n", problem.status, problem.title));
        }
        if (! Strings.isNullOrEmpty(problem.detail)) {
            sb.append(String.format("Detail message: %s\n", problem.detail));
        }
        if (! Strings.isNullOrEmpty(problem.reference)) {
            sb.append(String.format("Feilreferanse: %s\n", problem.reference));
        }
        if (! Strings.isNullOrEmpty(problem.internalMessage)) {
            sb.append(String.format("Additional info: %s\n", problem.internalMessage));
        }
        sb.append("Context:\n");
        sb.append(getContextDescription());
        if (problem.logStackTrace) {
            doLog(sb.toString(), problem.exception, problem.logLevel, problem.logger);
        } else {
            doLog(sb.toString(), problem.logLevel, problem.logger);
        }
    }

    public String getContextDescription() {
        StringBuilder sb = new StringBuilder();
        if (request != null) {
            if (! Strings.isNullOrEmpty(request.getRequestURI())) {
                String method = Strings.nullToEmpty(request.getMethod());
                String query = (Strings.isNullOrEmpty(request.getQueryString())) ? "" : "?" + request.getQueryString();
                sb.append(String.format("  uri: %s %s%s\n", method, request.getRequestURI(), query));
            }
            if (request.getUserPrincipal() != null) {
                sb.append(String.format("  userPrincipal: %s\n", request.getUserPrincipal().toString()));
            }
            if (request.getRemoteAddr() != null) {
                sb.append(String.format("  remoteAddr: %s\n", request.getRemoteAddr()));
            }
        } else {
            log.warn("Request context null in exceptionUtil");
        }

        if (headers != null) {
            Joiner joiner = Joiner.on(", ").skipNulls();
            headers.getRequestHeaders().entrySet().forEach(entry -> {
                String headerName = entry.getKey();
                String headerValue = joiner.join(entry.getValue());
                sb.append(String.format("  Header: %s = %s\n", headerName, headerValue));
            });
        }
        return sb.toString();

    }

    public static void doLog(String s, LogLevel logLevel, Logger logger) {
        switch (logLevel) {
            case ERROR: {
                logger.error(s);
                break;
            }

            case WARN: {
                logger.warn(s);
                break;
            }

            case INFO: {
                logger.info(s);
                break;
            }

            case DEBUG: {
                logger.debug(s);
                break;
            }

            case TRACE: {
                logger.trace(s);
                break;
            }

            default: {
                logger.error(s);
                break;
            }
        }
    }

    public static void doLog(String s, Throwable exception, LogLevel logLevel, Logger logger) {
        switch (logLevel) {
            case ERROR: {
                logger.error(s, exception);
                break;
            }

            case WARN: {
                logger.warn(s, exception);
                break;
            }

            case INFO: {
                logger.info(s, exception);
                break;
            }

            case DEBUG: {
                logger.debug(s, exception);
                break;
            }

            case TRACE: {
                logger.trace(s, exception);
                break;
            }

            default: {
                logger.error(s, exception);
                break;
            }
        }
    }


    /**
     * Gets the mediaType to use based on the clients Accept-header
     *
     * @return MediaType as String, default {@literal APPLICATION_PROBLEM_JSON}
     */
    public String getMediaType() {
        if (headers == null) {
            log.warn("context injected headers are null");
            return APPLICATION_PROBLEM_JSON;
        }
        List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        Optional<String> firstAcceptableMediaType = acceptableMediaTypes.stream().map(mediaTypeMap::get).filter(mt -> mt != null).findFirst();
        return firstAcceptableMediaType.orElse(APPLICATION_PROBLEM_JSON);
    }


    public static String lagFeilreferanse() {
        return UUID.randomUUID().toString();
    }

    public static boolean shouldPrintStacktrace(Throwable throwable, Map<Class<?>, Boolean> config) {
        Class<?> clazz = throwable.getClass();
        while (clazz.getSuperclass() != null && ! Throwable.class.equals(clazz.getSuperclass())) {
            if (config.containsKey(clazz)) {
                return config.get(clazz) == Boolean.TRUE;
            }
            clazz = clazz.getSuperclass();
        }
        return true;
    }

    public interface Configurator {
        ExceptionDescription.ExceptionDescriptionBuilder apply(ExceptionDescription.ExceptionDescriptionBuilder it);
    }
}
