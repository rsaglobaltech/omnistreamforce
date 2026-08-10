package de.omnistreamforce.web;

import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.ecommerce.EcommerceGenerator;
import de.omnistreamforce.domain.healthcare.HealthcareGenerator;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.engine.GenerationConfig;
import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.JsonEventSerializer;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OmniStreamForceWeb {
    private static final Logger log = LoggerFactory.getLogger(OmniStreamForceWeb.class);
    private final int port;
    private MultiDomainEngine engine;
    private Javalin app;
    private java.util.Map<String, java.util.Set<io.javalin.websocket.WsContext>> liveViewers = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.Map<String, Long> lastSendTimes = new java.util.concurrent.ConcurrentHashMap<>();

    public OmniStreamForceWeb(int port) {
        this.port = port;
    }

    public void start() {
        // Initialize real engine with a Console Logger publisher
        engine = new MultiDomainEngine(new JsonEventSerializer(), new de.omnistreamforce.engine.EventPublisher() {
            @Override
            public void publish(de.omnistreamforce.core.Event event, String topic) {
                String evDomain = event.domain() != null ? event.domain().toLowerCase() : "";
                java.util.Set<io.javalin.websocket.WsContext> viewers = liveViewers.get(evDomain);
                if (viewers != null && !viewers.isEmpty()) {
                    long now = System.currentTimeMillis();
                    long last = lastSendTimes.getOrDefault(evDomain, 0L);
                    // Send max ~10 events per second to the browser to avoid freezing UI
                    if (now - last > 100) {
                        lastSendTimes.put(evDomain, now);
                        String json = new String(new JsonEventSerializer().serialize(event));
                        for (io.javalin.websocket.WsContext ctx : viewers) {
                            if (ctx.session.isOpen()) {
                                ctx.send(json);
                            }
                        }
                    }
                }
            }
            @Override
            public void close() {}
        });

        app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/static";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/static/index.html");
        });

        app.get("/api/health", ctx -> ctx.json("{\"status\":\"UP\"}"));
        
        app.get("/api/domains", ctx -> {
            ctx.json(java.util.List.of("Healthcare", "Ecommerce", "FastFood"));
        });

        // GET /api/streams/active
        app.get("/api/streams/active", ctx -> {
            if (engine != null) {
                ctx.json(engine.activeDomains());
            } else {
                ctx.json(java.util.List.of());
            }
        });

        // DELETE /api/streams
        app.delete("/api/streams", ctx -> {
            String domain = ctx.queryParam("domain");
            if (domain != null && engine != null) {
                if (engine.removeDomain(domain)) {
                    ctx.json("{\"message\":\"Stream stopped for " + domain + "\"}");
                } else {
                    ctx.status(404).json("{\"message\":\"Domain not active\"}");
                }
            } else {
                ctx.status(400).json("{\"message\":\"Domain parameter missing\"}");
            }
        });

        // POST /api/streams
        app.post("/api/streams", ctx -> {
            try {
                String domain = ctx.queryParam("domain");
                if (domain == null) domain = "Healthcare";
                
                int eps = Integer.parseInt(ctx.queryParamAsClass("eps", String.class).getOrDefault("100"));
                
                String targetTopic = ctx.queryParam("targetTopic");
                if (targetTopic == null || targetTopic.isEmpty()) targetTopic = domain.toLowerCase() + "-events";
                
                String errorTopic = ctx.queryParam("errorTopic");
                if (errorTopic == null || errorTopic.isEmpty()) errorTopic = domain.toLowerCase() + "-errors";
                
                double errorRate = 0.05;
                String errRateStr = ctx.queryParam("errorRate");
                if (errRateStr != null && !errRateStr.isEmpty()) {
                    errorRate = Double.parseDouble(errRateStr) / 100.0;
                }
                
                de.omnistreamforce.engine.PublishingMode pubMode = de.omnistreamforce.engine.PublishingMode.STEADY;
                String modeStr = ctx.queryParam("mode");
                if ("BURST".equalsIgnoreCase(modeStr)) pubMode = de.omnistreamforce.engine.PublishingMode.BURST;
                else if ("SPIKE".equalsIgnoreCase(modeStr)) pubMode = de.omnistreamforce.engine.PublishingMode.SPIKE;
                else if ("RAMP".equalsIgnoreCase(modeStr)) pubMode = de.omnistreamforce.engine.PublishingMode.RAMP;
                
                de.omnistreamforce.engine.KeyStrategy kStrategy = de.omnistreamforce.engine.KeyStrategy.RANDOM;
                String kStr = ctx.queryParam("keyStrategy");
                if ("entityId".equalsIgnoreCase(kStr)) kStrategy = de.omnistreamforce.engine.KeyStrategy.ENTITY_ID;
                else if ("roundRobin".equalsIgnoreCase(kStr)) kStrategy = de.omnistreamforce.engine.KeyStrategy.ROUND_ROBIN;

                DomainGenerator gen = switch (domain.toLowerCase()) {
                    case "ecommerce" -> new EcommerceGenerator();
                    case "fastfood" -> new FastFoodGenerator();
                    default -> new HealthcareGenerator();
                };
                
                GenerationConfig conf = new GenerationConfig(
                    domain, 
                    targetTopic, 
                    errorTopic, 
                    eps, 
                    errorRate, 
                    pubMode, 
                    60L, 
                    kStrategy, 
                    "id", 
                    1, 
                    (short)1
                );
                
                TopicMapping mapping = new TopicMapping(
                    domain, 
                    targetTopic, 
                    errorTopic, 
                    1, 
                    (short) 1, 
                    true
                );
                engine.addDomain(gen, conf, mapping);
                
                ctx.status(201).json("{\"message\":\"Stream started for " + domain + "\"}");
            } catch (Exception e) {
                log.error("Failed to start stream", e);
                ctx.status(400).result(e.getMessage());
            }
        });

        // POST /api/streams/{domain}/pause
        app.post("/api/streams/{domain}/pause", ctx -> {
            String domain = ctx.pathParam("domain");
            if (engine != null && engine.getEngine(domain) != null) {
                engine.getEngine(domain).pause();
                ctx.json("{\"message\":\"Paused\"}");
            } else {
                ctx.status(404).json("{\"error\":\"Not found\"}");
            }
        });

        // POST /api/streams/{domain}/resume
        app.post("/api/streams/{domain}/resume", ctx -> {
            String domain = ctx.pathParam("domain");
            if (engine != null && engine.getEngine(domain) != null) {
                engine.getEngine(domain).resume();
                ctx.json("{\"message\":\"Resumed\"}");
            } else {
                ctx.status(404).json("{\"error\":\"Not found\"}");
            }
        });

        // PUT /api/streams/{domain}/eps
        app.put("/api/streams/{domain}/eps", ctx -> {
            String domain = ctx.pathParam("domain");
            int eps = Integer.parseInt(ctx.queryParam("val"));
            if (engine != null && engine.getEngine(domain) != null) {
                engine.getEngine(domain).setEventsPerSecond(eps);
                ctx.json("{\"message\":\"EPS updated\"}");
            } else {
                ctx.status(404).json("{\"error\":\"Not found\"}");
            }
        });

        // WebSocket for Live Data Viewer
        app.ws("/ws/streams/{domain}", ws -> {
            ws.onConnect(ctx -> {
                String domain = ctx.pathParam("domain").toLowerCase();
                liveViewers.computeIfAbsent(domain, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(ctx);
                ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(10));
            });
            ws.onClose(ctx -> {
                String domain = ctx.pathParam("domain").toLowerCase();
                java.util.Set<io.javalin.websocket.WsContext> viewers = liveViewers.get(domain);
                if (viewers != null) viewers.remove(ctx);
            });
        });

        java.util.Set<io.javalin.websocket.WsContext> clients = new java.util.concurrent.ConcurrentHashMap<io.javalin.websocket.WsContext, Boolean>().newKeySet();

        app.ws("/ws/dashboard", ws -> {
            ws.onConnect(ctx -> {
                log.info("Dashboard connected: {}", ctx.sessionId());
                clients.add(ctx);
            });
            ws.onClose(ctx -> {
                log.info("Dashboard disconnected: {}", ctx.sessionId());
                clients.remove(ctx);
            });
        });

        app.start(port);
        engine.startAll();
        log.info("OmniStreamForce Web Interface started on http://localhost:{}", port);

        Thread broadcaster = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                    if (!clients.isEmpty()) {
                        // Extract real snapshot and convert to JSON
                        var snap = engine.aggregateStats();
                        
                        double globalEps = 0.0;
                        double globalLatency = 0.0;
                        int domainsActive = snap.perDomain().size();
                        if (domainsActive > 0) {
                            for (var ds : snap.perDomain().values()) {
                                globalEps += ds.eventsPerSecond();
                                globalLatency += ds.avgLatencyMs();
                            }
                            globalLatency /= domainsActive;
                        }

                        String json = String.format(java.util.Locale.US,
                            "{\"totalEvents\": %d, \"eps\": %.1f, \"totalErrors\": %d, \"latency\": %.1f}",
                            snap.totalEvents(),
                            globalEps,
                            snap.totalErrors(),
                            globalLatency
                        );
                        for (io.javalin.websocket.WsContext ctx : clients) {
                            if (ctx.session.isOpen()) {
                                ctx.send(json);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        broadcaster.setDaemon(true);
        broadcaster.start();
    }

    public void stop() {
        if (engine != null) engine.stopAll();
        if (app != null) app.stop();
    }
}
