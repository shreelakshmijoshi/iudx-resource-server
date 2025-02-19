package iudx.resource.server.apiserver.subscription.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.resource.server.common.ResponseUrn;
import java.util.List;

import static iudx.resource.server.databroker.util.Constants.ENTITIES;

public record GetResultModel(List<String> listString) {
    public JsonObject constructSuccessResponse() {
        return new JsonObject()
                .put("type", ResponseUrn.SUCCESS_URN.getUrn())
                .put("title", ResponseUrn.SUCCESS_URN.getMessage().toLowerCase())
                .put("results", new JsonArray().add(new JsonObject().put(ENTITIES, new JsonArray(listString))));
    }

}
