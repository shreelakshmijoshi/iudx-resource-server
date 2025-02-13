package iudx.resource.server.databroker.util;

public class Constants {
    public static final String DATA_BROKER_SERVICE_ADDRESS = "iudx.rs.broker.service";
    public static final String REQUEST_GET = "GET";
    public static final String REQUEST_POST = "POST";
    public static final String REQUEST_PUT = "PUT";
    public static final String REQUEST_DELETE = "DELETE";
    public static String UNIQUE_ATTR_Q = "rs-unique-attributes";
    public static String ASYNC_QUERY_Q = "rs-async-query";
    public static String TOKEN_INVALID_Q = "rs-invalid-sub";
    public static final String QUEUE_NAME = "queueName";
    public static final String PASSWORD = "password";
    public static final String TAGS = "tags";
    public static final String NONE = "None";
    public static final String CONFIGURE = "configure";
    public static final String DENY = "";
    public static final String READ = "read";
    public static final String WRITE = "write";
    public static final String QUEUE = "queue";
    public static final String X_MESSAGE_TTL_NAME = "x-message-ttl";
    public static final String X_MAXLENGTH_NAME = "x-max-length";
    public static final String X_QUEUE_MODE_NAME = "x-queue-mode";
    public static final long X_MESSAGE_TTL_VALUE = 86400000; // 24hours
    public static final int X_MAXLENGTH_VALUE = 10000;
    public static final String X_QUEUE_MODE_VALUE = "lazy";
    public static final String X_QUEUE_TYPE = "durable";
    public static final String X_QUEUE_ARGUMENTS = "arguments";
    public static final String DATA_WILDCARD_ROUTINGKEY = "/.*";
    public static final String EXCHANGE_NAME = "exchangeName";
    public static final String EXCHANGE = "exchange";
    public static final String USER_NAME = "username";
    public static final String APIKEY = "apiKey";
    public static final String ID = "id";
    public static final String URL = "URL";
    public static final String VHOST = "vHost";
    public static final String PORT = "port";
    public static final String VHOST_PERMISSIONS = "vhostPermissions";
    public static final String API_KEY_MESSAGE =
            "Use the apiKey returned on registration, if lost please use /resetPassword API";
    public static final String ENTITIES = "entities";
}
