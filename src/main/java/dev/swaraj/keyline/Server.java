package dev.swaraj.keyline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** JDK-only API; keeps the demo small enough to inspect end to end. */
public final class Server implements AutoCloseable {
    private final HttpServer server;
    private final OrderService store = new OrderService();

    public Server(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("BIND_HOST", "127.0.0.1"),port),0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    public int port() {return server.getAddress().getPort();}
    public void start(){server.start();}
    @Override public void close(){server.stop(0);}

    private void handle(HttpExchange request) throws IOException {
        try {
            String path=request.getRequestURI().getPath();
            String method=request.getRequestMethod();
            if (path.equals("/health") && method.equals("GET")) {respond(request,200,"{\"status\":\"ok\"}");return;}
            if (path.equals("/orders") && method.equals("POST")) {
                Map<String,String> query=params(request.getRequestURI().getRawQuery());
                long amount=Long.parseLong(query.getOrDefault("amountPence","0"));
                OrderService.Order order=store.create(query.get("customer"),amount,request.getRequestHeaders().getFirst("Idempotency-Key"));
                respond(request,201,toJson(order));return;
            }
            String[] segments=path.split("/");
            if (segments.length>=3 && segments[1].equals("orders")) {
                String id=segments[2];
                if (segments.length==3 && method.equals("GET")) {respond(request,200,toJson(store.get(id)));return;}
                if (segments.length==4 && method.equals("POST") && (segments[3].equals("confirm") || segments[3].equals("fulfill"))) {
                    String header=request.getRequestHeaders().getFirst("If-Match");
                    if (header==null){respond(request,428,"{\"error\":\"If-Match version required\"}");return;}
                    long version=Long.parseLong(header.replace("\"",""));
                    OrderService.Status next=segments[3].equals("confirm")?OrderService.Status.CONFIRMED:OrderService.Status.FULFILLED;
                    respond(request,200,toJson(store.transition(id,version,next)));return;
                }
            }
            respond(request,404,"{\"error\":\"Unknown endpoint\"}");
        } catch (OrderService.NotFound ex) {respond(request,404,error(ex.getMessage()));}
          catch (OrderService.Conflict ex) {respond(request,409,error(ex.getMessage()));}
          catch (IllegalArgumentException ex) {respond(request,400,error(ex.getMessage()));}
        finally {request.close();}
    }

    private static Map<String,String> params(String query) {
        Map<String,String> result=new HashMap<>();
        if (query==null || query.isBlank())return result;
        for(String pair:query.split("&")){
            String[] parts=pair.split("=",2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                       URLDecoder.decode(parts.length==2?parts[1]:"",StandardCharsets.UTF_8));
        }
        return result;
    }
    private static String escape(String s) {
        StringBuilder b=new StringBuilder();
        for (char c:s.toCharArray()) {
            switch(c){case '"' -> b.append("\\\"");case '\\' -> b.append("\\\\");case '\n' -> b.append("\\n");case '\r' -> b.append("\\r");case '\t' -> b.append("\\t");default -> {if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}
        }
        return b.toString();
    }
    private static String error(String message){return "{\"error\":\""+escape(message)+"\"}";}
    private static String toJson(OrderService.Order x) {
        return "{\"id\":\""+x.id()+"\",\"customer\":\""+escape(x.customer())+"\",\"amountPence\":"+x.amountPence()+",\"status\":\""+x.status()+"\",\"version\":"+x.version()+"}";
    }
    private static void respond(HttpExchange r,int code,String body) throws IOException {
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        r.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        r.sendResponseHeaders(code,b.length);
        try(var out=r.getResponseBody()){out.write(b);}
    }
    public static void main(String[] args) throws IOException {
        int port=args.length>0?Integer.parseInt(args[0]):8080;
        Server server=new Server(port);server.start();System.out.println("Keyline listening on http://127.0.0.1:"+server.port());
    }
}
