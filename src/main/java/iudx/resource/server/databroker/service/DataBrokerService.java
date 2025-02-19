package iudx.resource.server.databroker.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.resource.server.apiserver.subscription.model.SubscriptionImplModel;
import iudx.resource.server.databroker.model.SubscriptionResponseModel;
import java.util.List;

@VertxGen
@ProxyGen
public interface DataBrokerService {
  @GenIgnore
  static DataBrokerService createProxy(Vertx vertx, String address) {
    return new DataBrokerServiceVertxEBProxy(vertx, address);
  }
  Future<SubscriptionResponseModel> registerStreamingSubscription(SubscriptionImplModel subscriptionImplModel);

  Future<JsonObject> registerAdaptor(JsonObject request, String vhost);

  Future<JsonObject> deleteAdaptor(JsonObject request, String vhost);

  Future<JsonObject> listAdaptor(JsonObject request, String vhost);

  Future<JsonObject> updateStreamingSubscription(JsonObject request);

  Future<List<String>> appendStreamingSubscription(SubscriptionImplModel subscriptionImplModel, String subId);

  Future<Void> deleteStreamingSubscription(String queueName, String userid);

  Future<List<String>> listStreamingSubscription(String subscriptionID);

  Future<JsonObject> publishFromAdaptor(JsonArray request, String vhost);

  Future<JsonObject> resetPassword(String userId);

  Future<Void> publishMessage(JsonObject body, String toExchange, String routingKey);
}
