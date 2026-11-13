package dev.swaraj.keyline;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class SmokeTest {
    public static void main(String[] args) throws Exception {
        OrderService s=new OrderService();
        var a=s.create("Sam",4500,"payment-1");
        assert s.create("Sam",4500,"payment-1").id().equals(a.id());
        try{s.create("Sam",999,"payment-1");throw new AssertionError("must conflict");}
        catch(OrderService.Conflict expected){}
        var confirmed=s.transition(a.id(),1,OrderService.Status.CONFIRMED);
        assert confirmed.version()==2;
        try{s.transition(a.id(),1,OrderService.Status.FULFILLED);throw new AssertionError("must version-conflict");}
        catch(OrderService.Conflict expected){}
        assert s.transition(a.id(),2,OrderService.Status.FULFILLED).status()==OrderService.Status.FULFILLED;
        try(Server server=new Server(0);HttpClient http=HttpClient.newHttpClient()) {
            server.start();String url="http://127.0.0.1:"+server.port();
            HttpRequest req=HttpRequest.newBuilder(URI.create(url+"/orders?customer=Chris&amountPence=1500"))
                .header("Idempotency-Key","http-test-1").POST(HttpRequest.BodyPublishers.noBody()).build();
            var resp=http.send(req,HttpResponse.BodyHandlers.ofString());
            assert resp.statusCode()==201:resp.body();
            assert resp.body().contains("\"status\":\"CREATED\"");
            var health=http.send(HttpRequest.newBuilder(URI.create(url+"/health")).build(),HttpResponse.BodyHandlers.ofString());
            assert health.statusCode()==200;
        }
        System.out.println("Keyline service and HTTP smoke tests passed");
    }
}
