package iudx.resource.server.apiserver.subscription.controller;

import static iudx.resource.server.apiserver.util.Constants.*;
import static iudx.resource.server.cache.util.Constants.CACHE_SERVICE_ADDRESS;
import static iudx.resource.server.common.Constants.AUTH_SERVICE_ADDRESS;
import static iudx.resource.server.database.postgres.util.Constants.PG_SERVICE_ADDRESS;
import static iudx.resource.server.databroker.util.Constants.BAD_REQUEST_DATA;
import static iudx.resource.server.databroker.util.Constants.DATA_BROKER_SERVICE_ADDRESS;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import iudx.resource.server.apiserver.handler.FailureHandler;
import iudx.resource.server.apiserver.subscription.model.DeleteSubsResultModel;
import iudx.resource.server.apiserver.subscription.model.GetResultModel;
import iudx.resource.server.apiserver.subscription.model.PostModelSubscription;
import iudx.resource.server.apiserver.subscription.model.SubscriptionData;
import iudx.resource.server.apiserver.subscription.service.SubscriptionService;
import iudx.resource.server.apiserver.subscription.service.SubscriptionServiceImpl;
import iudx.resource.server.apiserver.subscription.util.SubsType;
import iudx.resource.server.authenticator.AuthenticationService;
import iudx.resource.server.authenticator.handler.authentication.AuthHandler;
import iudx.resource.server.authenticator.handler.authentication.TokenIntrospectHandler;
import iudx.resource.server.authenticator.handler.authorization.AuthValidationHandler;
import iudx.resource.server.authenticator.handler.authorization.AuthorizationHandler;
import iudx.resource.server.authenticator.handler.authorization.GetIdHandler;
import iudx.resource.server.authenticator.handler.authorization.TokenRevokedHandler;
import iudx.resource.server.authenticator.model.DxRole;
import iudx.resource.server.cache.service.CacheService;
import iudx.resource.server.common.Api;
import iudx.resource.server.common.CatalogueService;
import iudx.resource.server.common.RequestType;
import iudx.resource.server.common.ResultModel;
import iudx.resource.server.common.validation.handler.ValidationHandler;
import iudx.resource.server.database.postgres.model.PostgresResultModel;
import iudx.resource.server.database.postgres.service.PostgresService;
import iudx.resource.server.databroker.service.DataBrokerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubscriptionController {
  private static final Logger LOGGER = LogManager.getLogger(SubscriptionController.class);
  private final Router router;
  private final Vertx vertx;
  private final Api api;
  ValidationHandler subsValidationHandler;
  FailureHandler failureHandler;
  private PostgresService postgresService;
  private SubscriptionService subscriptionService;
  private DataBrokerService dataBrokerService;
  private CacheService cacheService;
  private AuthenticationService authenticator;
  private String audience;
  private JsonObject config;

  public SubscriptionController(Vertx vertx, Router router, Api api, JsonObject config) {
    this.vertx = vertx;
    this.router = router;
    this.api = api;
    this.audience = config.getString("audience");
    this.config = config;
    this.subsValidationHandler = new ValidationHandler(vertx, RequestType.SUBSCRIPTION);
    this.failureHandler = new FailureHandler();
  }

  public void init() {
    CatalogueService catalogueService = new CatalogueService(cacheService, config, vertx);

    AuthHandler authHandler = new AuthHandler(authenticator);
    Handler<RoutingContext> getIdHandler =
        new GetIdHandler(api).withNormalisedPath(api.getSubscriptionUrl());

    Handler<RoutingContext> isTokenRevoked = new TokenRevokedHandler(cacheService).isTokenRevoked();
    Handler<RoutingContext> validateToken =
        new AuthValidationHandler(api, cacheService, catalogueService);

    Handler<RoutingContext> userAndAdminAccessHandler =
        new AuthorizationHandler()
            .setUserRolesForEndpoint(
                DxRole.DELEGATE, DxRole.CONSUMER, DxRole.PROVIDER, DxRole.ADMIN);
    Handler<RoutingContext> tokenIntrospectHandler =
        new TokenIntrospectHandler().validateTokenForRs(audience);

    // TODO: Need to add auth and auditing insert

    proxyRequired();

    router
        .post(api.getSubscriptionUrl())
        .handler(subsValidationHandler)
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::postSubscriptions)
        .failureHandler(failureHandler);

    router
        .patch(api.getSubscriptionUrl() + "/:userid/:alias")
        .handler(subsValidationHandler)
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::appendSubscription)
        .failureHandler(failureHandler);

    router
        .put(api.getSubscriptionUrl() + "/:userid/:alias")
        .handler(subsValidationHandler)
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::updateSubscription)
        .failureHandler(failureHandler);

    router
        .get(api.getSubscriptionUrl() + "/:userid/:alias")
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::getSubscription)
        .failureHandler(failureHandler);

    router
        .get(api.getSubscriptionUrl())
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::getAllSubscriptionForUser)
        .failureHandler(failureHandler);

    router
        .delete(api.getSubscriptionUrl() + "/:userid/:alias")
        .handler(getIdHandler)
        .handler(authHandler)
        .handler(tokenIntrospectHandler)
        .handler(validateToken)
        .handler(userAndAdminAccessHandler)
        .handler(isTokenRevoked)
        .handler(this::deleteSubscription)
        .failureHandler(failureHandler);

    subscriptionService =
        new SubscriptionServiceImpl(postgresService, dataBrokerService, cacheService);
  }

  private void appendSubscription(RoutingContext routingContext) {
    LOGGER.trace("Info: appendSubscription method started");
    HttpServerRequest request = routingContext.request();
    String userid = request.getParam(USER_ID);
    String alias = request.getParam(JSON_ALIAS);
    String subsId = userid + "/" + alias;
    JsonObject requestJson = routingContext.body().asJsonObject();
    String instanceId = request.getHeader(HEADER_HOST);
    /*requestJson.put(SUBSCRIPTION_ID, subsId);
    requestJson.put(JSON_INSTANCEID, instanceId);*/
    String subscriptionType = SubsType.STREAMING.type;
    /*requestJson.put(SUB_TYPE, subscriptionType);
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");*/
    HttpServerResponse response = routingContext.response();
    /*String entities = requestJson.getJsonArray("entities").getString(0);*/
    /*JsonObject cacheJson = new JsonObject().put("key", entities).put("type", CATALOGUE_CACHE);*/
    /*requestJson.put(USER_ID, authInfo.getString(USER_ID));*/

    String entities = requestJson.getJsonArray("entities").getString(0);
    String userId =
        "fd47486b-3497-4248-ac1e-082e4d37a66c"; // TODO: Change this to take userId from AuthInfo
    PostModelSubscription postModelSubscription =
        new PostModelSubscription(
            userId, subscriptionType, instanceId, entities, requestJson.getString("name"));

    Future<SubscriptionData> subsReq =
        subscriptionService.appendSubscription(postModelSubscription, subsId);
    subsReq.onComplete(
        subsRequestHandler -> {
          if (subsRequestHandler.succeeded()) {
            LOGGER.info("result : " + subsRequestHandler.result());
            routingContext.data().put(RESPONSE_SIZE, 0);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(subsRequestHandler.result().streamingResult().toString());
            /*Future.future(fu -> updateAuditTable(routingContext));
            handleSuccessResponse(
                    response, ResponseType.Created.getCode(), subsRequestHandler.result().toString());*/
          } else {
            LOGGER.error("Fail: Bad request");
            ResultModel rs = new ResultModel(subsRequestHandler.cause().getMessage(), response);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(rs.getStatusCode())
                .end(rs.toJson().toString());
          }
        });
  }

  private void updateSubscription(RoutingContext routingContext) {
    LOGGER.trace("Info: updateSubscription method started");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String userid = request.getParam(USER_ID);
    String alias = request.getParam(JSON_ALIAS);
    String subsId = userid + "/" + alias;
    JsonObject requestJson = routingContext.body().asJsonObject();
    String instanceId = request.getHeader(HEADER_HOST);
    String subscriptionType = SubsType.STREAMING.type;
    /*requestJson.put(SUB_TYPE, subscriptionType);
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");*/
    if (requestJson.getString(JSON_NAME).equalsIgnoreCase(alias)) {
      String entities = requestJson.getJsonArray("entities").getString(0);
      Future<JsonObject> subsReq =
          subscriptionService.updateSubscription(
              entities, subsId, new JsonObject() /*authInfo*/); // TODO:Change authinfo into expiry
      subsReq.onComplete(
          subsRequestHandler -> {
            if (subsRequestHandler.succeeded()) {
              LOGGER.info("result : " + subsRequestHandler.result());
              routingContext.data().put(RESPONSE_SIZE, 0);
              /*Future.future(fu -> updateAuditTable(routingContext));*/
              /* handleSuccessResponse(
              response, ResponseType.Created.getCode(), subsRequestHandler.result().toString());*/
              response
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                  .end(subsRequestHandler.result().toString());
            } else {
              LOGGER.error("Fail: Bad request");
              ResultModel rs = new ResultModel(subsRequestHandler.cause().getMessage(), response);
              response
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                  .setStatusCode(rs.getStatusCode())
                  .end(rs.toJson().toString());
            }
          });
    } else {
      LOGGER.error("Fail: Bad request");
      response.setStatusCode(400);
      response.putHeader(CONTENT_TYPE, APPLICATION_JSON);
      response.end(BAD_REQUEST_DATA);
    }
  }

  private void getSubscription(RoutingContext routingContext) {
    LOGGER.trace("Info: getSubscription method started");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String domain = request.getParam(USER_ID);
    String alias = request.getParam(JSON_ALIAS);
    String subsId = domain + "/" + alias;
    /*JsonObject requestJson = new JsonObject();
    String instanceId = request.getHeader(HEADER_HOST);
    requestJson.put(SUBSCRIPTION_ID, subsId);
    requestJson.put(JSON_INSTANCEID, instanceId);*/
    String subscriptionType = SubsType.STREAMING.type;
    /*
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    requestJson.put(JSON_CONSUMER, authInfo.getString(JSON_CONSUMER));
    requestJson.put("authInfo", authInfo);*/

    // TODO: Model-> userid(domain), alias,SUBSCRIPTION_ID,JSON_INSTANCEID,SUB_TYPE,JSON_CONSUMER,

    Future<GetResultModel> subsReq = subscriptionService.getSubscription(subsId, subscriptionType);
    subsReq.onComplete(
        subHandler -> {
          if (subHandler.succeeded()) {
            LOGGER.info("Success: Getting subscription");
            routingContext.data().put(RESPONSE_SIZE, 0);

            response.putHeader(CONTENT_TYPE, APPLICATION_JSON).end(subHandler.result().toString());
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(subHandler.result().constructSuccessResponse().toString());
            ResultModel rs = new ResultModel(subHandler.cause().getMessage(), response);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(rs.getStatusCode())
                .end(rs.toJson().toString());
          }
        });
  }

  private void getAllSubscriptionForUser(RoutingContext routingContext) {
    LOGGER.trace("Info: getAllSubscriptionForUser method started");
    HttpServerResponse response = routingContext.response();
    // TODO: Need to create AuthInfo class while authentication and authorization and take out
    // userid
    /*JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");*/
    /*authInfo.setUserId("fd47486b-3497-4248-ac1e-082e4d37a66c");*/
    JsonObject jsonObj = new JsonObject();
    /*jsonObj.put(USER_ID, authInfo.getString(USER_ID));*/
    Future<PostgresResultModel> subsReq =
        subscriptionService.getAllSubscriptionQueueForUser(
            /*authInfo.getUserId()*/ "fd47486b-3497-4248-ac1e-082e4d37a66c"); // TODO: pass userid
    subsReq.onComplete(
        subHandler -> {
          if (subHandler.succeeded()) {
            LOGGER.info("Success: Getting subscription queue" + subHandler.result());
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(subHandler.result().toJson().toString());
            /*handleSuccessResponse(
            response, ResponseType.Ok.getCode(), subHandler.result().toString());*/
          } else {
            LOGGER.error("Fail: Bad request");
            ResultModel rs = new ResultModel(subHandler.cause().getMessage(), response);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(rs.getStatusCode())
                .end(rs.toJson().toString());
          }
        });
  }

  private void deleteSubscription(RoutingContext routingContext) {
    // TODO: Make Models and Remove RoutingContext
    LOGGER.trace("Info: deleteSubscription() method started;");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String userid = request.getParam(USER_ID);
    String alias = request.getParam(JSON_ALIAS);
    String subsId = userid + "/" + alias;
    /*JsonObject requestJson = new JsonObject();
    String instanceId = request.getHeader(HEADER_HOST);
    requestJson.put(SUBSCRIPTION_ID, subsId);
    requestJson.put(JSON_INSTANCEID, instanceId);*/
    String subscriptionType = SubsType.STREAMING.type;
    /*requestJson.put(SUB_TYPE, subscriptionType);*/
    /*JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    requestJson.put(USER_ID, authInfo.getString(USER_ID));
    requestJson.put("authInfo", authInfo);*/
    String userId = "fd47486b-3497-4248-ac1e-082e4d37a66c"; // TODO://Take out from auth info

    Future<DeleteSubsResultModel> subsReq =
        subscriptionService.deleteSubscription(subsId, subscriptionType, userId);
    subsReq.onComplete(
        subHandler -> {
          if (subHandler.succeeded()) {
            routingContext.data().put(RESPONSE_SIZE, 0);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(subHandler.result().toJson().toString());
            /*Future.future(fu -> updateAuditTable(routingContext));
            handleSuccessResponse(
                    response, ResponseType.Ok.getCode(), subHandler.result().toString());*/
          } else {
            ResultModel rs = new ResultModel(subHandler.cause().getMessage(), response);
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(rs.getStatusCode())
                .end(rs.toJson().toString());
          }
        });
  }

  private void postSubscriptions(RoutingContext routingContext) {
    LOGGER.trace("Info: postSubscriptions() method started");
    HttpServerRequest request = routingContext.request();
    JsonObject requestBody = routingContext.body().asJsonObject();
    String instanceId = request.getHeader(HEADER_HOST);
    String subscriptionType = SubsType.STREAMING.type;
    /*requestBody.put(SUB_TYPE, subscriptionType);*/
    /*JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");*/

    JsonObject jsonObj = requestBody.copy();
    jsonObj.put(USER_ID, "authInfo.getString(USER_ID)");
    jsonObj.put(JSON_INSTANCEID, instanceId);

    HttpServerResponse response = routingContext.response();

    String entities = jsonObj.getJsonArray("entities").getString(0);

    String userId =
        "fd47486b-3497-4248-ac1e-082e4d37a66c"; // TODO: Change this to take userId from AuthInfo
    PostModelSubscription postModelSubscription =
        new PostModelSubscription(
            userId, subscriptionType, instanceId, entities, requestBody.getString("name"));

    // TODO: Model -> instanceid,substype,userid,entities(ri),name,role,did,drl

    subscriptionService
        .createSubscription(postModelSubscription)
        .onSuccess(
            subHandler -> {
              LOGGER.info("Success: Handle Subscription request;");
              routingContext.data().put(RESPONSE_SIZE, 0);
              response
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                  .end(subHandler.constructSuccessResponse().toString());
              /*Future.future(fu -> updateAuditTable(routingContext));
              handleSuccessResponse(
                  response, ResponseType.Created.getCode(), subHandler.result().toString());*/
            })
        .onFailure(
            failure -> {
              ResultModel rs = new ResultModel(failure.getMessage(), response);
              response
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                  .setStatusCode(rs.getStatusCode())
                  .end(rs.toJson().toString());
              // routingContext.fail(new DxRuntimeException(failure.getMessage()));
            });
  }

  private void proxyRequired() {
    postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);
    dataBrokerService = DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    cacheService = CacheService.createProxy(vertx, CACHE_SERVICE_ADDRESS);
    authenticator = AuthenticationService.createProxy(vertx, AUTH_SERVICE_ADDRESS);
  }
}
