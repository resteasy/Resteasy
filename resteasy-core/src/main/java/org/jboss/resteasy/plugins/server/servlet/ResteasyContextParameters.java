package org.jboss.resteasy.plugins.server.servlet;

/**
 * constant names of resteasy configuration variables within a servlet
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface ResteasyContextParameters {
   String RESTEASY_PROVIDERS = "resteasy.providers";

   String RESTEASY_USE_BUILTIN_PROVIDERS = "resteasy.use.builtin.providers";
   String RESTEASY_SCAN_PROVIDERS = "resteasy.scan.providers";
   String RESTEASY_SCAN = "resteasy.scan";
   String RESTEASY_SCAN_RESOURCES = "resteasy.scan.resources";
   String RESTEASY_JNDI_RESOURCES = "resteasy.jndi.resources";
   String RESTEASY_RESOURCES = "resteasy.resources";
   String RESTEASY_MEDIA_TYPE_MAPPINGS = "resteasy.media.type.mappings";
   String RESTEASY_LANGUAGE_MAPPINGS = "resteasy.language.mappings";
   String RESTEASY_MEDIA_TYPE_PARAM_MAPPING = "resteasy.media.type.param.mapping";
   String RESTEASY_ROLE_BASED_SECURITY = "resteasy.role.based.security";
   String RESTEASY_SCANNED_BY_DEPLOYER = "resteasy.scanned.by.deployer";
   String RESTEASY_JNDI_COMPONENT_RESOURCES = "resteasy.jndi.component.resources";
   String RESTEASY_UNWRAPPED_EXCEPTIONS = "resteasy.unwrapped.exceptions";
   String RESTEASY_EXPAND_ENTITY_REFERENCES = "resteasy.document.expand.entity.references";
   String RESTEASY_SECURE_PROCESSING_FEATURE = "resteasy.document.secure.processing.feature";
   String RESTEASY_DISABLE_DTDS = "resteasy.document.secure.disableDTDs";
   String RESTEASY_GZIP_MAX_INPUT = "resteasy.gzip.max.input";
   String RESTEASY_SECURE_RANDOM_MAX_USE = "resteasy.secure.random.max.use";
   String RESTEASY_ADD_CHARSET = "resteasy.add.charset";
   String RESTEASY_DISABLE_HTML_SANITIZER = "resteasy.disable.html.sanitizer";
   String RESTEASY_ORIGINAL_WEBAPPLICATIONEXCEPTION_BEHAVIOR = "resteasy.original.webapplicationexception.behavior";

   /**
    * Enable tracing support.
    * <p>
    * It allows service developer to get diagnostic information about request processing by RESTEasy.
    * Those diagnostic/tracing information are returned in response headers ({@code X-RESTEasy-Tracing-nnn}).
    * The feature should not be switched on on production environment.
    * <p>
    * Allowed values:
    * <ul>
    * <li>{@code OFF} - tracing support is disabled.</li>
    * <li>{@code ON_DEMAND} - tracing support is in 'stand by' mode, it is enabled on demand by existence of request HTTP header</li>
    * <li>{@code ALL} - tracing support is enabled for every request.</li>
    * </ul>
    * Type of the property value is {@code String}. The default value is {@code "OFF"}.
    * <p>
    * The name of the configuration property is <code>{@value}</code>.
    * </p>
    */
   String RESTEASY_TRACING_TYPE = "resteasy.server.tracing.type";
   String RESTEASY_TRACING_TYPE_OFF = "OFF";
   String RESTEASY_TRACING_TYPE_ALL = "ALL";
   String RESTEASY_TRACING_TYPE_ON_DEMAND = "ON_DEMAND";

   /**
    * Set level o tracing information.
    * <p>
    * The property allows to set application default level o diagnostic information.
    * Tracing level can be changed for each request by specifying request HTTP header {@code X-RESTEasy-Tracing-Threshold}.
    * <p>
    * Allowed values:
    * <ul>
    * <li>{@code SUMMARY}</li>
    * <li>{@code TRACE}</li>
    * <li>{@code VERBOSE}</li>
    * </ul>
    * Type of the property value is {@code String}. The default value is {@code "TRACE"}.
    * <p>
    * The name of the configuration property is <code>{@value}</code>.
    * </p>
    */
   String RESTEASY_TRACING_THRESHOLD = "resteasy.server.tracing.threshold";
   String RESTEASY_TRACING_LEVEL_SUMMARY = "SUMMARY";
   String RESTEASY_TRACING_LEVEL_TRACE = "TRACE";
   String RESTEASY_TRACING_LEVEL_VERBOSE = "VERBOSE";


   // used to store an instance of tracing logger
   String RESTEASY_TRACING_INSTANCE = "resteasy.server.tracing.instance";

   // these scanned variables are provided by a deployer
   String RESTEASY_SCANNED_RESOURCES = "resteasy.scanned.resources";
   String RESTEASY_SCANNED_RESOURCE_CLASSES_WITH_BUILDER = "resteasy.scanned.resource.classes.with.builder";
   String RESTEASY_SCANNED_PROVIDERS = "resteasy.scanned.providers";
   String RESTEASY_SCANNED_JNDI_RESOURCES = "resteasy.scanned.jndi.resources";
   String RESTEASY_CONTEXT_OBJECTS = "resteasy.context.objects";
   String RESTEASY_USE_CONTAINER_FORM_PARAMS = "resteasy.use.container.form.params";
   String RESTEASY_DEPLOYMENTS = "resteasy.deployments";
   String RESTEASY_SERVLET_MAPPING_PREFIX = "resteasy.servlet.mapping.prefix";
   String RESTEASY_WIDER_REQUEST_MATCHING = "resteasy.wider.request.matching";

   String RESTEASY_PREFER_JACKSON_OVER_JSONB = "resteasy.preferJacksonOverJsonB";
   String RESTEASY_PATCH_FILTER_DISABLED = "resteasy.patchfilter.disabled";
   String RESTEASY_PATCH_FILTER_LEGACY = "resteasy.patchfilter.legacy";

    String RESTEASY_STATISTICS_ENABLED = "resteasy.statistics.enabled";

    // Added for quarkus.  Initial use switch from warning msg to exception message.
    String RESTEASY_FAIL_FAST_ON_MULTIPLE_RESOURCES_MATCHING = "resteasy.fail.fast.on.multiple.resources.matching";
    String RESTEASY_MATCH_CACHE_ENABLED = "resteasy.match.cache.enabled";
    String RESTEASY_MATCH_CACHE_SIZE = "resteasy.match.cache.size";

    String RESTEASY_ASYNC_TIMEOUT_SCHEDULER_MIN_POOL_SIZE = "resteasy.async.timeout.scheduler.min.pool.size";
}
