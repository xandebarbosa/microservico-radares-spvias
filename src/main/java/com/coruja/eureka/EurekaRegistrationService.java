package com.coruja.eureka;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EurekaRegistrationService {
    private static final Logger LOG = Logger.getLogger(EurekaRegistrationService.class);

    @RestClient
    EurekaRestClient eurekaClient;

    @ConfigProperty(name = "quarkus.application.name")
    String appName;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "eureka.instance.hostname", defaultValue = "localhost")
    String hostname;

    private String instanceId;
    private String payload;

    void onStart(@Observes StartupEvent ev) {
        String appNameUpper = appName.toUpperCase();

        // Pega o IP real do container na rede do Docker
        String ipAddress = "127.0.0.1";
        try {
            ipAddress = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            LOG.warn("Não foi possível determinar o IP local, usando localhost");
        }

        // O Eureka usa o instanceId para diferenciar instâncias
        this.instanceId = hostname + ":" + appNameUpper + ":" + port;

        // Monta o JSON forçando o ipAddr e dizendo ao Eureka para preferir o IP (preferIpAddress: true)
        this.payload = """
            {
               "instance": {
                  "instanceId": "%s",
                  "hostName": "%s",
                  "app": "%s",
                  "ipAddr": "%s",
                  "status": "UP",
                  "port": {"$": %d, "@enabled": "true"},
                  "vipAddress": "%s",
                  "dataCenterInfo": {
                     "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                     "name": "MyOwn"
                  },
                  "metadata": {
                     "management.port": "%d"
                  }
               }
            }
            """.formatted(instanceId, ipAddress, appNameUpper, ipAddress, port, appNameUpper, port);

        registrar();
    }

    private void registrar() {
        try {
            Response response = eurekaClient.register(appName.toUpperCase(), payload);

            // 🔹 VALIDAR O STATUS DO REGISTO TAMBÉM
            if (response.getStatus() == 204 || response.getStatus() == 200) {
                LOG.infof("✅ Microsserviço Rondon registrado no Eureka Server [%s]", instanceId);
            } else {
                LOG.warnf("⚠️ Falha ao registrar Microsserviço Rondon (Status %d)", response.getStatus());
            }
        } catch (Exception e) {
            LOG.warnf("⚠️ Erro de rede ao registrar no Eureka (Tentará novamente no heartbeat): %s", e.getMessage());
        }
    }

    // Mantém o BFF sabendo que o container do Quarkus continua vivo
    @Scheduled(every = "30s")
    void heartbeat() {
        try {
            Response response = eurekaClient.heartbeat(appName.toUpperCase(), instanceId);

            // 🔹 O SEGREDO ESTÁ AQUI: Capturar o 404 diretamente pelo Status Code
            if (response.getStatus() == 404) {
                LOG.warn("⚠️ Eureka não reconheceu a instância (404). Forçando novo registro...");
                registrar();
            }

        } catch (Exception e) {
            // Cai aqui se o Eureka estiver com o container desligado (Connection Refused).
            // O scheduler não morre, ele tenta de novo em 30 segundos!
            LOG.debugf("Falha de conexão no heartbeat: %s", e.getMessage());
        }
    }
}
