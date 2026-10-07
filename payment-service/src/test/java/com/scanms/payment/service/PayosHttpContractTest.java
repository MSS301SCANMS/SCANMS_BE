package com.scanms.payment.service;

import com.scanms.payment.exception.AppException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PayosHttpContractTest {
    @Test void numberedObjectPayoutResponseIsReconciledAndUnknownShapeFailsClosed() throws Exception {
        AtomicReference<String> response=new AtomicReference<>("{\"code\":\"00\",\"data\":{\"payouts\":{\"0\":{\"id\":\"payout-id\",\"referenceId\":\"withdrawal-id\"}}}}");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/payouts",exchange -> {
            if(!"referenceId=withdrawal-id".equals(exchange.getRequestURI().getQuery())) throw new IllegalStateException("Wrong reconciliation reference");
            byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length); try(var body=exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var provider=new PayosProvider(); ReflectionTestUtils.setField(provider,"baseUrl","http://127.0.0.1:"+server.getAddress().getPort());
            ReflectionTestUtils.setField(provider,"payoutClient","client"); ReflectionTestUtils.setField(provider,"payoutApiKey","api"); ReflectionTestUtils.setField(provider,"payoutChecksum","checksum");
            assertEquals("payout-id",provider.findPayout("withdrawal-id").get("id"));
            response.set("{\"code\":\"00\",\"data\":{\"payouts\":{}}}"); assertEquals(Map.of(),provider.findPayout("withdrawal-id"));
            response.set("{\"code\":\"00\",\"data\":{\"unexpected\":[]}}"); assertThrows(AppException.class,() -> provider.findPayout("withdrawal-id"));
        } finally { server.stop(0); }
    }
}
